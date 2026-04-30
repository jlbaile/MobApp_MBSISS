package com.example.mbsiss;

import android.util.Log;

/**
 * IdOcrParser — Revised (scan-everything approach)
 *
 * OLD approach: tried to block known noise lines before parsing.
 *   Problem: legitimate field values were also being blocked, especially
 *   on the back ID where the registrar zone sat close to real fields.
 *
 * NEW approach: parse ALL lines, trust printed field labels.
 *   - Every line is checked against known label prefixes
 *   - Value is taken from the same line (after the colon) or the next line
 *   - No lines are skipped upfront — if something fills the wrong field,
 *     the guard corrects it in the review dialog
 *   - parseFront / parseBack return void; field count check is removed
 *     (dialog always opens so guard can fill manually if needed)
 *
 * This makes the back ID reliable because "Address:", "Contact No.:",
 * "Name:" and "In case of emergency" are PRINTED LABELS — they will
 * always appear in the OCR output regardless of surrounding noise.
 */
public class IdOcrParser {

    private static final String TAG = "IdOcrParser";

    // ─── Front ID ─────────────────────────────────────────────────────────────
    //
    // CNSC front ID has no printed field labels for name — identified by shape.
    // All other fields are keyword-identifiable:
    //   "Student No."  → studentId
    //   "COLLEGE OF"   → college
    //   "BS IN" / "BACHELOR" → course
    //   ALL-CAPS 2–5 word line not matching any header → studentName
    //
    public static void parseFront(String rawText, StudentData data) {
        String[] lines = rawText.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line  = lines[i].trim();
            String upper = line.toUpperCase();
            if (line.isEmpty()) continue;

            // ── Student ID ────────────────────────────────────────────────────
            if (upper.contains("STUDENT NO") && data.studentId == null) {
                String val = afterColon(line);
                if (val.isEmpty()) val = afterDash(line);
                if (val.isEmpty() && i + 1 < lines.length)
                    val = lines[i + 1].trim();
                val = val.split("\\s")[0].trim();   // strip trailing garbage
                if (!val.isEmpty()) {
                    data.studentId = val;
                    Log.d(TAG, "studentId = " + val);
                }
            }

            // Regex fallback: bare "19-1034" on its own line
            if (data.studentId == null && line.matches("\\d{2,4}-\\d{3,6}")) {
                data.studentId = line;
                Log.d(TAG, "studentId (regex) = " + line);
            }

            // ── College ───────────────────────────────────────────────────────
            if (upper.contains("COLLEGE OF")
                    && !upper.contains("STATE COLLEGE")
                    && data.college == null) {
                data.college = line;
                Log.d(TAG, "college = " + line);
            }

            // ── Course ────────────────────────────────────────────────────────
            if (data.course == null && (
                    upper.contains("BS IN")       ||
                            upper.contains("BS.")         ||
                            upper.contains("BACHELOR OF") ||
                            upper.contains("BACHELOR IN"))) {
                data.course = line;
                Log.d(TAG, "course = " + line);
            }

            // ── Student Name ──────────────────────────────────────────────────
            // No printed label. Identified as: ALL-CAPS, 2–5 words,
            // only letters/dots/hyphens, not a known header keyword.
            if (data.studentName == null && isPrintedName(upper)
                    && !isKnownHeader(upper)) {
                data.studentName = line;
                Log.d(TAG, "studentName = " + line);
            }
        }
    }

    // ─── Back ID ──────────────────────────────────────────────────────────────
    //
    // Back ID has PRINTED LABELS for every field:
    //   "Address:"           → address
    //   "Contact No.:"       → contactNumber
    //   "In case of emergency..." → marks start of emergency section
    //   "Name:"              → emergencyContact   (after emergency marker)
    //   "Address:"           → emergencyAddress   (after emergency marker)
    //   "Contact No.:"       → emergencyContactNumber (after emergency marker)
    //
    // Strategy: walk every line, look for label prefixes, grab value.
    // We do NOT skip any lines beforehand — if the registrar zone produces
    // text that accidentally matches a label, the guard will see it in the
    // dialog and correct it. In practice the registrar text ("NON-TRANSFERRABLE",
    // "REGISTRAR III", semester stamps) does NOT start with "Address:",
    // "Contact No.:", or "Name:", so false positives are extremely rare.
    //
    public static void parseBack(String rawText, StudentData data) {
        String[] lines = rawText.split("\n");
        boolean inEmergency = false;

        for (int i = 0; i < lines.length; i++) {
            // Strip leading pipe characters that ML Kit sometimes emits
            String line  = lines[i].trim().replaceAll("^[^A-Za-z0-9]+", "");
            String upper = line.toUpperCase();
            if (line.isEmpty()) continue;

            // ── Emergency section boundary ────────────────────────────────────
            if (upper.contains("IN CASE OF EMERGENCY") ||
                    upper.contains("CASE OF EMERGENCY")    ||
                    upper.contains("EMERGENCY, PLEASE")) {
                inEmergency = true;
                Log.d(TAG, "Emergency section started");
                continue;
            }

            if (!inEmergency) {

                // ── Personal Address ──────────────────────────────────────────
                if (isAddressLabel(upper) && data.address == null) {
                    String val = afterColon(line);
                    if (val.isEmpty() && i + 1 < lines.length)
                        val = lines[i + 1].trim();
                    if (!val.isEmpty() && val.length() >= 4) {
                        data.address = val;
                        Log.d(TAG, "address = " + val);
                    }
                }

                // ── Personal Contact Number ───────────────────────────────────
                if (isContactLabel(upper) && data.contactNumber == null) {
                    String val = afterColon(line);
                    if (val.isEmpty() && i + 1 < lines.length)
                        val = lines[i + 1].trim();
                    if (!val.isEmpty()) {
                        data.contactNumber = val;
                        Log.d(TAG, "contactNumber = " + val);
                    }
                }

            } else {

                // ── Emergency Contact Name ────────────────────────────────────
                if (isNameLabel(upper) && data.emergencyContact == null) {
                    String val = afterColon(line);
                    if (val.isEmpty() && i + 1 < lines.length)
                        val = lines[i + 1].trim();
                    if (!val.isEmpty()) {
                        data.emergencyContact = val;
                        Log.d(TAG, "emergencyContact = " + val);
                    }
                }

                // ── Emergency Address ─────────────────────────────────────────
                if (isAddressLabel(upper) && data.emergencyAddress == null) {
                    String val = afterColon(line);
                    if (val.isEmpty() && i + 1 < lines.length)
                        val = lines[i + 1].trim();
                    if (!val.isEmpty() && val.length() >= 4) {
                        data.emergencyAddress = val;
                        Log.d(TAG, "emergencyAddress = " + val);
                    }
                }

                // ── Emergency Contact Number ──────────────────────────────────
                if (isContactLabel(upper) && data.emergencyContactNumber == null) {
                    String val = afterColon(line);
                    if (val.isEmpty() && i + 1 < lines.length)
                        val = lines[i + 1].trim();
                    if (!val.isEmpty()) {
                        data.emergencyContactNumber = val;
                        Log.d(TAG, "emergencyContactNumber = " + val);
                    }
                }
            }
        }
    }

    // ─── Label Helpers ────────────────────────────────────────────────────────

    private static boolean isAddressLabel(String upper) {
        return upper.startsWith("ADDRESS") || upper.startsWith("ADD:");
    }

    private static boolean isContactLabel(String upper) {
        return upper.startsWith("CONTACT NO")     ||
                upper.startsWith("CONTACT NUMBER") ||
                upper.startsWith("CONTACT:");
    }

    private static boolean isNameLabel(String upper) {
        return upper.startsWith("NAME") || upper.startsWith("NAME:");
    }

    // ─── Front ID Helpers ─────────────────────────────────────────────────────

    private static boolean isPrintedName(String upper) {
        // ALL-CAPS, 2–5 words, only letters / spaces / dots / hyphens
        String[] words = upper.trim().split("\\s+");
        return words.length >= 2 && words.length <= 5
                && upper.matches("[A-Z .\\-]+");
    }

    private static boolean isKnownHeader(String upper) {
        return upper.contains("COLLEGE")    || upper.contains("BACHELOR") ||
                upper.contains("BS IN")      || upper.contains("STATE")    ||
                upper.contains("NORTE")      || upper.contains("CAMARINES")||
                upper.contains("UNIVERSITY") || upper.contains("INSTITUTE")||
                upper.contains("SCHOOL")     || upper.contains("STUDENT")  ||
                upper.contains("REGISTRAR")  || upper.contains("SEMESTER") ||
                upper.contains("DEPARTMENT") || upper.contains("MPA");
    }

    // ─── String Helpers ───────────────────────────────────────────────────────

    private static String afterColon(String line) {
        int idx = line.indexOf(":");
        if (idx >= 0 && idx < line.length() - 1)
            return line.substring(idx + 1).trim();
        return "";
    }

    /** Handles "Student No- 19-1034" where OCR uses a dash instead of colon. */
    private static String afterDash(String line) {
        String[] parts = line.split("-\\s*");
        if (parts.length >= 2) {
            String last       = parts[parts.length - 1].trim();
            String secondLast = parts[parts.length - 2].trim();
            if (secondLast.matches("\\d{2,4}") && last.matches("\\d{3,6}"))
                return secondLast + "-" + last;
        }
        return "";
    }
}