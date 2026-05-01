package com.example.mbsiss;

import android.util.Log;

/**
 * IdOcrParser — Back-ID Fix v4.1
 *
 * ONLY CHANGE from v4:
 *
 * Emergency Name was always null even though the raw OCR contained
 * "Name: JOHN PAUL DELA CRUZ" correctly.
 *
 * ROOT CAUSE: When the phone is held horizontally, ML Kit reads the card
 * in a different order and "Name:" appears BEFORE the "In case of
 * emergency" marker in the OCR stream. At that point inEmergency=false,
 * so the Name: key is never matched (the emergency name handler is only
 * reachable when inEmergency=true).
 *
 * FIX: Added a dedicated fallback pass — findEmergencyNameFallback().
 * After the main parse loop, if emergencyContact is still null, this
 * method scans the ENTIRE line array for any "Name:" key and takes its
 * value, regardless of where it appeared relative to the emergency marker.
 * This is safe because "Name:" never appears in the personal section of
 * the back ID — it is always the emergency contact name label.
 */
public class IdOcrParser {

    private static final String TAG = "IdOcrParser";

    // ─── Front ID ─────────────────────────────────────────────────────────────
    public static void parseFront(String rawText, StudentData data) {
        String[] lines = rawText.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            String upper = line.toUpperCase();
            if (line.isEmpty()) continue;

            if (upper.contains("STUDENT NO") && data.studentId == null) {
                String val = valueAfterColon(line);
                if (val.isEmpty()) val = valueAfterDash(line);
                if (val.isEmpty()) val = nextValueSkippingNoise(lines, i);
                val = firstToken(val);
                if (!val.isEmpty()) {
                    data.studentId = val;
                    Log.d(TAG, "FRONT studentId = " + val);
                }
            }

            if (data.studentId == null && line.matches("\\d{2,4}-\\d{3,6}")) {
                data.studentId = line;
                Log.d(TAG, "FRONT studentId [regex] = " + line);
            }

            if (upper.contains("COLLEGE OF")
                    && !upper.contains("STATE COLLEGE")
                    && data.college == null) {
                data.college = line;
                Log.d(TAG, "FRONT college = " + line);
            }

            if (data.course == null && (
                    upper.contains("BS IN") || upper.contains("BS.") ||
                            upper.contains("BACHELOR OF") || upper.contains("BACHELOR IN"))) {
                data.course = line;
                Log.d(TAG, "FRONT course = " + line);
            }

            if (data.studentName == null && isNameCaption(upper)) {
                for (int k = i - 1; k >= 0; k--) {
                    String prev = lines[k].trim();
                    if (prev.isEmpty()) continue;
                    if (isPrintedName(prev.toUpperCase()) && !isKnownHeader(prev.toUpperCase()))
                        data.studentName = prev;
                    break;
                }
            }
        }

        if (data.studentName == null) {
            for (String line : lines) {
                String upper = line.trim().toUpperCase();
                if (isPrintedName(upper) && !isKnownHeader(upper)) {
                    data.studentName = line.trim();
                    break;
                }
            }
        }
    }

    // ─── Back ID ──────────────────────────────────────────────────────────────
    public static void parseBack(String rawText, StudentData data) {
        String[] lines = rawText.split("\n");

        int emergencyLine     = findEmergencyMarkerLine(lines);
        int lastRegistrarLine = findLastRegistrarLine(lines, emergencyLine);

        Log.d(TAG, "BACK boundaries → registrar ends: " + lastRegistrarLine
                + " | emergency starts: " + emergencyLine);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim().replaceAll("^[^A-Za-z0-9]+", "");
            String upper = line.toUpperCase();
            if (line.isEmpty()) continue;

            if (i == emergencyLine) continue;

            if (isRegistrarLine(upper)) {
                Log.d(TAG, "BACK skip registrar: " + line);
                continue;
            }

            if (lastRegistrarLine >= 0 && emergencyLine >= 0
                    && i > lastRegistrarLine && i < emergencyLine) {
                Log.d(TAG, "BACK skip stamp zone: " + line);
                continue;
            }

            boolean inEmergency = (emergencyLine >= 0) && (i > emergencyLine);

            if (!inEmergency) {
                // Personal Address
                if (isAddressKey(upper) && data.address == null) {
                    String inline = valueAfterColon(line);
                    if (inline.isEmpty()) inline = valueAfterKeyword(line, "ADDRESS");
                    if (!inline.isEmpty()) {
                        data.address = collectAddressLines(lines, i, inline);
                    } else {
                        int[] result = extractAddressValue(lines, i);
                        if (result[1] >= 0) {
                            String firstLine = lines[result[1]].trim()
                                    .replaceAll("^[^A-Za-z0-9]+", "");
                            data.address = collectAddressLines(lines, result[1], firstLine);
                        }
                    }
                    if (data.address != null)
                        Log.d(TAG, "BACK address = " + data.address);
                }

                // Personal Contact Number
                if (isContactKey(upper) && data.contactNumber == null) {
                    String val = extractContactValue(lines, i, line);
                    if (!val.isEmpty()) {
                        data.contactNumber = val;
                        Log.d(TAG, "BACK contactNumber = " + val
                                + (looksLikePhone(val) ? " ✓" : " [verify]"));
                    }
                }

            } else {
                // Emergency Name
                if (isNameKey(upper) && data.emergencyContact == null) {
                    String val = valueAfterColon(line);
                    if (val.isEmpty()) val = nextValueSkippingNoise(lines, i);
                    if (!val.isEmpty()) {
                        data.emergencyContact = val;
                        Log.d(TAG, "BACK emergencyContact = " + val);
                    }
                }

                // Emergency Address
                if (isAddressKey(upper) && data.emergencyAddress == null) {
                    String inline = valueAfterColon(line);
                    if (inline.isEmpty()) inline = valueAfterKeyword(line, "ADDRESS");
                    if (!inline.isEmpty()) {
                        data.emergencyAddress = collectAddressLines(lines, i, inline);
                    } else {
                        int valIdx = findNextValueLineIndex(lines, i);
                        if (valIdx >= 0) {
                            String firstLine = lines[valIdx].trim()
                                    .replaceAll("^[^A-Za-z0-9]+", "");
                            data.emergencyAddress = collectAddressLines(lines, valIdx, firstLine);
                        }
                    }
                    if (data.emergencyAddress != null)
                        Log.d(TAG, "BACK emergencyAddress = " + data.emergencyAddress);
                }

                // Emergency Contact Number
                if (isContactKey(upper) && data.emergencyContactNumber == null) {
                    String val = extractContactValue(lines, i, line);
                    if (!val.isEmpty()) {
                        data.emergencyContactNumber = val;
                        Log.d(TAG, "BACK emergencyContactNumber = " + val
                                + (looksLikePhone(val) ? " ✓" : " [verify]"));
                    }
                }
            }
        }

        // ── THE FIX: Emergency Name fallback ──────────────────────────────────
        // If emergencyContact is still null after the main loop, "Name:" was
        // present in the OCR text but appeared before the emergency marker
        // (happens when phone is horizontal — reversed reading order).
        // Safe to scan the whole array: "Name:" on the back ID always means
        // emergency contact name — it never labels a personal field.
        if (data.emergencyContact == null) {
            data.emergencyContact = findEmergencyNameFallback(lines);
            if (data.emergencyContact != null)
                Log.d(TAG, "BACK emergencyContact [fallback] = " + data.emergencyContact);
        }

        // ── Swap personal ↔ emergency address and contact number ──────────────
        // The 90° bitmap rotation applied in CameraFragment before OCR causes
        // ML Kit to assign the personal section values into the emergency fields
        // and vice versa. We correct this here by swapping the two pairs.
        //
        // Before swap:  data.address             = emergency address value
        //               data.contactNumber        = emergency contact value
        //               data.emergencyAddress     = personal address value
        //               data.emergencyContactNumber = personal contact value
        //
        // After swap:   data.address             = personal address value  ✓
        //               data.contactNumber        = personal contact value  ✓
        //               data.emergencyAddress     = emergency address value ✓
        //               data.emergencyContactNumber = emergency contact value ✓
        //
        // Emergency Name (data.emergencyContact) is correct — not swapped.
        String tempAddress = data.address;
        data.address = data.emergencyAddress;
        data.emergencyAddress = tempAddress;

        String tempContact = data.contactNumber;
        data.contactNumber = data.emergencyContactNumber;
        data.emergencyContactNumber = tempContact;

        Log.d(TAG, "BACK after swap → address=" + data.address
                + " | contactNumber=" + data.contactNumber
                + " | emergencyAddress=" + data.emergencyAddress
                + " | emergencyContactNumber=" + data.emergencyContactNumber);
    }

    // ─── Emergency name fallback ───────────────────────────────────────────────

    /**
     * Scans all lines for a "Name:" key and returns its value.
     * Called only when the main loop failed to find the emergency name,
     * which happens when OCR reads the card out of top-to-bottom order.
     *
     * Guards against false positives:
     * - Skips lines inside the stamp dead zone (noise)
     * - Skips the front-ID "-Name-" caption (isNameCaption check)
     * - Only accepts values that are not known headers or registrar text
     */
    private static String findEmergencyNameFallback(String[] lines) {
        // Re-compute boundaries for safety
        int emergencyLine     = findEmergencyMarkerLine(lines);
        int lastRegistrarLine = findLastRegistrarLine(lines, emergencyLine);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim().replaceAll("^[^A-Za-z0-9]+", "");
            String upper = line.toUpperCase();
            if (line.isEmpty()) continue;

            // Skip stamp dead zone
            if (lastRegistrarLine >= 0 && emergencyLine >= 0
                    && i > lastRegistrarLine && i < emergencyLine) continue;

            // Skip registrar lines
            if (isRegistrarLine(upper)) continue;

            // Skip the emergency marker itself
            if (i == emergencyLine) continue;

            if (isNameKey(upper)) {
                String val = valueAfterColon(line);
                if (val.isEmpty()) val = nextValueSkippingNoise(lines, i);
                // Sanity check: value should not be a known header or empty
                if (!val.isEmpty() && !isKnownHeader(val.toUpperCase())
                        && !isRegistrarLine(val.toUpperCase())) {
                    return val;
                }
            }
        }
        return null;
    }

    // ─── Section boundary finders ─────────────────────────────────────────────

    private static int findEmergencyMarkerLine(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            String s = lines[i].trim().replaceAll("^[^A-Za-z0-9]+", "").toUpperCase();
            if (s.contains("EMERGENCY") && !isAddressKey(s)
                    && !isContactKey(s) && !isNameKey(s))
                return i;
        }
        return -1;
    }

    private static int findLastRegistrarLine(String[] lines, int emergencyLine) {
        int last = -1;
        int limit = (emergencyLine >= 0) ? emergencyLine : lines.length;
        for (int i = 0; i < limit; i++) {
            String s = lines[i].trim().replaceAll("^[^A-Za-z0-9]+", "").toUpperCase();
            if (isRegistrarLine(s)) last = i;
        }
        return last;
    }

    // ─── Address collection ───────────────────────────────────────────────────

    private static String collectAddressLines(String[] lines,
                                              int startIndex,
                                              String firstVal) {
        StringBuilder sb = new StringBuilder(firstVal);
        for (int k = startIndex + 1; k < Math.min(startIndex + 6, lines.length); k++) {
            String next = lines[k].trim().replaceAll("^[^A-Za-z0-9]+", "");
            if (next.isEmpty()) continue;
            String upper = next.toUpperCase();
            if (isAddressKey(upper) || isContactKey(upper) || isNameKey(upper)) break;
            if (upper.contains("EMERGENCY") || isRegistrarLine(upper)) break;
            if (looksLikePhone(next)) break;
            if (sb.toString().toUpperCase().contains(next.toUpperCase())) continue;
            sb.append(" ").append(next);
        }
        return sb.toString();
    }

    private static int[] extractAddressValue(String[] lines, int keyIndex) {
        int valIdx = findNextValueLineIndex(lines, keyIndex);
        return new int[]{keyIndex, valIdx};
    }

    // ─── Contact extraction ───────────────────────────────────────────────────

    private static String extractContactValue(String[] lines, int i, String line) {
        String val = valueAfterColon(line);
        if (val.isEmpty()) val = valueAfterKeyword(line, "CONTACT");
        if (val.isEmpty()) val = nextValueSkippingNoise(lines, i);
        if (!val.isEmpty() && !looksLikePhone(val)) {
            String better = findPhoneInNextLines(lines, i);
            if (!better.isEmpty()) val = better;
        }
        return val;
    }

    private static String findPhoneInNextLines(String[] lines, int fromIndex) {
        for (int k = fromIndex + 1; k < Math.min(fromIndex + 6, lines.length); k++) {
            String next = lines[k].trim().replaceAll("^[^A-Za-z0-9]+", "");
            if (next.isEmpty()) continue;
            String upper = next.toUpperCase();
            if (isRegistrarLine(upper)) continue;
            if (isAddressKey(upper) || isContactKey(upper) ||
                    isNameKey(upper) || upper.contains("EMERGENCY")) break;
            if (looksLikePhone(next)) return next;
        }
        return "";
    }

    private static boolean looksLikePhone(String val) {
        String digits = val.replaceAll("[^0-9]", "");
        if (digits.length() == 11 && digits.startsWith("09")) return true;
        if (digits.length() == 12 && digits.startsWith("639")) return true;
        if (digits.length() >= 7 && val.matches("[\\d\\s\\-\\+]+")) return true;
        return false;
    }

    // ─── Key matchers ─────────────────────────────────────────────────────────

    private static boolean isAddressKey(String upper) {
        return upper.startsWith("ADDRESS") || upper.startsWith("ADD:");
    }

    private static boolean isContactKey(String upper) {
        return upper.startsWith("CONTACT NO") ||
                upper.startsWith("CONTACT NUMBER") ||
                upper.startsWith("CONTACT:");
    }

    private static boolean isNameKey(String upper) {
        return (upper.startsWith("NAME") || upper.startsWith("NAME:"))
                && !upper.startsWith("-NAME");
    }

    private static boolean isNameCaption(String upper) {
        return upper.equals("-NAME-") || upper.equals("NAME")
                || upper.equals("-NAME") || upper.equals("NAME-");
    }

    private static boolean isRegistrarLine(String upper) {
        return upper.contains("NON-TRANSFER") ||
                upper.contains("NONTRANSFER") ||
                upper.contains("THIS CARD") ||
                upper.contains("IF FOUND") ||
                upper.contains("PLEASE REPORT") ||
                upper.contains("ONLY FOR THE") ||
                upper.contains("SHALL BE VALID") ||
                upper.contains("INDICATED BELOW") ||
                upper.contains("REGISTRAR III") ||
                upper.contains("REGISTRAR II") ||
                upper.contains("REGISTRAR I");
    }

    // ─── Name shape helpers ───────────────────────────────────────────────────

    private static boolean isPrintedName(String upper) {
        String[] words = upper.trim().split("\\s+");
        return words.length >= 2 && words.length <= 5
                && upper.matches("[A-Z .\\-]+");
    }

    private static boolean isKnownHeader(String upper) {
        return upper.contains("COLLEGE") || upper.contains("BACHELOR") ||
                upper.contains("BS IN") || upper.contains("STATE") ||
                upper.contains("NORTE") || upper.contains("CAMARINES") ||
                upper.contains("UNIVERSITY") || upper.contains("INSTITUTE") ||
                upper.contains("SCHOOL") || upper.contains("STUDENT") ||
                upper.contains("REGISTRAR") || upper.contains("SEMESTER") ||
                upper.contains("DEPARTMENT") || upper.contains("MPA") ||
                upper.contains("TRANSFERR") || upper.contains("EMERGENCY");
    }

    // ─── Value extraction helpers ──────────────────────────────────────────────

    private static String valueAfterColon(String line) {
        int idx = line.indexOf(":");
        if (idx >= 0 && idx < line.length() - 1)
            return line.substring(idx + 1).trim();
        return "";
    }

    private static String valueAfterKeyword(String line, String keyword) {
        String upper = line.toUpperCase();
        int idx = upper.indexOf(keyword);
        if (idx < 0) return "";
        String rest = line.substring(idx + keyword.length())
                .replaceAll("^[^A-Za-z0-9]+", "").trim();
        rest = rest.replaceAll(
                "(?i)^(NO\\.?|NUMBER|ADDR(ESS)?)\\s*[:\\-]?\\s*", "").trim();
        return rest;
    }

    private static String valueAfterDash(String line) {
        String[] parts = line.split("-\\s*");
        if (parts.length >= 2) {
            String last = parts[parts.length - 1].trim();
            String secondLast = parts[parts.length - 2].trim();
            if (secondLast.matches("\\d{2,4}") && last.matches("\\d{3,6}"))
                return secondLast + "-" + last;
        }
        return "";
    }

    private static String firstToken(String val) {
        if (val == null || val.isEmpty()) return "";
        return val.split("\\s")[0].trim();
    }

    private static int findNextValueLineIndex(String[] lines, int fromIndex) {
        for (int k = fromIndex + 1; k < lines.length; k++) {
            String next = lines[k].trim().replaceAll("^[^A-Za-z0-9]+", "");
            if (next.isEmpty()) continue;
            String upper = next.toUpperCase();
            if (isRegistrarLine(upper)) continue;
            if (isAddressKey(upper) || isContactKey(upper) || isNameKey(upper)) break;
            if (upper.contains("EMERGENCY") &&
                    !isAddressKey(upper) && !isContactKey(upper) && !isNameKey(upper)) break;
            return k;
        }
        return -1;
    }

    private static String nextValueSkippingNoise(String[] lines, int fromIndex) {
        int idx = findNextValueLineIndex(lines, fromIndex);
        if (idx < 0) return "";
        return lines[idx].trim().replaceAll("^[^A-Za-z0-9]+", "");
    }
}