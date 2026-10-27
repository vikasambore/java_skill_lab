package com.emergencykit.model;

import java.time.LocalDate;

// Represents a set of instructions (steps stored as multi-line text).
// Extends EmergencyRecord and always has type = INSTRUCTION.
public class Instruction extends EmergencyRecord {

    // No extra fields -- steps are stored in the 'description' field (lines separated by \n)

    // Constructor for creating a NEW instruction
    public Instruction() {
        super(RecordType.INSTRUCTION);
    }

    // Constructor for loading from the database
    public Instruction(int id, String title, String description,
                        String source, LocalDate lastVerified) {
        super(id, RecordType.INSTRUCTION, title, description, source, lastVerified);
    }

    // Shows the instruction title, numbered steps, and footer info.
    // Steps are stored as lines separated by \n in the description field.
    @Override
    public String display() {
        StringBuilder sb = new StringBuilder();
        sb.append("  Instruction\n");
        sb.append("  -----------\n");
        sb.append("  Title : ").append(getTitle()).append("\n");
        sb.append("  Steps :\n");

        String desc = getDescription();
        if (desc != null && !desc.isEmpty()) {
            // Split the description into individual lines/steps
            String[] steps = desc.split("\n");
            for (String step : steps) {
                sb.append("    ").append(step.trim()).append("\n");
            }
        } else {
            sb.append("    (No steps provided)\n");
        }

        sb.append(commonFooter());
        return sb.toString();
    }
}
