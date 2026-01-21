package opencraft.ui;

import opencraft.network.MinecraftVersionManager.MinecraftVersion;
import opencraft.utils.MinecraftPathResolver;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Custom renderer for the version combo box that displays:
 * - Version name with [Fabric] badge for Fabric versions
 * - "Downloaded" indicator for locally available versions
 */
public class VersionComboBoxRenderer extends JPanel implements ListCellRenderer<MinecraftVersion> {
  private static final long serialVersionUID = 1L;

  private final JLabel versionLabel;
  private final JLabel fabricBadge;
  private final JLabel statusLabel;

  @SuppressWarnings("this-escape")
  public VersionComboBoxRenderer() {
    setLayout(new BorderLayout());
    setOpaque(true);

    // Left side: version name
    versionLabel = new JLabel();
    versionLabel.setFont(new Font("Arial", Font.PLAIN, 14));
    versionLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 5));

    // Center: Fabric badge
    fabricBadge = new JLabel("[Fabric]");
    fabricBadge.setFont(new Font("Arial", Font.BOLD, 10));
    fabricBadge.setForeground(new Color(139, 195, 74)); // Light green color for Fabric
    fabricBadge.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    // Right side: download status
    statusLabel = new JLabel("Downloaded");
    statusLabel.setFont(new Font("Arial", Font.PLAIN, 10));
    statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 10));

    // Create a panel for version + badge
    JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    leftPanel.setOpaque(false);
    leftPanel.add(versionLabel);
    leftPanel.add(fabricBadge);

    add(leftPanel, BorderLayout.WEST);
    add(statusLabel, BorderLayout.EAST);
  }

  /**
   * Checks if a version is downloaded locally.
   * For vanilla versions: checks libraries_{version}.txt
   * For Fabric versions: checks the Fabric version JSON
   */
  private boolean isVersionDownloaded(MinecraftVersion version) {
    if (version == null) {
      return false;
    }

    try {
      Path minecraftDir = MinecraftPathResolver.getMinecraftDirectory();

      if (version.isFabric()) {
        // Check for Fabric version JSON and base vanilla version
        Path fabricJson = minecraftDir.resolve("versions")
            .resolve(version.getId())
            .resolve(version.getId() + ".json");
        Path vanillaLibraries = minecraftDir.resolve("libraries_" + version.getBaseGameVersion() + ".txt");
        return Files.exists(fabricJson) && Files.exists(vanillaLibraries);
      } else {
        // Check for vanilla version
        Path librariesFile = minecraftDir.resolve("libraries_" + version.getId() + ".txt");
        return Files.exists(librariesFile);
      }
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public Component getListCellRendererComponent(JList<? extends MinecraftVersion> list,
      MinecraftVersion value,
      int index,
      boolean isSelected,
      boolean cellHasFocus) {
    if (value == null) {
      versionLabel.setText("");
      fabricBadge.setVisible(false);
      statusLabel.setVisible(false);
      return this;
    }

    // Set the version text (base game version without [Fabric])
    versionLabel.setText(value.getBaseGameVersion());

    // Show/hide Fabric badge
    fabricBadge.setVisible(value.isFabric());

    // Check if this version is downloaded
    boolean isDownloaded = isVersionDownloaded(value);
    statusLabel.setVisible(isDownloaded);

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
    statusLabel.setForeground(new Color(150, 150, 150)); // Slightly dimmed

    return this;
  }
}
