package com.emergencykit.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// Represents a checklist (e.g. Fire Evacuation Checklist).
// Extends EmergencyRecord and implements Printable for file export.
public class Checklist extends EmergencyRecord implements Printable {

    // The list of checklist items. Starts empty, items are added later.
    private List<ChecklistItem> items;

    // Constructor for creating a NEW checklist
    public Checklist() {
        super(RecordType.CHECKLIST);
        this.items = new ArrayList<>(); // always start with an empty list
    }

    // Constructor for loading from the database
    public Checklist(int id, String title, String description,
                      String source, LocalDate lastVerified) {
        super(id, RecordType.CHECKLIST, title, description, source, lastVerified);
        this.items = new ArrayList<>();
    }

    // --- Item management methods ---

    // Adds a new item to the checklist.
    // The order number is automatically set to the next available number.
    public void addItem(String text) {
        int nextOrder = items.size() + 1;
        items.add(new ChecklistItem(text, nextOrder));
    }

    public List<ChecklistItem> getItems() {
        return items;
    }

    // Replaces all items (used when loading fresh from database)
    public void setItems(List<ChecklistItem> items) {
        this.items = items;
    }

    // Removes all items (useful when re-entering items during update)
    public void clearItems() {
        items.clear();
    }

    // Removes one item by its position in the list (0-based index).
    // After removal, re-numbers all items to keep order clean.
    public void removeItem(int index) {
        if (index >= 0 && index < items.size()) {
            items.remove(index);
            // Re-number the remaining items
            for (int i = 0; i < items.size(); i++) {
                items.get(i).setItemOrder(i + 1);
            }
        }
    }

    // Shows the checklist with numbered items and checkboxes
    @Override
    public String display() {
        StringBuilder sb = new StringBuilder();
        sb.append("  Checklist\n");
        sb.append("  ---------\n");
        sb.append("  Title : ").append(getTitle()).append("\n");
        sb.append("  Items :\n");
        if (items.isEmpty()) {
            sb.append("    (No items)\n");
        } else {
            for (ChecklistItem item : items) {
                sb.append("    [ ] ").append(item.getItemOrder())
                  .append(". ").append(item.getItemText()).append("\n");
            }
        }
        sb.append(commonFooter());
        return sb.toString();
    }

    // Formats the checklist for export to a .txt file.
    // Uses ASCII-only characters, 46 columns wide.
    @Override
    public String toPrintableText() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy");
        String today = LocalDate.now().format(fmt);
        String verifiedDate = getLastVerified() != null ? getLastVerified().format(fmt) : "Unknown";
        String status = isStale() ? "[STALE] verify before use" : "CURRENT";

        StringBuilder sb = new StringBuilder();
        String border = "==============================================";
        String divider = "----------------------------------------------";

        sb.append(border).append("\n");
        // Title in uppercase, fitted to the box width
        sb.append(" ").append(fit(getTitle().toUpperCase(), 44)).append("\n");
        sb.append(border).append("\n");
        sb.append(String.format(" %-13s : %s\n", "Source", fit(getSource(), 28)));
        sb.append(String.format(" %-13s : %s\n", "Last verified", verifiedDate));
        sb.append(String.format(" %-13s : %s\n", "Status", status));
        sb.append(divider).append("\n");

        // Each item with a checkbox
        for (ChecklistItem item : items) {
            String itemLine = item.getItemOrder() + ". " + item.getItemText();
            sb.append(" [ ] ").append(fit(itemLine, 40)).append("\n");
        }

        sb.append(divider).append("\n");
        sb.append(String.format(" %-13s : %s\n", "Printed on", today));
        sb.append(border).append("\n");

        return sb.toString();
    }

    // Helper: truncates a string with "..." if it exceeds the given width.
    // This ensures printed text never overflows the box.
    private String fit(String text, int width) {
        if (text == null) return "";
        if (text.length() <= width) return text;
        // Truncate and add "..." to show it was cut
        return text.substring(0, width - 3) + "...";
    }
}
