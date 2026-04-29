package com.example.mbsiss;

import com.google.gson.annotations.SerializedName;

public class ViolationLogData {
    @SerializedName("id")             public int id;
    @SerializedName("student_id")     public String studentId;
    @SerializedName("student_name")   public String studentName;
    @SerializedName("course")         public String course;
    @SerializedName("violation_name") public String violationName;
    @SerializedName("category")       public String category;
    @SerializedName("severity")       public String severity;
    @SerializedName("notes")          public String notes;
    @SerializedName("recorded_at")    public String recordedAt;
}