package com.example.mbsiss;

import com.google.gson.annotations.SerializedName;

// ─── Response received from login.php ─────────────────────────────────────────

public class LoginResponse {
    @SerializedName("success")   public boolean success;
    @SerializedName("message")   public String  message;
    @SerializedName("username")  public String  username;
    @SerializedName("full_name") public String  fullName;
    @SerializedName("role")      public String  role;
    // We use the DB row id formatted as "ID-XXXXX" for display
    @SerializedName("staff_id")  public String  staffId;
}