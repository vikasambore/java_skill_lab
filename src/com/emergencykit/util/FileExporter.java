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

// Exports printable records to text files and lists saved files on disk.
// Used by Main when users choose to save checklists or view exported files.
public class FileExporter {

    private static final String EXPORTS_DIR = "exports";

    public static Path exportChecklist(Checklist checklist) throws IOException {
        Path exportsPath = Paths.get(EXPORTS_DIR);
        if (!Files.exists(exportsPath)) {
            Files.createDirectories(exportsPath);
        }

        String fileName = "checklist_" + checklist.getId() + "_" + slug(checklist.getTitle()) + ".txt";
        Path filePath = exportsPath.resolve(fileName);

        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write(checklist.toPrintableText());
        }

        return filePath;
    }

    public static String slug(String title) {
        if (title == null) return "checklist";
        String lower = title.toLowerCase();
        String result = lower.replaceAll("[^a-z0-9]+", "-");
        result = result.replaceAll("^-+|-+$", "");
        if (result.length() > 40) {
            result = result.substring(0, 40);
        }
        if (result.isEmpty()) return "checklist";
        return result;
    }

    public static List<Path> listExports() throws IOException {
        Path exportsPath = Paths.get(EXPORTS_DIR);
        if (!Files.exists(exportsPath)) {
            return new java.util.ArrayList<>();
        }

        try (Stream<Path> stream = Files.list(exportsPath)) {
            return stream
                .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".txt"))
                .sorted()
                .collect(Collectors.toList());
        }
    }

    public static String readFile(Path path) throws IOException {
        return Files.readString(path);
    }
}
