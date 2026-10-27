package com.emergencykit.model;

import java.time.LocalDate;

// Represents a designated assembly point during an emergency.
// Extends EmergencyRecord and always has type = ASSEMBLY.
public class AssemblyPoint extends EmergencyRecord {

    // Detailed directions for reaching this assembly point
    private String locationDescription;

    // Constructor for creating a NEW assembly point
    public AssemblyPoint() {
        super(RecordType.ASSEMBLY);
    }

    // Constructor for loading from the database
    public AssemblyPoint(int id, String title, String description,
                          String locationDescription,
                          String source, LocalDate lastVerified) {
        super(id, RecordType.ASSEMBLY, title, description, source, lastVerified);
        this.locationDescription = locationDescription;
    }

    public String getLocationDescription() { return locationDescription; }
    public void setLocationDescription(String locationDescription) {
        this.locationDescription = locationDescription;
    }

    // Shows the assembly point title, its location details, and footer info
    @Override
    public String display() {
        StringBuilder sb = new StringBuilder();
        sb.append("  Assembly Point\n");
        sb.append("  --------------\n");
        sb.append("  Title    : ").append(getTitle()).append("\n");
        sb.append("  Location : ").append(locationDescription != null ? locationDescription : "N/A").append("\n");
        if (getDescription() != null && !getDescription().isEmpty()) {
            sb.append("  Notes    : ").append(getDescription()).append("\n");
        }
        sb.append(commonFooter());
        return sb.toString();
    }
}
