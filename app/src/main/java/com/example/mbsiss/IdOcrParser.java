package com.example.mbsiss;

public class IdOcrParser {

    // Minimum fields needed to pass quality check
    public static final int FRONT_MIN_FIELDS = 3;
    public static final int BACK_MIN_FIELDS  = 3;

    // ─── Front ID ─────────────────────────────────────────────────────────────
    //
    // CNSC front ID layout (no printed field labels — identified by keywords):
    //
    //   CAMARINES NORTE STATE COLLEGE   ← skip (school header)
    //   Daet, Camarines Norte           ← skip (mixed-case → handwritten filter)
    //   COLLEGE OF ENGINEERING          ← college   (keyword: COLLEGE OF)
    //   [photo + logo area]
    //   Student No. 19-1034             ← studentId (keyword: STUDENT NO)
    //   Birthdate: MAR 15, 2000         ← skip
    //   JOHN LAURENCE A. BAILE          ← name      (ALL-CAPS, 2-6 words)
    //   -Name-                          ← skip (label caption)
    //   BS IN ELECTRICAL ENGINEERING    ← course    (keyword: BS IN)
    //   -Course-                        ← skip
    //   [yellow zone: signature]        ← masked before OCR reaches here
    //
    public static int parseFront(String rawText, StudentData data) {
        String[] lines = rawText.split("\n");
        int fieldCount = 0;

        for (int i = 0; i < lines.length; i++) {
            String line  = lines[i].trim();
            String upper = line.toUpperCase();

            if (line.isEmpty()) continue;
            if (isLikelyHandwritten(line)) continue;
            if (isFrontNoise(upper)) continue;   // skip known non-data lines

            // ── Student ID ──────────────────────────────────────────────────
            if (upper.contains("STUDENT NO") || upper.contains("STUDENT NO.")) {
                String val = extractAfterColon(line);
                // Also check after dash: "Student No- 19-1034"
                if (val.isEmpty()) val = extractAfterDash(line);
                // Value may be on the next line
                if (val.isEmpty() && i + 1 < lines.length) {
                    String next = lines[i + 1].trim();
                    if (next.matches("\\d{2,4}-\\d{3,6}")) val = next;
                }
                if (!val.isEmpty() && data.studentId == null) {
                    // Strip any trailing non-ID text (e.g. "19-1034 Birthdate...")
                    val = val.split("\\s")[0].trim();
                    data.studentId = val;
                    fieldCount++;
                }
            }

            // Regex fallback — bare ID number on its own line e.g. "19-1034"
            if (data.studentId == null && line.matches("\\d{2,4}-\\d{3,6}")) {
                data.studentId = line;
                fieldCount++;
            }

            // ── College ─────────────────────────────────────────────────────
            // Printed as "COLLEGE OF ENGINEERING" — no label, keyword only.
            // Exclude "STATE COLLEGE" (school name in header).
            if (upper.contains("COLLEGE OF")
                    && !upper.contains("STATE COLLEGE")
                    && data.college == null) {
                data.college = line;
                fieldCount++;
            }

            // ── Course ──────────────────────────────────────────────────────
            if ((upper.contains("BS IN")       ||
                    upper.contains("BS.IN")       ||
                    upper.contains("BACHELOR OF") ||
                    upper.contains("BACHELOR IN")) && data.course == null) {
                data.course = line;
                fieldCount++;
            }

            // ── Student Name ────────────────────────────────────────────────
            // No label on card. ALL-CAPS line, 2-6 words, letters/dots/hyphens,
            // not matching any header keyword.
            if (data.studentName == null
                    && isPrintedName(upper)
                    && !upper.contains("COLLEGE")
                    && !upper.contains("BACHELOR")
                    && !upper.contains("BS IN")
                    && !upper.contains("STATE")
                    && !upper.contains("NORTE")
                    && !upper.contains("CAMARINES")
                    && !upper.contains("UNIVERSITY")
                    && !upper.contains("INSTITUTE")
                    && !upper.contains("SCHOOL")
                    && !upper.contains("DEPARTMENT")
                    && !upper.contains("STUDENT")
                    && !upper.contains("REGISTRAR")
                    && !upper.contains("MPA")
                    && !upper.contains("SEMESTER")) {
                data.studentName = line;
                fieldCount++;
            }
        }

        return fieldCount;
    }

    // ─── Back ID ──────────────────────────────────────────────────────────────
    //
    // CNSC back ID layout:
    //
    //   Address:      <personal address>
    //   Contact No.:  <personal contact number>
    //   ┌─────────────────────────────────────────┐
    //   │  This card is non-transferrable ...      │  ← SKIP (registrar zone)
    //   │  [signature]                             │  ← SKIP
    //   │  SHEILA P. SAPUSAO, MPA  Registrar III  │  ← SKIP
    //   │  [semester stamp]                        │  ← SKIP
    //   └─────────────────────────────────────────┘
    //   In case of emergency, please contact:      ← boundary marker
    //   Name:         <emergency name>
    //   Address:      <emergency address>
    //   Contact No.:  <emergency contact number>
    //
    // The registrar zone is handled two ways:
    //   1. CameraFragment masks it spatially (vertical crop of middle third).
    //   2. IdOcrParser skips any line that matches registrar/disclaimer keywords.
    //
    // Parser ALWAYS trusts the printed label. No swap logic.
    // Rare defects (wrong values under labels) are corrected by the guard
    // manually in the review dialog.
    //
    public static int parseBack(String rawText, StudentData data) {
        String[] lines = rawText.split("\n");
        int fieldCount = 0;
        boolean inEmergencySection   = false;
        boolean inRegistrarZone      = false;

        for (int i = 0; i < lines.length; i++) {
            // Strip leading non-alphanumeric chars (e.g. ML Kit pipe: "|Address:" → "Address:")
            String line  = lines[i].trim().replaceAll("^[^A-Za-z0-9]+", "");
            String upper = line.toUpperCase();

            if (line.isEmpty()) continue;

            // ── Registrar zone entry/exit ────────────────────────────────────
            // Enter when we see the disclaimer text.
            // Exit when we see the emergency section header.
            if (isRegistrarZoneLine(upper)) {
                inRegistrarZone = true;
                continue;
            }

            // ── Emergency section boundary ───────────────────────────────────
            if (upper.contains("IN CASE OF EMERGENCY") ||
                    upper.contains("EMERGENCY, PLEASE")    ||
                    upper.contains("CASE OF EMERGENCY")) {
                inRegistrarZone    = false;
                inEmergencySection = true;
                continue;
            }

            // Skip everything inside the registrar zone
            if (inRegistrarZone) continue;

            if (isLikelyHandwritten(line)) continue;

            if (!inEmergencySection) {

                // ── Personal Address ─────────────────────────────────────────
                if ((upper.startsWith("ADDRESS") || upper.startsWith("ADD:"))
                        && data.address == null) {
                    String val = extractAfterColon(line);
                    if (val.isEmpty() && i + 1 < lines.length
                            && !isLabel(lines[i + 1])) {
                        val = lines[i + 1].trim();
                    }
                    // Multi-line address continuation
                    if (!val.isEmpty() && i + 2 < lines.length
                            && !isLabel(lines[i + 2])
                            && !isLikelyHandwritten(lines[i + 2])) {
                        String cont = lines[i + 2].trim().toUpperCase();
                        if (cont.contains("CAMARINES") || cont.contains("NORTE") ||
                                cont.contains("BARANGAY")  || cont.contains("PUROK")  ||
                                cont.contains("SAN")) {
                            val = val + " " + lines[i + 2].trim();
                        }
                    }
                    if (!val.isEmpty() && val.length() >= 4) {
                        data.address = val;
                        fieldCount++;
                    }
                }

                // ── Personal Contact Number ──────────────────────────────────
                if ((upper.startsWith("CONTACT NO")     ||
                        upper.startsWith("CONTACT NUMBER") ||
                        upper.startsWith("CONTACT:"))
                        && data.contactNumber == null) {
                    String val = extractAfterColon(line);
                    if (val.isEmpty() && i + 1 < lines.length) {
                        String next = lines[i + 1].trim();
                        if (looksLikePhoneNumber(next)) val = next;
                    }
                    if (!val.isEmpty()) {
                        data.contactNumber = val;
                        fieldCount++;
                    }
                }

            } else {

                // ── Emergency Contact Name ───────────────────────────────────
                if ((upper.startsWith("NAME") || upper.startsWith("NAME:"))
                        && data.emergencyContact == null) {
                    String val = extractAfterColon(line);
                    if (val.isEmpty() && i + 1 < lines.length
                            && !isLabel(lines[i + 1])) {
                        val = lines[i + 1].trim();
                    }
                    if (!val.isEmpty() && !isLikelyHandwritten(val)) {
                        data.emergencyContact = val;
                        fieldCount++;
                    }
                }

                // ── Emergency Address ────────────────────────────────────────
                if ((upper.startsWith("ADDRESS") || upper.startsWith("ADD:"))
                        && data.emergencyAddress == null) {
                    String val = extractAfterColon(line);
                    if (val.isEmpty() && i + 1 < lines.length
                            && !isLabel(lines[i + 1])) {
                        val = lines[i + 1].trim();
                    }
                    // Multi-line continuation
                    if (!val.isEmpty() && i + 2 < lines.length
                            && !isLabel(lines[i + 2])
                            && !isLikelyHandwritten(lines[i + 2])) {
                        String cont = lines[i + 2].trim().toUpperCase();
                        if (cont.contains("CAMARINES") || cont.contains("NORTE") ||
                                cont.contains("PUROK")     || cont.contains("SAN")) {
                            val = val + " " + lines[i + 2].trim();
                        }
                    }
                    if (!val.isEmpty() && val.length() >= 4 && !isLikelyHandwritten(val)) {
                        data.emergencyAddress = val;
                        fieldCount++;
                    }
                }

                // ── Emergency Contact Number ─────────────────────────────────
                if ((upper.startsWith("CONTACT NO")     ||
                        upper.startsWith("CONTACT NUMBER") ||
                        upper.startsWith("CONTACT:"))
                        && data.emergencyContactNumber == null) {
                    String val = extractAfterColon(line);
                    if (val.isEmpty() && i + 1 < lines.length) {
                        String next = lines[i + 1].trim();
                        if (looksLikePhoneNumber(next)) val = next;
                    }
                    if (!val.isEmpty()) {
                        data.emergencyContactNumber = val;
                        fieldCount++;
                    }
                }
            }
        }

        return fieldCount;
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Returns true if a line from the FRONT ID is known noise that should
     * never be treated as a data field.
     * Covers: subtitle captions (-Name-, -Course-), birthdate lines,
     * and the school address line.
     */
    private static boolean isFrontNoise(String upper) {
        return upper.equals("-NAME-")
                || upper.equals("NAME")
                || upper.equals("-COURSE-")
                || upper.equals("COURSE")
                || upper.equals("-SIGNATURE-")
                || upper.equals("SIGNATURE")
                || upper.startsWith("BIRTHDATE")
                || upper.startsWith("BIRTH DATE")
                || upper.contains("DAET, CAMARINES")
                || (upper.contains("CAMARINES NORTE") && upper.contains("DAET"));
    }

    /**
     * Returns true if a line belongs to the registrar zone on the BACK ID.
     * This zone contains the semester validity disclaimer, registrar signature,
     * registrar name/title, and semester stamp — none of which should be parsed.
     *
     * Triggered by any of these keywords appearing in the line:
     *   - "NON-TRANSFERRABLE" / "NON TRANSFERABLE"
     *   - "SEMESTER INDICATED"
     *   - "COLLEGE REGISTRAR"
     *   - "REGISTRAR"
     *   - "SHEILA" (registrar name — specific to CNSC)
     *   - "SAPUSAO" (registrar surname)
     *   - "MPA" (registrar title)
     *   - "1ST SEMESTER" / "2ND SEMESTER"
     *   - "ENROLLED" / "REGISTERED"
     */
    private static boolean isRegistrarZoneLine(String upper) {
        return upper.contains("NON-TRANSFERR")
                || upper.contains("NON TRANSFER")
                || upper.contains("SEMESTER INDICATED")
                || upper.contains("PLEASE REPORT")
                || upper.contains("COLLEGE REGISTRAR")
                || upper.contains("REGISTRAR")
                || upper.contains("SHEILA")
                || upper.contains("SAPUSAO")
                || upper.contains(", MPA")
                || upper.contains("1ST SEMESTER")
                || upper.contains("2ND SEMESTER")
                || upper.contains("ENROLLED")
                || upper.contains("REGISTERED")
                || upper.contains("VALID ONLY")
                || upper.contains("THIS CARD");
    }

    /**
     * Heuristic: handwritten text tends to be mixed-case and short.
     * If more than 40% of letter characters are lowercase AND the line is
     * shorter than 30 chars, treat it as handwritten and skip it.
     */
    private static boolean isLikelyHandwritten(String line) {
        if (line.length() < 2) return true;
        int upper = 0, lower = 0;
        for (char c : line.toCharArray()) {
            if (Character.isUpperCase(c)) upper++;
            else if (Character.isLowerCase(c)) lower++;
        }
        int total = upper + lower;
        if (total == 0) return false;
        return ((double) lower / total) > 0.4 && line.length() < 30;
    }

    /**
     * True if the line starts with a known printed field label.
     * Used to stop multi-line value collection from spilling into the next field.
     */
    private static boolean isLabel(String line) {
        String u = line.trim().toUpperCase();
        return u.startsWith("ADDRESS")  || u.startsWith("CONTACT") ||
                u.startsWith("NAME")     || u.startsWith("IN CASE") ||
                u.startsWith("COLLEGE")  || u.startsWith("COURSE")  ||
                u.startsWith("STUDENT");
    }

    /**
     * True if the string is ALL-CAPS, contains only letters/spaces/dots/hyphens,
     * and has between 2 and 6 words — matches printed names on the front ID.
     */
    private static boolean isPrintedName(String upper) {
        if (!upper.equals(upper.toUpperCase())) return false;
        String[] words = upper.trim().split("\\s+");
        return words.length >= 2 && words.length <= 6
                && upper.matches("[A-Z .\\-]+");
    }

    /** Extracts the text after the first colon on a line, trimmed. */
    private static String extractAfterColon(String line) {
        int idx = line.indexOf(":");
        if (idx >= 0 && idx < line.length() - 1) {
            return line.substring(idx + 1).trim();
        }
        return "";
    }

    /**
     * Extracts value after the last dash on a line.
     * Handles labels like "Student No- 19-1034" where OCR uses dash not colon.
     * Only returns something if the result after the dash looks like a student ID.
     */
    private static String extractAfterDash(String line) {
        // Find last dash
        int idx = line.lastIndexOf("-");
        if (idx >= 0 && idx < line.length() - 1) {
            String candidate = line.substring(idx + 1).trim();
            // Must look like a student ID e.g. "1034" (rest of "19-1034")
            // Actually the full ID has a dash so look for the split differently:
            // "Student No- 19-1034" → after first dash-space = "19-1034"
        }
        // Safer: extract everything after the last space-dash-space pattern
        String[] parts = line.split("-\\s*");
        if (parts.length >= 2) {
            // Take the last two dash-separated parts as the ID
            String last = parts[parts.length - 1].trim();
            String secondLast = parts[parts.length - 2].trim();
            // secondLast should be 2-4 digits, last should be 3-6 digits
            if (secondLast.matches("\\d{2,4}") && last.matches("\\d{3,6}")) {
                return secondLast + "-" + last;
            }
        }
        return "";
    }

    /** True if the string looks like a Philippine mobile or landline number. */
    static boolean looksLikePhoneNumber(String val) {
        String clean = val.replaceAll("[\\s\\-]", "");
        return clean.matches("0\\d{10}")      // 09XXXXXXXXX
                || clean.matches("\\+63\\d{10}")  // +63XXXXXXXXX
                || clean.matches("\\d{7,11}");    // generic 7-11 digit number
    }
}