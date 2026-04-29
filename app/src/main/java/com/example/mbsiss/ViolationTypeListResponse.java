package com.example.mbsiss;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ViolationTypeListResponse {
    @SerializedName("success") public boolean success;
    @SerializedName("data")    public List<ViolationTypeData> data;
}