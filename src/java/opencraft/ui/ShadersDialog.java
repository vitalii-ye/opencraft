package opencraft.ui;

import opencraft.mods.InstalledMod;
import opencraft.mods.ShaderManager;
import opencraft.network.MinecraftVersionManager.MinecraftVersion;
import opencraft.network.ModrinthApiClient.ModrinthProject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Dialog for managing Minecraft shaders.
 * Allows users to search, install, and remove shaders from Modrinth.
 * Automatically installs Iris shader mod when needed.
 */
public class ShadersDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial")
    private final MinecraftVersion version;
    private final String gameVersion;

    private JTextField searchField;
    private JButton searchButton;
    private DefaultListModel<ModrinthProject> searchResultsModel;
    private JList<ModrinthProject> searchResultsList;
    private DefaultListModel<InstalledMod> installedShadersModel;
    private JList<InstalledMod> installedShadersList;
    private JLabel statusLabel;
    private JLabel irisStatusLabel;

    // Colors matching the main launcher theme
    private static final Color BG_COLOR = new Color(20, 20, 20);
    private static final Color PANEL_COLOR = new Color(30, 30, 30);
    private static final Color INPUT_COLOR = new Color(45, 45, 45);
    private static final Color TEXT_COLOR = Color.WHITE;
    private static final Color ACCENT_COLOR = new Color(156, 39, 176); // Purple for shaders

    @SuppressWarnings({"this-escape", "serial"})
    public ShadersDialog(Frame parent, MinecraftVersion version) {
        super(parent, "Manage Shaders - " + version.getDisplayName(), true);
        this.version = version;
        this.gameVersion = version.getBaseGameVersion();

        initializeUI();
        loadInstalledShaders();
        checkIrisStatus();
        
        setSize(600, 500);
        setLocationRelativeTo(parent);
    }

    private void initializeUI() {
        setBackground(BG_COLOR);
        getContentPane().setBackground(BG_COLOR);
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(15, 15, 15, 15));

        // Top panel - Iris status and Search
        JPanel topPanel = createTopPanel();
        add(topPanel, BorderLayout.NORTH);

        // Center panel - Split between installed and search results
        JSplitPane splitPane = createMainContent();
        add(splitPane, BorderLayout.CENTER);

        // Bottom panel - Status
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BG_COLOR);
        panel.setBorder(new EmptyBorder(0, 0, 10, 0));

        // Iris status panel
        JPanel irisPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        irisPanel.setBackground(new Color(40, 40, 40));
        irisPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 60)),
                new EmptyBorder(8, 10, 8, 10)
        ));

        JLabel irisLabel = new JLabel("Iris Shader Mod:");
        irisLabel.setForeground(TEXT_COLOR);
        irisLabel.setFont(new Font("Arial", Font.BOLD, 12));

        irisStatusLabel = new JLabel("Checking...");
        irisStatusLabel.setForeground(new Color(255, 193, 7)); // Amber
        irisStatusLabel.setFont(new Font("Arial", Font.PLAIN, 12));

        irisPanel.add(irisLabel);
        irisPanel.add(irisStatusLabel);

        // Search panel
        JPanel searchPanel = new JPanel(new BorderLayout(10, 0));
        searchPanel.setBackground(BG_COLOR);

        JLabel searchLabel = new JLabel("Search Shaders on Modrinth:");
        searchLabel.setForeground(TEXT_COLOR);
        searchLabel.setFont(new Font("Arial", Font.PLAIN, 12));

        searchField = new JTextField();
        searchField.setBackground(INPUT_COLOR);
        searchField.setForeground(TEXT_COLOR);
        searchField.setCaretColor(TEXT_COLOR);
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 60)),
                new EmptyBorder(8, 10, 8, 10)
        ));
        searchField.addActionListener(e -> performSearch());

        searchButton = new RoundedButton("Search");
        searchButton.setBackground(ACCENT_COLOR);
        searchButton.setForeground(TEXT_COLOR);
        searchButton.setPreferredSize(new Dimension(80, 35));
        searchButton.addActionListener(e -> performSearch());

        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
        inputPanel.setBackground(BG_COLOR);
        inputPanel.add(searchField, BorderLayout.CENTER);
        inputPanel.add(searchButton, BorderLayout.EAST);

        searchPanel.add(searchLabel, BorderLayout.NORTH);
        searchPanel.add(inputPanel, BorderLayout.CENTER);

        panel.add(irisPanel, BorderLayout.NORTH);
        panel.add(searchPanel, BorderLayout.CENTER);

        return panel;
    }

    private JSplitPane createMainContent() {
        // Installed shaders panel (top)
        JPanel installedPanel = createInstalledShadersPanel();

        // Search results panel (bottom)
        JPanel searchResultsPanel = createSearchResultsPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, installedPanel, searchResultsPanel);
        splitPane.setResizeWeight(0.4);
        splitPane.setDividerLocation(180);
        splitPane.setBackground(BG_COLOR);
        splitPane.setBorder(null);

        return splitPane;
    }

    private JPanel createInstalledShadersPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(PANEL_COLOR);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(50, 50, 50)),
                new EmptyBorder(10, 10, 10, 10)
        ));

        JLabel titleLabel = new JLabel("Installed Shader Packs");
        titleLabel.setForeground(TEXT_COLOR);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 14));

        installedShadersModel = new DefaultListModel<>();
        installedShadersList = new JList<>(installedShadersModel);
        installedShadersList.setBackground(INPUT_COLOR);
        installedShadersList.setForeground(TEXT_COLOR);
        installedShadersList.setSelectionBackground(ACCENT_COLOR);
        installedShadersList.setCellRenderer(new InstalledShaderRenderer());

        JScrollPane scrollPane = new JScrollPane(installedShadersList);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));
        scrollPane.getViewport().setBackground(INPUT_COLOR);

        JButton removeButton = new RoundedButton("Remove Selected");
        removeButton.setBackground(new Color(244, 67, 54)); // Red
        removeButton.setForeground(TEXT_COLOR);
        removeButton.setPreferredSize(new Dimension(140, 30));
        removeButton.addActionListener(e -> removeSelectedShader());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(PANEL_COLOR);
        buttonPanel.add(removeButton);

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createSearchResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(PANEL_COLOR);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(50, 50, 50)),
                new EmptyBorder(10, 10, 10, 10)
        ));

        JLabel titleLabel = new JLabel("Available Shaders");
        titleLabel.setForeground(TEXT_COLOR);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 14));

        searchResultsModel = new DefaultListModel<>();
        searchResultsList = new JList<>(searchResultsModel);
        searchResultsList.setBackground(INPUT_COLOR);
        searchResultsList.setForeground(TEXT_COLOR);
        searchResultsList.setSelectionBackground(ACCENT_COLOR);
        searchResultsList.setCellRenderer(new ModrinthShaderRenderer());

        JScrollPane scrollPane = new JScrollPane(searchResultsList);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));
        scrollPane.getViewport().setBackground(INPUT_COLOR);

        JButton installButton = new RoundedButton("Install Selected");
        installButton.setBackground(ACCENT_COLOR);
        installButton.setForeground(TEXT_COLOR);
        installButton.setPreferredSize(new Dimension(140, 30));
        installButton.addActionListener(e -> installSelectedShader());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(PANEL_COLOR);
        buttonPanel.add(installButton);

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_COLOR);
        panel.setBorder(new EmptyBorder(10, 0, 0, 0));

        statusLabel = new JLabel("Ready");
        statusLabel.setForeground(new Color(150, 150, 150));
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 12));

        JButton closeButton = new RoundedButton("Close");
        closeButton.setBackground(INPUT_COLOR);
        closeButton.setForeground(TEXT_COLOR);
        closeButton.setPreferredSize(new Dimension(80, 30));
        closeButton.addActionListener(e -> dispose());

        panel.add(statusLabel, BorderLayout.WEST);
        panel.add(closeButton, BorderLayout.EAST);

        return panel;
    }

    private void checkIrisStatus() {
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return ShaderManager.isIrisInstalled(gameVersion);
            }

            @Override
            protected void done() {
                try {
                    boolean installed = get();
                    if (installed) {
                        irisStatusLabel.setText("Installed");
                        irisStatusLabel.setForeground(new Color(76, 175, 80)); // Green
                    } else {
                        irisStatusLabel.setText("Not installed (will be auto-installed with first shader)");
                        irisStatusLabel.setForeground(new Color(255, 193, 7)); // Amber
                    }
                } catch (Exception e) {
                    irisStatusLabel.setText("Unknown");
                    irisStatusLabel.setForeground(new Color(244, 67, 54)); // Red
                }
            }
        };
        worker.execute();
    }

    private void loadInstalledShaders() {
        SwingWorker<List<InstalledMod>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<InstalledMod> doInBackground() throws Exception {
                return ShaderManager.getInstalledShaders();
            }

            @Override
            protected void done() {
                try {
                    List<InstalledMod> shaders = get();
                    installedShadersModel.clear();
                    for (InstalledMod shader : shaders) {
                        installedShadersModel.addElement(shader);
                    }
                    setStatus("Found " + shaders.size() + " installed shaders");
                } catch (Exception e) {
                    setStatus("Error loading shaders: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void performSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            // Load popular shaders if no query
            query = "shader";
        }

        setStatus("Searching...");
        searchButton.setEnabled(false);
        final String searchQuery = query;

        SwingWorker<List<ModrinthProject>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<ModrinthProject> doInBackground() throws Exception {
                return ShaderManager.searchShaders(searchQuery, gameVersion);
            }

            @Override
            protected void done() {
                try {
                    List<ModrinthProject> results = get();
                    searchResultsModel.clear();
                    for (ModrinthProject project : results) {
                        searchResultsModel.addElement(project);
                    }
                    setStatus("Found " + results.size() + " shaders");
                } catch (Exception e) {
                    setStatus("Search error: " + e.getMessage());
                } finally {
                    searchButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void installSelectedShader() {
        ModrinthProject selected = searchResultsList.getSelectedValue();
        if (selected == null) {
            setStatus("Please select a shader to install");
            return;
        }

        setStatus("Installing " + selected.getTitle() + "...");

        SwingWorker<InstalledMod, String> worker = new SwingWorker<>() {
            @Override
            protected InstalledMod doInBackground() throws Exception {
                return ShaderManager.installShader(selected, gameVersion, this::publish);
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    setStatus(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                try {
                    InstalledMod shader = get();
                    if (shader != null) {
                        installedShadersModel.addElement(shader);
                        setStatus("Installed: " + shader.getDisplayName());
                        // Refresh Iris status in case it was auto-installed
                        checkIrisStatus();
                    }
                } catch (Exception e) {
                    setStatus("Install error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private void removeSelectedShader() {
        InstalledMod selected = installedShadersList.getSelectedValue();
        if (selected == null) {
            setStatus("Please select a shader to remove");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Remove " + selected.getDisplayName() + "?",
                "Confirm Remove",
                JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                return ShaderManager.removeShader(selected, msg -> setStatus(msg));
            }

            @Override
            protected void done() {
                try {
                    if (get()) {
                        installedShadersModel.removeElement(selected);
                        setStatus("Removed: " + selected.getDisplayName());
                    }
                } catch (Exception e) {
                    setStatus("Remove error: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void setStatus(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
    }

    /**
     * Custom renderer for installed shaders list.
     */
    @SuppressWarnings("serial")
    private class InstalledShaderRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof InstalledMod) {
                InstalledMod shader = (InstalledMod) value;
                setText(shader.getDisplayName() + " (" + shader.getFormattedSize() + ")");
            }

            setBackground(isSelected ? ACCENT_COLOR : INPUT_COLOR);
            setForeground(TEXT_COLOR);
            setBorder(new EmptyBorder(5, 10, 5, 10));

            return this;
        }
    }

    /**
     * Custom renderer for Modrinth shader search results.
     */
    @SuppressWarnings("serial")
    private class ModrinthShaderRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof ModrinthProject) {
                ModrinthProject project = (ModrinthProject) value;
                String downloads = formatDownloads(project.getDownloads());
                setText("<html><b>" + project.getTitle() + "</b> - " + downloads + " downloads<br/>" +
                        "<small>" + truncate(project.getDescription(), 60) + "</small></html>");
            }

            setBackground(isSelected ? ACCENT_COLOR : INPUT_COLOR);
            setForeground(TEXT_COLOR);
            setBorder(new EmptyBorder(5, 10, 5, 10));

            return this;
        }

        private String formatDownloads(int downloads) {
            if (downloads >= 1000000) {
                return String.format("%.1fM", downloads / 1000000.0);
            } else if (downloads >= 1000) {
                return String.format("%.1fK", downloads / 1000.0);
            }
            return String.valueOf(downloads);
        }

        private String truncate(String text, int maxLength) {
            if (text == null) return "";
            if (text.length() <= maxLength) return text;
            return text.substring(0, maxLength - 3) + "...";
        }
    }
}
