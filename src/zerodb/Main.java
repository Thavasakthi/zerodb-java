package zerodb;

import zerodb.cli.CommandParser;
import zerodb.core.Database;
import zerodb.util.Constants;
import zerodb.wal.RecoveryManager;

import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;

/**
 * Entry point for ZeroDB Interactive Command Line Interface (CLI).
 */
public class Main {

    public static void main(String[] args) {
        printBanner();

        try (Database db = new Database();
             BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {

            RecoveryManager.RecoveryReport report = db.getRecoveryReport();
            if (report.getPendingWalReplayed() > 0) {
                System.out.println("[RECOVERY] Recovered " + report.getPendingWalReplayed() + " uncommitted transaction(s) from WAL.");
            }
            System.out.println("[READY] Loaded " + db.size() + " key(s) into index. Type 'HELP' for available commands.\n");

            CommandParser parser = new CommandParser(db);

            while (true) {
                System.out.print(Constants.CLI_PROMPT);
                System.out.flush();

                String line = reader.readLine();
                if (line == null) { // EOF (Ctrl+D)
                    System.out.println("\nGoodbye!");
                    break;
                }

                String response = parser.parseAndExecute(line);
                if (response == null) {
                    System.out.println("Goodbye!");
                    break;
                }

                if (!response.isEmpty()) {
                    System.out.println(response);
                }
            }

        } catch (Exception e) {
            System.err.println("Fatal Database Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printBanner() {
        System.out.println("""
                 =============================================================
                  ______                _____  ____  
                 |___  /               |  __ \\|  _ \\ 
                    / /  ___ _ __ ___  | |  | | |_) |
                   / /  / _ \\ '__/ _ \\ | |  | |  _ < 
                  / /__|  __/ | | (_) || |__| | |_) |
                 /_____|\\___|_|  \\___/ |_____/|____/ 
                 
                 ZeroDB v1.0.0-HACKATHON
                 A Lightweight Dependency-Free Embedded Key-Value Database
                 Built strictly with Java 21 Standard Library ONLY
                 =============================================================
                """);
    }
}
