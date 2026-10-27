-- sample_data.sql
-- Safe to run multiple times: deletes all data first, then re-inserts.
-- Run AFTER schema.sql

USE emergency_kit;

-- Clear existing data (child tables first to avoid foreign key errors)
DELETE FROM checklist_items;
DELETE FROM records;
DELETE FROM users;

-- Insert default users
-- Passwords are hashed with SHA2 (same as Java PasswordUtil)
INSERT INTO users (username, password_hash, role) VALUES
 ('admin',  SHA2('admin123', 256),  'ADMIN'),
 ('viewer', SHA2('viewer123', 256), 'VIEWER');

-- Insert records with explicit IDs so checklist_items can reference them
-- Recent records: 5 to 90 days old
-- Stale records: 200+ days old (at least 4 stale entries)

INSERT INTO records (id, type, title, description, phone, contact_role, location_description, source, last_verified) VALUES

-- CONTACT records (campus emergency contacts)
(1,  'CONTACT', 'Campus Security',
     'Main campus security control room. Available 24/7.',
     '0120-2345678', 'Chief Security Officer',
     NULL, 'Campus Security Office',
     DATE_SUB(CURDATE(), INTERVAL 30 DAY)),

(2,  'CONTACT', 'Fire Station',
     'City fire station nearest to campus.',
     '101', 'Fire Control Room',
     NULL, 'Fire & Rescue Services',
     DATE_SUB(CURDATE(), INTERVAL 5 DAY)),

(3,  'CONTACT', 'Ambulance / Medical Centre',
     'Campus medical centre for first aid and ambulance.',
     '0120-9876543', 'Medical Officer',
     NULL, 'Health Centre',
     DATE_SUB(CURDATE(), INTERVAL 60 DAY)),

(4,  'CONTACT', 'Hospital Emergency',
     'Nearest government hospital emergency ward.',
     '102', 'Emergency Ward Supervisor',
     NULL, 'District Hospital',
     DATE_SUB(CURDATE(), INTERVAL 200 DAY)),

(5,  'CONTACT', 'Disaster Management Cell',
     'Campus disaster management coordination cell.',
     '0120-1122334', 'Disaster Management Coordinator',
     NULL, 'Disaster Management Cell',
     DATE_SUB(CURDATE(), INTERVAL 250 DAY)),

(6,  'CONTACT', 'Hostel Warden Emergency',
     'Hostel warden on duty for student emergencies.',
     '9876543210', 'Hostel Warden',
     NULL, 'Hostel Administration',
     DATE_SUB(CURDATE(), INTERVAL 90 DAY)),

-- ASSEMBLY point records
(7,  'ASSEMBLY', 'Main Ground Assembly Point',
     'Large open area near the main entrance of the campus.',
     NULL, NULL,
     'Head to the main ground from any building. Follow the green arrows on the floor. This is the primary assembly point for all students and staff.',
     'Campus Security Office',
     DATE_SUB(CURDATE(), INTERVAL 45 DAY)),

(8,  'ASSEMBLY', 'Library Front Lawn Assembly Point',
     'Open lawn in front of the central library building.',
     NULL, NULL,
     'Exit from the east wing of academic block. Turn left towards the library. Assemble on the front lawn. Do not block the main road.',
     'Campus Safety Committee',
     DATE_SUB(CURDATE(), INTERVAL 320 DAY)),

(9,  'ASSEMBLY', 'Hostel Parking Area Assembly Point',
     'Designated for hostel residents only.',
     NULL, NULL,
     'Hostel residents should assemble at the hostel parking area. Warden will take attendance. Do not leave the area until cleared.',
     'Hostel Administration',
     DATE_SUB(CURDATE(), INTERVAL 70 DAY)),

-- CHECKLIST records (items added separately below)
(10, 'CHECKLIST', 'Fire Evacuation Checklist',
     NULL, NULL, NULL, NULL,
     'Campus Security Office',
     DATE_SUB(CURDATE(), INTERVAL 15 DAY)),

(11, 'CHECKLIST', 'Earthquake Preparedness Checklist',
     NULL, NULL, NULL, NULL,
     'Disaster Management Cell',
     DATE_SUB(CURDATE(), INTERVAL 420 DAY)),

(12, 'CHECKLIST', 'Medical Emergency Checklist',
     NULL, NULL, NULL, NULL,
     'Health Centre',
     DATE_SUB(CURDATE(), INTERVAL 80 DAY)),

-- INSTRUCTION records
(13, 'INSTRUCTION', 'How to Use a Fire Extinguisher',
     'Step 1: Pull the safety pin from the handle.\nStep 2: Aim the nozzle at the base of the fire.\nStep 3: Squeeze the handle firmly to release the agent.\nStep 4: Sweep the nozzle from side to side across the base.\nStep 5: Stand back 6-8 feet from the fire.\nStep 6: If fire is not out in 30 seconds, evacuate immediately.',
     NULL, NULL, NULL,
     'Fire & Rescue Services',
     DATE_SUB(CURDATE(), INTERVAL 10 DAY)),

(14, 'INSTRUCTION', 'Basic CPR Steps',
     'Step 1: Check the scene for safety before approaching.\nStep 2: Check if the person is responsive by tapping their shoulder.\nStep 3: Call for help or shout for someone to call 102.\nStep 4: Tilt the head back and lift the chin to open airway.\nStep 5: Give 2 rescue breaths if trained to do so.\nStep 6: Begin chest compressions - 30 compressions per cycle.\nStep 7: Push hard and fast at the center of the chest.\nStep 8: Continue until help arrives or the person recovers.',
     NULL, NULL, NULL,
     'Health Centre',
     DATE_SUB(CURDATE(), INTERVAL 25 DAY)),

(15, 'INSTRUCTION', 'What to Do During a Lockdown',
     'Step 1: Immediately move to the nearest room or office.\nStep 2: Lock or barricade the door from inside.\nStep 3: Turn off all lights and stay away from windows.\nStep 4: Silence your mobile phone.\nStep 5: Stay calm and keep everyone quiet.\nStep 6: Do not open the door until official all-clear is given.\nStep 7: Call Campus Security at 0120-2345678 only if safe to do so.',
     NULL, NULL, NULL,
     'Campus Security Office',
     DATE_SUB(CURDATE(), INTERVAL 50 DAY));

-- Insert checklist items for record 10 (Fire Evacuation)
INSERT INTO checklist_items (record_id, item_text, item_order) VALUES
(10, 'Sound the fire alarm immediately', 1),
(10, 'Evacuate via the nearest emergency exit', 2),
(10, 'Do not use elevators during evacuation', 3),
(10, 'Proceed to the Main Ground assembly point', 4),
(10, 'Report to your class teacher or department head', 5),
(10, 'Do not re-enter the building until all-clear is given', 6);

-- Insert checklist items for record 11 (Earthquake Preparedness)
INSERT INTO checklist_items (record_id, item_text, item_order) VALUES
(11, 'Drop to hands and knees immediately', 1),
(11, 'Take cover under a sturdy desk or table', 2),
(11, 'Hold on and protect your head and neck', 3),
(11, 'Stay away from windows and exterior walls', 4),
(11, 'After shaking stops, check yourself for injuries', 5),
(11, 'Evacuate if the building structure is damaged', 6);

-- Insert checklist items for record 12 (Medical Emergency)
INSERT INTO checklist_items (record_id, item_text, item_order) VALUES
(12, 'Assess the situation and ensure your own safety first', 1),
(12, 'Call Medical Centre at 0120-9876543', 2),
(12, 'Stay with the patient and keep them calm', 3),
(12, 'Do not move the patient if spinal injury is suspected', 4),
(12, 'Provide basic first aid if trained', 5);
