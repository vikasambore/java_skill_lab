package com.emergencykit.model;

import java.time.LocalDate;

public class Instruction extends EmergencyRecord {

    public Instruction() {
        super(RecordType.INSTRUCTION);
    }

    public Instruction(int id, String title, String description,
                        String source, LocalDate lastVerified) {
        super(id, RecordType.INSTRUCTION, title, description, source, lastVerified);
    }

    @Override
    public String display() {
        StringBuilder sb = new StringBuilder();
        sb.append("  Instruction\n");
        sb.append("  -----------\n");
        sb.append("  Title : ").append(getTitle()).append("\n");
        sb.append("  Steps :\n");

        String desc = getDescription();
        if (desc != null && !desc.isEmpty()) {
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
