package com.example.mbsiss;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * SettingFragment — Change Password edition
 * ───────────────────────────────────────────
 * CHANGE: tvChangePassword now opens a dialog with:
 *   • Current Password  (show/hide toggle)
 *   • New Password      (show/hide toggle)
 *   • Confirm Password  (show/hide toggle)
 * Validates locally then POSTs to change_password.php.
 */
public class SettingFragment extends Fragment {

    private SessionManager sessionManager;

    public SettingFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_setting, container, false);

        sessionManager = new SessionManager(requireContext());

        // ── Bind views ────────────────────────────────────────────────────
        TextView tvStaffId        = view.findViewById(R.id.tvStaffId);
        TextView tvUsername       = view.findViewById(R.id.tvUsername);
        TextView tvFullName       = view.findViewById(R.id.tvFullName);
        TextView tvRole           = view.findViewById(R.id.tvRole);
        TextView tvChangePassword = view.findViewById(R.id.tvChangePassword);
        Button   btnLogout        = view.findViewById(R.id.btnLogout);

        // ── Populate from session ─────────────────────────────────────────
        tvStaffId.setText(sessionManager.getStaffId().isEmpty()
                ? "ID-00000" : sessionManager.getStaffId());
        tvUsername.setText(sessionManager.getUsername().isEmpty()
                ? "—" : sessionManager.getUsername());
        tvFullName.setText(sessionManager.getFullName().isEmpty()
                ? "—" : sessionManager.getFullName());
        tvRole.setText(sessionManager.getRole().isEmpty()
                ? "—" : sessionManager.getRole());

        // ── Change password ───────────────────────────────────────────────
        tvChangePassword.setOnClickListener(v -> showChangePasswordDialog());

        // ── Log Out ───────────────────────────────────────────────────────
        btnLogout.setOnClickListener(v -> showLogoutConfirmation());

        return view;
    }

    // ─── Change Password Dialog ────────────────────────────────────────────
    private void showChangePasswordDialog() {
        // Build dialog layout programmatically so we control every detail
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(56, 24, 56, 8);

        // ── Current Password row ──────────────────────────────────────────
        TextView labelCurrent = makeLabel("Current Password");
        layout.addView(labelCurrent);
        LinearLayout rowCurrent = makePasswordRow();
        EditText etCurrent      = (EditText) rowCurrent.getChildAt(0);
        TextView toggleCurrent  = (TextView) rowCurrent.getChildAt(1);
        layout.addView(rowCurrent);

        // ── New Password row ──────────────────────────────────────────────
        TextView labelNew = makeLabel("New Password");
        labelNew.setPadding(0, 20, 0, 0);
        layout.addView(labelNew);
        LinearLayout rowNew = makePasswordRow();
        EditText etNew      = (EditText) rowNew.getChildAt(0);
        TextView toggleNew  = (TextView) rowNew.getChildAt(1);
        layout.addView(rowNew);

        // ── Confirm Password row ──────────────────────────────────────────
        TextView labelConfirm = makeLabel("Confirm New Password");
        labelConfirm.setPadding(0, 20, 0, 0);
        layout.addView(labelConfirm);
        LinearLayout rowConfirm = makePasswordRow();
        EditText etConfirm      = (EditText) rowConfirm.getChildAt(0);
        TextView toggleConfirm  = (TextView) rowConfirm.getChildAt(1);
        layout.addView(rowConfirm);

        // ── Wire show/hide toggles ────────────────────────────────────────
        wireToggle(toggleCurrent, etCurrent);
        wireToggle(toggleNew,     etNew);
        wireToggle(toggleConfirm, etConfirm);

        // ── Build and show dialog ─────────────────────────────────────────
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Change Password")
                .setView(layout)
                .setPositiveButton("Update", null) // set manually below
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();

        // Override positive button so dialog doesn't auto-dismiss on error
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String current = etCurrent.getText().toString().trim();
            String newPass  = etNew.getText().toString().trim();
            String confirm  = etConfirm.getText().toString().trim();

            // ── Local validation ──────────────────────────────────────────
            if (current.isEmpty()) {
                etCurrent.setError("Enter your current password");
                etCurrent.requestFocus();
                return;
            }
            if (newPass.isEmpty()) {
                etNew.setError("Enter a new password");
                etNew.requestFocus();
                return;
            }
            if (newPass.length() < 6) {
                etNew.setError("Must be at least 6 characters");
                etNew.requestFocus();
                return;
            }
            if (!newPass.equals(confirm)) {
                etConfirm.setError("Passwords do not match");
                etConfirm.requestFocus();
                return;
            }
            if (newPass.equals(current)) {
                etNew.setError("New password must differ from current");
                etNew.requestFocus();
                return;
            }

            // ── Disable button while requesting ───────────────────────────
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Updating…");

            // ── Call API ──────────────────────────────────────────────────
            submitPasswordChange(
                    sessionManager.getUsername(),
                    current,
                    newPass,
                    dialog);
        });
    }

    // ─── API call ──────────────────────────────────────────────────────────
    private void submitPasswordChange(String username,
                                      String currentPass,
                                      String newPass,
                                      AlertDialog dialog) {

        ApiService api = RetrofitClient.getInstance().create(ApiService.class);
        api.changePassword(new ChangePasswordRequest(username, currentPass, newPass))
                .enqueue(new Callback<ApiResponse>() {

                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call,
                                           @NonNull Response<ApiResponse> response) {

                        if (!isAdded()) return; // fragment detached guard

                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().success) {

                            dialog.dismiss();
                            Toast.makeText(requireContext(),
                                    "✅ Password updated successfully!",
                                    Toast.LENGTH_LONG).show();

                        } else {
                            // Re-enable button and show server error
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                    .setEnabled(true);
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                    .setText("Update");

                            String msg = (response.body() != null
                                    && response.body().message != null)
                                    ? response.body().message
                                    : "Failed to update password.";
                            Toast.makeText(requireContext(), msg,
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call,
                                          @NonNull Throwable t) {

                        if (!isAdded()) return;

                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setEnabled(true);
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setText("Update");

                        Toast.makeText(requireContext(),
                                "Network error: " + t.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ─── Logout dialog ─────────────────────────────────────────────────────
    private void showLogoutConfirmation() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Log Out", (d, w) -> performLogout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performLogout() {
        sessionManager.logout();
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    // ─── Dialog UI helpers ─────────────────────────────────────────────────

    /** Small grey label above each field. */
    private TextView makeLabel(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(12f);
        tv.setTextColor(0xFF6B7280);
        tv.setPadding(0, 0, 0, 4);
        return tv;
    }

    /**
     * Returns a horizontal LinearLayout containing:
     *   [0] EditText  — password input (dots by default)
     *   [1] TextView  — 👁 toggle button
     */
    private LinearLayout makePasswordRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundResource(android.R.drawable.edit_text);

        EditText et = new EditText(requireContext());
        et.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        et.setTransformationMethod(PasswordTransformationMethod.getInstance());
        et.setHint("••••••••");
        et.setTextSize(14f);
        et.setBackground(null);
        et.setPadding(8, 8, 0, 8);
        et.setSingleLine(true);

        TextView toggle = new TextView(requireContext());
        toggle.setLayoutParams(new LinearLayout.LayoutParams(
                (int)(40 * getResources().getDisplayMetrics().density),
                LinearLayout.LayoutParams.MATCH_PARENT));
        toggle.setText("👁");
        toggle.setTextSize(15f);
        toggle.setGravity(android.view.Gravity.CENTER);
        toggle.setClickable(true);
        toggle.setFocusable(true);

        row.addView(et);
        row.addView(toggle);
        return row;
    }

    /** Toggles visibility on the EditText and flips the eye icon. */
    private void wireToggle(TextView toggle, EditText field) {
        final boolean[] visible = {false};
        toggle.setOnClickListener(v -> {
            visible[0] = !visible[0];
            if (visible[0]) {
                field.setTransformationMethod(
                        HideReturnsTransformationMethod.getInstance());
                toggle.setText("🙈");
            } else {
                field.setTransformationMethod(
                        PasswordTransformationMethod.getInstance());
                toggle.setText("👁");
            }
            field.setSelection(field.getText().length());
        });
    }
}