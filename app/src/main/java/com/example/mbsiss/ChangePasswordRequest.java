package com.example.mbsiss;

import com.google.gson.annotations.SerializedName;

// ─── Request body sent to change_password.php ─────────────────────────────

public class ChangePasswordRequest {
    @SerializedName("username")         public String username;
    @SerializedName("current_password") public String currentPassword;
    @SerializedName("new_password")     public String newPassword;

    public ChangePasswordRequest(String username,
                                 String currentPassword,
                                 String newPassword) {
        this.username        = username;
        this.currentPassword = currentPassword;
        this.newPassword     = newPassword;
    }
}