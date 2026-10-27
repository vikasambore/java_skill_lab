package com.emergencykit.model;

// This enum lists all the types of emergency records we support.
// Each type has a user-friendly label shown in the menus.
public enum RecordType {

    CONTACT("Emergency Contact"),
    ASSEMBLY("Assembly Point"),
    CHECKLIST("Checklist"),
    INSTRUCTION("Instruction");

    // The friendly name to show users (like "Emergency Contact" instead of "CONTACT")
    private String friendlyLabel;

    // Enum constructor to store the label
    RecordType(String friendlyLabel) {
        this.friendlyLabel = friendlyLabel;
    }

    // Returns the friendly label for display
    public String label() {
        return friendlyLabel;
    }
}
