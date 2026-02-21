package opencraft.ui;

import opencraft.mods.InstalledMod;
import opencraft.mods.ModManager;
import opencraft.mods.ShaderManager;
import opencraft.network.MinecraftVersionManager.MinecraftVersion;
import opencraft.network.ModrinthApiClient.ModrinthProject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Unified dialog for managing both Minecraft mods and shaders.
 * Features a tabbed interface with separate panels for mods and shaders,
 * each showing search results on the left and installed items on the right.
 */
public class UnifiedModsDialog extends JDialog {
  private static final long serialVersionUID = 1L;

  @SuppressWarnings("serial")
  private final MinecraftVersion version;
  private final String gameVersion;
  private final transient ModManager modManager;
  private final transient ShaderManager shaderManager;

  // Colors matching the main launcher theme
  private static final Color BG_COLOR = new Color(20, 20, 20);
  private static final Color PANEL_COLOR = new Color(30, 30, 30);
  private static final Color INPUT_COLOR = new Color(45, 45, 45);
  private static final Color TEXT_COLOR = Color.WHITE;
  private static final Color MODS_ACCENT_COLOR = new Color(76, 175, 80); // Green
  private static final Color SHADERS_ACCENT_COLOR = new Color(156, 39, 176); // Purple

  @SuppressWarnings({ "this-escape", "serial" })
  public UnifiedModsDialog(Frame parent, MinecraftVersion version) {
    super(parent, "Manage Mods & Shaders - " + version.getDisplayName(), true);
    this.version = version;
    this.gameVersion = version.getBaseGameVersion();
    this.modManager = new ModManager();
    this.shaderManager = new ShaderManager();

    initializeUI();

    setSize(1280, 800);
    setMinimumSize(new Dimension(1280, 800));
    setLocationRelativeTo(parent);
  }

  private void initializeUI() {
    setBackground(BG_COLOR);
    getContentPane().setBackground(BG_COLOR);
    setLayout(new BorderLayout(10, 10));
    ((JPanel) getContentPane()).setBorder(new EmptyBorder(15, 15, 15, 15));

    // Create tabbed pane
    JTabbedPane tabbedPane = new JTabbedPane();
    tabbedPane.setBackground(BG_COLOR);
    tabbedPane.setForeground(TEXT_COLOR);
    tabbedPane.setFont(new Font("Arial", Font.BOLD, 14));

    // Add Mods tab
    JPanel modsPanel = new ModsTabPanel();
    tabbedPane.addTab("Mods", modsPanel);

    // Add Shaders tab
    JPanel shadersPanel = new ShadersTabPanel();
    tabbedPane.addTab("Shaders", shadersPanel);

    add(tabbedPane, BorderLayout.CENTER);

    // Bottom panel - Close button
    JPanel bottomPanel = createBottomPanel();
    add(bottomPanel, BorderLayout.SOUTH);
  }

  private JPanel createBottomPanel() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    panel.setBackground(BG_COLOR);
    panel.setBorder(new EmptyBorder(10, 0, 0, 0));

    JButton closeButton = new RoundedButton("Close");
    closeButton.setBackground(INPUT_COLOR);
    closeButton.setForeground(TEXT_COLOR);
    closeButton.setPreferredSize(new Dimension(100, 35));
    closeButton.addActionListener(e -> dispose());

    panel.add(closeButton);

    return panel;
  }

  /**
   * Panel for the Mods tab with search on left and installed mods on right.
   */
  @SuppressWarnings("serial")
  private class ModsTabPanel extends JPanel {
    private JTextField searchField;
    private JButton searchButton;
    private DefaultListModel<ModrinthProject> searchResultsModel;
    private JList<ModrinthProject> searchResultsList;
    private DefaultListModel<InstalledMod> installedModsModel;
    private JList<InstalledMod> installedModsList;
    private JLabel statusLabel;

    public ModsTabPanel() {
      setBackground(BG_COLOR);
      setLayout(new BorderLayout(10, 10));

      // Create horizontal split pane
      JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
      splitPane.setResizeWeight(0.45);
      splitPane.setDividerLocation(550);
      splitPane.setBackground(BG_COLOR);
      splitPane.setBorder(null);

      // Left panel - Search
      JPanel leftPanel = createSearchPanel();
      splitPane.setLeftComponent(leftPanel);

      // Right panel - Installed mods
      JPanel rightPanel = createInstalledPanel();
      splitPane.setRightComponent(rightPanel);

      add(splitPane, BorderLayout.CENTER);

      // Load installed mods
      loadInstalledMods();
    }

    private JPanel createSearchPanel() {
      JPanel panel = new JPanel(new BorderLayout(5, 5));
      panel.setBackground(PANEL_COLOR);
      panel.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(new Color(50, 50, 50)),
          new EmptyBorder(15, 15, 15, 15)));

      // Search header
      JLabel titleLabel = new JLabel("Search Modrinth");
      titleLabel.setForeground(TEXT_COLOR);
      titleLabel.setFont(new Font("Arial", Font.BOLD, 16));

      // Search input area
      JPanel searchInputPanel = new JPanel(new BorderLayout(5, 5));
      searchInputPanel.setBackground(PANEL_COLOR);
      searchInputPanel.setBorder(new EmptyBorder(10, 0, 10, 0));

      searchField = new JTextField();
      searchField.setBackground(INPUT_COLOR);
      searchField.setForeground(TEXT_COLOR);
      searchField.setCaretColor(TEXT_COLOR);
      searchField.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(new Color(60, 60, 60)),
          new EmptyBorder(10, 12, 10, 12)));
      searchField.setFont(new Font("Arial", Font.PLAIN, 13));
      searchField.addActionListener(e -> performSearch());

      searchButton = new RoundedButton("Search");
      searchButton.setBackground(MODS_ACCENT_COLOR);
      searchButton.setForeground(TEXT_COLOR);
      searchButton.setPreferredSize(new Dimension(100, 40));
      searchButton.addActionListener(e -> performSearch());

      searchInputPanel.add(searchField, BorderLayout.CENTER);
      searchInputPanel.add(searchButton, BorderLayout.EAST);

      // Search results
      JLabel resultsLabel = new JLabel("Search Results");
      resultsLabel.setForeground(new Color(180, 180, 180));
      resultsLabel.setFont(new Font("Arial", Font.BOLD, 13));
      resultsLabel.setBorder(new EmptyBorder(5, 0, 5, 0));

      searchResultsModel = new DefaultListModel<>();
      searchResultsList = new JList<>(searchResultsModel);
      searchResultsList.setBackground(INPUT_COLOR);
      searchResultsList.setForeground(TEXT_COLOR);
      searchResultsList.setSelectionBackground(MODS_ACCENT_COLOR);
      searchResultsList.setCellRenderer(new ModrinthProjectRenderer());

      JScrollPane scrollPane = new JScrollPane(searchResultsList);
      scrollPane.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));
      scrollPane.getViewport().setBackground(INPUT_COLOR);

      // Install button
      JButton installButton = new RoundedButton("Install Selected");
      installButton.setBackground(MODS_ACCENT_COLOR);
      installButton.setForeground(TEXT_COLOR);
      installButton.setPreferredSize(new Dimension(150, 35));
      installButton.addActionListener(e -> installSelectedMod());

      JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
      buttonPanel.setBackground(PANEL_COLOR);
      buttonPanel.setBorder(new EmptyBorder(5, 0, 0, 0));
      buttonPanel.add(installButton);

      panel.add(titleLabel, BorderLayout.NORTH);
      panel.add(searchInputPanel, BorderLayout.NORTH);

      JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
      centerPanel.setBackground(PANEL_COLOR);
      centerPanel.add(resultsLabel, BorderLayout.NORTH);
      centerPanel.add(scrollPane, BorderLayout.CENTER);
      centerPanel.add(buttonPanel, BorderLayout.SOUTH);

      panel.add(centerPanel, BorderLayout.CENTER);

      return panel;
    }

    private JPanel createInstalledPanel() {
      JPanel panel = new JPanel(new BorderLayout(5, 5));
      panel.setBackground(PANEL_COLOR);
      panel.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(new Color(50, 50, 50)),
          new EmptyBorder(15, 15, 15, 15)));

      // Header
      JLabel titleLabel = new JLabel("Installed Mods");
      titleLabel.setForeground(TEXT_COLOR);
      titleLabel.setFont(new Font("Arial", Font.BOLD, 16));

      // Installed mods list
      installedModsModel = new DefaultListModel<>();
      installedModsList = new JList<>(installedModsModel);
      installedModsList.setBackground(INPUT_COLOR);
      installedModsList.setForeground(TEXT_COLOR);
      installedModsList.setSelectionBackground(MODS_ACCENT_COLOR);
      installedModsList.setCellRenderer(new InstalledModRenderer());

      JScrollPane scrollPane = new JScrollPane(installedModsList);
      scrollPane.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));
      scrollPane.getViewport().setBackground(INPUT_COLOR);

      // Buttons
      JButton removeButton = new RoundedButton("Remove Selected");
      removeButton.setBackground(new Color(244, 67, 54)); // Red
      removeButton.setForeground(TEXT_COLOR);
      removeButton.setPreferredSize(new Dimension(150, 35));
      removeButton.addActionListener(e -> removeSelectedMod());

      JButton refreshButton = new RoundedButton("Refresh");
      refreshButton.setBackground(new Color(63, 81, 181)); // Indigo
      refreshButton.setForeground(TEXT_COLOR);
      refreshButton.setPreferredSize(new Dimension(100, 35));
      refreshButton.addActionListener(e -> loadInstalledMods());

      JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
      buttonPanel.setBackground(PANEL_COLOR);
      buttonPanel.add(removeButton);
      buttonPanel.add(refreshButton);

      // Status label
      statusLabel = new JLabel("Ready");
      statusLabel.setForeground(new Color(150, 150, 150));
      statusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
      statusLabel.setBorder(new EmptyBorder(5, 0, 0, 0));

      JPanel bottomPanel = new JPanel(new BorderLayout());
      bottomPanel.setBackground(PANEL_COLOR);
      bottomPanel.add(buttonPanel, BorderLayout.NORTH);
      bottomPanel.add(statusLabel, BorderLayout.SOUTH);

      panel.add(titleLabel, BorderLayout.NORTH);
      panel.add(scrollPane, BorderLayout.CENTER);
      panel.add(bottomPanel, BorderLayout.SOUTH);

      return panel;
    }

    private void loadInstalledMods() {
      SwingWorker<List<InstalledMod>, Void> worker = new SwingWorker<>() {
        @Override
        protected List<InstalledMod> doInBackground() throws Exception {
          return modManager.getInstalledMods(gameVersion);
        }

        @Override
        protected void done() {
          try {
            List<InstalledMod> mods = get();
            installedModsModel.clear();
            for (InstalledMod mod : mods) {
              installedModsModel.addElement(mod);
            }
            setStatus("Found " + mods.size() + " installed mods");
          } catch (Exception e) {
            setStatus("Error loading mods: " + e.getMessage());
          }
        }
      };
      worker.execute();
    }

    private void performSearch() {
      String query = searchField.getText().trim();
      if (query.isEmpty()) {
        setStatus("Please enter a search query");
        return;
      }

      setStatus("Searching...");
      searchButton.setEnabled(false);

      SwingWorker<List<ModrinthProject>, Void> worker = new SwingWorker<>() {
        @Override
        protected List<ModrinthProject> doInBackground() throws Exception {
          return modManager.searchMods(query, gameVersion);
        }

        @Override
        protected void done() {
          try {
            List<ModrinthProject> results = get();
            searchResultsModel.clear();
            for (ModrinthProject project : results) {
              searchResultsModel.addElement(project);
            }
            setStatus("Found " + results.size() + " mods");
          } catch (Exception e) {
            setStatus("Search error: " + e.getMessage());
          } finally {
            searchButton.setEnabled(true);
          }
        }
      };
      worker.execute();
    }

    private void installSelectedMod() {
      ModrinthProject selected = searchResultsList.getSelectedValue();
      if (selected == null) {
        setStatus("Please select a mod to install");
        return;
      }

      setStatus("Installing " + selected.getTitle() + "...");

      SwingWorker<InstalledMod, String> worker = new SwingWorker<InstalledMod, String>() {
        @Override
        protected InstalledMod doInBackground() throws Exception {
          return modManager.installMod(selected, gameVersion, this::publish);
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
            InstalledMod mod = get();
            if (mod != null) {
              installedModsModel.addElement(mod);
              setStatus("Installed: " + mod.getDisplayName());
            }
          } catch (Exception e) {
            setStatus("Install error: " + e.getMessage());
            e.printStackTrace();
          }
        }
      };
      worker.execute();
    }

    private void removeSelectedMod() {
      InstalledMod selected = installedModsList.getSelectedValue();
      if (selected == null) {
        setStatus("Please select a mod to remove");
        return;
      }

      int confirm = JOptionPane.showConfirmDialog(UnifiedModsDialog.this,
          "Remove " + selected.getDisplayName() + "?",
          "Confirm Remove",
          JOptionPane.YES_NO_OPTION);

      if (confirm != JOptionPane.YES_OPTION) {
        return;
      }

      SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
        @Override
        protected Boolean doInBackground() {
          return modManager.removeMod(selected, msg -> setStatus(msg));
        }

        @Override
        protected void done() {
          try {
            if (get()) {
              installedModsModel.removeElement(selected);
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
  }

  /**
   * Panel for the Shaders tab with search on left and installed shaders on right.
   */
  @SuppressWarnings("serial")
  private class ShadersTabPanel extends JPanel {
    private JTextField searchField;
    private JButton searchButton;
    private DefaultListModel<ModrinthProject> searchResultsModel;
    private JList<ModrinthProject> searchResultsList;
    private DefaultListModel<InstalledMod> installedShadersModel;
    private JList<InstalledMod> installedShadersList;
    private JLabel statusLabel;
    private JLabel irisStatusLabel;

    public ShadersTabPanel() {
      setBackground(BG_COLOR);
      setLayout(new BorderLayout(10, 10));

      // Create horizontal split pane
      JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
      splitPane.setResizeWeight(0.45);
      splitPane.setDividerLocation(550);
      splitPane.setBackground(BG_COLOR);
      splitPane.setBorder(null);

      // Left panel - Search
      JPanel leftPanel = createSearchPanel();
      splitPane.setLeftComponent(leftPanel);

      // Right panel - Installed shaders
      JPanel rightPanel = createInstalledPanel();
      splitPane.setRightComponent(rightPanel);

      add(splitPane, BorderLayout.CENTER);

      // Load installed shaders and check Iris status
      loadInstalledShaders();
      checkIrisStatus();
    }

    private JPanel createSearchPanel() {
      JPanel panel = new JPanel(new BorderLayout(5, 5));
      panel.setBackground(PANEL_COLOR);
      panel.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(new Color(50, 50, 50)),
          new EmptyBorder(15, 15, 15, 15)));

      // Search header
      JLabel titleLabel = new JLabel("Search Shaders");
      titleLabel.setForeground(TEXT_COLOR);
      titleLabel.setFont(new Font("Arial", Font.BOLD, 16));

      // Search input area
      JPanel searchInputPanel = new JPanel(new BorderLayout(5, 5));
      searchInputPanel.setBackground(PANEL_COLOR);
      searchInputPanel.setBorder(new EmptyBorder(10, 0, 10, 0));

      searchField = new JTextField();
      searchField.setBackground(INPUT_COLOR);
      searchField.setForeground(TEXT_COLOR);
      searchField.setCaretColor(TEXT_COLOR);
      searchField.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(new Color(60, 60, 60)),
          new EmptyBorder(10, 12, 10, 12)));
      searchField.setFont(new Font("Arial", Font.PLAIN, 13));
      searchField.addActionListener(e -> performSearch());

      searchButton = new RoundedButton("Search");
      searchButton.setBackground(SHADERS_ACCENT_COLOR);
      searchButton.setForeground(TEXT_COLOR);
      searchButton.setPreferredSize(new Dimension(100, 40));
      searchButton.addActionListener(e -> performSearch());

      searchInputPanel.add(searchField, BorderLayout.CENTER);
      searchInputPanel.add(searchButton, BorderLayout.EAST);

      // Search results
      JLabel resultsLabel = new JLabel("Available Shaders");
      resultsLabel.setForeground(new Color(180, 180, 180));
      resultsLabel.setFont(new Font("Arial", Font.BOLD, 13));
      resultsLabel.setBorder(new EmptyBorder(5, 0, 5, 0));

      searchResultsModel = new DefaultListModel<>();
      searchResultsList = new JList<>(searchResultsModel);
      searchResultsList.setBackground(INPUT_COLOR);
      searchResultsList.setForeground(TEXT_COLOR);
      searchResultsList.setSelectionBackground(SHADERS_ACCENT_COLOR);
      searchResultsList.setCellRenderer(new ModrinthProjectRenderer());

      JScrollPane scrollPane = new JScrollPane(searchResultsList);
      scrollPane.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));
      scrollPane.getViewport().setBackground(INPUT_COLOR);

      // Install button
      JButton installButton = new RoundedButton("Install Selected");
      installButton.setBackground(SHADERS_ACCENT_COLOR);
      installButton.setForeground(TEXT_COLOR);
      installButton.setPreferredSize(new Dimension(150, 35));
      installButton.addActionListener(e -> installSelectedShader());

      JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
      buttonPanel.setBackground(PANEL_COLOR);
      buttonPanel.setBorder(new EmptyBorder(5, 0, 0, 0));
      buttonPanel.add(installButton);

      panel.add(titleLabel, BorderLayout.NORTH);
      panel.add(searchInputPanel, BorderLayout.NORTH);

      JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
      centerPanel.setBackground(PANEL_COLOR);
      centerPanel.add(resultsLabel, BorderLayout.NORTH);
      centerPanel.add(scrollPane, BorderLayout.CENTER);
      centerPanel.add(buttonPanel, BorderLayout.SOUTH);

      panel.add(centerPanel, BorderLayout.CENTER);

      return panel;
    }

    private JPanel createInstalledPanel() {
      JPanel panel = new JPanel(new BorderLayout(5, 5));
      panel.setBackground(PANEL_COLOR);
      panel.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(new Color(50, 50, 50)),
          new EmptyBorder(15, 15, 15, 15)));

      // Header with Iris status
      JPanel headerPanel = new JPanel(new BorderLayout(5, 10));
      headerPanel.setBackground(PANEL_COLOR);

      JLabel titleLabel = new JLabel("Installed Shader Packs");
      titleLabel.setForeground(TEXT_COLOR);
      titleLabel.setFont(new Font("Arial", Font.BOLD, 16));

      // Iris status
      JPanel irisPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
      irisPanel.setBackground(new Color(40, 40, 40));
      irisPanel.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(new Color(60, 60, 60)),
          new EmptyBorder(8, 10, 8, 10)));

      JLabel irisLabel = new JLabel("Iris Shader Mod:");
      irisLabel.setForeground(TEXT_COLOR);
      irisLabel.setFont(new Font("Arial", Font.BOLD, 11));

      irisStatusLabel = new JLabel("Checking...");
      irisStatusLabel.setForeground(new Color(255, 193, 7)); // Amber
      irisStatusLabel.setFont(new Font("Arial", Font.PLAIN, 11));

      irisPanel.add(irisLabel);
      irisPanel.add(irisStatusLabel);

      headerPanel.add(titleLabel, BorderLayout.NORTH);
      headerPanel.add(irisPanel, BorderLayout.SOUTH);

      // Installed shaders list
      installedShadersModel = new DefaultListModel<>();
      installedShadersList = new JList<>(installedShadersModel);
      installedShadersList.setBackground(INPUT_COLOR);
      installedShadersList.setForeground(TEXT_COLOR);
      installedShadersList.setSelectionBackground(SHADERS_ACCENT_COLOR);
      installedShadersList.setCellRenderer(new InstalledModRenderer());

      JScrollPane scrollPane = new JScrollPane(installedShadersList);
      scrollPane.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));
      scrollPane.getViewport().setBackground(INPUT_COLOR);

      // Buttons
      JButton removeButton = new RoundedButton("Remove Selected");
      removeButton.setBackground(new Color(244, 67, 54)); // Red
      removeButton.setForeground(TEXT_COLOR);
      removeButton.setPreferredSize(new Dimension(150, 35));
      removeButton.addActionListener(e -> removeSelectedShader());

      JButton refreshButton = new RoundedButton("Refresh");
      refreshButton.setBackground(new Color(63, 81, 181)); // Indigo
      refreshButton.setForeground(TEXT_COLOR);
      refreshButton.setPreferredSize(new Dimension(100, 35));
      refreshButton.addActionListener(e -> {
        loadInstalledShaders();
        checkIrisStatus();
      });

      JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
      buttonPanel.setBackground(PANEL_COLOR);
      buttonPanel.add(removeButton);
      buttonPanel.add(refreshButton);

      // Status label
      statusLabel = new JLabel("Ready");
      statusLabel.setForeground(new Color(150, 150, 150));
      statusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
      statusLabel.setBorder(new EmptyBorder(5, 0, 0, 0));

      JPanel bottomPanel = new JPanel(new BorderLayout());
      bottomPanel.setBackground(PANEL_COLOR);
      bottomPanel.add(buttonPanel, BorderLayout.NORTH);
      bottomPanel.add(statusLabel, BorderLayout.SOUTH);

      panel.add(headerPanel, BorderLayout.NORTH);
      panel.add(scrollPane, BorderLayout.CENTER);
      panel.add(bottomPanel, BorderLayout.SOUTH);

      return panel;
    }

    private void checkIrisStatus() {
      SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
        @Override
        protected Boolean doInBackground() throws Exception {
          return shaderManager.isIrisInstalled(gameVersion);
        }

        @Override
        protected void done() {
          try {
            boolean installed = get();
            if (installed) {
              irisStatusLabel.setText("Installed");
              irisStatusLabel.setForeground(new Color(76, 175, 80)); // Green
            } else {
              irisStatusLabel.setText("Not installed (auto-installs with first shader)");
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
          return shaderManager.getInstalledShaders();
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
        query = "shader";
      }

      setStatus("Searching...");
      searchButton.setEnabled(false);
      final String searchQuery = query;

      SwingWorker<List<ModrinthProject>, Void> worker = new SwingWorker<>() {
        @Override
        protected List<ModrinthProject> doInBackground() throws Exception {
          return shaderManager.searchShaders(searchQuery, gameVersion);
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

      SwingWorker<InstalledMod, String> worker = new SwingWorker<InstalledMod, String>() {
        @Override
        protected InstalledMod doInBackground() throws Exception {
          return shaderManager.installShader(selected, gameVersion, this::publish);
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

      int confirm = JOptionPane.showConfirmDialog(UnifiedModsDialog.this,
          "Remove " + selected.getDisplayName() + "?",
          "Confirm Remove",
          JOptionPane.YES_NO_OPTION);

      if (confirm != JOptionPane.YES_OPTION) {
        return;
      }

      SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
        @Override
        protected Boolean doInBackground() {
          return shaderManager.removeShader(selected, msg -> setStatus(msg));
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
  }

  /**
   * Custom renderer for installed mods/shaders list.
   */
  @SuppressWarnings("serial")
  private static class InstalledModRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value,
        int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

      if (value instanceof InstalledMod) {
        InstalledMod mod = (InstalledMod) value;
        setText(mod.getDisplayName() + " v" + mod.getVersion() + " (" + mod.getFormattedSize() + ")");
      }

      setBackground(isSelected ? list.getSelectionBackground() : INPUT_COLOR);
      setForeground(TEXT_COLOR);
      setBorder(new EmptyBorder(5, 10, 5, 10));

      return this;
    }
  }

  /**
   * Custom renderer for Modrinth project search results.
   */
  @SuppressWarnings("serial")
  private static class ModrinthProjectRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value,
        int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

      if (value instanceof ModrinthProject) {
        ModrinthProject project = (ModrinthProject) value;
        String downloads = formatDownloads(project.getDownloads());
        setText("<html><b>" + project.getTitle() + "</b> - " + downloads + " downloads<br/>" +
            "<small>" + truncate(project.getDescription(), 80) + "</small></html>");
      }

      setBackground(isSelected ? list.getSelectionBackground() : INPUT_COLOR);
      setForeground(TEXT_COLOR);
      setBorder(new EmptyBorder(8, 12, 8, 12));

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
      if (text == null)
        return "";
      if (text.length() <= maxLength)
        return text;
      return text.substring(0, maxLength - 3) + "...";
    }
  }
}
