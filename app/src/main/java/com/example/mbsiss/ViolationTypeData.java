package com.example.mbsiss;

import com.google.gson.annotations.SerializedName;

public class ViolationTypeData {
    @SerializedName("id")       public int id;
    @SerializedName("category") public String category;
    @SerializedName("name")     public String name;
    @SerializedName("severity") public String severity;
}