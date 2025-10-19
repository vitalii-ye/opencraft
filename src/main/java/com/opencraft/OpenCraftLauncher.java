package com.opencraft;

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
import java.util.List;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class OpenCraftLauncher extends JFrame {
  private static final long serialVersionUID = 1L;
  private JTextField usernameField;
  private JComboBox<MinecraftVersionManager.MinecraftVersion> versionComboBox;
  private JButton playButton;
  private JButton downloadButton;
  private JButton refreshVersionsButton;
  private String originalUsername; // Track the original username from file

  @SuppressWarnings("this-escape")
  public OpenCraftLauncher() {
    initializeGUI();
    loadUsernameFromOptions(); // Load username on startup
    loadAvailableVersions(); // Load available Minecraft versions
  }

  private void initializeGUI() {
    setTitle("OpenCraft Launcher");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(600, 300);

    // Create main panel
    JPanel mainPanel = new JPanel(new BorderLayout());
    mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    // Create top panel for user input and buttons
    JPanel topPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(5, 5, 5, 5);

    // Username row
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.WEST;
    topPanel.add(new JLabel("Username:"), gbc);
    gbc.gridx = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;
    usernameField = new JTextField("OpenCitizen", 15);
    topPanel.add(usernameField, gbc);

    // Version row
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0;
    topPanel.add(new JLabel("Version:"), gbc);
    gbc.gridx = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;
    
    JPanel versionPanel = new JPanel(new BorderLayout());
    versionComboBox = new JComboBox<>();
    versionComboBox.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                    boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof MinecraftVersionManager.MinecraftVersion) {
          MinecraftVersionManager.MinecraftVersion version = (MinecraftVersionManager.MinecraftVersion) value;
          setText(version.getId() + " (" + version.getType() + ")");
        }
        return this;
      }
    });
    
    refreshVersionsButton = new JButton("↻");
    refreshVersionsButton.setPreferredSize(new Dimension(30, 25));
    refreshVersionsButton.setToolTipText("Refresh versions");
    
    versionPanel.add(versionComboBox, BorderLayout.CENTER);
    versionPanel.add(refreshVersionsButton, BorderLayout.EAST);
    topPanel.add(versionPanel, gbc);

    // Email row (initially hidden)
    gbc.gridx = 0;
    gbc.gridy = 2;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0;
    // JLabel emailLabelText = new JLabel("Email:");
    // emailLabelText.setVisible(false);
    // topPanel.add(emailLabelText, gbc);
    gbc.gridx = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;
    // emailLabel = new JLabel("");
    // emailLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
    // emailLabel.setOpaque(true);
    // emailLabel.setBackground(Color.LIGHT_GRAY);
    // emailLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    // emailLabel.setVisible(false);
    // topPanel.add(emailLabel, gbc);

    // Store references to email components for show/hide
    // emailLabel.putClientProperty("emailLabelText", emailLabelText);

    // Buttons row
    gbc.gridx = 1;
    gbc.gridy = 3;
    gbc.gridwidth = 2;
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0;
    JPanel buttonPanel = new JPanel(new GridLayout(2, 1));
    buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));

    downloadButton = new JButton("Download");
    playButton = new JButton("Play");

    buttonPanel.add(downloadButton);
    buttonPanel.add(playButton);
    topPanel.add(buttonPanel, gbc);

    mainPanel.add(topPanel, BorderLayout.NORTH);

    add(mainPanel);

    // Add action listeners
    playButton.addActionListener(new PlayButtonListener());
    downloadButton.addActionListener(new DownloadButtonListener());
    refreshVersionsButton.addActionListener(e -> loadAvailableVersions());

    // Set initial focus
    SwingUtilities.invokeLater(() -> usernameField.requestFocus());
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

      // playButton.setEnabled(false);
      playButton.setText("Starting...");
      // outputArea.setText("");

      // Run minecraft launcher in a separate thread
      SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
        @Override
        protected Void doInBackground() throws Exception {
          launchMinecraft(finalUsername);
          return null;
        }

        @Override
        protected void process(java.util.List<String> chunks) {
          for (String message : chunks) {
            // outputArea.append(message + "\n");
            // outputArea.setCaretPosition(outputArea.getDocument().getLength());
          }
        }

        @Override
        protected void done() {
          playButton.setEnabled(true);
          playButton.setText("Play");
        }
      };

      worker.execute();
    }
  }

  private class DownloadButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      MinecraftVersionManager.MinecraftVersion selectedVersion = (MinecraftVersionManager.MinecraftVersion) versionComboBox.getSelectedItem();
      if (selectedVersion == null) {
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
      // outputArea.setText("");

      final String versionId = selectedVersion.getId();
      final String manifestUrl = selectedVersion.getUrl();

      // Run downloader in a separate thread
      SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
        @Override
        protected Void doInBackground() throws Exception {
          try {
            publish("Starting Minecraft " + versionId + " download...");
            java.nio.file.Path baseDir = java.nio.file.Path.of("minecraft");

            // Redirect System.out to capture download progress
            java.io.PrintStream originalOut = System.out;
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            System.setOut(new java.io.PrintStream(baos));

            MinecraftDownloader.downloadMinecraft(manifestUrl, baseDir, versionId);

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
            // outputArea.append(message + "\n");
            // outputArea.setCaretPosition(outputArea.getDocument().getLength());
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

  private void launchMinecraft(String username) {
    try {
      MinecraftVersionManager.MinecraftVersion selectedVersion = (MinecraftVersionManager.MinecraftVersion) versionComboBox.getSelectedItem();
      if (selectedVersion == null) {
        SwingUtilities.invokeLater(() -> {
          JOptionPane.showMessageDialog(OpenCraftLauncher.this, 
              "Please select a Minecraft version first.", 
              "No Version Selected", 
              JOptionPane.WARNING_MESSAGE);
        });
        return;
      }

      String versionId = selectedVersion.getId();

      // Check if required files exist
      File librariesFile = new File("minecraft/libraries_" + versionId + ".txt");
      File opencraftOptions = new File("minecraft/opencraft_options.txt");
      File minecraftJar = new File("minecraft/versions/" + versionId + "/" + versionId + ".jar");
      File versionJson = new File("minecraft/versions/" + versionId + "/" + versionId + ".json");

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
          // outputArea.append("Creating opencraft_options.txt with default
          // settings...\n");
        });
        try {
          // Create the file with default user setting
          String defaultOptions = "username:OpenCitizen\n";
          Files.write(opencraftOptions.toPath(), defaultOptions.getBytes());
          SwingUtilities.invokeLater(() -> {
            // outputArea.append("Created opencraft_options.txt successfully.\n");
          });
        } catch (IOException ioException) {
          SwingUtilities.invokeLater(() -> {
            // outputArea.append("Error creating opencraft_options.txt: " +
            // ioException.getMessage() + "\n");
          });
          return;
        }
      }

      SwingUtilities.invokeLater(() -> {
        // outputArea.append("Starting Minecraft " + versionId + " for user: " + username + "\n");
        // outputArea.append("Loading libraries...\n");
      });

      // Read libraries path
      String librariesPath = Files.readString(Paths.get("minecraft/libraries_" + versionId + ".txt")).trim();

      // Read version manifest to get asset index
      com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      com.fasterxml.jackson.databind.JsonNode versionManifest = mapper.readTree(versionJson);
      String assetIndex = versionManifest.path("assetIndex").path("id").asText();

      // Build the command
      ProcessBuilder pb = new ProcessBuilder();

      // Check if running on macOS and add -XstartOnFirstThread flag
      String osName = System.getProperty("os.name").toLowerCase();
      boolean isMac = osName.contains("mac") || osName.contains("darwin");

      if (isMac) {
        pb.command(
            "java",
            "-XstartOnFirstThread",
            "-cp", librariesPath + File.pathSeparator + "minecraft/versions/" + versionId + "/" + versionId + ".jar",
            "-Xmx2G",
            "-Xms1G",
            "-Djava.library.path=minecraft/libraries/natives",
            "-Dfile.encoding=UTF-8",
            "net.minecraft.client.main.Main",
            "--version", versionId,
            "--accessToken", "dummy",
            "--uuid", "0B004000-00E0-00A0-0500-000000700000",
            "--username", username,
            "--userType", "legacy",
            "--versionType", "release",
            "--gameDir", "minecraft",
            "--assetsDir", "minecraft/assets",
            "--assetIndex", assetIndex,
            "--clientId", "dummy");
      } else {
        pb.command(
            "java",
            "-cp", librariesPath + File.pathSeparator + "minecraft/versions/" + versionId + "/" + versionId + ".jar",
            "-Xmx2G",
            "-Xms1G",
            "-Djava.library.path=minecraft/libraries/natives",
            "-Dfile.encoding=UTF-8",
            "net.minecraft.client.main.Main",
            "--version", versionId,
            "--accessToken", "dummy",
            "--uuid", "0B004000-00E0-00A0-0500-000000700000",
            "--username", username,
            "--userType", "legacy",
            "--versionType", "release",
            "--gameDir", "minecraft",
            "--assetsDir", "minecraft/assets",
            "--assetIndex", assetIndex,
            "--clientId", "dummy");
      }

      // Set working directory to the project root
      pb.directory(new File("."));

      SwingUtilities.invokeLater(() -> {
        // outputArea.append("Launching Minecraft...\n");
        // outputArea.append("Command: " + String.join(" ", pb.command()) + "\n");
        // outputArea.append("Working directory: " + pb.directory().getAbsolutePath() +
        // "\n");
        // outputArea.append("=====================================\n");
      });

      // Start the process
      Process process = pb.start();

      // Read output from the process
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          final String outputLine = line;
          SwingUtilities.invokeLater(() -> {
            // outputArea.append(outputLine + "\n");
            // outputArea.setCaretPosition(outputArea.getDocument().getLength());
          });
        }
      }

      // Wait for process to complete
      int exitCode = process.waitFor();
      SwingUtilities.invokeLater(() -> {
        // outputArea.append("=====================================\n");
        // outputArea.append("Minecraft exited with code: " + exitCode + "\n");
      });

    } catch (IOException e) {
      SwingUtilities.invokeLater(() -> {
        // outputArea.append("Error starting Minecraft: " + e.getMessage() + "\n");
        e.printStackTrace();
      });
    } catch (InterruptedException e) {
      SwingUtilities.invokeLater(() -> {
        // outputArea.append("Minecraft launch was interrupted: " + e.getMessage() +
        // "\n");
      });
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Loads username and version from opencraft_options.txt file if it exists
   */
  private void loadUsernameFromOptions() {
    File optionsFile = new File("minecraft/opencraft_options.txt");
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
    Path optionsPath = Paths.get("minecraft/opencraft_options.txt");
    try {
      Files.createDirectories(optionsPath.getParent());
      
      StringBuilder options = new StringBuilder();
      options.append("username:").append(usernameField.getText().trim()).append("\n");
      
      // Save selected version
      MinecraftVersionManager.MinecraftVersion selectedVersion = (MinecraftVersionManager.MinecraftVersion) versionComboBox.getSelectedItem();
      if (selectedVersion != null) {
        options.append("version:").append(selectedVersion.getId()).append("\n");
      }
      
      Files.writeString(optionsPath, options.toString(), StandardCharsets.UTF_8);
      originalUsername = usernameField.getText().trim();
    } catch (IOException e) {
      // Error saving options - could log this if needed
    }
  }

  /**
   * Saves username to opencraft_options.txt file (backward compatibility)
   */
  private void saveUsernameToOptions(String username) {
    saveOptionsToFile();
  }

  /**
   * Loads the last used version from opencraft_options.txt file
   */
  private String loadLastUsedVersion() {
    File optionsFile = new File("minecraft/opencraft_options.txt");
    if (optionsFile.exists()) {
      try {
        String content = Files.readString(optionsFile.toPath()).trim();
        String[] lines = content.split("\n");
        
        for (String line : lines) {
          if (line.startsWith("version:")) {
            return line.substring("version:".length());
          }
        }
      } catch (IOException e) {
        // Error reading file
      }
    }
    return null; // No saved version
  }

  /**
   * Loads available Minecraft versions from the official manifest
   */
  private void loadAvailableVersions() {
    SwingWorker<List<MinecraftVersionManager.MinecraftVersion>, Void> worker = new SwingWorker<List<MinecraftVersionManager.MinecraftVersion>, Void>() {
      @Override
      protected List<MinecraftVersionManager.MinecraftVersion> doInBackground() throws Exception {
        // Only fetch release versions to avoid overwhelming the user with snapshots
        return MinecraftVersionManager.fetchReleaseVersions();
      }

      @Override
      protected void done() {
        try {
          List<MinecraftVersionManager.MinecraftVersion> versions = get();
          
          // Clear existing items
          versionComboBox.removeAllItems();
          
          // Add versions to combo box
          for (MinecraftVersionManager.MinecraftVersion version : versions) {
            versionComboBox.addItem(version);
          }
          
          // Load last used version and set it as selected
          String lastUsedVersion = loadLastUsedVersion();
          boolean versionFound = false;
          
          if (lastUsedVersion != null && !versions.isEmpty()) {
            for (int i = 0; i < versionComboBox.getItemCount(); i++) {
              MinecraftVersionManager.MinecraftVersion version = versionComboBox.getItemAt(i);
              if (version.getId().equals(lastUsedVersion)) {
                versionComboBox.setSelectedIndex(i);
                versionFound = true;
                break;
              }
            }
          }
          
          // If no saved version found or loading failed, set the latest version as default
          if (!versionFound && !versions.isEmpty()) {
            versionComboBox.setSelectedIndex(0);
          }
          
        } catch (Exception e) {
          JOptionPane.showMessageDialog(OpenCraftLauncher.this, 
              "Failed to load Minecraft versions: " + e.getMessage(), 
              "Version Load Error", 
              JOptionPane.ERROR_MESSAGE);
          
          // Add a fallback version (1.21) if loading fails
          versionComboBox.removeAllItems();
          MinecraftVersionManager.MinecraftVersion fallback = new MinecraftVersionManager.MinecraftVersion(
              "1.21", "release", 
              "https://piston-meta.mojang.com/v1/packages/ff7e92039cfb1dca99bad680f278c40edd82f0e1/1.21.json",
              "2024-06-13T12:00:00+00:00");
          versionComboBox.addItem(fallback);
        }
      }
    };
    
    worker.execute();
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      new OpenCraftLauncher().setVisible(true);
    });
  }
}
