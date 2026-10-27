package com.emergencykit.util;

import com.emergencykit.model.Checklist;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Handles exporting checklists to .txt files and listing/reading exported files.
public class FileExporter {

    // The folder where exported files are saved
    private static final String EXPORTS_DIR = "exports";

    // Exports a checklist to a .txt file in the exports/ folder.
    // Returns the path to the created file.
    public static Path exportChecklist(Checklist checklist) throws IOException {
        // Create the exports directory if it doesn't exist yet
        Path exportsPath = Paths.get(EXPORTS_DIR);
        if (!Files.exists(exportsPath)) {
            Files.createDirectories(exportsPath);
        }

        // Build the filename using the checklist ID and a URL-safe version of the title
        String fileName = "checklist_" + checklist.getId() + "_" + slug(checklist.getTitle()) + ".txt";
        Path filePath = exportsPath.resolve(fileName);

        // Write the formatted checklist text to the file
        // try-with-resources ensures the writer is always closed
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write(checklist.toPrintableText());
        }

        return filePath;
    }

    // Converts a title into a safe filename-friendly string.
    // e.g. "Fire Evacuation Checklist!" becomes "fire-evacuation-checklist"
    public static String slug(String title) {
        if (title == null) return "checklist";
        String lower = title.toLowerCase();
        // Replace any character that isn't a letter or digit with a dash
        String result = lower.replaceAll("[^a-z0-9]+", "-");
        // Remove leading and trailing dashes
        result = result.replaceAll("^-+|-+$", "");
        // Limit to 40 characters
        if (result.length() > 40) {
            result = result.substring(0, 40);
        }
        // If the result is empty (e.g. a non-English title), use default
        if (result.isEmpty()) return "checklist";
        return result;
    }

    // Lists all .txt files in the exports/ folder, sorted by name.
    // Returns an empty list if the folder doesn't exist yet.
    // IMPORTANT: The Stream from Files.list() must be closed, otherwise Windows
    // keeps the folder locked and you can't rename/delete files in it.
    public static List<Path> listExports() throws IOException {
        Path exportsPath = Paths.get(EXPORTS_DIR);
        if (!Files.exists(exportsPath)) {
            return new java.util.ArrayList<>(); // no exports folder yet
        }

        // Use try-with-resources on the stream to ensure it gets closed
        try (Stream<Path> stream = Files.list(exportsPath)) {
            return stream
                .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".txt"))
                .sorted()
                .collect(Collectors.toList());
        }
    }

    // Reads the entire content of a file and returns it as a String.
    public static String readFile(Path path) throws IOException {
        return Files.readString(path);
    }
}
