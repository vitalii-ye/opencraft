package opencraft;

import opencraft.execution.LauncherCommandBuilder;
import opencraft.execution.ProcessManager;
import opencraft.network.MinecraftDownloader;
import opencraft.network.MinecraftVersionManager;
import opencraft.network.VersionCacheManager;
import opencraft.ui.RoundedButton;
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
import java.nio.file.Paths;
import java.util.List;

public class OpenCraftLauncher extends JFrame {
  private static final long serialVersionUID = 1L;
  private JTextField usernameField;
  private final JComboBox<String> versionComboBox;
  private JButton playButton;
  private JButton downloadButton;
  private JButton screenshotsButton;
  private JButton ramUsageButton;
  
  private final transient ConfigurationManager configManager;
  private final transient ProcessManager processManager;
  private final transient VersionCacheManager versionCacheManager;
  private String lastUsedVersion; // Loaded from config to restore selection


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

    // Load last used version early so it can be used when populating combo box
    this.lastUsedVersion = configManager.getLastUsedVersion();

    initializeGUI();
    loadVersions(); // Load versions first (from cache or fetch)
    loadConfiguration(); // Load username (version is restored in populateVersionComboBox)
  }
  
  /**
   * Loads Minecraft versions from cache or fetches them from the server.
   * Uses hybrid caching approach with TTL and ETag validation.
   */
  private void loadVersions() {
    // Try to load from cache first
    List<MinecraftVersionManager.MinecraftVersion> cachedVersions = versionCacheManager.getCachedVersions();

    if (cachedVersions != null && !cachedVersions.isEmpty()) {
      // Cache is valid (within TTL), use it immediately
      System.out.println("Loading versions from cache (" + cachedVersions.size() + " versions)");
      populateVersionComboBox(cachedVersions);

      // Don't fetch in background if cache is fresh
      return;
    }

    // Cache is expired or doesn't exist, check if we need ETag validation
    if (versionCacheManager.needsValidation()) {
      // Cache exists but TTL expired, try to use old cache while validating
      try {
        List<MinecraftVersionManager.MinecraftVersion> oldCachedVersions = versionCacheManager.getCachedVersions();
        if (oldCachedVersions == null) {
          // Try to read from cache file directly
          oldCachedVersions = readCacheDirectly();
        }

        if (oldCachedVersions != null && !oldCachedVersions.isEmpty()) {
          System.out.println("Loading expired cache while validating (" + oldCachedVersions.size() + " versions)");
          populateVersionComboBox(oldCachedVersions);
        }
      } catch (Exception e) {
        System.err.println("Error loading expired cache: " + e.getMessage());
      }
    }

    // Fetch versions in background
    fetchVersionsInBackground();
  }

  /**
   * Reads cache directly even if expired (for initial UI load)
   */
  private List<MinecraftVersionManager.MinecraftVersion> readCacheDirectly() {
    try {
      Path cacheFile = MinecraftPathResolver.getMinecraftDirectory().resolve("version_cache.json");
      if (!Files.exists(cacheFile)) {
        return null;
      }

      com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(cacheFile.toFile());

      List<MinecraftVersionManager.MinecraftVersion> versions = new java.util.ArrayList<>();
      if (root.isArray()) {
        for (com.fasterxml.jackson.databind.JsonNode versionNode : root) {
          String id = versionNode.get("id").asText();
          String type = versionNode.get("type").asText();
          String url = versionNode.get("url").asText();
          String releaseTime = versionNode.get("releaseTime").asText();
          versions.add(new MinecraftVersionManager.MinecraftVersion(id, type, url, releaseTime));
        }
      }

      return versions;
    } catch (Exception e) {
      System.err.println("Error reading cache directly: " + e.getMessage());
      return null;
    }
  }

  /**
   * Fetches versions in background and updates UI when done
   */
  private void fetchVersionsInBackground() {
    SwingWorker<List<MinecraftVersionManager.MinecraftVersion>, Void> worker =
        new SwingWorker<List<MinecraftVersionManager.MinecraftVersion>, Void>() {
      @Override
      protected List<MinecraftVersionManager.MinecraftVersion> doInBackground() throws Exception {
        try {
          String storedETag = versionCacheManager.getStoredETag();

          // Fetch with ETag validation
          MinecraftVersionManager.VersionResponse response =
              MinecraftVersionManager.fetchAvailableVersionsWithETag(storedETag);

          if (response.isNotModified()) {
            // Data hasn't changed, update cache timestamp and return cached versions
            System.out.println("Versions not modified (304), using cache");
            List<MinecraftVersionManager.MinecraftVersion> cached = readCacheDirectly();
            if (cached != null) {
              // Update cache timestamp
              versionCacheManager.saveToCache(cached, storedETag);
            }
            return cached;
          }

          // Filter release versions only
          List<MinecraftVersionManager.MinecraftVersion> allVersions = response.getVersions();
          List<MinecraftVersionManager.MinecraftVersion> releaseVersions = new java.util.ArrayList<>();

          for (MinecraftVersionManager.MinecraftVersion version : allVersions) {
            if (version.isRelease()) {
              releaseVersions.add(version);
            }
          }

          // Save to cache
          versionCacheManager.saveToCache(releaseVersions, response.getEtag());

          System.out.println("Fetched " + releaseVersions.size() + " release versions from server");
          return releaseVersions;

        } catch (IOException | InterruptedException e) {
          System.err.println("Error fetching versions: " + e.getMessage());
          e.printStackTrace();

          // Try to use cache as fallback
          List<MinecraftVersionManager.MinecraftVersion> cached = readCacheDirectly();
          if (cached != null && !cached.isEmpty()) {
            System.out.println("Using cached versions as fallback");
            return cached;
          }

          throw e;
        }
      }

      @Override
      protected void done() {
        try {
          List<MinecraftVersionManager.MinecraftVersion> versions = get();
          if (versions != null && !versions.isEmpty()) {
            populateVersionComboBox(versions);
            System.out.println("Version list updated in UI");
          }
        } catch (Exception e) {
          System.err.println("Failed to update versions: " + e.getMessage());

          // Show error message to user
          SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(OpenCraftLauncher.this,
                "Failed to fetch Minecraft versions.\nPlease check your internet connection.\n\n" +
                "Error: " + e.getMessage(),
                "Version Fetch Error",
                JOptionPane.ERROR_MESSAGE);
          });
        }
      }
    };

    worker.execute();
  }

  /**
   * Populates the version combo box with the given versions
   */
  private void populateVersionComboBox(List<MinecraftVersionManager.MinecraftVersion> versions) {
    SwingUtilities.invokeLater(() -> {
      String currentSelection = (String) versionComboBox.getSelectedItem();

      versionComboBox.removeAllItems();

      for (MinecraftVersionManager.MinecraftVersion version : versions) {
        versionComboBox.addItem(version.getId());
      }

      // Prioritize: 1) lastUsedVersion from config, 2) current selection, 3) first item
      String targetVersion = null;

      // First time loading - use lastUsedVersion from config
      if (lastUsedVersion != null && currentSelection == null) {
        targetVersion = lastUsedVersion;
        System.out.println("Restoring last used version: " + targetVersion);
      }
      // Re-populating (e.g., after background refresh) - preserve current selection
      else if (currentSelection != null) {
        targetVersion = currentSelection;
      }

      // Try to select the target version
      if (targetVersion != null) {
        for (int i = 0; i < versionComboBox.getItemCount(); i++) {
          if (versionComboBox.getItemAt(i).equals(targetVersion)) {
            versionComboBox.setSelectedIndex(i);
            System.out.println("Successfully set version to: " + targetVersion);
            return; // Selection successful
          }
        }
        System.out.println("Version " + targetVersion + " not found in available versions");
      }

      // If no selection or selection not found, select first item
      if (versionComboBox.getSelectedIndex() == -1 && versionComboBox.getItemCount() > 0) {
        versionComboBox.setSelectedIndex(0);
        System.out.println("No matching version found, using first available: " + versionComboBox.getItemAt(0));
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
    mainPanel.setBackground(new Color(20, 20, 20));
    mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

    // Content Panel (Center)
    JPanel contentPanel = new JPanel(new GridBagLayout());
    contentPanel.setBackground(new Color(20, 20, 20));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(5, 5, 5, 5);
    gbc.fill = GridBagConstraints.HORIZONTAL;

    // Title
    JLabel titleLabel = new JLabel("OpenCraft Launcher", SwingConstants.CENTER);
    titleLabel.setForeground(Color.WHITE);
    titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    gbc.insets = new Insets(0, 0, 30, 0);
    contentPanel.add(titleLabel, gbc);

    // Username row (Hidden in screenshot but likely needed, keeping it but maybe less prominent or just there)
    // The screenshot doesn't show username field, but I should probably keep it for functionality.
    // I'll keep it.
    gbc.gridy = 1;
    gbc.gridwidth = 1;
    gbc.insets = new Insets(5, 5, 5, 5);
    
    // Username field styling
    usernameField = new JTextField("OpenCitizen", 15);
    usernameField.setBackground(new Color(45, 45, 45));
    usernameField.setForeground(Color.WHITE);
    usernameField.setCaretColor(Color.WHITE);
    usernameField.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    
    // Version row
    // Version ComboBox styling
    versionComboBox.setBackground(new Color(45, 45, 45));
    versionComboBox.setForeground(Color.WHITE);
    versionComboBox.setOpaque(true);
    
    // Custom renderer to show checkmark for downloaded versions
    versionComboBox.setRenderer(new VersionComboBoxRenderer());

    JPanel formPanel = new JPanel(new GridLayout(2, 1, 0, 10));
    formPanel.setBackground(new Color(20, 20, 20));
    formPanel.add(usernameField);
    
    JPanel versionPanel = new JPanel(new BorderLayout());
    versionPanel.setBackground(new Color(20, 20, 20));
    
    versionPanel.add(versionComboBox, BorderLayout.CENTER);
    formPanel.add(versionPanel);

    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.gridwidth = 2;
    gbc.insets = new Insets(0, 0, 20, 0);
    contentPanel.add(formPanel, gbc);

    // Buttons
    playButton = new RoundedButton("Play");
    playButton.setBackground(new Color(76, 175, 80)); // Green
    playButton.setForeground(Color.WHITE);
    playButton.setFont(new Font("Arial", Font.BOLD, 20));
    playButton.setPreferredSize(new Dimension(200, 50));

    downloadButton = new RoundedButton("Download");
    downloadButton.setBackground(new Color(45, 45, 45)); // Dark Grey
    downloadButton.setForeground(Color.WHITE);
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
    footerPanel.setBackground(new Color(20, 20, 20));
    footerPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

    screenshotsButton = new JButton("Screenshots");
    styleTextButton(screenshotsButton);
    
    ramUsageButton = new JButton("RAM Usage");
    styleTextButton(ramUsageButton);

    footerPanel.add(screenshotsButton, BorderLayout.WEST);
    footerPanel.add(ramUsageButton, BorderLayout.EAST);

    mainPanel.add(footerPanel, BorderLayout.SOUTH);

    add(mainPanel);

    // Add action listeners
    playButton.addActionListener(new PlayButtonListener());
    downloadButton.addActionListener(new DownloadButtonListener());
    screenshotsButton.addActionListener(new ScreenshotButtonListener());
    ramUsageButton.addActionListener(e -> JOptionPane.showMessageDialog(this, "RAM Usage: " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024 + "MB / " + Runtime.getRuntime().maxMemory() / 1024 / 1024 + "MB"));
    
    // Set initial focus
    SwingUtilities.invokeLater(() -> usernameField.requestFocus());
  }

  private void styleTextButton(JButton btn) {
    btn.setBorderPainted(false);
    btn.setFocusPainted(false);
    btn.setContentAreaFilled(false);
    btn.setForeground(Color.WHITE);
    btn.setFont(new Font("Arial", Font.PLAIN, 16));
    btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
  }

  private class PlayButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      String username = usernameField.getText().trim();
      if (username.isEmpty()) {
        username = "OpenCitizen";
      }

      // Save username and version to options file if they have changed
      configManager.setUsername(username);
      configManager.setLastUsedVersion(versionComboBox.getSelectedItem().toString());
      configManager.save();

      final String finalUsername = username;
      Object selectedItem = versionComboBox.getSelectedItem();
      if (selectedItem == null) {
          JOptionPane.showMessageDialog(OpenCraftLauncher.this,
              "Please select a Minecraft version first.",
              "No Version Selected",
              JOptionPane.WARNING_MESSAGE);
          return;
      }
      String versionId = selectedItem.toString();

      // Disable play button to prevent multiple instances
      playButton.setEnabled(false);
      playButton.setText("Starting...");
      downloadButton.setEnabled(false);

      // Run minecraft launcher in a separate thread
      SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
        @Override
        protected Void doInBackground() throws Exception {
          launchMinecraft(finalUsername, versionId);
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
      Object selectedItem = versionComboBox.getSelectedItem();
      if (selectedItem == null) {
        JOptionPane.showMessageDialog(OpenCraftLauncher.this,
            "Please select a Minecraft version first.",
            "No Version Selected",
            JOptionPane.WARNING_MESSAGE);
        return;
      }
      String versionId = selectedItem.toString();

      // Save selected version to options
      configManager.setLastUsedVersion(versionId);
      configManager.save();

      downloadButton.setEnabled(false);
      downloadButton.setText("Downloading...");
      // Run downloader in a separate thread
      SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
        @Override
        protected Void doInBackground() throws Exception {
          try {
            publish("Starting Minecraft " + versionId + " download...");
            java.nio.file.Path baseDir = MinecraftPathResolver.getMinecraftDirectory();

            // Find the specific version in the Minecraft version manifest
            java.util.List<MinecraftVersionManager.MinecraftVersion> versions = MinecraftVersionManager
                .fetchAvailableVersions();

            MinecraftVersionManager.MinecraftVersion targetVersion = null;
            for (MinecraftVersionManager.MinecraftVersion version : versions) {
              if (version.getId().equals(versionId)) {
                targetVersion = version;
                break;
              }
            }

            if (targetVersion == null) {
              throw new RuntimeException("Version " + versionId + " not found in Minecraft version manifest");
            }

            publish("Found version " + versionId + " in manifest, downloading...");

            // Use a custom output stream or callback instead of hijacking System.out
            MinecraftDownloader.downloadMinecraft(targetVersion, baseDir, this::publish);

            publish("Download completed successfully!");

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
            // Optionally update a status label or progress bar here
            System.out.println("Download: " + message);
          }
        }

        @Override
        protected void done() {
          downloadButton.setEnabled(true);
          downloadButton.setText("Download");
          List<MinecraftVersionManager.MinecraftVersion> currentVersions = versionCacheManager.getCachedVersions();
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
      System.out.println("Screenshot button clicked!");
      openScreenshotsDirectory();
    }
  }

  private void launchMinecraft(String username, String versionId) {
    try {
      Path minecraftDir = MinecraftPathResolver.getMinecraftDirectory();

      // Check if required files exist
      File librariesFile = minecraftDir.resolve("libraries_" + versionId + ".txt").toFile();
      File minecraftJar = minecraftDir.resolve("versions/" + versionId + "/" + versionId + ".jar").toFile();
      File versionJson = minecraftDir.resolve("versions/" + versionId + "/" + versionId + ".json").toFile();

      if (!librariesFile.exists()) {
        SwingUtilities.invokeLater(() -> {
          JOptionPane.showMessageDialog(OpenCraftLauncher.this,
              "Libraries file not found for version " + versionId + "!\nPlease download this version first.",
              "Files Missing",
              JOptionPane.ERROR_MESSAGE);
        });
        return;
      }

      if (!minecraftJar.exists()) {
        SwingUtilities.invokeLater(() -> {
          JOptionPane.showMessageDialog(OpenCraftLauncher.this,
              "Minecraft JAR not found for version " + versionId + "!\nPlease download this version first.",
              "Files Missing",
              JOptionPane.ERROR_MESSAGE);
        });
        return;
      }

      if (!versionJson.exists()) {
        SwingUtilities.invokeLater(() -> {
          JOptionPane.showMessageDialog(OpenCraftLauncher.this,
              "Version manifest not found for version " + versionId + "!\nPlease download this version first.",
              "Files Missing",
              JOptionPane.ERROR_MESSAGE);
        });
        return;
      }

      // Read libraries path
      String librariesPath = Files.readString(minecraftDir.resolve("libraries_" + versionId + ".txt")).trim();

      // Read version manifest to get asset index
      com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      com.fasterxml.jackson.databind.JsonNode versionManifest = mapper.readTree(versionJson);
      String assetIndex = versionManifest.path("assetIndex").path("id").asText();

      // Build the command
      String osName = System.getProperty("os.name").toLowerCase();
      boolean isMac = osName.contains("mac") || osName.contains("darwin");

      String minecraftDirStr = minecraftDir.toString();
      String versionJarPath = minecraftDir.resolve("versions/" + versionId + "/" + versionId + ".jar").toString();
      String nativesPath = minecraftDir.resolve("libraries/natives").toString();
      String assetsPath = minecraftDir.resolve("assets").toString();
      
      LauncherCommandBuilder builder = new LauncherCommandBuilder("net.minecraft.client.main.Main", Paths.get(nativesPath));
      
      if (isMac) {
          builder.addJvmArg("-XstartOnFirstThread");
          builder.addJvmArg("-Xmx4G");
          builder.addJvmArg("-Xms1G");
      } else {
          builder.addJvmArg("-Xmx4G");
          builder.addJvmArg("-Xms1G");
      }
      
      builder.addJvmArg("-Dfile.encoding=UTF-8");
      
      // Add classpath
      builder.addClasspathEntry(librariesPath);
      builder.addClasspathEntry(versionJarPath);
      
      // Add game args
      builder.addGameArg("--version");
      builder.addGameArg(versionId);
      builder.addGameArg("--accessToken");
      builder.addGameArg("dummy");
      builder.addGameArg("--uuid");
      builder.addGameArg("0B004000-00E0-00A0-0500-000000700000");
      builder.addGameArg("--username");
      builder.addGameArg(username);
      builder.addGameArg("--userType");
      builder.addGameArg("legacy");
      builder.addGameArg("--versionType");
      builder.addGameArg("release");
      builder.addGameArg("--gameDir");
      builder.addGameArg(minecraftDirStr);
      builder.addGameArg("--assetsDir");
      builder.addGameArg(assetsPath);
      builder.addGameArg("--assetIndex");
      builder.addGameArg(assetIndex);
      builder.addGameArg("--clientId");
      builder.addGameArg("dummy");

      List<String> command = builder.build();

      // Start the process
      processManager.startProcess(command, line -> System.out.println("MC: " + line));

      // Don't wait for process - let it run independently
      // This prevents the launcher from freezing and allows proper game exit
      SwingUtilities.invokeLater(() -> {
        System.out.println("DEBUG: Minecraft process started successfully");
        playButton.setText("Running");
      });

    } catch (IOException e) {
      SwingUtilities.invokeLater(() -> {
        System.out.println("DEBUG: Error starting Minecraft: " + e.getMessage());
        e.printStackTrace();
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
        System.out.println("DEBUG: Minecraft process exited");
      } catch (InterruptedException e) {
        System.out.println("DEBUG: Minecraft monitoring interrupted");
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

  /**
   * Loads username and version from opencraft_options.txt file if it exists
   */

  public static void main(String[] args) {
    try {
      // Use CrossPlatformLookAndFeel (Metal) to ensure custom colors are respected on all platforms (especially macOS)
      UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
    } catch (Exception e) {
      e.printStackTrace();
    }
    SwingUtilities.invokeLater(() -> {
      new OpenCraftLauncher().setVisible(true);
    });
  }
}
