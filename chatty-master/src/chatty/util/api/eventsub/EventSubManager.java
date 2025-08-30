
package chatty.util.api.eventsub;

import chatty.Logging;
import chatty.gui.components.eventlog.EventLog;
import chatty.util.Debugging;
import chatty.util.StringUtil;
import chatty.util.api.TwitchApi;
import chatty.util.jws.JWSClient;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 *
 * @author tduva
 */
public class EventSubManager {
    
    private final static Logger LOGGER = Logger.getLogger(EventSubManager.class.getName());
    
    private final TwitchApi api;
    
    private final Connections c;
    private final EventSubListener listener;

    /**
     * Storage of user ids for easier lookup to turn an id into a channel name.
     */
    private final Map<String, String> userIds = Collections.synchronizedMap(new HashMap<String, String>());
    
    private final Set<Topic> pendingTopics = Collections.synchronizedSet(new HashSet<Topic>());
    
    private volatile String localUserId;
    private volatile String localUsername;
    
    private final RaidTopicManager raidTopicManager;
    
    private boolean loggedTopicsYet;
    
    public EventSubManager(String server, final EventSubListener listener, TwitchApi api) {
        this.api = api;
        this.listener = listener;
        this.c = init(server);
        this.raidTopicManager = new RaidTopicManager(this, api);
    }
    
    private Connections init(String server) {
        try {
            return new Connections(new URI(server), new ConnectionsMessageHandler() {
                
                @Override
                public void handleReceived(int id, String received, Message message) {
                    listener.info(String.format(Locale.ROOT, "%s--> %s",
                            c.getConnectionPrefix(id),
                            StringUtil.trim(received)));
                    if (message != null && message.data != null) {
                        listener.messageReceived(message);
                    }
                }
                
                @Override
                public void handleSent(int id, String sent) {
                    listener.info(String.format(Locale.ROOT, "[%d(%d)/%d(%d)]<-- %s",
                            id,
                            c.getNumTopics(id),
                            c.getNumConnections(),
                            c.getNumTopics(),
                            sent));
                }
                
                @Override
                public void handleDisconnect(int id) {
                    
                }
                
                @Override
                public void handleConnect(int id, JWSClient c) {
                    
                }

                @Override
                public void handleRegisterError(int responseCode) {
                    if (responseCode == 429) {
                        EventLog.addSystemEvent("session.eventsub.limit",
                                                "EventSub error",
                                                "EventSub has reached an unexpected request limit, "
                                                + "which will cause some features to not fully work, "
                                                + "such as displaying Mod Actions, AutoMod and others. "
                                                + "Note that API limits are per Twitch account/Client ID, "
                                                + "so if you have other Chatty instances running it may affect limits on this one.");
                        if (!loggedTopicsYet) {
                            // Only log on error once per session
                            logActiveTopics();
                            loggedTopicsYet = true;
                        }
                    }
                }
                
            }, api);
        }
        catch (URISyntaxException ex) {
            LOGGER.warning("[EventSub] Invalid server: "+server);
            return null;
        }
    }
    
    /**
     * Only for testing. May cause issues.
     * 
     * @param input 
     */
    public void simulate(String input) {
        c.simulate(input);
    }
    
    public void logActiveTopics() {
        api.getEventSubSubs(s -> {
            listener.info(String.format("[Current topics according to API]\n%s total\nPer sesssion: %s\n%s",
                                              s.total, s.getCountBySession(), s.toString()));
        });
        listener.info(String.format("[Current topics according to Chatty]%s\n",
                                          getTopics()));
    }
    
    //==========================
    // Topics / various
    //==========================
    
    /**
     * Set the username of this Chatty user, which is used for listening to the
     * correct mod log topic (which requires the mod user id).
     * 
     * @param username 
     */
    public void setLocalUsername(String username) {
        if (localUsername == null || !localUsername.equals(username)) {
            this.localUsername = username;
            this.localUserId = null;
            requestUserId(username);
        }
    }
    
    public void listenRaid(String username) {
        raidTopicManager.listen(username, username.equals(localUsername));
    }
    
    public void unlistenRaid(String username) {
        raidTopicManager.unlisten(username);
    }
    
    protected void listenRaidInternal(String username) {
        addTopic(new Raid(username));
    }
    
    protected void unlistenRaidInternal(String username) {
        removeTopic(new Raid(username));
    }
    
    public void listenPoll(String username) {
        addTopic(new PollStart(username));
        addTopic(new PollEnd(username));
    }
    
    public void unlistenPoll(String username) {
        removeTopic(new PollStart(username));
        removeTopic(new PollEnd(username));
    }
    
    public void listenShield(String username) {
        addTopic(new ShieldBegin(username));
        addTopic(new ShieldEnd(username));
    }
    
    public void unlistenShield(String username) {
        removeTopic(new ShieldBegin(username));
        removeTopic(new ShieldEnd(username));
    }
    
    public void listenShoutouts(String username) {
        addTopic(new ShoutoutCreate(username));
    }
    
    public void unlistenShoutouts(String username) {
        removeTopic(new ShoutoutCreate(username));
    }
    
    public void listenModActions(String username) {
        addTopic(new ChannelModerate(username));
    }
    
    public void unlistenModActions(String username) {
        removeTopic(new ChannelModerate(username));
    }
    
    public void listenAutoMod(String username) {
        addTopic(new AutoModMessageHold(username));
        addTopic(new AutoModMessageUpdate(username));
    }
    
    public void unlistenAutoMod(String username) {
        removeTopic(new AutoModMessageHold(username));
        removeTopic(new AutoModMessageUpdate(username));
    }
    
    public void listenSuspiciousMessage(String username) {
        addTopic(new SuspiciousMessage(username));
        addTopic(new SuspiciousUpdate(username));
    }
    
    public void unlistenSuspicousMessage(String username) {
        removeTopic(new SuspiciousMessage(username));
        removeTopic(new SuspiciousUpdate(username));
    }
    
    public void listenWarnings(String username) {
        addTopic(new WarningAcknowledge(username));
    }
    
    public void unlistenWarnings(String username) {
        removeTopic(new WarningAcknowledge(username));
    }
    
    public void listenMessageHeld(String username) {
        /**
         * Currently IRC sends a message about a message held, so don't need
         * this, just not for apporved/denied. For private terms (even when
         * triggered by mods) EventSub seems to be silent both for the
         * broadcaster and the affected user (but at message from IRC is still
         * received for the message being held).
         */
//        addTopic(new UserMessageHeld(username));
        addTopic(new UserMessageUpdate(username));
    }
    
    public void unlistenMessageHeld(String username) {
//        removeTopic(new UserMessageHeld(username));
        removeTopic(new UserMessageUpdate(username));
    }
    
    public void listenPoints(String username) {
        addTopic(new ChannelPointsRedemption(username));
        addTopic(new ChannelPointsRedemptionUpdate(username));
    }
    
    public void unlistenPoints(String username) {
        removeTopic(new ChannelPointsRedemption(username));
        removeTopic(new ChannelPointsRedemptionUpdate(username));
    }
    
    /**
     * Adds the given topic to be requested. If the topic is already being
     * listened to, it will still be added, processed and tried to listen to
     * again (which will simply do nothing).
     * 
     * If the topic doesn't require any additional data, it will be listened to
     * immediately, otherwise it will request any missing data and stay pending
     * until the data is available. This currently only refers to resolving a
     * user/stream name to an id.
     * 
     * @param topic 
     */
    private void addTopic(Topic topic) {
        if (c == null) {
            return;
        }
        boolean added = pendingTopics.add(topic);
        if (added) {
            checkPendingTopics();
            if (!topic.isReady()) {
                topic.request();
            }
        }
    }
    
    private void removeTopic(Topic topic) {
        if (c == null) {
            return;
        }
        pendingTopics.remove(topic);
        if (topic.isReady()) {
            // Only if all required data is already there is it likely that
            // the topic already added
            c.removeTopic(topic);
        }
    }
    
    private void checkPendingTopics() {
        Set<Topic> readyTopics = new HashSet<>();
        synchronized(pendingTopics) {
            Iterator<Topic> it = pendingTopics.iterator();
            while (it.hasNext()) {
                Topic topic = it.next();
                if (topic.isReady()) {
                    readyTopics.add(topic);
                    it.remove();
                }
            }
        }
        Debugging.println("es", "[EventSub] Send: %s, Pending: %s",
                readyTopics, pendingTopics);
        for (Topic topic : readyTopics) {
            boolean success = c.addTopic(topic);
            if (!success) {
                // Prefixed with "session" so it can create a notification again
                // next session, even if marked as read
                EventLog.addSystemEvent("session.eventsub.maxtopics",
                        "EventSub max channels reached",
                        "The amount of channels you have joined (especially "
                        + "where you are a moderator) will cause some features "
                                + "to not fully work, such as displaying Mod "
                                + "Actions, AutoMod and others.");
            }
        }
    }
    
    /**
     * Get the user id for the given username, or wait until it has been
     * requested and act on it then.
     * 
     * @param username A valid Twitch username
     * @return The user id, or -1 if user id still has to be requested
     */
    private void requestUserId(String username) {
        api.waitForUserId(r -> {
            EventSubManager.this.setUserId(username, r.getId(username));
        }, username);
    }
    
    private String getUserId(String username) {
        if (StringUtil.isNullOrEmpty(username)) {
            return null;
        }
        synchronized(userIds) {
            for (Map.Entry<String, String> entry : userIds.entrySet()) {
                if (entry.getValue().equals(username)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /**
     * The given userId is now known, so act on it if necessary.
     * 
     * @param username
     * @param userId 
     */
    private void setUserId(String username, String userId) {
        userIds.put(userId, username);
        
        // If local userId hasn't been set yet, request everything now
        if (localUserId == null && username.equals(localUsername)) {
            localUserId = userId;
        }
        
        checkPendingTopics();
    }
    
    //==========================
    // Connection stuff
    //==========================
    
    /**
     * Get a textual representation of the connection status for output to the
     * user.
     *
     * @return
     */
    public String getStatus() {
        if (c == null) {
            return "Not initialized";
        }
        return c.getStatus();
    }
    
    public void disconnect() {
        if (c != null) {
            c.disconnect();
        }
    }
    
    public boolean isConnected() {
        return c != null && c.isConnected();
    }
    
    public void reconnect() {
        if (c != null) {
            c.reconnect();
        }
    }
    
    public void tokenUpdated() {
        c.tokenUpdated();
    }
    
    public String getTopics() {
        Map<String, List<Topic>> topics = c.getTopics();
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, List<Topic>> entry : topics.entrySet()) {
            b.append("\n[").append(entry.getKey()).append("]\n");
            List<StreamTopic> topics2 = new ArrayList<>();
            entry.getValue().forEach(t -> {
                if (t instanceof StreamTopic) {
                    topics2.add((StreamTopic) t);
                }
                else {
                    LOGGER.warning("Unexpected topic: "+t);
                }
            });
            Collections.sort(topics2, (o1, o2) -> {
                         return o1.stream.compareTo(o2.stream);
                     });
            int count = 0;
            String currentStream = null;
            for (StreamTopic t : topics2) {
                if (currentStream != null
                        && !t.stream.equals(currentStream)) {
                    b.append("\n---\n");
                    count = 0;
                }
                count++;
                currentStream = t.stream;
                b.append(count).append(".");
                b.append(t).append("(").append(t.getCost()).append(")\n");
            }
        }
        b.append("\n");
        b.append(c.getDebugText());
        return b.toString();
    }
    
    //==========================
    // Topic Classes
    //==========================
    /**
     * A topic that requires a stream id, based on a stream name.
     */
    private abstract class StreamTopic extends Topic {
        
        protected final String stream;
        
        StreamTopic(String stream) {
            this.stream = stream;
        }
        
        @Override
        public void request() {
            requestUserId(stream);
        }
        
        @Override
        public boolean isReady() {
            return getUserId(stream) != null;
        }
        
        @Override
        public int hashCode() {
            int hash = 5;
            hash = 71 * hash + Objects.hashCode(this.stream);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final StreamTopic other = (StreamTopic) obj;
            if (!Objects.equals(this.stream, other.stream)) {
                return false;
            }
            return true;
        }
        
        @Override
        public String toString() {
            return getClass().getSimpleName()+" "+stream;
        }
        
    }
    
    private class Raid extends StreamTopic {

        Raid(String stream) {
            super(stream);
        }
        
        @Override
        public String make(String sessionId) {
            String userId = getUserId(stream);
            if (userId != null) {
                Map<String, String> condition = new HashMap<>();
                condition.put("from_broadcaster_user_id", userId);
                return Helper.makeAddEventSubBody("channel.raid", condition, sessionId, "1");
            }
            return null;
        }

        @Override
        public int getExpectedCost() {
            return 1;
        }
        
        @Override
        public Topic copy() {
            return new Raid(stream);
        }

    }
    
    private class PollStart extends StreamTopic {

        PollStart(String stream) {
            super(stream);
        }
        
        @Override
        public String make(String sessionId) {
            String userId = getUserId(stream);
            if (userId != null) {
                Map<String, String> condition = new HashMap<>();
                condition.put("broadcaster_user_id", userId);
                return Helper.makeAddEventSubBody("channel.poll.begin", condition, sessionId, "1");
            }
            return null;
        }

        @Override
        public int getExpectedCost() {
            return 0;
        }
        
        @Override
        public Topic copy() {
            return new PollStart(stream);
        }

    }
    
    private class PollEnd extends StreamTopic {

        PollEnd(String stream) {
            super(stream);
        }
        
        @Override
        public String make(String sessionId) {
            String userId = getUserId(stream);
            if (userId != null) {
                Map<String, String> condition = new HashMap<>();
                condition.put("broadcaster_user_id", userId);
                return Helper.makeAddEventSubBody("channel.poll.end", condition, sessionId, "1");
            }
            return null;
        }

        @Override
        public int getExpectedCost() {
            return 0;
        }
        
        @Override
        public Topic copy() {
            return new PollEnd(stream);
        }

    }
    
    private class ShieldBegin extends StreamTopic {

        ShieldBegin(String stream) {
            super(stream);
        }
        
        @Override
        public String make(String sessionId) {
            String userId = getUserId(stream);
            if (userId != null && localUserId != null) {
                Map<String, String> condition = new HashMap<>();
                condition.put("broadcaster_user_id", userId);
                condition.put("moderator_user_id", localUserId);
                return Helper.makeAddEventSubBody("channel.shield_mode.begin", condition, sessionId, "1");
            }
            return null;
        }

        @Override
        public int getExpectedCost() {
            return 0;
        }
        
        @Override
        public Topic copy() {
            return new ShieldBegin(stream);
        }

    }
    
    private class ShieldEnd extends StreamTopic {

        ShieldEnd(String stream) {
            super(stream);
        }
        
        @Override
        public String make(String sessionId) {
            String userId = getUserId(stream);
            if (userId != null && localUserId != null) {
                Map<String, String> condition = new HashMap<>();
                condition.put("broadcaster_user_id", userId);
                condition.put("moderator_user_id", localUserId);
                return Helper.makeAddEventSubBody("channel.shield_mode.end", condition, sessionId, "1");
            }
            return null;
        }

        @Override
        public int getExpectedCost() {
            return 0;
        }
        
        @Override
        public Topic copy() {
            return new ShieldEnd(stream);
        }

    }
    
    private class ShoutoutCreate extends StreamTopic {

        ShoutoutCreate(String stream) {
            super(stream);
        }
        
        @Override
        public String make(String sessionId) {
            String userId = getUserId(stream);
            if (userId != null && localUserId != null) {
                Map<String, String> condition = new HashMap<>();
                condition.put("broadcaster_user_id", userId);
                condition.put("moderator_user_id", localUserId);
                return Helper.makeAddEventSubBody("channel.shoutout.create", condition, sessionId, "1");
            }
            return null;
        }

        @Override
        public int getExpectedCost() {
            return 0;
        }
        
        @Override
        public Topic copy() {
            return new ShoutoutCreate(stream);
        }

    }
    
    private class ChannelModerate extends StreamTopic {

        ChannelModerate(String stream) {
            super(stream);
        }
        
        @Override
        public String make(String sessionId) {
            String userId = getUserId(stream);
            if (userId != null && localUserId != null) {
                Map<String, String> condition = new HashMap<>();
                condition.put("broadcaster_user_id", userId);
                condition.put("moderator_user_id", localUserId);
                return Helper.makeAddEventSubBody("channel.moderate", condition, sessionId, "2");
            }
            return null;
        }

        @Override
        public int getExpectedCost() {
            return 0;
        }
        
        @Override
        public Topic copy() {
            return new ChannelModerate(stream);
        }

    }
    
    private class AutoModMessageHold extends StreamTopic {

        AutoModMessageHold(String stream) {
            super(stream);
        }
        
        @Override
        public String make(String sessionId) {
            String userId = getUserId(stream);
            if (userId != null && localUserId != null) {
                Map<String, String> condition = new HashMap<>();
                condition.put("broadcaster_user_id", userId);
                condition.put("moderator_user_id", localUserId);
                return Helper.makeAddEventSubBody("automod.message.hold", condition, sessionId, "2");
            }
            return null;
        }

        @Override
        public int getExpectedCost() {
            return 0;
        }
        
        @Override
        public Topic copy() {
            return new AutoModMessageHold(stream);
        }

    }
    
    private class AutoModMessageUpdate extends StreamTopic {

        AutoModMessageUpdate(String stream) {
            super(stream);
        }
        
        @Override
        public String make(String sessionId) {
            String userId = getUserId(stream);
            if (userId != null && localUserId != null) {
                Map<String, String> condition = new HashMap<>();
                condition.put("broadcaster_user_id", userId);
                condition.put("moderator_user_id", localUserId);
                return Helper.makeAddEventSubBody("automod.message.update", condition, sessionId, "2");
            }
            return null;
        }

        @Override
        public int getExpectedCost() {
            return 0;
        }
        
        @Override
        public Topic copy() {
            return new AutoModMessageUpdate(stream);
        }

    }
    
    private class SuspiciousMessage extends StreamTopic {

        SuspiciousMessage(String stream) {
            super(stream);
        }
        
        @Override
        public String make(String sessionId) {
            String userId = getUserId(stream);
            if (userId != null && localUserId != null) {
                Map<String, String> condition = new HashMap<>();
                condition.put("broadcaster_user_id", userId);
                condition.put("moderator_user_id", localUserId);
                return Helper.makeAddEventSubBody("channel.suspicious_user.message", condition, sessionId, "1");
            }
            return null;
        }

        @Override
        public int getExpectedCost() {
            return 0;
        }
        
        @Override
        public Topic copy() {
            return new SuspiciousMessage(stream);
        }

    }
    
    private class SuspiciousUpdate extends StreamTopic {

        SuspiciousUpdate(String stream) {
            super(stream);
        }
        
        @Override
        public String make(String sessionId) {
            String userId = getUserId(stream);
            if (userId != null && localUserId != null) {
                Map<String, String> condition = new HashMap<>();
                condition.put("broadcaster_user_id", userId);
                condition.put("moderator_user_id", localUserId);
                return Helper.makeAddEventSubBody("channel.suspicious_user.update", condition, sessionId, "1");
            }
            return null;
        }

        @Override
        public int getExpectedCost() {
            return 0;
        }
        
        @Override
        public Topic copy() {
            return new SuspiciousUpdate(stream);
        }

    }
    
    private class WarningAcknowledge extends StreamTopic {

        WarningAcknowledge(String stream) {
            super(stream);
        }
        
        @Override
        public String make(String sessionId) {
            String userId = getUserId(stream);
            if (userId != null && localUserId != null) {
                Map<String, String> condition = new HashMap<>();
                condition.put("broadcaster_user_id", userId);
                condition.put("moderator_user_id", localUserId);
                return Helper.makeAddEventSubBody("channel.warning.acknowledge", condition, sessionId, "1");
            }
            return null;
        }

        @Override
        public int getExpectedCost() {
            return 0;
        }
        
        @Override
        public Topic copy() {
            return new WarningAcknowledge(stream);
        }

    }
    
    private class UserMessageHeld extends StreamTopic {

        UserMessageHeld(String stream) {
            super(stream);
        }
        
        @Override
        public String make(String sessionId) {
            String userId = getUserId(stream);
            if (userId != null && localUserId != null) {
                Map<String, String> condition = new HashMap<>();
                condition.put("broadcaster_user_id", userId);
                condition.put("user_id", localUserId);
                return Helper.makeAddEventSubBody("channel.chat.user_message_hold", condition, sessionId, "1");
            }
            return null;
        }

        @Override
        public int getExpectedCost() {
            return 0;
        }
        
        @Override
        public Topic copy() {
            return new UserMessageHeld(stream);
        }

    }
    
    private class UserMessageUpdate extends StreamTopic {

        UserMessageUpdate(String stream) {
            super(stream);
        }
        
        @Override
        public String make(String sessionId) {
            String userId = getUserId(stream);
            if (userId != null && localUserId != null) {
                Map<String, String> condition = new HashMap<>();
                condition.put("broadcaster_user_id", userId);
                condition.put("user_id", localUserId);
                return Helper.makeAddEventSubBody("channel.chat.user_message_update", condition, sessionId, "1");
            }
            return null;
        }

        @Override
        public int getExpectedCost() {
            return 0;
        }
        
        @Override
        public Topic copy() {
            return new UserMessageUpdate(stream);
        }

    }
    
    private class ChannelPointsRedemption extends StreamTopic {

        ChannelPointsRedemption(String stream) {
            super(stream);
        }
        
        @Override
        public String make(String sessionId) {
            String userId = getUserId(stream);
            if (userId != null) {
                Map<String, String> condition = new HashMap<>();
                condition.put("broadcaster_user_id", userId);
                return Helper.makeAddEventSubBody("channel.channel_points_custom_reward_redemption.add", condition, sessionId, "1");
            }
            return null;
        }

        @Override
        public int getExpectedCost() {
            return 0;
        }
        
        @Override
        public Topic copy() {
            return new ChannelPointsRedemption(stream);
        }
        
    }

    private class ChannelPointsRedemptionUpdate extends StreamTopic {

        ChannelPointsRedemptionUpdate(String stream) {
            super(stream);
        }
        
        @Override
        public String make(String sessionId) {
            String userId = getUserId(stream);
            if (userId != null) {
                Map<String, String> condition = new HashMap<>();
                condition.put("broadcaster_user_id", userId);
                return Helper.makeAddEventSubBody("channel.channel_points_custom_reward_redemption.update", condition, sessionId, "1");
            }
            return null;
        }

        @Override
        public int getExpectedCost() {
            return 0;
        }
        
        @Override
        public Topic copy() {
            return new ChannelPointsRedemptionUpdate(stream);
        }
        
    }
    
}
