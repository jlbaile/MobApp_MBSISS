package com.example.mbsiss;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class StudentListResponse {
    @SerializedName("success") public boolean success;
    @SerializedName("data")    public List<StudentData> data;
}