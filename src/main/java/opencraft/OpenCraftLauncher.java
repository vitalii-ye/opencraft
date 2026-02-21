package opencraft;

import opencraft.execution.ProcessManager;
import opencraft.execution.FabricLauncher;
import opencraft.execution.VanillaGameLauncher;
import opencraft.mods.ModManager;
import opencraft.network.FabricDownloader;
import opencraft.network.FabricVersionManager;
import opencraft.model.FabricLoaderVersion;
import opencraft.network.MinecraftDownloader;
import opencraft.network.MinecraftVersionManager;
import opencraft.network.VersionProvider;
import opencraft.model.MinecraftVersion;
import opencraft.network.VersionCacheManager;
import opencraft.ui.RoundedButton;
import opencraft.ui.Theme;
import opencraft.ui.VersionComboBoxRenderer;
import opencraft.utils.ConfigurationManager;
import opencraft.utils.MinecraftPathResolver;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class OpenCraftLauncher extends JFrame {
  private static final long serialVersionUID = 1L;

  private static void debug(String msg) {
    System.out.println(msg);
  }
  private JTextField usernameField;
  private final JComboBox<MinecraftVersion> versionComboBox;
  private JButton playButton;
  private JButton downloadButton;
  private JButton screenshotsButton;
  private JButton modsButton;

  private final transient ConfigurationManager configManager;
  private final transient ProcessManager processManager;
  private final transient VersionCacheManager versionCacheManager;
  private final transient VersionProvider versionManager;
  private final transient FabricVersionManager fabricVersionManager;
  private final transient MinecraftDownloader minecraftDownloader;
  private final transient FabricDownloader fabricDownloader;
  private final transient FabricLauncher fabricLauncher;
  private final transient VersionLoader versionLoader;
  private String lastUsedVersion; // Loaded from config to restore selection
  
  // Cache for Fabric loader version
  private transient String latestFabricLoaderVersion = null;


  /**
   * Opens the Minecraft directory in the system's default file explorer
   */
  private void openScreenshotsDirectory() {
    try {
      Path screenshotsDir = MinecraftPathResolver.getScreenshotsDirectory();
      File dirFile = screenshotsDir.toFile();

      // Create directory if it doesn't exist
      if (!dirFile.exists()) {
        dirFile.mkdirs();
      }

      Desktop.getDesktop().open(dirFile);
    } catch (IOException e) {
      SwingUtilities.invokeLater(() -> {
        JOptionPane.showMessageDialog(OpenCraftLauncher.this,
            "Error opening directory: " + e.getMessage(),
            "Error",
            JOptionPane.ERROR_MESSAGE);
      });
      e.printStackTrace();
    }
  }

  @SuppressWarnings("this-escape")
  public OpenCraftLauncher() {
    this.versionComboBox = new JComboBox<>();
    this.playButton = new RoundedButton("Play");
    this.configManager = new ConfigurationManager();
    this.processManager = new ProcessManager();
    this.versionCacheManager = new VersionCacheManager();
    this.versionManager = new MinecraftVersionManager();
    this.fabricVersionManager = new FabricVersionManager();
    this.minecraftDownloader = new MinecraftDownloader();
    this.fabricDownloader = new FabricDownloader();
    this.fabricLauncher = new FabricLauncher(this.fabricDownloader);
    this.versionLoader = new VersionLoader(versionCacheManager, versionManager);

    // Load last used version early so it can be used when populating combo box
    this.lastUsedVersion = configManager.getLastUsedVersion();

    initializeGUI();
    loadVersions(); // Load versions first (from cache or fetch)
    loadConfiguration(); // Load username (version is restored in populateVersionComboBox)
  }

  /**
   * Loads Minecraft versions from cache or fetches them from the server.
   * Delegates the caching/fetching logic to {@link VersionLoader};
   * this method is responsible only for dispatching results to the UI.
   */
  private void loadVersions() {
    versionLoader.loadVersions(
        versions -> {
          // Delivered from background thread or EDT - populateVersionComboBox handles EDT dispatch
          populateVersionComboBox(versions);
          debug("Version list updated in UI");
        },
        error -> {
          System.err.println("Failed to update versions: " + error.getMessage());
          SwingUtilities.invokeLater(() ->
              JOptionPane.showMessageDialog(OpenCraftLauncher.this,
                  "Failed to fetch Minecraft versions.\nPlease check your internet connection.\n\n" +
                  "Error: " + error.getMessage(),
                  "Version Fetch Error",
                  JOptionPane.ERROR_MESSAGE));
        });
  }

  /**
   * Populates the version combo box with both vanilla and Fabric versions.
   * For each vanilla version, adds a corresponding Fabric version if supported.
   */
  private void populateVersionComboBox(List<MinecraftVersion> versions) {
    SwingUtilities.invokeLater(() -> {
      MinecraftVersion currentSelection = (MinecraftVersion) versionComboBox.getSelectedItem();
      String currentSelectionId = currentSelection != null ? currentSelection.getId() : null;

      versionComboBox.removeAllItems();

      // Fetch latest Fabric loader version if not cached
      if (latestFabricLoaderVersion == null) {
        try {
          FabricLoaderVersion loader = fabricVersionManager.getLatestStableLoader();
          if (loader != null) {
            latestFabricLoaderVersion = loader.getVersion();
          }
        } catch (Exception e) {
          System.err.println("Failed to fetch Fabric loader version: " + e.getMessage());
        }
      }

      // Add both vanilla and Fabric versions for each release
      for (MinecraftVersion version : versions) {
        // Add vanilla version
        versionComboBox.addItem(version);

        // Add Fabric version if loader is available
        if (latestFabricLoaderVersion != null) {
          MinecraftVersion fabricVersion = version.toFabricVersion(latestFabricLoaderVersion);
          versionComboBox.addItem(fabricVersion);
        }
      }

      // Prioritize: 1) lastUsedVersion from config, 2) current selection, 3) first item
      String targetVersionId = null;

      // First time loading - use lastUsedVersion from config
      if (lastUsedVersion != null && currentSelectionId == null) {
        targetVersionId = lastUsedVersion;
        debug("Restoring last used version: " + targetVersionId);
      }
      // Re-populating (e.g., after background refresh) - preserve current selection
      else if (currentSelectionId != null) {
        targetVersionId = currentSelectionId;
      }

      // Try to select the target version
      if (targetVersionId != null) {
        for (int i = 0; i < versionComboBox.getItemCount(); i++) {
          MinecraftVersion item = versionComboBox.getItemAt(i);
          if (item.getId().equals(targetVersionId)) {
            versionComboBox.setSelectedIndex(i);
            debug("Successfully set version to: " + targetVersionId);
            return; // Selection successful
          }
        }
        debug("Version " + targetVersionId + " not found in available versions");
      }

      // If no selection or selection not found, select first item
      if (versionComboBox.getSelectedIndex() == -1 && versionComboBox.getItemCount() > 0) {
        versionComboBox.setSelectedIndex(0);
        debug("No matching version found, using first available: " +
            versionComboBox.getItemAt(0).getDisplayName());
      }
    });
  }

  private void loadConfiguration() {
    // Load username
    String username = configManager.getUsername();
    usernameField.setText(username);

    // Note: Version selection is handled in populateVersionComboBox()
    // using the lastUsedVersion field loaded in constructor
  }

  private void initializeGUI() {
    setTitle("OpenCraft Launcher");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(360, 450); // Increased height slightly

    // Create main panel
    JPanel mainPanel = new JPanel(new BorderLayout());
    mainPanel.setBackground(Theme.BG_COLOR);
    mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

    // Content Panel (Center)
    JPanel contentPanel = new JPanel(new GridBagLayout());
    contentPanel.setBackground(Theme.BG_COLOR);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(5, 5, 5, 5);
    gbc.fill = GridBagConstraints.HORIZONTAL;

    // Title
    JLabel titleLabel = new JLabel("OpenCraft Launcher", SwingConstants.CENTER);
    titleLabel.setForeground(Theme.TEXT_COLOR);
    titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    gbc.insets = new Insets(0, 0, 30, 0);
    contentPanel.add(titleLabel, gbc);

    gbc.gridy = 1;
    gbc.gridwidth = 1;
    gbc.insets = new Insets(5, 5, 5, 5);

    // Username field styling
    usernameField = new JTextField("OpenCitizen", 15);
    usernameField.setBackground(Theme.INPUT_COLOR);
    usernameField.setForeground(Theme.TEXT_COLOR);
    usernameField.setCaretColor(Theme.TEXT_COLOR);
    usernameField.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    // Version ComboBox styling
    versionComboBox.setBackground(Theme.INPUT_COLOR);
    versionComboBox.setForeground(Theme.TEXT_COLOR);
    versionComboBox.setOpaque(true);

    // Custom renderer to show checkmark for downloaded versions
    versionComboBox.setRenderer(new VersionComboBoxRenderer());

    JPanel formPanel = new JPanel(new GridLayout(2, 1, 0, 10));
    formPanel.setBackground(Theme.BG_COLOR);
    formPanel.add(usernameField);

    JPanel versionPanel = new JPanel(new BorderLayout());
    versionPanel.setBackground(Theme.BG_COLOR);

    versionPanel.add(versionComboBox, BorderLayout.CENTER);
    formPanel.add(versionPanel);

    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.gridwidth = 2;
    gbc.insets = new Insets(0, 0, 20, 0);
    contentPanel.add(formPanel, gbc);

    // Buttons
    playButton = new RoundedButton("Play");
    playButton.setBackground(Theme.ACCENT_GREEN); // Green
    playButton.setForeground(Theme.TEXT_COLOR);
    playButton.setFont(new Font("Arial", Font.BOLD, 20));
    playButton.setPreferredSize(new Dimension(200, 50));

    downloadButton = new RoundedButton("Download");
    downloadButton.setBackground(Theme.INPUT_COLOR); // Dark Grey
    downloadButton.setForeground(Theme.TEXT_COLOR);
    downloadButton.setFont(new Font("Arial", Font.PLAIN, 14));
    downloadButton.setPreferredSize(new Dimension(150, 40));

    gbc.gridy = 2;
    gbc.insets = new Insets(10, 0, 10, 0);
    gbc.fill = GridBagConstraints.NONE; // Do not stretch buttons
    contentPanel.add(playButton, gbc);

    gbc.gridy = 3;
    gbc.insets = new Insets(0, 0, 0, 0);
    contentPanel.add(downloadButton, gbc);

    mainPanel.add(contentPanel, BorderLayout.CENTER);

    // Footer Panel (South)
    JPanel footerPanel = new JPanel(new BorderLayout());
    footerPanel.setBackground(Theme.BG_COLOR);
    footerPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

    screenshotsButton = new JButton("Screenshots");
    styleTextButton(screenshotsButton);

    modsButton = new JButton("Mods");
    styleTextButton(modsButton);

    footerPanel.add(screenshotsButton, BorderLayout.WEST);
    footerPanel.add(modsButton, BorderLayout.EAST);

    mainPanel.add(footerPanel, BorderLayout.SOUTH);

    add(mainPanel);

    // Add action listeners
    playButton.addActionListener(new PlayButtonListener());
    downloadButton.addActionListener(new DownloadButtonListener());
    screenshotsButton.addActionListener(new ScreenshotButtonListener());
    modsButton.addActionListener(e -> openUnifiedModsDialog());

    // Set initial focus
    SwingUtilities.invokeLater(() -> usernameField.requestFocus());
  }

  private void styleTextButton(JButton btn) {
    btn.setBorderPainted(false);
    btn.setFocusPainted(false);
    btn.setContentAreaFilled(false);
    btn.setForeground(Theme.TEXT_COLOR);
    btn.setFont(new Font("Arial", Font.PLAIN, 16));
    btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
  }

  /**
   * Opens the unified mods and shaders management dialog.
   */
  private void openUnifiedModsDialog() {
    MinecraftVersion selectedVersion = (MinecraftVersion) versionComboBox.getSelectedItem();
    if (selectedVersion == null) {
      JOptionPane.showMessageDialog(this,
          "Please select a Minecraft version first.",
          "No Version Selected",
          JOptionPane.WARNING_MESSAGE);
      return;
    }

    if (!selectedVersion.isFabric()) {
      JOptionPane.showMessageDialog(this,
          "Mods and shaders are only supported for Fabric versions.\nPlease select a Fabric version (e.g., \"1.21 [Fabric]\").",
          "Fabric Required",
          JOptionPane.WARNING_MESSAGE);
      return;
    }

    // Open unified mods dialog
    opencraft.ui.UnifiedModsDialog dialog = new opencraft.ui.UnifiedModsDialog(this, selectedVersion);
    dialog.setVisible(true);
  }

  private class PlayButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      String username = usernameField.getText().trim();
      if (username.isEmpty()) {
        username = "OpenCitizen";
      }

      MinecraftVersion selectedVersion = (MinecraftVersion) versionComboBox.getSelectedItem();
      if (selectedVersion == null) {
          JOptionPane.showMessageDialog(OpenCraftLauncher.this,
              "Please select a Minecraft version first.",
              "No Version Selected",
              JOptionPane.WARNING_MESSAGE);
          return;
      }

      // Save username and version to options file if they have changed
      configManager.setUsername(username);
      configManager.setLastUsedVersion(selectedVersion.getId());
      configManager.save();

      final String finalUsername = username;
      final MinecraftVersion finalVersion = selectedVersion;

      // Disable play button to prevent multiple instances
      playButton.setEnabled(false);
      playButton.setText("Starting...");
      downloadButton.setEnabled(false);

      // Run minecraft launcher in a separate thread
      SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
        @Override
        protected Void doInBackground() throws Exception {
          if (finalVersion.isFabric()) {
            launchFabricMinecraft(finalUsername, finalVersion);
          } else {
            launchMinecraft(finalUsername, finalVersion.getId());
          }
          return null;
        }

        @Override
        protected void done() {
          // Only re-enable if process is not running
          if (!processManager.isRunning()) {
            playButton.setEnabled(true);
            playButton.setText("Play");
            downloadButton.setEnabled(true);
          } else {
            // Start a background thread to monitor the process
            monitorMinecraftProcess();
          }
        }
      };

      worker.execute();
    }
  }

  private class DownloadButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      MinecraftVersion selectedVersion = (MinecraftVersion) versionComboBox.getSelectedItem();
      if (selectedVersion == null) {
        JOptionPane.showMessageDialog(OpenCraftLauncher.this,
            "Please select a Minecraft version first.",
            "No Version Selected",
            JOptionPane.WARNING_MESSAGE);
        return;
      }

      // Save selected version to options
      configManager.setLastUsedVersion(selectedVersion.getId());
      configManager.save();

      final MinecraftVersion finalVersion = selectedVersion;

      downloadButton.setEnabled(false);
      downloadButton.setText("Downloading...");

      // Run downloader in a separate thread
      SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
        @Override
        protected Void doInBackground() throws Exception {
          try {
            java.nio.file.Path baseDir = MinecraftPathResolver.getMinecraftDirectory();

            if (finalVersion.isFabric()) {
              // Download Fabric version
              publish("Downloading Fabric " + finalVersion.getBaseGameVersion() + "...");

              // First, ensure vanilla version is downloaded
              publish("Checking vanilla " + finalVersion.getBaseGameVersion() + "...");
              java.nio.file.Path vanillaLibs = baseDir.resolve("libraries_" + finalVersion.getBaseGameVersion() + ".txt");

              if (!java.nio.file.Files.exists(vanillaLibs)) {
                // Download vanilla first
                publish("Downloading vanilla " + finalVersion.getBaseGameVersion() + "...");
                java.util.List<MinecraftVersion> versions =
                    versionManager.fetchAvailableVersions();

                MinecraftVersion vanillaVersion = null;
                for (MinecraftVersion v : versions) {
                  if (v.getId().equals(finalVersion.getBaseGameVersion())) {
                    vanillaVersion = v;
                    break;
                  }
                }

                if (vanillaVersion != null) {
                  minecraftDownloader.downloadMinecraft(vanillaVersion, baseDir, this::publish);
                }
              }

              // Now download Fabric
              publish("Downloading Fabric loader...");
              fabricDownloader.downloadFabric(
                  finalVersion.getBaseGameVersion(),
                  finalVersion.getFabricLoaderVersion(),
                  this::publish
              );

              publish("Fabric download complete!");
            } else {
              // Download vanilla version
              publish("Starting Minecraft " + finalVersion.getId() + " download...");

              // Find the specific version in the Minecraft version manifest
              java.util.List<MinecraftVersion> versions =
                  versionManager.fetchAvailableVersions();

              MinecraftVersion targetVersion = null;
              for (MinecraftVersion version : versions) {
                if (version.getId().equals(finalVersion.getId())) {
                  targetVersion = version;
                  break;
                }
              }

              if (targetVersion == null) {
                throw new RuntimeException("Version " + finalVersion.getId() +
                    " not found in Minecraft version manifest");
              }

              publish("Found version " + finalVersion.getId() + " in manifest, downloading...");
              minecraftDownloader.downloadMinecraft(targetVersion, baseDir, this::publish);
              publish("Download completed successfully!");
            }

          } catch (Exception ex) {
            publish("Error during download: " + ex.getMessage());
            ex.printStackTrace();
          }
          return null;
        }

        @Override
        protected void process(java.util.List<String> chunks) {
          for (String message : chunks) {
            downloadButton.setText(message.length() > 20 ? message.substring(0, 17) + "..." : message);
            debug("Download: " + message);
          }
        }

        @Override
        protected void done() {
          downloadButton.setEnabled(true);
          downloadButton.setText("Download");
          List<MinecraftVersion> currentVersions = versionCacheManager.getCachedVersions();
          if (currentVersions != null) {
            populateVersionComboBox(currentVersions);
          }
        }
      };

      worker.execute();
    }
  }

  private class ScreenshotButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      debug("Screenshot button clicked!");
      openScreenshotsDirectory();
    }
  }

  /**
   * Launches vanilla Minecraft by delegating all business logic to
   * {@link VanillaGameLauncher}. This method retains only UI error handling.
   */
  private void launchMinecraft(String username, String versionId) {
    try {
      Path minecraftDir = MinecraftPathResolver.getMinecraftDirectory();
      VanillaGameLauncher gameLauncher = new VanillaGameLauncher(processManager);
      gameLauncher.launch(username, versionId, minecraftDir,
          line -> System.out.println("MC: " + line));

      SwingUtilities.invokeLater(() -> {
        debug("DEBUG: Minecraft process started successfully");
        playButton.setText("Running");
      });

    } catch (IOException e) {
      final String message = e.getMessage();
      SwingUtilities.invokeLater(() -> {
        debug("DEBUG: Error starting Minecraft: " + message);
        e.printStackTrace();
        JOptionPane.showMessageDialog(OpenCraftLauncher.this,
            message,
            "Launch Error",
            JOptionPane.ERROR_MESSAGE);
        playButton.setEnabled(true);
        playButton.setText("Play");
        downloadButton.setEnabled(true);
      });
    }
  }

  /**
   * Launches Minecraft with Fabric mod loader.
   * Uses the existing FabricLauncher class for Fabric-specific launch logic.
   */
  private void launchFabricMinecraft(String username, MinecraftVersion version) {
    try {
      Path minecraftDir = MinecraftPathResolver.getMinecraftDirectory();
      String fabricVersionId = version.getId();
      String baseGameVersion = version.getBaseGameVersion();

      // Check if Fabric version is installed
      File fabricJson = minecraftDir.resolve("versions/" + fabricVersionId + "/" + fabricVersionId + ".json").toFile();
      File baseLibraries = minecraftDir.resolve("libraries_" + baseGameVersion + ".txt").toFile();

      if (!fabricJson.exists()) {
        SwingUtilities.invokeLater(() -> {
          JOptionPane.showMessageDialog(OpenCraftLauncher.this,
              "Fabric version not found!\nPlease download this version first.",
              "Files Missing",
              JOptionPane.ERROR_MESSAGE);
        });
        return;
      }

      if (!baseLibraries.exists()) {
        SwingUtilities.invokeLater(() -> {
          JOptionPane.showMessageDialog(OpenCraftLauncher.this,
              "Vanilla version " + baseGameVersion + " not found!\nPlease download this version first.",
              "Files Missing",
              JOptionPane.ERROR_MESSAGE);
        });
        return;
      }

      // Sync version-specific mods to the game mods folder
      try {
        Path gameModsDir = minecraftDir.resolve("mods");
        debug("Syncing mods for version " + baseGameVersion + " to: " + gameModsDir);
        ModManager modManager = new ModManager();
        modManager.syncModsToDirectory(baseGameVersion, gameModsDir, msg -> System.out.println("Mod sync: " + msg));
      } catch (IOException e) {
        debug("Warning: Failed to sync mods: " + e.getMessage());
        // Non-fatal, continue with launch
      }

      // Sync shaderpacks to the game shaderpacks folder
      try {
        Path sourceShaderpacksDir = MinecraftPathResolver.getShaderpacksDirectory();
        Path gameShaderpacksDir = minecraftDir.resolve("shaderpacks");
        // When gameDir is root, shaderpacks dir is already correct; skip redundant copy
        if (Files.exists(sourceShaderpacksDir) && !sourceShaderpacksDir.equals(gameShaderpacksDir)) {
          Files.createDirectories(gameShaderpacksDir);
          Files.walk(sourceShaderpacksDir)
               .filter(Files::isRegularFile)
               .forEach(source -> {
                 try {
                   Path dest = gameShaderpacksDir.resolve(sourceShaderpacksDir.relativize(source));
                   Files.createDirectories(dest.getParent());
                   Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
                   debug("Synced shader: " + source.getFileName());
                 } catch (IOException e) {
                   System.err.println("Failed to copy shader " + source + ": " + e.getMessage());
                 }
               });
        }
      } catch (IOException e) {
        debug("Warning: Failed to sync shaderpacks: " + e.getMessage());
        // Non-fatal, continue with launch
      }

      // Use FabricLauncher to launch
      debug("Launching Fabric version: " + fabricVersionId);
      fabricLauncher.launchMinecraft(fabricVersionId, username, minecraftDir);

      SwingUtilities.invokeLater(() -> {
        debug("DEBUG: Fabric Minecraft process started successfully");
        playButton.setText("Running");
      });

    } catch (Exception e) {
      SwingUtilities.invokeLater(() -> {
        debug("DEBUG: Error starting Fabric Minecraft: " + e.getMessage());
        e.printStackTrace();
        JOptionPane.showMessageDialog(OpenCraftLauncher.this,
            "Error launching Fabric: " + e.getMessage(),
            "Launch Error",
            JOptionPane.ERROR_MESSAGE);
        playButton.setEnabled(true);
        playButton.setText("Play");
        downloadButton.setEnabled(true);
      });
    }
  }

  /**
   * Monitors the Minecraft process in a background thread and re-enables
   * the Play button when it exits
   */
  private void monitorMinecraftProcess() {
    Thread monitorThread = new Thread(() -> {
      try {
        processManager.waitFor();
        debug("DEBUG: Minecraft process exited");
      } catch (InterruptedException e) {
        debug("DEBUG: Minecraft monitoring interrupted");
        Thread.currentThread().interrupt();
      } finally {
        SwingUtilities.invokeLater(() -> {
          playButton.setEnabled(true);
          playButton.setText("Play");
          downloadButton.setEnabled(true);
        });
      }
    }, "Minecraft-Monitor");
    monitorThread.setDaemon(true);
    monitorThread.start();
  }

  public static void main(String[] args) {
    try {
      // Use CrossPlatformLookAndFeel (Metal) to ensure custom colors are respected on all platforms
      UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
    } catch (Exception e) {
      e.printStackTrace();
    }
    SwingUtilities.invokeLater(() -> {
      new OpenCraftLauncher().setVisible(true);
    });
  }
}
