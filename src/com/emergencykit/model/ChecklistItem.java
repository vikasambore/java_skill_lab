package com.emergencykit.model;

// Holds a single line item with sequence order inside a checklist.
// Used by Checklist and RecordDAOImpl for displaying and storing steps.
public class ChecklistItem {

    private int id;
    private String itemText;
    private int itemOrder;

    public ChecklistItem(String itemText, int itemOrder) {
        this.itemText = itemText;
        this.itemOrder = itemOrder;
    }

    public ChecklistItem(int id, String itemText, int itemOrder) {
        this.id = id;
        this.itemText = itemText;
        this.itemOrder = itemOrder;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getItemText() { return itemText; }
    public void setItemText(String itemText) { this.itemText = itemText; }

    public int getItemOrder() { return itemOrder; }
    public void setItemOrder(int itemOrder) { this.itemOrder = itemOrder; }

    @Override
    public String toString() {
        return "  " + itemOrder + ". " + itemText;
    }
}
