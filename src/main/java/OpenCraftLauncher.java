package com.opencraft;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * OpenCraft Launcher - A simple GUI launcher for Minecraft
 * This class provides a simple Swing-based interface to launch Minecraft
 */
public class OpenCraftLauncher extends JFrame {
    private JTextField usernameField;
    private JButton playButton;
    private JButton downloadButton;
    private JTextArea outputArea;
    private String originalUsername; // Track the original username from file
    
    public OpenCraftLauncher() {
        initializeGUI();
        loadUsernameFromOptions(); // Load username on startup
    }
    
    private void initializeGUI() {
        setTitle("OpenCraft Launcher");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 400);
        setLocationRelativeTo(null);
        
        // Create main panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Create top panel for username input
        JPanel topPanel = new JPanel(new FlowLayout());
        JLabel usernameLabel = new JLabel("Username:");
        usernameField = new JTextField("OpenCitizen", 15);
        downloadButton = new JButton("Download");
        playButton = new JButton("Play");
        
        topPanel.add(usernameLabel);
        topPanel.add(usernameField);
        topPanel.add(downloadButton);
        topPanel.add(playButton);
        
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
        
        // Add action listener to play button
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
            
            // Save username to options file if it has changed
            if (!username.equals(originalUsername)) {
                saveUsernameToOptions(username);
            }
            
            final String finalUsername = username;
            
            playButton.setEnabled(false);
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
                "--clientId", "dummy"
            );
            
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
            try (var reader = process.inputReader()) {
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