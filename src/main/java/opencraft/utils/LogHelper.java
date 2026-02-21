package opencraft.utils;

import java.util.function.Consumer;

/**
 * Utility class for consistent logging across the application.
 * Delegates to a provided logger if available, or falls back to System.out.
 */
public final class LogHelper {
    private LogHelper() {}

    /**
     * Logs a message using the provided logger, or System.out if logger is null.
     *
     * @param logger  Optional consumer to receive log messages
     * @param message The message to log
     */
    public static void log(Consumer<String> logger, String message) {
        if (logger != null) {
            logger.accept(message);
        } else {
            System.out.println(message);
        }
    }
}
