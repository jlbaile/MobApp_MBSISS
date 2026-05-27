package com.example.mbsiss;

import com.google.gson.annotations.SerializedName;

public class ViolationRequest {
    @SerializedName("student_id")        public String studentId;
    @SerializedName("violation_type_id") public int violationTypeId;
    @SerializedName("notes")             public String notes;

    public ViolationRequest(String studentId, int violationTypeId, String notes) {
        this.studentId       = studentId;
        this.violationTypeId = violationTypeId;
        this.notes           = notes;
    }
}