package com.example.mbsiss;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * LoginActivity — redesigned edition
 * ─────────────────────────────────────
 * CHANGES from previous version:
 *   • Wired up btnTogglePassword (👁 eye icon) to show/hide password.
 *   • Error message now uses bg_error_box drawable (red-tinted box).
 *   • Everything else (session check, API call, navigation) unchanged.
 */
public class LoginActivity extends AppCompatActivity {

    private EditText    etUsername, etPassword;
    private TextView    btnTogglePassword;
    private Button      btnLogin;
    private ProgressBar progressBar;
    private TextView    tvError;

    private SessionManager sessionManager;
    private boolean         isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);

        // Already logged in → skip straight to MainActivity
        if (sessionManager.isLoggedIn()) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_login);

        etUsername        = findViewById(R.id.etUsername);
        etPassword        = findViewById(R.id.etPassword);
        btnTogglePassword = findViewById(R.id.btnTogglePassword);
        btnLogin          = findViewById(R.id.btnLogin);
        progressBar       = findViewById(R.id.progressBarLogin);
        tvError           = findViewById(R.id.tvLoginError);

        btnLogin.setOnClickListener(v -> attemptLogin());
        btnTogglePassword.setOnClickListener(v -> togglePasswordVisibility());
    }

    // ─── Show / hide password ──────────────────────────────────────────────
    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;

        if (isPasswordVisible) {
            // Show plain text — move cursor to end
            etPassword.setTransformationMethod(
                    HideReturnsTransformationMethod.getInstance());
            btnTogglePassword.setText("🙈");
        } else {
            // Hide with dots
            etPassword.setTransformationMethod(
                    PasswordTransformationMethod.getInstance());
            btnTogglePassword.setText("👁");
        }

        // Keep cursor at the end after toggling
        etPassword.setSelection(etPassword.getText().length());
    }

    // ─── Login attempt ─────────────────────────────────────────────────────
    private void attemptLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        tvError.setVisibility(View.GONE);

        if (username.isEmpty()) {
            etUsername.setError("Username is required");
            etUsername.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }

        setLoading(true);

        ApiService api = RetrofitClient.getInstance().create(ApiService.class);
        api.login(new LoginRequest(username, password))
                .enqueue(new Callback<LoginResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<LoginResponse> call,
                                           @NonNull Response<LoginResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().success) {

                            LoginResponse body = response.body();
                            sessionManager.createLoginSession(
                                    body.username,
                                    body.fullName,
                                    body.role,
                                    body.staffId);
                            goToMain();

                        } else {
                            String msg = (response.body() != null
                                    && response.body().message != null)
                                    ? response.body().message
                                    : "Invalid username or password.";
                            showError(msg);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<LoginResponse> call,
                                          @NonNull Throwable t) {
                        setLoading(false);
                        showError("Network error: " + t.getMessage());
                    }
                });
    }

    // ─── Helpers ───────────────────────────────────────────────────────────
    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? "Signing in…" : "Sign In");
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }
}