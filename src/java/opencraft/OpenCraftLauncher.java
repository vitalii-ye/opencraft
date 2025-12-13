package opencraft;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class OpenCraftLauncher extends JFrame {
  private static final long serialVersionUID = 1L;
  private JTextField usernameField;
  private JComboBox<String> versionComboBox = new JComboBox<>(new String[] { "1.21", "1.21.10" });
  private JButton playButton = new RoundedButton("Play");
  private JButton downloadButton;
  private JButton refreshVersionsButton;
  private JButton screenshotsButton;
  private JButton ramUsageButton;
  private String originalUsername; // Track the original username from file
  private transient Process minecraftProcess = null; // Track the running Minecraft process

  /**
   * Gets the platform-specific Minecraft directory
   * On macOS: ~/Library/Application Support/minecraft
   * On Windows: %appdata%\.minecraft
   * On Linux: ~/.minecraft
   */
  private static Path getMinecraftDirectory() {
    String osName = System.getProperty("os.name").toLowerCase();
    String userHome = System.getProperty("user.home");
    
    if (osName.contains("mac") || osName.contains("darwin")) {
      return Paths.get(userHome, "Library", "Application Support", "minecraft");
    } else if (osName.contains("win")) {
      String appData = System.getenv("APPDATA");
      if (appData != null) {
        return Paths.get(appData, ".minecraft");
      }
      return Paths.get(userHome, "AppData", "Roaming", ".minecraft");
    } else {
      // Linux and other Unix-like systems
      return Paths.get(userHome, ".minecraft");
    }
  }

  /**
   * Opens the Minecraft directory in the system's default file explorer
   */
  private void openScreenshotsDirectory() {
    try {
      String osName = System.getProperty("os.name").toLowerCase();
      Path screenshotsDir = getMinecraftDirectory().resolve("screenshots");
      File dirFile = screenshotsDir.toFile();

      // Create directory if it doesn't exist
      if (!dirFile.exists()) {
        dirFile.mkdirs();
      }
      
      if (osName.contains("mac") || osName.contains("darwin")) {
        Runtime.getRuntime().exec(new String[]{"open", screenshotsDir.toString()});
      } else if (osName.contains("win")) {
        Runtime.getRuntime().exec(new String[]{"explorer", screenshotsDir.toString()});
      } else {
        // Linux - try xdg-open
        Runtime.getRuntime().exec(new String[]{"xdg-open", screenshotsDir.toString()});
      }
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
    initializeGUI();
    loadUsernameFromOptions();
    String vers = loadLastUsedVersion();
    if (vers != null) {
      System.out.println("DEBUG: Attempting to set version to: " + vers);
      boolean found = false;
      for (int i = 0; i < versionComboBox.getItemCount(); i++) {
        if (versionComboBox.getItemAt(i).equals(vers)) {
          versionComboBox.setSelectedIndex(i);
          found = true;
          System.out.println("DEBUG: Successfully set version to: " + vers);
          break;
        }
      }
      if (!found) {
        System.out.println("DEBUG: Version " + vers + " not found in combo box, keeping default");
      }
    } else {
      System.out.println("DEBUG: No version loaded from file, using default");
    }
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
    // Combine Username and Version into a cleaner look if possible, or just stack them.
    // Let's stack them.
    
    // Version ComboBox styling
    versionComboBox.setBackground(new Color(45, 45, 45));
    versionComboBox.setForeground(Color.WHITE);
    versionComboBox.setOpaque(true);
    
    // Custom renderer to ensure text is visible on all platforms (especially macOS)
    versionComboBox.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (isSelected) {
          setBackground(new Color(76, 175, 80));
          setForeground(Color.WHITE);
        } else {
          setBackground(new Color(45, 45, 45));
          setForeground(Color.WHITE);
        }
        return this;
      }
    });
    
    JPanel formPanel = new JPanel(new GridLayout(2, 1, 0, 10));
    formPanel.setBackground(new Color(20, 20, 20));
    formPanel.add(usernameField);
    
    JPanel versionPanel = new JPanel(new BorderLayout());
    versionPanel.setBackground(new Color(20, 20, 20));
    
    refreshVersionsButton = new JButton("↻");
    refreshVersionsButton.setPreferredSize(new Dimension(30, 30));
    refreshVersionsButton.setToolTipText("Refresh versions");
    refreshVersionsButton.setBackground(new Color(45, 45, 45));
    refreshVersionsButton.setForeground(Color.WHITE);
    refreshVersionsButton.setBorderPainted(false);
    refreshVersionsButton.setFocusPainted(false);
    
    versionPanel.add(versionComboBox, BorderLayout.CENTER);
    // versionPanel.add(refreshVersionsButton, BorderLayout.EAST);
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
      if (!username.equals(originalUsername)) {
        saveOptionsToFile();
      } else {
        // Still save version even if username hasn't changed
        saveOptionsToFile();
      }

      final String finalUsername = username;
      String versionId = versionComboBox.getSelectedItem().toString();

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
          if (minecraftProcess == null || !minecraftProcess.isAlive()) {
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
      String versionId = versionComboBox.getSelectedItem().toString();
      if (versionId == null) {
        JOptionPane.showMessageDialog(OpenCraftLauncher.this,
            "Please select a Minecraft version first.",
            "No Version Selected",
            JOptionPane.WARNING_MESSAGE);
        return;
      }

      // Save selected version to options
      saveOptionsToFile();

      downloadButton.setEnabled(false);
      downloadButton.setText("Downloading...");
      // Run downloader in a separate thread
      SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
        @Override
        protected Void doInBackground() throws Exception {
          try {
            publish("Starting Minecraft " + versionId + " download...");
            java.nio.file.Path baseDir = getMinecraftDirectory();

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

            // Redirect System.out to capture download progress
            java.io.PrintStream originalOut = System.out;
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            System.setOut(new java.io.PrintStream(baos));

            MinecraftDownloader.downloadMinecraft(targetVersion, baseDir);

            // Restore original System.out
            System.setOut(originalOut);

            // Publish the captured output
            String output = baos.toString();
            for (String line : output.split("\n")) {
              if (!line.trim().isEmpty()) {
                publish(line);
              }
            }

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
            System.out.println("Download: " + message);
          }
        }

        @Override
        protected void done() {
          downloadButton.setEnabled(true);
          downloadButton.setText("Download");
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
      Path minecraftDir = getMinecraftDirectory();

      // Check if required files exist
      File librariesFile = minecraftDir.resolve("libraries_" + versionId + ".txt").toFile();
      File opencraftOptions = minecraftDir.resolve("opencraft_options.txt").toFile();
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

      if (!opencraftOptions.exists()) {
        SwingUtilities.invokeLater(() -> {
          System.out.println("DEBUG: opencraft_options.txt not found, creating with default settings...");
        });
        try {
          // Create the file with default user setting
          String defaultOptions = "username:OpenCitizen\nversionId:1.21\n";
          Files.write(opencraftOptions.toPath(), defaultOptions.getBytes());
          SwingUtilities.invokeLater(() -> {
            System.out.println("DEBUG: Created default opencraft_options.txt");
          });
        } catch (IOException ioException) {
          SwingUtilities.invokeLater(() -> {
            System.out.println("DEBUG: Error creating opencraft_options.txt: " +
                ioException.getMessage());
          });
          return;
        }
      }

      // Read libraries path
      String librariesPath = Files.readString(minecraftDir.resolve("libraries_" + versionId + ".txt")).trim();

      // Read version manifest to get asset index
      com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      com.fasterxml.jackson.databind.JsonNode versionManifest = mapper.readTree(versionJson);
      String assetIndex = versionManifest.path("assetIndex").path("id").asText();

      // Build the command
      ProcessBuilder pb = new ProcessBuilder();

      // Check if running on macOS and add -XstartOnFirstThread flag
      String osName = System.getProperty("os.name").toLowerCase();
      boolean isMac = osName.contains("mac") || osName.contains("darwin");

      String minecraftDirStr = minecraftDir.toString();
      String versionJarPath = minecraftDir.resolve("versions/" + versionId + "/" + versionId + ".jar").toString();
      String nativesPath = minecraftDir.resolve("libraries/natives").toString();
      String assetsPath = minecraftDir.resolve("assets").toString();

      if (isMac) {
        pb.command(
            "java",
            "-XstartOnFirstThread",
            "-cp", librariesPath + File.pathSeparator + versionJarPath,
            "-Xmx2G",
            "-Xms1G",
            "-Djava.library.path=" + nativesPath,
            "-Dfile.encoding=UTF-8",
            "net.minecraft.client.main.Main",
            "--version", versionId,
            "--accessToken", "dummy",
            "--uuid", "0B004000-00E0-00A0-0500-000000700000",
            "--username", username,
            "--userType", "legacy",
            "--versionType", "release",
            "--gameDir", minecraftDirStr,
            "--assetsDir", assetsPath,
            "--assetIndex", assetIndex,
            "--clientId", "dummy");
      } else {
        pb.command(
            "java",
            "-cp", librariesPath + File.pathSeparator + versionJarPath,
            "-Xmx4G",
            "-Xms1G",
            "-Djava.library.path=" + nativesPath,
            "-Dfile.encoding=UTF-8",
            "net.minecraft.client.main.Main",
            "--version", versionId,
            "--accessToken", "dummy",
            "--uuid", "0B004000-00E0-00A0-0500-000000700000",
            "--username", username,
            "--userType", "legacy",
            "--versionType", "release",
            "--gameDir", minecraftDirStr,
            "--assetsDir", assetsPath,
            "--assetIndex", assetIndex,
            "--clientId", "dummy");
      }

      // Set working directory to the Minecraft directory (important for world saving)
      pb.directory(minecraftDir.toFile());

      // On Windows, redirect I/O to prevent process blocking
      if (osName.contains("win")) {
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
      } else {
        pb.inheritIO();
      }

      // Start the process
      minecraftProcess = pb.start();

      // Don't wait for process - let it run independently
      // This prevents the launcher from freezing and allows proper game exit
      SwingUtilities.invokeLater(() -> {
        System.out.println("DEBUG: Minecraft process started successfully");
        playButton.setText("Running");
      });

    } catch (IOException e) {
      minecraftProcess = null;
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
    new Thread(() -> {
      try {
        if (minecraftProcess != null && minecraftProcess.isAlive()) {
          int exitCode = minecraftProcess.waitFor();
          System.out.println("DEBUG: Minecraft process exited with code: " + exitCode);
        }
      } catch (InterruptedException e) {
        System.out.println("DEBUG: Minecraft monitoring interrupted");
        Thread.currentThread().interrupt();
      } finally {
        minecraftProcess = null;
        SwingUtilities.invokeLater(() -> {
          playButton.setEnabled(true);
          playButton.setText("Play");
          downloadButton.setEnabled(true);
        });
      }
    }, "Minecraft-Monitor").start();
  }

  /**
   * Loads username and version from opencraft_options.txt file if it exists
   */
  private void loadUsernameFromOptions() {
    File optionsFile = getMinecraftDirectory().resolve("opencraft_options.txt").toFile();
    if (optionsFile.exists()) {
      try {
        String content = Files.readString(optionsFile.toPath()).trim();
        String[] lines = content.split("\n");

        for (String line : lines) {
          if (line.startsWith("username:")) {
            String username = line.substring("username:".length());
            originalUsername = username;
            usernameField.setText(username);
          }
        }

        if (originalUsername == null) {
          // Handle old format - single line with username
          if (content.startsWith("username:")) {
            String username = content.substring("username:".length());
            originalUsername = username;
            usernameField.setText(username);
          } else {
            originalUsername = "OpenCitizen";
          }
        }
      } catch (IOException e) {
        originalUsername = "OpenCitizen";
      }
    } else {
      originalUsername = "OpenCitizen";
    }
  }

  /**
   * Saves username and version to opencraft_options.txt file
   */
  private void saveOptionsToFile() {
    Path optionsPath = getMinecraftDirectory().resolve("opencraft_options.txt");
    try {
      Files.createDirectories(optionsPath.getParent());

      StringBuilder options = new StringBuilder();
      String username = usernameField.getText().trim();
      String version = versionComboBox.getSelectedItem().toString();

      options.append("username:").append(username).append("\n");
      options.append("versionId:").append(version).append("\n");

      System.out.println("DEBUG: Saving username: " + username + ", version: " + version);
      Files.writeString(optionsPath, options.toString(), StandardCharsets.UTF_8);
      originalUsername = username;
      System.out.println("DEBUG: Successfully saved options to file");
    } catch (IOException e) {
      System.out.println("DEBUG: Error saving options: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Loads the last used version from opencraft_options.txt file
   */
  private String loadLastUsedVersion() {
    File optionsFile = getMinecraftDirectory().resolve("opencraft_options.txt").toFile();
    if (optionsFile.exists()) {
      try {
        String content = Files.readString(optionsFile.toPath()).trim();
        String[] lines = content.split("\n");

        for (String line : lines) {
          if (line.startsWith("versionId:")) {
            String version = line.substring("versionId:".length());
            System.out.println("DEBUG: Loaded version from file: " + version);
            return version;
          }
        }
        System.out.println("DEBUG: No versionId line found in options file");
      } catch (IOException e) {
        System.out.println("DEBUG: Error reading options file: " + e.getMessage());
      }
    } else {
      System.out.println("DEBUG: Options file does not exist");
    }
    return null; // No saved version
  }

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
