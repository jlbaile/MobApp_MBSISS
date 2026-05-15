package com.example.mbsiss;

import com.google.gson.annotations.SerializedName;

// ─── Request body sent to login.php ───────────────────────────────────────────
// Used by ApiService.login()

public class LoginRequest {
    @SerializedName("username") public String username;
    @SerializedName("password") public String password;

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
}