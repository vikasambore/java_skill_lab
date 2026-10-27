package com.emergencykit.util;

import com.emergencykit.exception.InvalidDataException;
import com.emergencykit.model.EmergencyRecord;

import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

// Handles loading database configuration and creating connections.
// We use LAZY loading -- config is loaded only when first needed, not at class load time.
// This prevents a nasty Java error called ExceptionInInitializerError if db.properties is missing.
public class DBConnection {

    // Cached properties (loaded only once)
    private static Properties props = null;

    // Private constructor -- this class is never instantiated, only its static methods are used
    private DBConnection() {}

    // Loads the db.properties file and reads settings.
    // Called lazily inside getConnection() on first use.
    public static void loadConfig() throws IOException {
        props = new Properties();

        // try-with-resources automatically closes the FileReader when done
        try (FileReader reader = new FileReader("db.properties")) {
            props.load(reader);
        } catch (IOException e) {
            // Give the user a friendly message explaining what to do
            throw new IOException(
                "Cannot find db.properties in the project root.\n" +
                "Please copy db.properties.example to db.properties and set your MySQL password."
            );
        }

        // Read the stale.days setting and apply it globally
        String staleDaysStr = props.getProperty("stale.days", "180").trim();
        try {
            int staleDays = Integer.parseInt(staleDaysStr);
            if (staleDays <= 0) {
                // Invalid value: use default and warn the user
                System.out.println("Warning: stale.days must be > 0. Using default value of 180.");
                EmergencyRecord.STALE_DAYS = 180;
            } else {
                EmergencyRecord.STALE_DAYS = staleDays;
            }
        } catch (NumberFormatException e) {
            // Non-numeric value: use default and warn
            System.out.println("Warning: stale.days '" + staleDaysStr + "' is not a number. Using default 180.");
            EmergencyRecord.STALE_DAYS = 180;
        }
    }

    // Creates and returns a new database connection.
    // Each caller closes the connection using try-with-resources.
    public static Connection getConnection() throws SQLException, IOException {
        // Load config if not already loaded
        if (props == null) {
            loadConfig();
        }

        String url      = props.getProperty("db.url");
        String user     = props.getProperty("db.user");
        String password = props.getProperty("db.password");

        // DriverManager creates a fresh connection each time
        return DriverManager.getConnection(url, user, password);
    }

    // Tests whether the database connection works.
    // Called at startup so we fail fast with a clear message.
    public static void testConnection() throws SQLException, IOException {
        // Just open and immediately close -- if it doesn't throw, we're good
        try (Connection conn = getConnection()) {
            // Connection opened successfully
        }
    }
}
