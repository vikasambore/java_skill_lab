package com.emergencykit.model;

// Represents a single item in a checklist (e.g. "Sound the fire alarm").
// Items have an order number so they display in the right sequence.
public class ChecklistItem {

    private int id;         // database id
    private String itemText; // the actual checklist item text
    private int itemOrder;   // position in the list (1, 2, 3, ...)

    // Constructor for creating a NEW item (no database id yet)
    public ChecklistItem(String itemText, int itemOrder) {
        this.itemText = itemText;
        this.itemOrder = itemOrder;
    }

    // Constructor for loading from the database
    public ChecklistItem(int id, String itemText, int itemOrder) {
        this.id = id;
        this.itemText = itemText;
        this.itemOrder = itemOrder;
    }

    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getItemText() { return itemText; }
    public void setItemText(String itemText) { this.itemText = itemText; }

    public int getItemOrder() { return itemOrder; }
    public void setItemOrder(int itemOrder) { this.itemOrder = itemOrder; }

    // Shows the item in a user-friendly way, e.g. "  2. Sound the alarm"
    @Override
    public String toString() {
        return "  " + itemOrder + ". " + itemText;
    }
}
