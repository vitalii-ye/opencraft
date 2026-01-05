package opencraft.ui;

import opencraft.utils.MinecraftPathResolver;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class VersionComboBoxRenderer extends JPanel implements ListCellRenderer<String> {
    private static final long serialVersionUID = 1L;

    private final JLabel versionLabel;
    private final JLabel checkmarkLabel;

    @SuppressWarnings("this-escape")
    public VersionComboBoxRenderer() {
        setLayout(new BorderLayout());
        setOpaque(true);

        versionLabel = new JLabel();
        versionLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        versionLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 5));

        checkmarkLabel = new JLabel("Downloaded");
        checkmarkLabel.setFont(new Font("Arial", Font.PLAIN, 10));
        checkmarkLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 10));

        add(versionLabel, BorderLayout.WEST);
        add(checkmarkLabel, BorderLayout.EAST);
    }

    private boolean isVersionDownloaded(String versionId) {
        try {
            Path minecraftDir = MinecraftPathResolver.getMinecraftDirectory();
            Path librariesFile = minecraftDir.resolve("libraries_" + versionId + ".txt");
            return Files.exists(librariesFile);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends String> list, String value,
                                                  int index, boolean isSelected, boolean cellHasFocus) {
        if (value == null) {
            versionLabel.setText("");
            checkmarkLabel.setVisible(false);
            return this;
        }

        // Set the version text
        versionLabel.setText(value);

        // Check if this version is downloaded and show/hide checkmark
        boolean isDownloaded = isVersionDownloaded(value);
        checkmarkLabel.setVisible(isDownloaded);

        // Set colors based on selection
        Color backgroundColor;
        Color foregroundColor = Color.WHITE;

        if (isSelected) {
            backgroundColor = new Color(80, 80, 80);
        } else {
            backgroundColor = new Color(40, 40, 40);
        }

        setBackground(backgroundColor);
        versionLabel.setForeground(foregroundColor);
        checkmarkLabel.setForeground(foregroundColor);

        return this;
    }
}
