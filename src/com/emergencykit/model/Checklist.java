package com.emergencykit.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// Represents an emergency preparedness checklist containing ordered items.
// Used by RecordService, Main, and FileExporter to manage and export tasks.
public class Checklist extends EmergencyRecord implements Printable {

    private List<ChecklistItem> items;

    public Checklist() {
        super(RecordType.CHECKLIST);
        this.items = new ArrayList<>();
    }

    public Checklist(int id, String title, String description,
                      String source, LocalDate lastVerified) {
        super(id, RecordType.CHECKLIST, title, description, source, lastVerified);
        this.items = new ArrayList<>();
    }

    public void addItem(String text) {
        int nextOrder = items.size() + 1;
        items.add(new ChecklistItem(text, nextOrder));
    }

    public List<ChecklistItem> getItems() {
        return items;
    }

    public void setItems(List<ChecklistItem> items) {
        this.items = items;
    }

    public void clearItems() {
        items.clear();
    }

    public void removeItem(int index) {
        if (index >= 0 && index < items.size()) {
            items.remove(index);
            for (int i = 0; i < items.size(); i++) {
                items.get(i).setItemOrder(i + 1);
            }
        }
    }

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
        sb.append(" ").append(fit(getTitle().toUpperCase(), 44)).append("\n");
        sb.append(border).append("\n");
        sb.append(String.format(" %-13s : %s\n", "Source", fit(getSource(), 28)));
        sb.append(String.format(" %-13s : %s\n", "Last verified", verifiedDate));
        sb.append(String.format(" %-13s : %s\n", "Status", status));
        sb.append(divider).append("\n");

        for (ChecklistItem item : items) {
            String itemLine = item.getItemOrder() + ". " + item.getItemText();
            sb.append(" [ ] ").append(fit(itemLine, 40)).append("\n");
        }

        sb.append(divider).append("\n");
        sb.append(String.format(" %-13s : %s\n", "Printed on", today));
        sb.append(border).append("\n");

        return sb.toString();
    }

    private String fit(String text, int width) {
        if (text == null) return "";
        if (text.length() <= width) return text;
        return text.substring(0, width - 3) + "...";
    }
}
