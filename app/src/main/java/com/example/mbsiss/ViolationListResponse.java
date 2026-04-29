package com.example.mbsiss;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ViolationListResponse {
    @SerializedName("success") public boolean success;
    @SerializedName("data")    public List<ViolationLogData> data;
}