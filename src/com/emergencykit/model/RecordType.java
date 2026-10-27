package com.emergencykit.model;

public enum RecordType {

    CONTACT("Emergency Contact"),
    ASSEMBLY("Assembly Point"),
    CHECKLIST("Checklist"),
    INSTRUCTION("Instruction");

    private String friendlyLabel;

    RecordType(String friendlyLabel) {
        this.friendlyLabel = friendlyLabel;
    }

    public String label() {
        return friendlyLabel;
    }
}
