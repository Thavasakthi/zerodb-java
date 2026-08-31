package zerodb.cli;

import zerodb.core.Command;
import zerodb.core.Database;

import java.io.IOException;
import java.util.Map;

/**
 * Parses user CLI inputs and executes commands on the Database instance.
 */
public class CommandParser {

    private final Database db;

    public CommandParser(Database db) {
        this.db = db;
    }

    /**
     * Parses a command line string and returns formatted CLI output.
     * @return Output message or null if EXIT command issued.
     */
    public String parseAndExecute(String rawInput) {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return "";
        }

        String input = rawInput.trim();
        String[] parts = input.split("\\s+", 2);
        String commandStr = parts[0];
        Command command = Command.parse(commandStr);

        try {
            switch (command) {
                case PUT:
                    return handlePut(parts);
                case GET:
                    return handleGet(parts);
                case DELETE:
                    return handleDelete(parts);
                case LIST:
                    return handleList();
                case SIZE:
                    return handleSize();
                case CLEAR:
                    return handleClear();
                case HELP:
                    return getHelpMessage();
                case EXIT:
                    return null; // Signals CLI exit
                case UNKNOWN:
                default:
                    return "ERROR: Unknown command '" + commandStr + "'. Type 'HELP' for supported commands.";
            }
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String handlePut(String[] parts) throws IOException {
        if (parts.length < 2 || parts[1].trim().isEmpty()) {
            return "ERROR: Invalid usage. Syntax: PUT <key> <value>";
        }

        String args = parts[1].trim();
        String[] keyVal = args.split("\\s+", 2);

        String key = keyVal[0];
        String value = keyVal.length > 1 ? keyVal[1] : "";

        if (key.isEmpty()) {
            return "ERROR: Key cannot be empty.";
        }

        db.put(key, value);
        return "OK";
    }

    private String handleGet(String[] parts) throws IOException {
        if (parts.length < 2 || parts[1].trim().isEmpty()) {
            return "ERROR: Invalid usage. Syntax: GET <key>";
        }

        String key = parts[1].trim();
        String value = db.get(key);

        if (value == null) {
            return "Key not found: " + key;
        }

        return value;
    }

    private String handleDelete(String[] parts) throws IOException {
        if (parts.length < 2 || parts[1].trim().isEmpty()) {
            return "ERROR: Invalid usage. Syntax: DELETE <key>";
        }

        String key = parts[1].trim();
        boolean deleted = db.delete(key);

        if (!deleted) {
            return "Key not found: " + key;
        }

        return "OK";
    }

    private String handleList() throws IOException {
        Map<String, String> records = db.list();
        if (records.isEmpty()) {
            return "(empty database)";
        }

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, String> entry : records.entrySet()) {
            if (count > 0) sb.append("\n");
            sb.append(entry.getKey()).append(" = ").append(entry.getValue());
            count++;
        }
        return sb.toString();
    }

    private String handleSize() throws IOException {
        int keyCount = db.size();
        String stats = db.getStats();
        return "Size: " + keyCount + " key(s) | " + stats;
    }

    private String handleClear() throws IOException {
        db.clear();
        return "OK (Database cleared)";
    }

    public static String getHelpMessage() {
        return """
                ZeroDB Command Reference:
                -----------------------------------------------------
                  PUT <key> <value>   - Insert or update a key-value pair
                  GET <key>           - Retrieve value by key
                  DELETE <key>        - Remove a key from the database
                  LIST                - Display all active key-value pairs
                  SIZE                - Show key count and database storage stats
                  CLEAR               - Purge all keys and reset database storage
                  HELP                - Display this help message
                  EXIT                - Close ZeroDB and exit CLI
                -----------------------------------------------------
                """;
    }
}
