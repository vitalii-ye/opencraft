package opencraft.ui;

import java.awt.Color;

/**
 * Centralized UI color constants shared across launcher UI components.
 * Eliminates duplicated inline color declarations in OpenCraftLauncher,
 * UnifiedModsDialog, and VersionComboBoxRenderer.
 */
public final class Theme {

    private Theme() {}

    // --- Background layers ---
    /** Main window background: darkest layer. */
    public static final Color BG_COLOR = new Color(20, 20, 20);
    /** Card / panel background: one step lighter than BG_COLOR. */
    public static final Color PANEL_COLOR = new Color(30, 30, 30);
    /** Slightly lighter panel used in combo-box item background. */
    public static final Color ITEM_COLOR = new Color(40, 40, 40);
    /** Input field and list background. */
    public static final Color INPUT_COLOR = new Color(45, 45, 45);

    // --- Borders ---
    /** Outer panel border line. */
    public static final Color BORDER_COLOR = new Color(50, 50, 50);
    /** Inner input / scroll-pane border line. */
    public static final Color INPUT_BORDER_COLOR = new Color(60, 60, 60);
    /** Selected-item highlight background (combo-box). */
    public static final Color SELECTED_COLOR = new Color(80, 80, 80);

    // --- Foregrounds ---
    /** Primary text color. */
    public static final Color TEXT_COLOR = Color.WHITE;
    /** Subtle / secondary text (labels, status). */
    public static final Color TEXT_DIM_COLOR = new Color(150, 150, 150);
    /** Results / section heading text. */
    public static final Color TEXT_MUTED_COLOR = new Color(180, 180, 180);

    // --- Accent colors ---
    /** Primary green accent (play button, mods selection highlight). */
    public static final Color ACCENT_GREEN = new Color(76, 175, 80);
    /** Light green used for Fabric version badge. */
    public static final Color ACCENT_FABRIC = new Color(139, 195, 74);
    /** Purple accent used for the Shaders tab. */
    public static final Color ACCENT_PURPLE = new Color(156, 39, 176);
    /** Indigo used for Refresh buttons. */
    public static final Color ACCENT_INDIGO = new Color(63, 81, 181);
    /** Red / danger color for Remove buttons and error states. */
    public static final Color ACCENT_RED = new Color(244, 67, 54);
    /** Amber used for warning / pending states. */
    public static final Color ACCENT_AMBER = new Color(255, 193, 7);
}
