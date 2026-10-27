package com.emergencykit.model;

// Defines the interface for generating plain-text printable formatted output.
// Implemented by Checklist and used by FileExporter to generate export files.
public interface Printable {
    String toPrintableText();
}
