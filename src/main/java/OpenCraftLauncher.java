package com.opencraft;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;

/**
 * OpenCraft Launcher - A simple GUI launcher for Minecraft
 * This class provides a simple Swing-based interface to launch Minecraft
 */
public class OpenCraftLauncher extends JFrame {
  private static final long serialVersionUID = 1L;

  private JTextField usernameField;
  private JLabel emailLabel; // Label to display authenticated user's email
  private JButton playButton;
  private JButton downloadButton;
  private JButton loginButton;
  private JTextArea outputArea;
  private String originalUsername; // Track the original username from file
  private String authenticatedEmail; // Store authenticated user's email
  private boolean isAuthenticated = false; // Authentication status
  private transient HttpClient httpClient;
  private transient ServerSocket callbackServer; // For receiving OAuth callback
  private static final int CALLBACK_PORT = 8080; // Local port for OAuth callback
  private static final String AUTH_URL = "http://localhost:8000/auth/google"; // Your website auth URL

  @SuppressWarnings("this-escape")
  public OpenCraftLauncher() {
    httpClient = HttpClient.newHttpClient();
    initializeGUI();
    loadUsernameFromOptions(); // Load username on startup
  }

  private void initializeGUI() {
    setTitle("OpenCraft Launcher");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(900, 450);
    setLocationRelativeTo(null);

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

    // Email row (initially hidden)
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0;
    JLabel emailLabelText = new JLabel("Email:");
    emailLabelText.setVisible(false);
    topPanel.add(emailLabelText, gbc);
    gbc.gridx = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;
    emailLabel = new JLabel("");
    emailLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
    emailLabel.setOpaque(true);
    emailLabel.setBackground(Color.LIGHT_GRAY);
    emailLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    emailLabel.setVisible(false);
    topPanel.add(emailLabel, gbc);

    // Store references to email components for show/hide
    emailLabel.putClientProperty("emailLabelText", emailLabelText);

    // Buttons row
    gbc.gridx = 0;
    gbc.gridy = 2;
    gbc.gridwidth = 2;
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0;
    JPanel buttonPanel = new JPanel(new FlowLayout());

    loginButton = new JButton("Login");
    downloadButton = new JButton("Download");
    playButton = new JButton("Play");

    // Initially disable play button until authenticated
    // playButton.setEnabled(false);

    buttonPanel.add(loginButton);
    buttonPanel.add(downloadButton);
    buttonPanel.add(playButton);
    topPanel.add(buttonPanel, gbc);

    // Create output area
    outputArea = new JTextArea();
    outputArea.setEditable(false);
    outputArea.setBackground(Color.BLACK);
    outputArea.setForeground(Color.WHITE);
    outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    JScrollPane scrollPane = new JScrollPane(outputArea);
    scrollPane.setPreferredSize(new Dimension(480, 300));

    mainPanel.add(topPanel, BorderLayout.NORTH);
    mainPanel.add(scrollPane, BorderLayout.CENTER);

    add(mainPanel);

    // Add action listeners
    loginButton.addActionListener(new LoginButtonListener());
    playButton.addActionListener(new PlayButtonListener());
    downloadButton.addActionListener(new DownloadButtonListener());

    // Set initial focus
    SwingUtilities.invokeLater(() -> usernameField.requestFocus());

    // Add authentication status to output
    // outputArea.append("Authentication status: Not authenticated\n");
    // outputArea.append("Please login to enable the Play button.\n\n");
  }

  private class LoginButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      if (isAuthenticated) {
        // If already authenticated, allow logout
        logout();
        return;
      }

      loginButton.setEnabled(false);
      loginButton.setText("Authenticating...");
      outputArea.append("Starting authentication process...\n");

      // Start authentication in a separate thread
      SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
        @Override
        protected Void doInBackground() throws Exception {
          authenticate();
          return null;
        }

        @Override
        protected void process(java.util.List<String> chunks) {
          for (String message : chunks) {
            outputArea.append(message + "\n");
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
          }
        }

        @Override
        protected void done() {
          // Button state will be updated by authentication result
        }
      };

      worker.execute();
    }

    private void authenticate() {
      try {
        // Start local callback server
        callbackServer = new ServerSocket(CALLBACK_PORT);
        SwingUtilities.invokeLater(() -> {
          outputArea.append("Started local callback server on port " + CALLBACK_PORT + "\n");
          outputArea.append("Opening browser for authentication...\n");
        });

        // Open browser with authentication URL
        String authUrlWithCallback = AUTH_URL + "?callback=http://localhost:" + CALLBACK_PORT + "/callback";
        Desktop.getDesktop().browse(URI.create(authUrlWithCallback));

        SwingUtilities.invokeLater(() -> {
          outputArea.append("Please complete authentication in your browser.\n");
          outputArea.append("Waiting for authentication response...\n");
        });

        // Wait for callback
        Socket clientSocket = callbackServer.accept();
        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);

        // Read the HTTP request
        String requestLine = reader.readLine();
        SwingUtilities.invokeLater(() -> {
          outputArea.append("Received callback: " + requestLine + "\n");
        });

        // Parse the request to extract authentication result
        boolean authSuccess = false;
        String username = null;
        String characterName = null;

        if (requestLine != null && requestLine.startsWith("GET /callback")) {
          // Extract query parameters
          String[] parts = requestLine.split(" ");
          if (parts.length >= 2) {
            String path = parts[1];
            if (path.contains("?")) {
              String queryString = path.split("\\?")[1];
              String[] params = queryString.split("&");

              for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2) {
                  String key = keyValue[0];
                  String value = java.net.URLDecoder.decode(keyValue[1], "UTF-8");

                  if ("success".equals(key) && "true".equals(value)) {
                    authSuccess = true;
                  } else if ("username".equals(key)) {
                    username = value;
                  } else if ("character_name".equals(key)) {
                    characterName = value;
                  }
                }
              }
            }
          }
        }

        // Send response to browser
        String responseBody;
        if (authSuccess) {
          responseBody = "<html><body><h2>Authentication Successful!</h2><p>You can close this window and return to OpenCraft Launcher.</p></body></html>";
        } else {
          responseBody = "<html><body><h2>Authentication Failed</h2><p>Please try again.</p></body></html>";
        }

        writer.println("HTTP/1.1 200 OK");
        writer.println("Content-Type: text/html");
        writer.println("Content-Length: " + responseBody.length());
        writer.println();
        writer.println(responseBody);
        writer.flush();

        // Close connections
        clientSocket.close();
        callbackServer.close();

        // Update UI based on authentication result
        final boolean finalAuthSuccess = authSuccess;
        final String finalUsername = username;
        final String finalCharacterName = characterName;

        SwingUtilities.invokeLater(() -> {
          if (finalAuthSuccess) {
            isAuthenticated = true;
            authenticatedEmail = finalUsername; // Store the email
            loginButton.setText("Logout");
            loginButton.setEnabled(true);
            playButton.setEnabled(true);

            // Show email and disable username field
            showEmailAndDisableUsername(finalUsername);

            outputArea.append("Authentication successful!\n");
            outputArea.append("isAuthenticated = " + isAuthenticated + "\n");

            if (finalUsername != null) {
              outputArea.append("Authenticated as: " + finalUsername + "\n");

              // Use character name if available, otherwise use email as username
              if (finalCharacterName != null && !finalCharacterName.trim().isEmpty()) {
                usernameField.setText(finalCharacterName);
                outputArea.append("Character name: " + finalCharacterName + "\n");
              } else {
                usernameField.setText(finalUsername);
                outputArea.append("No character name set. Using email as username.\n");
              }
            }

            outputArea.append("Play button is now enabled.\n\n");
          } else {
            outputArea.append("Authentication failed. Please try again.\n");
            outputArea.append("isAuthenticated = " + isAuthenticated + "\n\n");
            loginButton.setText("Login");
            loginButton.setEnabled(true);
          }
        });

      } catch (Exception ex) {
        SwingUtilities.invokeLater(() -> {
          outputArea.append("Authentication error: " + ex.getMessage() + "\n");
          outputArea.append("isAuthenticated = " + isAuthenticated + "\n\n");
          loginButton.setText("Login");
          loginButton.setEnabled(true);
        });

        try {
          if (callbackServer != null && !callbackServer.isClosed()) {
            callbackServer.close();
          }
        } catch (IOException closeEx) {
          // Ignore close errors
        }
      }
    }

    private void logout() {
      isAuthenticated = false;
      authenticatedEmail = null;
      loginButton.setText("Login");
      playButton.setEnabled(false);

      // Hide email and enable username field
      hideEmailAndEnableUsername();

      outputArea.append("Logged out successfully.\n");
      outputArea.append("isAuthenticated = " + isAuthenticated + "\n");
      outputArea.append("Please login to enable the Play button.\n\n");
    }
  }

  private class PlayButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      // Check if user is authenticated
      // if (!isAuthenticated) {
      // outputArea.append("Please login first before playing.\n");
      // return;
      // }

      String username = usernameField.getText().trim();
      if (username.isEmpty()) {
        username = "OpenCitizen";
      }

      // Only save username to options file if user is not authenticated (field is
      // editable)
      // When authenticated, the username field is disabled and shouldn't be saved
      // if (!isAuthenticated && !username.equals(originalUsername)) {
      // saveUsernameToOptions(username);
      // }

      final String finalUsername = username;

      // playButton.setEnabled(false);
      playButton.setText("Starting...");
      outputArea.setText("");

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
            outputArea.append(message + "\n");
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
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
      downloadButton.setEnabled(false);
      downloadButton.setText("Downloading...");
      outputArea.setText("");

      // Run downloader in a separate thread
      SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
        @Override
        protected Void doInBackground() throws Exception {
          try {
            publish("Starting Minecraft download...");
            String manifestUrl = "https://piston-meta.mojang.com/v1/packages/ff7e92039cfb1dca99bad680f278c40edd82f0e1/1.21.json";
            java.nio.file.Path baseDir = java.nio.file.Path.of("minecraft");

            // Redirect System.out to capture download progress
            java.io.PrintStream originalOut = System.out;
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            System.setOut(new java.io.PrintStream(baos));

            MinecraftDownloader.downloadMinecraft(manifestUrl, baseDir);

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
            outputArea.append(message + "\n");
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
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
      // Check if required files exist
      File librariesFile = new File("minecraft/libraries.txt");
      File opencraftOptions = new File("minecraft/opencraft_options.txt");
      File minecraftJar = new File("minecraft/versions/1.21/1.21.jar");

      if (!librariesFile.exists()) {
        SwingUtilities.invokeLater(() -> {
          outputArea.append("Error: minecraft/libraries.txt not found!\n");
          outputArea.append("Please run the downloader first.\n");
        });
        return;
      }

      if (!opencraftOptions.exists()) {
        SwingUtilities.invokeLater(() -> {
          outputArea.append("Creating opencraft_options.txt with default settings...\n");
        });
        try {
          // Create the file with default user setting
          String defaultOptions = "username:OpenCitizen\n";
          Files.write(opencraftOptions.toPath(), defaultOptions.getBytes());
          SwingUtilities.invokeLater(() -> {
            outputArea.append("Created opencraft_options.txt successfully.\n");
          });
        } catch (IOException ioException) {
          SwingUtilities.invokeLater(() -> {
            outputArea.append("Error creating opencraft_options.txt: " + ioException.getMessage() + "\n");
          });
          return;
        }
      }

      if (!minecraftJar.exists()) {
        SwingUtilities.invokeLater(() -> {
          outputArea.append("Error: minecraft/versions/1.21/1.21.jar not found!\n");
          outputArea.append("Please run the downloader first.\n");
        });
        return;
      }

      SwingUtilities.invokeLater(() -> {
        outputArea.append("Starting Minecraft for user: " + username + "\n");
        outputArea.append("Loading libraries...\n");
      });

      // Read libraries path
      String librariesPath = Files.readString(Paths.get("minecraft/libraries.txt")).trim();

      // Build the command
      ProcessBuilder pb = new ProcessBuilder();
      
      // Check if running on macOS and add -XstartOnFirstThread flag
      String osName = System.getProperty("os.name").toLowerCase();
      boolean isMac = osName.contains("mac") || osName.contains("darwin");
      
      if (isMac) {
        pb.command(
            "java",
            "-XstartOnFirstThread",
            "-cp", librariesPath + File.pathSeparator + "minecraft/versions/1.21/1.21.jar",
            "-Xmx2G",
            "-Xms1G",
            "-Djava.library.path=minecraft/libraries/natives",
            "-Dfile.encoding=UTF-8",
            "net.minecraft.client.main.Main",
            "--version", "1.21",
            "--accessToken", "dummy",
            "--uuid", "0B004000-00E0-00A0-0500-000000700000",
            "--username", username,
            "--userType", "legacy",
            "--versionType", "release",
            "--gameDir", "minecraft",
            "--assetsDir", "minecraft/assets",
            "--assetIndex", "17",
            "--clientId", "dummy");
      } else {
        pb.command(
            "java",
            "-cp", librariesPath + File.pathSeparator + "minecraft/versions/1.21/1.21.jar",
            "-Xmx2G",
            "-Xms1G",
            "-Djava.library.path=minecraft/libraries/natives",
            "-Dfile.encoding=UTF-8",
            "net.minecraft.client.main.Main",
            "--version", "1.21",
            "--accessToken", "dummy",
            "--uuid", "0B004000-00E0-00A0-0500-000000700000",
            "--username", username,
            "--userType", "legacy",
            "--versionType", "release",
            "--gameDir", "minecraft",
            "--assetsDir", "minecraft/assets",
            "--assetIndex", "17",
            "--clientId", "dummy");
      }

      // Set working directory to the project root
      pb.directory(new File("."));

      SwingUtilities.invokeLater(() -> {
        outputArea.append("Launching Minecraft...\n");
        outputArea.append("Command: " + String.join(" ", pb.command()) + "\n");
        outputArea.append("Working directory: " + pb.directory().getAbsolutePath() + "\n");
        outputArea.append("=====================================\n");
      });

      // Start the process
      Process process = pb.start();

      // Read output from the process
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          final String outputLine = line;
          SwingUtilities.invokeLater(() -> {
            outputArea.append(outputLine + "\n");
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
          });
        }
      }

      // Wait for process to complete
      int exitCode = process.waitFor();
      SwingUtilities.invokeLater(() -> {
        outputArea.append("=====================================\n");
        outputArea.append("Minecraft exited with code: " + exitCode + "\n");
      });

    } catch (IOException e) {
      SwingUtilities.invokeLater(() -> {
        outputArea.append("Error starting Minecraft: " + e.getMessage() + "\n");
        e.printStackTrace();
      });
    } catch (InterruptedException e) {
      SwingUtilities.invokeLater(() -> {
        outputArea.append("Minecraft launch was interrupted: " + e.getMessage() + "\n");
      });
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Shows the email label and disables the username field when authenticated
   */
  private void showEmailAndDisableUsername(String email) {
    emailLabel.setText(email);
    emailLabel.setVisible(true);

    // Show the "Email:" label too
    JLabel emailLabelText = (JLabel) emailLabel.getClientProperty("emailLabelText");
    if (emailLabelText != null) {
      emailLabelText.setVisible(true);
    }

    // Disable username field
    usernameField.setEnabled(false);
    usernameField.setBackground(Color.LIGHT_GRAY);

    // Force layout refresh
    revalidate();
    repaint();
  }

  /**
   * Hides the email label and enables the username field when logged out
   */
  private void hideEmailAndEnableUsername() {
    emailLabel.setVisible(false);

    // Hide the "Email:" label too
    JLabel emailLabelText = (JLabel) emailLabel.getClientProperty("emailLabelText");
    if (emailLabelText != null) {
      emailLabelText.setVisible(false);
    }

    // Enable username field and restore original background
    usernameField.setEnabled(true);
    usernameField.setBackground(Color.WHITE);

    // Force layout refresh
    revalidate();
    repaint();
  }

  /**
   * Loads username from opencraft_options.txt file if it exists
   */
  private void loadUsernameFromOptions() {
    File optionsFile = new File("minecraft/opencraft_options.txt");
    if (optionsFile.exists()) {
      try {
        String content = Files.readString(optionsFile.toPath()).trim();
        if (content.startsWith("username:")) {
          String username = content.substring("username:".length());
          originalUsername = username;
          usernameField.setText(username);
          outputArea.append("Loaded username from options: " + username + "\n");
        } else {
          // Handle old format or other formats
          originalUsername = "OpenCitizen";
        }
      } catch (IOException e) {
        outputArea.append("Error reading opencraft_options.txt: " + e.getMessage() + "\n");
        originalUsername = "OpenCitizen";
      }
    } else {
      originalUsername = "OpenCitizen";
    }
  }

  /**
   * Saves username to opencraft_options.txt file
   */
  private void saveUsernameToOptions(String username) {
    File optionsFile = new File("minecraft/opencraft_options.txt");
    try {
      // Create minecraft directory if it doesn't exist
      optionsFile.getParentFile().mkdirs();

      String content = "username:" + username;
      Files.write(optionsFile.toPath(), content.getBytes());
      originalUsername = username; // Update tracked original username
      outputArea.append("Saved username to options: " + username + "\n");
    } catch (IOException e) {
      outputArea.append("Error saving username to opencraft_options.txt: " + e.getMessage() + "\n");
    }
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      new OpenCraftLauncher().setVisible(true);
    });
  }
}
