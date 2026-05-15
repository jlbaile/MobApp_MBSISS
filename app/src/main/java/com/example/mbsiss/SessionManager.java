package com.example.mbsiss;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SessionManager
 * ──────────────
 * Wraps SharedPreferences so every fragment / activity reads
 * and writes login state from one place.
 *
 * Usage
 * ─────
 *   SessionManager session = new SessionManager(context);
 *
 *   // On successful login:
 *   session.createLoginSession(username, fullName, role, staffId);
 *
 *   // Check before showing main UI:
 *   if (!session.isLoggedIn()) { // redirect to LoginActivity }
 *
 *   // On logout:
 *   session.logout();
 */
public class SessionManager {

    // SharedPreferences file name
    private static final String PREF_NAME   = "MBSISSSession";
    private static final int    PREF_MODE   = Context.MODE_PRIVATE;

    // Keys
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    public  static final String KEY_USERNAME      = "username";
    public  static final String KEY_FULL_NAME     = "fullName";
    public  static final String KEY_ROLE          = "role";
    public  static final String KEY_STAFF_ID      = "staffId";

    private final SharedPreferences pref;
    private final SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        pref   = context.getSharedPreferences(PREF_NAME, PREF_MODE);
        editor = pref.edit();
    }

    /** Persist a successful login. Call this right after the server confirms auth. */
    public void createLoginSession(String username,
                                   String fullName,
                                   String role,
                                   String staffId) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_USERNAME,  username);
        editor.putString(KEY_FULL_NAME, fullName);
        editor.putString(KEY_ROLE,      role);
        editor.putString(KEY_STAFF_ID,  staffId);
        editor.apply();
    }

    /** Returns true if a session exists (user is logged in). */
    public boolean isLoggedIn() {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    /** Returns the stored username, or empty string if none. */
    public String getUsername() {
        return pref.getString(KEY_USERNAME, "");
    }

    /** Returns the stored full name, or empty string if none. */
    public String getFullName() {
        return pref.getString(KEY_FULL_NAME, "");
    }

    /** Returns the stored role ("Admin" or "Staff"), or empty string. */
    public String getRole() {
        return pref.getString(KEY_ROLE, "");
    }

    /** Returns the stored staff ID, or empty string. */
    public String getStaffId() {
        return pref.getString(KEY_STAFF_ID, "");
    }

    /**
     * Clears all session data and marks the user as logged out.
     * After calling this, isLoggedIn() returns false.
     */
    public void logout() {
        editor.clear();
        editor.apply();
    }
}