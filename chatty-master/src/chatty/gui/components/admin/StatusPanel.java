
package chatty.gui.components.admin;

import chatty.Helper;
import chatty.Room;
import chatty.gui.GuiUtil;
import chatty.gui.MainGui;
import static chatty.gui.components.admin.AdminDialog.SMALL_BUTTON_INSETS;
import static chatty.gui.components.admin.AdminDialog.hideableLabel;
import static chatty.gui.components.admin.AdminDialog.makeGbc;
import chatty.gui.components.menus.CommandActionEvent;
import chatty.gui.components.menus.CommandMenuItems;
import chatty.gui.components.menus.ContextMenu;
import chatty.gui.components.menus.TextSelectionMenu;
import chatty.lang.Language;
import chatty.util.DateTime;
import chatty.util.StringUtil;
import chatty.util.api.ChannelStatus;
import chatty.util.api.ChannelStatus.StreamTag;
import chatty.util.api.ResultManager;
import chatty.util.api.StreamCategory;
import chatty.util.api.StreamLabels;
import chatty.util.api.StreamLabels.StreamLabel;
import chatty.util.api.TwitchApi;
import chatty.util.commands.Parameters;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 *
 * @author tduva
 */
public class StatusPanel extends JPanel implements Scrollable {
    
    /**
     * Set to not updating the channel info after this time (re-enable buttons).
     * This is also done when channel info is received, so when it was set
     * successfully it will be set to not updating immediately. This basicially
     * is only for the case of error.
     */
    private static final int PUT_RESULT_DELAY = 5000;
    
    private final JTextArea status = new JTextArea();
    private final JTextField game = new JTextField(20);
    private final JTextArea streamTags = new JTextArea();
    private final JTextArea streamLabels = new JTextArea();
    private final JButton update = new JButton(Language.getString("admin.button.update"));
    private final JLabel updated = new JLabel("No info loaded");
    private final JLabel putResult = new JLabel("...");
    private final JButton selectGame = new JButton(Language.getString("admin.button.selectGame"));
    private final JButton removeGame = new JButton(Language.getString("admin.button.removeGame"));
    private final JButton selectTags = new JButton(Language.getString("admin.button.selectTags"));
    private final JButton removeTags = new JButton(Language.getString("admin.button.removeTags"));
    private final JButton selectLabels = new JButton(Language.getString("admin.button.selectLabels"));
    private final JButton removeLabels = new JButton(Language.getString("admin.button.removeLabels"));
    private final JButton reloadButton = new JButton(Language.getString("admin.button.reload"));
    private final JButton historyButton = new JButton(Language.getString("admin.button.presets"));
    private final JButton addToHistoryButton = new JButton(Language.getString("admin.button.fav"));
    private final SelectGameDialog selectGameDialog;
    private final SelectTagsDialog selectTagsDialog;
    private final StatusHistoryDialog statusHistoryDialog;
    
    private final AdminDialog parent;
    private final MainGui main;
    private final TwitchApi api;
    
    private String currentChannel;
    private boolean statusEdited;
    private final List<StreamTag> currentStreamTags = new ArrayList<>();
    private final List<StreamLabel> currentStreamLabels = new ArrayList<>();
    private StreamCategory currentStreamCategory = StreamCategory.EMPTY;
    private long infoLastLoaded;
    
    private final Map<String, CacheItem> cache = new HashMap<>();
    
    private boolean loading;
    private boolean loadingStatus;
    private String statusLoadError;
    private String statusPutResult;
    private long lastPutResult = -1;
    private ChannelStatus channelStatusToCheck;
    private boolean updateStatusAfterLoad = false;
    
    public StatusPanel(AdminDialog parent, MainGui main, TwitchApi api) {
        
        this.parent = parent;
        this.main = main;
        this.api = api;
        
        selectGameDialog = new SelectGameDialog(main, api);
        selectTagsDialog = new SelectTagsDialog(main, api);
        statusHistoryDialog = new StatusHistoryDialog(parent, main.getStatusHistory());
        
        GridBagConstraints gbc;

        setLayout(new GridBagLayout());
        
        JPanel presetPanel = new JPanel();
        presetPanel.setLayout(new GridBagLayout());

        historyButton.setMargin(SMALL_BUTTON_INSETS);
        historyButton.setToolTipText("Open status presets containing favorites "
                + "and status history");
        historyButton.setMnemonic(KeyEvent.VK_P);
        gbc = makeGbc(2, 0, 1, 1);
        gbc.insets = new Insets(5, 5, 5, -1);
        gbc.anchor = GridBagConstraints.EAST;
        presetPanel.add(historyButton, gbc);
        
        addToHistoryButton.setMargin(SMALL_BUTTON_INSETS);
        addToHistoryButton.setToolTipText("Add current status to favorites");
        addToHistoryButton.setMnemonic(KeyEvent.VK_F);
        gbc = makeGbc(3, 0, 1, 1);
        gbc.insets = new Insets(5, 0, 5, 5);
        gbc.anchor = GridBagConstraints.EAST;
        presetPanel.add(addToHistoryButton, gbc);

        updated.setHorizontalAlignment(JLabel.CENTER);
        gbc = makeGbc(1,0,1,1);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(5, 10, 5, 5);
        presetPanel.add(updated, gbc);
        
        reloadButton.setMargin(SMALL_BUTTON_INSETS);
        reloadButton.setIcon(new ImageIcon(AdminDialog.class.getResource("view-refresh.png")));
        reloadButton.setMnemonic(KeyEvent.VK_R);
        gbc = makeGbc(0,0,1,1);
        gbc.anchor = GridBagConstraints.EAST;
        presetPanel.add(reloadButton, gbc);
        
        gbc = makeGbc(0, 1, 3, 1);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0,0,0,0);
        add(presetPanel, gbc);
        
        
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setBorder(BorderFactory.createCompoundBorder(
                game.getBorder(), 
                BorderFactory.createEmptyBorder(2, 3, 3, 2)));
        status.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                statusEdited();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                statusEdited();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                statusEdited();
            }
        });
        GuiUtil.resetFocusTraversalKeys(status);
        status.getAccessibleContext().setAccessibleName(Language.getString("admin.input.title"));
        GuiUtil.installLengthLimitDocumentFilter(status, 500, false);
        gbc = makeGbc(0,2,3,1);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(status, gbc);
        
        game.getAccessibleContext().setAccessibleName(Language.getString("admin.input.game"));
        game.setEditable(false);
        gbc = makeGbc(0,3,1,1);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        add(game, gbc);
        
        selectGame.setMargin(SMALL_BUTTON_INSETS);
        selectGame.setMnemonic(KeyEvent.VK_G);
        gbc = makeGbc(1,3,1,1);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(selectGame, gbc);

        removeGame.setMargin(SMALL_BUTTON_INSETS);
        removeGame.getAccessibleContext().setAccessibleName(Language.getString("admin.button.removeGame2"));
        gbc = makeGbc(2,3,1,1);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(removeGame,gbc);
        
        streamTags.getAccessibleContext().setAccessibleName(Language.getString("admin.input.tags"));
        streamTags.setEditable(false);
        streamTags.setBackground(game.getBackground());
        streamTags.setBorder(game.getBorder());
        streamTags.setLineWrap(true);
        streamTags.setWrapStyleWord(true);
        gbc = makeGbc(0,4,1,1);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(streamTags, gbc);
        
        selectTags.setMargin(SMALL_BUTTON_INSETS);
        gbc = makeGbc(1,4,1,1);
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(selectTags, gbc);
        
        removeTags.setMargin(SMALL_BUTTON_INSETS);
        removeTags.getAccessibleContext().setAccessibleName(Language.getString("admin.button.removeTags2"));
        gbc = makeGbc(2,4,1,1);
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(removeTags, gbc);
        
        streamLabels.getAccessibleContext().setAccessibleName(Language.getString("admin.input.labels"));
        streamLabels.setEditable(false);
        streamLabels.setBackground(game.getBackground());
        streamLabels.setBorder(game.getBorder());
        streamLabels.setLineWrap(true);
        streamLabels.setWrapStyleWord(true);
        gbc = makeGbc(0,5,1,1);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(streamLabels, gbc);
        
        selectLabels.setMargin(SMALL_BUTTON_INSETS);
        gbc = makeGbc(1,5,1,1);
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(selectLabels, gbc);
        
        removeLabels.setMargin(SMALL_BUTTON_INSETS);
        removeLabels.getAccessibleContext().setAccessibleName(Language.getString("admin.button.removeLabels2"));
        gbc = makeGbc(2,5,1,1);
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(removeLabels, gbc);
        
        gbc = makeGbc(0,6,3,1);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        update.setMnemonic(KeyEvent.VK_U);
        update.setToolTipText(Language.getString("admin.button.update.tip"));
        add(update, gbc);
        
        gbc = makeGbc(0,7,3,1);
        add(putResult,gbc);
        
        ActionListener actionListener = new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if (e.getSource() == update) {
                    if (currentChannel != null && !currentChannel.isEmpty()) {
                        loadingStatus = true;
                        setLoading(true);
                        ChannelStatus putStatus = ChannelStatus.createPut(
                                currentChannel,
                                status.getText(),
                                currentStreamCategory,
                                currentStreamTags,
                                currentStreamLabels);
                        channelStatusToCheck = putStatus;
                        main.putChannelInfo(putStatus);
                        addCurrentToHistory();
                    }
                } else if (e.getSource() == reloadButton) {
                    getChannelInfo();
                } else if (e.getSource() == selectGame) {
                    selectGameDialog.setLocationRelativeTo(StatusPanel.this);
                    StreamCategory result = selectGameDialog.open(currentStreamCategory);
                    if (result != null) {
                        currentStreamCategory = result;
                        game.setText(result.name);
                        statusEdited();
                    }
                } else if (e.getSource() == removeGame) {
                    currentStreamCategory = StreamCategory.EMPTY;
                    game.setText("");
                    statusEdited();
                } else if (e.getSource() == selectTags) {
                    selectTagsDialog.setLocationRelativeTo(StatusPanel.this);
                    List<StreamTag> result = selectTagsDialog.open(currentStreamTags);
                    if (result != null) {
                        setTags(result);
                        statusEdited();
                    }
                } else if (e.getSource() == removeTags) {
                    setTags(null);
                    statusEdited();
                } else if (e.getSource() == selectLabels) {
                    SelectLabelsDialog dialog = new SelectLabelsDialog(main);
                    dialog.setLocationRelativeTo(StatusPanel.this);
                    List<StreamLabel> result = dialog.open(currentStreamLabels);
                    if (result != null) {
                        setLabels(result);
                        statusEdited();
                    }
                } else if (e.getSource() == removeLabels) {
                    setLabels(StreamLabels.copyAutoLabelsOnly(currentStreamLabels));
                } else if (e.getSource() == historyButton) {
                    statusHistoryDialog.setLocationRelativeTo(StatusPanel.this);
                    StatusHistoryEntry result = statusHistoryDialog.showDialog(currentStreamCategory);
                    if (result != null) {
                        // A null value means that value shouldn't be used to
                        // change the info, it would be empty otherwise
                        if (result.title != null) {
                            status.setText(result.title);
                        }
                        if (result.game != null) {
                            currentStreamCategory = result.game;
                            game.setText(result.game.name);
                        }
                        if (result.tags != null) {
                            setTags(result.tags);
                        }
                        if (result.labels != null) {
                            setLabels(result.labels);
                        }
                    }
                } else if (e.getSource() == addToHistoryButton) {
                    addCurrentToFavorites();
                }
            }
        };
        
        reloadButton.addActionListener(actionListener);
        selectGame.addActionListener(actionListener);
        removeGame.addActionListener(actionListener);
        selectTags.addActionListener(actionListener);
        removeTags.addActionListener(actionListener);
        selectLabels.addActionListener(actionListener);
        removeLabels.addActionListener(actionListener);
        historyButton.addActionListener(actionListener);
        addToHistoryButton.addActionListener(actionListener);
        update.addActionListener(actionListener);
        
        addContextMenu(this);
        addContextMenu(update);
        addContextMenu(game);
        addContextMenu(streamTags);
        TextSelectionMenu.install(status);
        
        /**
         * Update category ids and names if necessary. Category favorites get
         * updated in SelectGameDialog.
         */
        api.subscribe(ResultManager.Type.CATEGORY_RESULT, (ResultManager.CategoryResult) categories -> {
            SwingUtilities.invokeLater(() -> {
                if (categories != null) {
                    // Update status history
                    for (StreamCategory category : categories) {
                        main.getStatusHistory().updateCategory(category);
                    }
                    // Update currently selected category
                    if (!currentStreamCategory.hasId()) {
                        for (StreamCategory category : categories) {
                            if (currentStreamCategory.nameMatches(category)) {
                                currentStreamCategory = category;
                            }
                        }
                    }
                }
            });
        });
    }
    
    private void addContextMenu(JComponent comp) {
        comp.addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                openContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                openContextMenu(e);
            }
            
            private void openContextMenu(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    Parameters params = Parameters.create("");
                    params.put("title", status.getText());
                    params.put("game", game.getText());
                    params.put("tag-ids", StringUtil.join(currentStreamTags, ",", o -> {
                        return ((StreamTag) o).getName();
                    }));
                    params.put("tag-names", StringUtil.join(currentStreamTags, ",", o -> {
                        return ((StreamTag) o).getDisplayName();
                    }));
                    Room room = Room.createRegular(Helper.toChannel(currentChannel));
                    
                    ContextMenu m = new ContextMenu() {

                        @Override
                        public void actionPerformed(ActionEvent e) {
                            if (e instanceof CommandActionEvent) {
                                // Command Context Menu
                                CommandActionEvent c = (CommandActionEvent)e;
                                main.anonCustomCommand(room, c.getCommand(), params);
                                addCurrentToHistory();
                            }
                            // Dock item
                            parent.helper.menuAction(e);
                        }
                    };
                    CommandMenuItems.addCommands(CommandMenuItems.MenuType.ADMIN, m, params);
                    m.addSeparator();
                    parent.helper.addToContextMenu(m);
                    m.show(e.getComponent(), e.getPoint().x, e.getPoint().y);
                }
            }
        });
    }
    
    public void changeChannel(String channel) {
        if (channel.equals(currentChannel)) {
            return;
        }
        // Save previous data
        saveToCache();
        currentChannel = channel;
        // Load data from new channel (if available)
        if (loadFromCache()) {
            setLoading(false);
        }
        else {
            status.setText("");
            game.setText("");
            setTags(null);
            setLabels(null);

            // This will reset last loaded anyway
            getChannelInfo();
        }
        setPutResult("");
    }
    
    private void setTags(Collection<StreamTag> tags) {
        currentStreamTags.clear();
        if (tags == null) {
            streamTags.setText(null);
        } else {
            for (StreamTag t : tags) {
                if (t != null && t.isValid()) {
                    currentStreamTags.add(t);
                }
            }
            streamTags.setText(StringUtil.join(currentStreamTags, ", "));
        }
    }
    
    private void setLabels(Collection<StreamLabel> labels) {
        currentStreamLabels.clear();
        if (labels == null) {
            streamLabels.setText(null);
        } else {
            currentStreamLabels.addAll(labels);
            StringBuilder b = new StringBuilder();
            for (StreamLabel label : labels) {
                if (b.length() != 0) {
                    b.append(", ");
                }
                b.append(label.getId());
                if (!label.isEditable()) {
                    b.append(" (auto)");
                }
            }
            streamLabels.setText(b.toString());
        }
    }
    
    public void channelStatusReceived(ChannelStatus channelStatus, TwitchApi.RequestResultCode result) {
        if (channelStatus.channelLogin.equals(currentChannel)) {
            if (channelStatusToCheck != null
                    && channelStatusToCheck.channelLogin.equals(currentChannel)) {
                String difference = channelStatusToCheck.getStatusDifference(channelStatus);
                if (!difference.isEmpty()) {
                    JOptionPane.showMessageDialog(this,
                            String.format("Stream Status may not have been updated, "
                                    + "possibly due to an invalid title (e.g. swear words) or tags (setting no tags at all may not work). "
                                    + "You can use the reload button to load the current Status, overwriting what you changed.\n\n"
                                    + "Not updated: %s\n\n"
                                    + "[Current Status]\n"
                                    + "Title: '%s'\n"
                                    + "Category: '%s'\n"
                                    + "Tags: %s\n"
                                    + "Labels: %s", difference, channelStatus.title, channelStatus.category, channelStatus.tags, channelStatus.labels),
                            "Update failed", JOptionPane.WARNING_MESSAGE);
                }
                else {
                    setLabels(channelStatus.labels);
                }
            }
            channelStatusToCheck = null;
            if (result == TwitchApi.RequestResultCode.SUCCESS) {
                if (updateStatusAfterLoad) {
                    status.setText(channelStatus.title);
                    currentStreamCategory = channelStatus.category;
                    game.setText(channelStatus.category.name);
                    setTags(channelStatus.tags);
                    setLabels(channelStatus.labels);
                }
            }
            else {
                infoLastLoaded = -1;
                if (result == TwitchApi.RequestResultCode.NOT_FOUND) {
                    statusLoadError = "Channel not found";
                } else {
                    statusLoadError = "";
                }
            }
            updateStatusAfterLoad = false;
            loadingStatus = false;
            checkLoadingDone();
        }
    }

    /**
     * Sets the result text of a attempted status update, but doesn't set it
     * back to "not loading" state, which is done when the channel info is
     * returned (which is also contained in the response for this action)
     * 
     * @param result 
     */
    public void setPutResult(TwitchApi.RequestResultCode result, String error) {
        if (result == TwitchApi.RequestResultCode.SUCCESS) {
            statusPutResult = Language.getString("admin.infoUpdated");
            checkChannelStatus();
        } else {
            if (result == TwitchApi.RequestResultCode.ACCESS_DENIED) {
                statusPutResult = "Update: Access denied/Failed";
                int over = status.getText().length() - 140;
                if (over > 0) {
                    statusPutResult = "Update: Failed (Title "+over+" characters too long?)";
                }
                updated.setText("Error: Access denied");
            } else if (result == TwitchApi.RequestResultCode.FAILED) {
                String errorText = "Error: Unknown error";
                if (!StringUtil.isNullOrEmpty(error)) {
                    errorText = "Error: "+error;
                }
                statusPutResult = errorText;
                updated.setText(errorText);
            } else if (result == TwitchApi.RequestResultCode.NOT_FOUND) {
                statusPutResult = "Update: Channel not found.";
                updated.setText("Error: Channel not found.");
            } else if (result == TwitchApi.RequestResultCode.INVALID_STREAM_STATUS) {
                statusPutResult = "Update: Invalid title/game (possibly bad language)";
                updated.setText("Error: Invalid title/game");
            }
            // If error it's not updated anyway, so no need to check
            channelStatusToCheck = null;
        }
        lastPutResult = System.currentTimeMillis();
        loadingStatus = false;
        checkLoadingDone();
    }
    
    /**
     * Changes the text of the putResult label.
     * 
     * @param result 
     */
    protected void setPutResult(String result) {
        hideableLabel(putResult, result);
    }
    
    protected void dialogOpened() {
        setPutResult("");
    }
    
    /**
     * Request Channel Info from the API.
     */
    private void getChannelInfo() {
        loadingStatus = true;
        statusLoadError = null;
        updateStatusAfterLoad = true;
        
        setLoading(true);
        api.getChannelStatus(currentChannel);
    }
    
    private void checkChannelStatus() {
        if (channelStatusToCheck != null) {
            api.getChannelStatus(currentChannel);
        }
    }
    
    private void checkLoadingDone() {
        if (!loadingStatus) {
            statusEdited = false;
            updated.setText(Language.getString("admin.infoLoaded.now"));
            if (statusPutResult != null) {
                setPutResult(statusPutResult);
                statusPutResult = null;
            }
            if (statusLoadError != null) {
                infoLastLoaded = -1;
                String error = getError(statusLoadError);
                if (error.isEmpty()) {
                    error = "Unkonwn Error";
                }
                updated.setText("Loading failed: "+error);
                statusLoadError = null;
            } else {
                infoLastLoaded = System.currentTimeMillis();
            }
            setLoading(false);
        }
    }
    
    private static String getError(String message) {
        if (message != null) {
            if (!message.isEmpty()) {
                return message;
            }
        }
        return "";
    }
    
    /**
     * Set the dialog loading state, enabling or disabling controls.
     * 
     * @param loading 
     */
    private void setLoading(boolean loading) {
        if (loading) {
            updated.setText(Language.getString("admin.loading"));
            lastPutResult = -1;
        }
        update.setEnabled(!loading);
        selectGame.setEnabled(!loading);
        removeGame.setEnabled(!loading);
        selectTags.setEnabled(!loading);
        removeTags.setEnabled(!loading);
        selectLabels.setEnabled(!loading);
        removeLabels.setEnabled(!loading);
        reloadButton.setEnabled(!loading);
        historyButton.setEnabled(!loading);
        addToHistoryButton.setEnabled(!loading);
        this.loading = loading;
    }
    
    public void update() {
        if (!loading && infoLastLoaded > 0) {
            long timePassed = System.currentTimeMillis() - infoLastLoaded;
            if (statusEdited) {
                updated.setText(Language.getString("admin.infoLoaded.edited", DateTime.duration(timePassed, 1, 0)));
            } else {
                updated.setText(Language.getString("admin.infoLoaded", DateTime.duration(timePassed, 1, 0)));
            }
        }
        if (loading && lastPutResult > 0) {
            long ago = System.currentTimeMillis() - lastPutResult;
            if (ago > PUT_RESULT_DELAY) {
                setLoading(false);
            }
        }
    }
    
    private void statusEdited() {
        statusEdited = true;
    }

    public String getStatusHistorySorting() {
        return statusHistoryDialog.getSortOrder();
    }
    
    public void setStatusHistorySorting(String order) {
        statusHistoryDialog.setSortOrder(order);
    }
    
    /**
     * Adds the current status to the preset history
     */
    private void addCurrentToHistory() {
        String currentTitle = status.getText().trim();
        if (main.getSaveStatusHistorySetting()
                || main.getStatusHistory().isFavorite(currentTitle, currentStreamCategory, currentStreamTags, currentStreamLabels)) {
            main.getStatusHistory().addUsed(currentTitle, currentStreamCategory, currentStreamTags, StreamLabels.copyEditableLabelsOnly(currentStreamLabels));
        }
    }
    
    /**
     * Adds the current status to the preset favorites
     */
    private void addCurrentToFavorites() {
        main.getStatusHistory().addFavorite(status.getText().trim(), currentStreamCategory, currentStreamTags, StreamLabels.copyEditableLabelsOnly(currentStreamLabels));
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 20;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 20;
    }

    /**
     * Still resize horizontally despite this panel being in a scrollpane.
     */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
    
    private static class CacheItem {
        
        public final String channel;
        public final String title;
        public final StreamCategory category;
        public final List<StreamTag> tags;
        public final List<StreamLabel> labels;
        public final boolean statusEdited;
        public final long lastLoaded;
        
        public CacheItem(String channel, String title, StreamCategory category, List<StreamTag> tags, List<StreamLabel> labels, boolean statusEdited, long lastLoaded) {
            this.channel = channel;
            this.title = title;
            this.category = category;
            this.tags = new ArrayList<>(tags);
            this.labels = new ArrayList<>(labels);
            this.statusEdited = statusEdited;
            this.lastLoaded = lastLoaded;
        }
        
    }
    
    private void saveToCache() {
        if (!loading && !StringUtil.isNullOrEmpty(currentChannel)) {
            cache.put(currentChannel, new CacheItem(currentChannel, status.getText(),
                    currentStreamCategory, currentStreamTags, currentStreamLabels, statusEdited, infoLastLoaded));
        }
    }
    
    private boolean loadFromCache() {
        if (!StringUtil.isNullOrEmpty(currentChannel)) {
            CacheItem item = cache.get(currentChannel);
            if (item != null) {
                status.setText(item.title);
                currentStreamCategory = item.category;
                game.setText(item.category.name);
                setTags(item.tags);
                setLabels(item.labels);
                statusEdited = item.statusEdited;
                infoLastLoaded = item.lastLoaded;
                return true;
            }
        }
        return false;
    }

}
