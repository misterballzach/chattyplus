package chatty.gui.components.settings;

import java.awt.GridBagConstraints;
import javax.swing.JCheckBox;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class BotSettings extends SettingsPanel {

    public BotSettings(final SettingsDialog d) {

        JPanel bot = addTitledPanel("AI Bot Settings", 0);

        JCheckBox botEnabled = d.addSimpleBooleanSetting("botEnabled", "Enable AI Bot", "Enables the AI bot.");

        JPanel general = addTitledPanel("General", 0);
        general.add(botEnabled, d.makeGbc(0, 0, 1, 1, GridBagConstraints.WEST));

        JPanel credentials = addTitledPanel("Credentials", 1);

        credentials.add(new JLabel("Gemini API Key:"), d.makeGbc(0, 0, 1, 1, GridBagConstraints.EAST));
        JTextField geminiApiKey = d.addSimpleStringSetting("geminiApiKey", 20, true);
        credentials.add(geminiApiKey, d.makeGbc(1, 0, 1, 1, GridBagConstraints.WEST));

        credentials.add(new JLabel("Bot Username:"), d.makeGbc(0, 1, 1, 1, GridBagConstraints.EAST));
        JTextField botUsername = d.addSimpleStringSetting("botUsername", 20, true);
        credentials.add(botUsername, d.makeGbc(1, 1, 1, 1, GridBagConstraints.WEST));

        credentials.add(new JLabel("Bot OAuth Token:"), d.makeGbc(0, 2, 1, 1, GridBagConstraints.EAST));
        JTextField botToken = d.addSimpleStringSetting("botAccessToken", 20, true);
        credentials.add(botToken, d.makeGbc(1, 2, 1, 1, GridBagConstraints.WEST));

        JButton getTokenButton = new JButton("Get Token");
        getTokenButton.addActionListener(e -> {
            d.getMainGui().getToken(true, token -> {
                if (token != null) {
                    d.setStringSetting("botAccessToken", "oauth:" + token);
                }
            });
        });
        credentials.add(getTokenButton, d.makeGbc(2, 2, 1, 1, GridBagConstraints.WEST));

        JPanel personality = addTitledPanel("Personality", 2);

        EditorStringSetting botPersonality = d.addEditorStringSetting("botPersonality", 5, true, "Personality", true, "The personality of the bot.");
        personality.add(botPersonality, d.makeGbc(0, 0, 1, 1, GridBagConstraints.WEST));
    }
}
