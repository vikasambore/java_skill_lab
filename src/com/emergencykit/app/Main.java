package com.emergencykit.app;

import com.emergencykit.dao.RecordDAO;
import com.emergencykit.dao.RecordDAOImpl;
import com.emergencykit.dao.UserDAO;
import com.emergencykit.exception.InvalidDataException;
import com.emergencykit.exception.RecordNotFoundException;
import com.emergencykit.exception.UnauthorizedException;
import com.emergencykit.model.*;
import com.emergencykit.service.AuthService;
import com.emergencykit.service.RecordService;
import com.emergencykit.util.*;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

// Handles the console UI flow and interactive menu navigation.
// Used as the main application entry point to start the program.
public class Main {

    private static InputHelper input;
    private static AuthService authService;
    private static RecordService recordService;
    private static final boolean DEBUG = false;

    public static void main(String[] args) {
        input = new InputHelper();

        System.out.println("============================================");
        System.out.println("  Offline Emergency Information Kit");
        System.out.println("============================================");

        try {
            DBConnection.testConnection();
            System.out.println("Database connected successfully.");
        } catch (IOException e) {
            System.out.println("Configuration error: " + e.getMessage());
            return;
        } catch (SQLException e) {
            System.out.println("Cannot connect to MySQL. Please check:");
            System.out.println("  - Is MySQL running?");
            System.out.println("  - Is the password in db.properties correct?");
            System.out.println("  - Does the 'emergency_kit' database exist?");
            if (DEBUG) e.printStackTrace();
            return;
        }

        UserDAO userDAO = new UserDAO();
        authService = new AuthService(userDAO);

        RecordDAO recordDAO = new RecordDAOImpl();
        recordService = new RecordService(recordDAO, authService);

        try {
            boolean loggedIn = doLogin();
            if (!loggedIn) {
                System.out.println("Too many failed attempts. Goodbye.");
                return;
            }

            mainMenuLoop();

        } catch (NoSuchElementException e) {
            System.out.println("\nInput closed. Goodbye.");
        } catch (Exception e) {
            System.out.println("Something went wrong: " + e.getMessage());
            if (DEBUG) e.printStackTrace();
        }

        System.out.println("Goodbye!");
    }

    private static boolean doLogin() {
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            System.out.println("\n--- Login (" + attempt + "/" + maxAttempts + ") ---");

            String username = input.readRequired("Username: ");
            String password;

            if (System.console() != null) {
                char[] pwChars = System.console().readPassword("Password: ");
                password = new String(pwChars);
            } else {
                password = input.readRequired("Password (visible in NetBeans): ");
            }

            try {
                authService.login(username, password);
                System.out.println("Welcome, " + authService.getCurrentUser().getUsername()
                        + "! Role: " + authService.getCurrentUser().getRole());
                return true;
            } catch (UnauthorizedException e) {
                System.out.println("Login failed: " + e.getMessage());
                if (attempt < maxAttempts) {
                    System.out.println("Please try again.");
                }
            } catch (Exception e) {
                System.out.println("Error during login: " + e.getMessage());
                if (DEBUG) e.printStackTrace();
            }
        }
        return false;
    }

    private static final String[] MAIN_MENU = {
        "1. Search records",
        "2. Browse by category",
        "3. View all records (grouped by type)",
        "4. Stale records report",
        "5. View a record in full (by ID)",
        "6. Export a checklist to file",
        "7. View an exported file",
        "8. Admin menu",
        "9. Logout / Exit"
    };

    private static void mainMenuLoop() {
        while (true) {
            System.out.println("\n============ MAIN MENU ============");
            for (String option : MAIN_MENU) {
                System.out.println("  " + option);
            }
            System.out.println("===================================");

            int choice = input.readInt("Choose an option: ", 1, 9);

            try {
                switch (choice) {
                    case 1: doSearch(); break;
                    case 2: doBrowseByCategory(); break;
                    case 3: doViewAll(); break;
                    case 4: doStaleReport(); break;
                    case 5: doViewById(); break;
                    case 6: doExportChecklist(); break;
                    case 7: doViewExportedFile(); break;
                    case 8: doAdminMenu(); break;
                    case 9:
                        authService.logout();
                        return;
                    default:
                        System.out.println("Invalid choice. Please try again.");
                }
            } catch (InvalidDataException e) {
                System.out.println("Input error: " + e.getMessage());
            } catch (RecordNotFoundException e) {
                System.out.println("Not found: " + e.getMessage());
            } catch (UnauthorizedException e) {
                System.out.println("Access denied: " + e.getMessage());
            } catch (SQLException e) {
                System.out.println("Database error: " + e.getMessage());
                if (DEBUG) e.printStackTrace();
            } catch (IOException e) {
                System.out.println("File error: " + e.getMessage());
                if (DEBUG) e.printStackTrace();
            } catch (NoSuchElementException e) {
                throw e;
            } catch (Exception e) {
                System.out.println("Something went wrong: " + e.getMessage());
                if (DEBUG) e.printStackTrace();
            }
        }
    }

    private static void doSearch() throws SQLException, IOException, InvalidDataException {
        String keyword = input.readRequired("Enter search keyword: ");
        RecordType filterType = askTypeFilter();

        List<EmergencyRecord> results;
        if (filterType == null) {
            results = recordService.search(keyword);
        } else {
            results = recordService.search(keyword, filterType);
        }

        printRecordList(results, "search results");
    }

    private static RecordType askTypeFilter() {
        System.out.println("Filter by type?");
        System.out.println("  1) Contact   2) Assembly   3) Checklist   4) Instruction   (Enter = all types)");

        while (true) {
            String input2 = input.readLine("Choice: ");
            if (input2.isEmpty()) return null;

            switch (input2) {
                case "1": return RecordType.CONTACT;
                case "2": return RecordType.ASSEMBLY;
                case "3": return RecordType.CHECKLIST;
                case "4": return RecordType.INSTRUCTION;
                default:
                    System.out.println("Invalid choice. Enter 1, 2, 3, 4, or just press Enter for all types.");
            }
        }
    }

    private static void doBrowseByCategory() throws SQLException, IOException {
        System.out.println("Browse by category:");
        System.out.println("  1) Emergency Contacts");
        System.out.println("  2) Assembly Points");
        System.out.println("  3) Checklists");
        System.out.println("  4) Instructions");

        int choice = input.readInt("Choose a category: ", 1, 4);

        RecordType type;
        switch (choice) {
            case 1: type = RecordType.CONTACT; break;
            case 2: type = RecordType.ASSEMBLY; break;
            case 3: type = RecordType.CHECKLIST; break;
            case 4: type = RecordType.INSTRUCTION; break;
            default:
                System.out.println("Invalid choice. Please try again.");
                return;
        }

        List<EmergencyRecord> records = recordService.getByType(type);
        printRecordList(records, type.label() + " records");
    }

    private static void doViewAll() throws SQLException, IOException {
        Map<RecordType, List<EmergencyRecord>> grouped = recordService.groupByType();

        if (grouped.isEmpty()) {
            System.out.println("No records found.");
            return;
        }

        System.out.println("\n========== ALL RECORDS ==========");
        for (Map.Entry<RecordType, List<EmergencyRecord>> entry : grouped.entrySet()) {
            System.out.println("\n--- " + entry.getKey().label() + " ---");
            for (EmergencyRecord r : entry.getValue()) {
                System.out.println(r);
            }
        }
    }

    private static void doStaleReport() throws SQLException, IOException {
        List<EmergencyRecord> stale = recordService.getStaleRecords();

        System.out.println("\n========== STALE RECORDS REPORT ==========");
        System.out.println("Records not verified in more than " + EmergencyRecord.STALE_DAYS + " days:");
        System.out.println("(Oldest first)");

        if (stale.isEmpty()) {
            System.out.println("No stale records found. All records are current!");
            return;
        }

        System.out.println("Found " + stale.size() + " stale record(s):\n");
        for (EmergencyRecord r : stale) {
            System.out.println("  " + r + " (" + r.daysSinceVerified() + " days ago)");
        }
    }

    private static void doViewById() throws SQLException, IOException, RecordNotFoundException {
        int id = input.readInt("Enter record ID: ", 1, Integer.MAX_VALUE);
        EmergencyRecord record = recordService.getById(id);
        System.out.println("\n" + record.display());
    }

    private static void doExportChecklist()
            throws SQLException, IOException, RecordNotFoundException, InvalidDataException {
        int id = input.readInt("Enter checklist ID to export: ", 1, Integer.MAX_VALUE);
        Checklist checklist = recordService.getChecklist(id);

        Path exported = FileExporter.exportChecklist(checklist);
        System.out.println("Exported to: " + exported.toAbsolutePath());
    }

    private static void doViewExportedFile() throws IOException {
        List<Path> exports = FileExporter.listExports();

        if (exports.isEmpty()) {
            System.out.println("No exported files yet.");
            return;
        }

        System.out.println("\nExported files:");
        for (int i = 0; i < exports.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + exports.get(i).getFileName());
        }

        int choice = input.readInt("Choose file number: ", 1, exports.size());
        Path chosen = exports.get(choice - 1);

        System.out.println("\n" + FileExporter.readFile(chosen));
    }

    private static final String[] ADMIN_MENU = {
        "1. Add record",
        "2. Update record",
        "3. Delete record",
        "4. Re-verify record",
        "5. Import contacts from CSV",
        "6. Back to main menu"
    };

    private static void doAdminMenu()
            throws SQLException, IOException, UnauthorizedException,
                   RecordNotFoundException, InvalidDataException {

        authService.requireAdmin();

        while (true) {
            System.out.println("\n=========== ADMIN MENU ===========");
            for (String option : ADMIN_MENU) {
                System.out.println("  " + option);
            }
            System.out.println("===================================");

            int choice = input.readInt("Choose an option: ", 1, 6);

            try {
                switch (choice) {
                    case 1: doAddRecord(); break;
                    case 2: doUpdateRecord(); break;
                    case 3: doDeleteRecord(); break;
                    case 4: doReVerify(); break;
                    case 5: doImportCsv(); break;
                    case 6: return;
                    default:
                        System.out.println("Invalid choice. Please try again.");
                }
            } catch (InvalidDataException e) {
                System.out.println("Input error: " + e.getMessage());
            } catch (RecordNotFoundException e) {
                System.out.println("Not found: " + e.getMessage());
            } catch (UnauthorizedException e) {
                System.out.println("Access denied: " + e.getMessage());
            } catch (SQLException e) {
                System.out.println("Database error: " + e.getMessage());
                if (DEBUG) e.printStackTrace();
            } catch (IOException e) {
                System.out.println("File error: " + e.getMessage());
                if (DEBUG) e.printStackTrace();
            } catch (NoSuchElementException e) {
                throw e;
            } catch (Exception e) {
                System.out.println("Something went wrong: " + e.getMessage());
                if (DEBUG) e.printStackTrace();
            }
        }
    }

    private static void doAddRecord()
            throws SQLException, IOException, UnauthorizedException, InvalidDataException {
        System.out.println("\n-- Add Record --");
        System.out.println("Type: 1) Contact  2) Assembly  3) Checklist  4) Instruction");
        int typeChoice = input.readInt("Record type: ", 1, 4);

        EmergencyRecord record;

        switch (typeChoice) {
            case 1: record = buildContact(null); break;
            case 2: record = buildAssembly(null); break;
            case 3: record = buildChecklist(null); break;
            case 4: record = buildInstruction(null); break;
            default:
                System.out.println("Invalid type.");
                return;
        }

        int newId = recordService.addRecord(record);
        System.out.println("Record added successfully with ID: " + newId);
    }

    private static EmergencyContact buildContact(EmergencyContact existing) {
        EmergencyContact contact = (existing != null) ? existing : new EmergencyContact();

        String title = promptField("Title", existing != null ? existing.getTitle() : null, true);
        if (!title.isEmpty()) contact.setTitle(title);

        String phone = promptField("Phone", existing != null ? existing.getPhone() : null, true);
        if (!phone.isEmpty()) contact.setPhone(phone);

        String role = promptField("Contact role", existing != null ? existing.getContactRole() : null, false);
        if (!role.isEmpty()) contact.setContactRole(role);

        String source = promptField("Source/authority", existing != null ? existing.getSource() : null, true);
        if (!source.isEmpty()) contact.setSource(source);

        String desc = promptField("Description (optional)", existing != null ? existing.getDescription() : null, false);
        if (!desc.isEmpty() || existing == null) contact.setDescription(desc);

        LocalDate defaultDate = existing != null ? existing.getLastVerified() : LocalDate.now();
        LocalDate date = input.readDate("Last verified date (yyyy-MM-dd, Enter = " + DateUtil.format(defaultDate) + "): ", defaultDate);
        contact.setLastVerified(date);

        return contact;
    }

    private static AssemblyPoint buildAssembly(AssemblyPoint existing) {
        AssemblyPoint ap = (existing != null) ? existing : new AssemblyPoint();

        String title = promptField("Title", existing != null ? existing.getTitle() : null, true);
        if (!title.isEmpty()) ap.setTitle(title);

        String location = promptField("Location description", existing != null ? existing.getLocationDescription() : null, true);
        if (!location.isEmpty()) ap.setLocationDescription(location);

        String source = promptField("Source/authority", existing != null ? existing.getSource() : null, true);
        if (!source.isEmpty()) ap.setSource(source);

        String desc = promptField("Description/notes (optional)", existing != null ? existing.getDescription() : null, false);
        if (!desc.isEmpty() || existing == null) ap.setDescription(desc);

        LocalDate defaultDate = existing != null ? existing.getLastVerified() : LocalDate.now();
        LocalDate date = input.readDate("Last verified date (yyyy-MM-dd, Enter = " + DateUtil.format(defaultDate) + "): ", defaultDate);
        ap.setLastVerified(date);

        return ap;
    }

    private static Checklist buildChecklist(Checklist existing) {
        Checklist checklist = (existing != null) ? existing : new Checklist();

        String title = promptField("Title", existing != null ? existing.getTitle() : null, true);
        if (!title.isEmpty()) checklist.setTitle(title);

        String source = promptField("Source/authority", existing != null ? existing.getSource() : null, true);
        if (!source.isEmpty()) checklist.setSource(source);

        String desc = promptField("Description (optional)", existing != null ? existing.getDescription() : null, false);
        if (!desc.isEmpty() || existing == null) checklist.setDescription(desc);

        LocalDate defaultDate = existing != null ? existing.getLastVerified() : LocalDate.now();
        LocalDate date = input.readDate("Last verified date (yyyy-MM-dd, Enter = " + DateUtil.format(defaultDate) + "): ", defaultDate);
        checklist.setLastVerified(date);

        if (existing == null) {
            System.out.println("Enter checklist items (one per line, blank line to finish):");
            List<String> items = input.readMultiLine("");
            for (String item : items) {
                checklist.addItem(item);
            }
        } else {
            doChecklistItemsMenu(checklist);
        }

        return checklist;
    }

    private static void doChecklistItemsMenu(Checklist checklist) {
        System.out.println("\nChecklist items sub-menu:");
        System.out.println("  1. Keep items as they are");
        System.out.println("  2. Re-enter all items");
        System.out.println("  3. Add one item");
        System.out.println("  4. Remove one item");

        int choice = input.readInt("Choice: ", 1, 4);

        switch (choice) {
            case 1:
                break;

            case 2:
                checklist.clearItems();
                System.out.println("Enter new items (blank line to finish):");
                List<String> newItems = input.readMultiLine("");
                for (String item : newItems) {
                    checklist.addItem(item);
                }
                break;

            case 3:
                String newItem = input.readRequired("New item text: ");
                checklist.addItem(newItem);
                System.out.println("Item added.");
                break;

            case 4:
                if (checklist.getItems().isEmpty()) {
                    System.out.println("No items to remove.");
                } else {
                    System.out.println("Current items:");
                    for (int i = 0; i < checklist.getItems().size(); i++) {
                        System.out.println("  " + (i + 1) + ". " + checklist.getItems().get(i).getItemText());
                    }
                    int itemNum = input.readInt("Enter item number to remove: ", 1, checklist.getItems().size());
                    checklist.removeItem(itemNum - 1);
                    System.out.println("Item removed.");
                }
                break;

            default:
                System.out.println("Invalid choice.");
        }
    }

    private static Instruction buildInstruction(Instruction existing) {
        Instruction instruction = (existing != null) ? existing : new Instruction();

        String title = promptField("Title", existing != null ? existing.getTitle() : null, true);
        if (!title.isEmpty()) instruction.setTitle(title);

        String source = promptField("Source/authority", existing != null ? existing.getSource() : null, true);
        if (!source.isEmpty()) instruction.setSource(source);

        if (existing == null) {
            System.out.println("Enter steps one per line (blank line to finish):");
            List<String> steps = input.readMultiLine("");
            instruction.setDescription(String.join("\n", steps));
        } else {
            System.out.println("Current steps:\n" + existing.getDescription());
            System.out.println("Enter new steps (blank line to finish), or just press Enter to keep existing:");
            String firstLine = input.readOptional("First step (or Enter to keep): ");
            if (firstLine.isEmpty()) {
            } else {
                List<String> steps = new ArrayList<>();
                steps.add(firstLine);
                List<String> more = input.readMultiLine("Continue entering steps (blank to finish):");
                steps.addAll(more);
                instruction.setDescription(String.join("\n", steps));
            }
        }

        LocalDate defaultDate = existing != null ? existing.getLastVerified() : LocalDate.now();
        LocalDate date = input.readDate("Last verified date (yyyy-MM-dd, Enter = " + DateUtil.format(defaultDate) + "): ", defaultDate);
        instruction.setLastVerified(date);

        return instruction;
    }

    private static String promptField(String label, String existing, boolean required) {
        if (existing != null) {
            System.out.println("  Current " + label + ": " + existing);
            String val = input.readOptional("  New " + label + " (Enter to keep): ");
            return val;
        } else {
            if (required) {
                return input.readRequired(label + ": ");
            } else {
                return input.readOptional(label + " (optional, Enter to skip): ");
            }
        }
    }

    private static void doUpdateRecord()
            throws SQLException, IOException, UnauthorizedException,
                   RecordNotFoundException, InvalidDataException {
        int id = input.readInt("Enter record ID to update: ", 1, Integer.MAX_VALUE);
        EmergencyRecord existing = recordService.getById(id);

        System.out.println("\nUpdating: " + existing.display());
        System.out.println("(Press Enter to keep the current value for each field)");

        EmergencyRecord updated;
        switch (existing.getType()) {
            case CONTACT:
                updated = buildContact((EmergencyContact) existing);
                break;
            case ASSEMBLY:
                updated = buildAssembly((AssemblyPoint) existing);
                break;
            case CHECKLIST:
                updated = buildChecklist((Checklist) existing);
                break;
            case INSTRUCTION:
                updated = buildInstruction((Instruction) existing);
                break;
            default:
                System.out.println("Unknown type.");
                return;
        }

        boolean markVerified = input.readYesNo("Mark as re-verified today? (y/n): ");
        if (markVerified) {
            updated.setLastVerified(LocalDate.now());
        }

        recordService.updateRecord(updated);
        System.out.println("Record updated successfully.");
    }

    private static void doDeleteRecord()
            throws SQLException, IOException, UnauthorizedException, RecordNotFoundException {
        int id = input.readInt("Enter record ID to delete: ", 1, Integer.MAX_VALUE);

        EmergencyRecord record = recordService.getById(id);
        System.out.println("About to delete: " + record);

        boolean confirmed = input.readYesNo("Are you sure? (y/n): ");
        if (!confirmed) {
            System.out.println("Delete cancelled.");
            return;
        }

        recordService.deleteRecord(id);
        System.out.println("Record deleted.");
    }

    private static void doReVerify()
            throws SQLException, IOException, UnauthorizedException, RecordNotFoundException {
        int id = input.readInt("Enter record ID to mark as re-verified: ", 1, Integer.MAX_VALUE);
        recordService.markVerified(id);
        System.out.println("Record " + id + " marked as verified today (" + DateUtil.format(LocalDate.now()) + ").");
    }

    private static void doImportCsv()
            throws SQLException, IOException, UnauthorizedException, InvalidDataException {
        System.out.println("Default path: sample/contacts.csv");
        String path = input.readLine("CSV file path (Enter for default): ");
        if (path.isEmpty()) {
            path = "sample/contacts.csv";
        }

        RecordService.ImportResult result = recordService.importContacts(path);
        System.out.println("Import complete: " + result.imported + " imported, " + result.skipped + " duplicate(s) skipped.");
    }

    private static void printRecordList(List<EmergencyRecord> records, String context) {
        System.out.println("\n--- " + context + " ---");
        if (records.isEmpty()) {
            System.out.println("No matching records found.");
            return;
        }
        System.out.println("Found " + records.size() + " record(s):");
        for (EmergencyRecord r : records) {
            System.out.println("  " + r);
        }
    }
}
