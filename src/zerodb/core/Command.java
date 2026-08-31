package zerodb.core;

/**
 * Supported database CLI commands.
 */
public enum Command {
    PUT,
    GET,
    DELETE,
    LIST,
    SIZE,
    CLEAR,
    HELP,
    EXIT,
    UNKNOWN;

    public static Command parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            return UNKNOWN;
        }
        try {
            return Command.valueOf(input.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
