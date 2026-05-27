package com.example.mbsiss;

import com.google.gson.annotations.SerializedName;

public class LoginResponse {
    @SerializedName("success")   public boolean success;
    @SerializedName("message")   public String  message;
    @SerializedName("username")  public String  username;
    @SerializedName("full_name") public String  fullName;   // ← snake_case in JSON
    @SerializedName("role")      public String  role;
    @SerializedName("staff_id")  public String  staffId;    // ← snake_case in JSON
}