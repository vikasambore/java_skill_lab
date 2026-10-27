package com.emergencykit.util;

import com.emergencykit.exception.InvalidDataException;
import com.emergencykit.model.EmergencyRecord;

import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

// Manages loading database configuration and creating JDBC connections.
// Used by DAO classes and startup checks to connect to the MySQL database.
public class DBConnection {

    private static Properties props = null;

    private DBConnection() {}

    public static void loadConfig() throws IOException {
        props = new Properties();

        try (FileReader reader = new FileReader("db.properties")) {
            props.load(reader);
        } catch (IOException e) {
            throw new IOException(
                "Cannot find db.properties in the project root.\n" +
                "Please copy db.properties.example to db.properties and set your MySQL password."
            );
        }

        String staleDaysStr = props.getProperty("stale.days", "180").trim();
        try {
            int staleDays = Integer.parseInt(staleDaysStr);
            if (staleDays <= 0) {
                System.out.println("Warning: stale.days must be > 0. Using default value of 180.");
                EmergencyRecord.STALE_DAYS = 180;
            } else {
                EmergencyRecord.STALE_DAYS = staleDays;
            }
        } catch (NumberFormatException e) {
            System.out.println("Warning: stale.days '" + staleDaysStr + "' is not a number. Using default 180.");
            EmergencyRecord.STALE_DAYS = 180;
        }
    }

    public static Connection getConnection() throws SQLException, IOException {
        if (props == null) {
            loadConfig();
        }

        String url      = props.getProperty("db.url");
        String user     = props.getProperty("db.user");
        String password = props.getProperty("db.password");

        return DriverManager.getConnection(url, user, password);
    }

    public static void testConnection() throws SQLException, IOException {
        try (Connection conn = getConnection()) {
        }
    }
}
