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
  private JButton playButton;
  private JButton downloadButton;
  private JButton refreshVersionsButton;
  private String originalUsername; // Track the original username from file

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

    refreshVersionsButton = new JButton("↻");
    refreshVersionsButton.setPreferredSize(new Dimension(30, 25));
    refreshVersionsButton.setToolTipText("Refresh versions");

    versionPanel.add(versionComboBox, BorderLayout.CENTER);
    versionPanel.add(refreshVersionsButton, BorderLayout.EAST);
    topPanel.add(versionPanel, gbc);

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
      String versionId = versionComboBox.getSelectedItem().toString();

      // playButton.setEnabled(false);
      playButton.setText("Starting...");

      // Run minecraft launcher in a separate thread
      SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
        @Override
        protected Void doInBackground() throws Exception {
          launchMinecraft(finalUsername, versionId);
          return null;
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
            java.nio.file.Path baseDir = java.nio.file.Path.of("minecraft");

            // Find the specific version in the Minecraft version manifest
            java.util.List<MinecraftVersionManager.MinecraftVersion> versions = 
                MinecraftVersionManager.fetchAvailableVersions();
            
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

  private void launchMinecraft(String username, String versionId) {
    try {

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
          System.out.println("DEBUG: opencraft_options.txt not found, creating with default settings...");
        });
        try {
          // Create the file with default user setting
          String defaultOptions = "username:OpenCitizen\nversionId:1.21.10\n";
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

      // Start the process
      Process process = pb.start();

      // Wait for process to complete
      int exitCode = process.waitFor();
      SwingUtilities.invokeLater(() -> {
        System.out.println("DEBUG: Minecraft process exited with code: " + exitCode);
      });

    } catch (

    IOException e) {
      SwingUtilities.invokeLater(() -> {
        System.out.println("DEBUG: Error starting Minecraft: " + e.getMessage());
        e.printStackTrace();
      });
    } catch (InterruptedException e) {
      SwingUtilities.invokeLater(() -> {
        System.out.println("DEBUG: Minecraft launch was interrupted: " + e.getMessage());
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
    File optionsFile = new File("minecraft/opencraft_options.txt");
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
    SwingUtilities.invokeLater(() -> {
      new OpenCraftLauncher().setVisible(true);
    });
  }
}
