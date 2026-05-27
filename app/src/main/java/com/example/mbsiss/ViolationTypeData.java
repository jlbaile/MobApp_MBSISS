package com.example.mbsiss;

import com.google.gson.annotations.SerializedName;

public class ViolationTypeData {
    // ⚠️ FIX: The server returns id as a JSON string ("4"), not a number.
    // Gson cannot silently coerce "4" → int, so this must be String.
    // ViolationRequest still sends violation_type_id as int (that's correct —
    // we call Integer.parseInt() when building the request).
    @SerializedName("id")       public String id;
    @SerializedName("category") public String category;
    @SerializedName("name")     public String name;
    @SerializedName("severity") public String severity;
}