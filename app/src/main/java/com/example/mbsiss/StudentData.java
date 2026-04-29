package com.example.mbsiss;

import com.google.gson.annotations.SerializedName;

public class StudentData {
    @SerializedName("student_id")               public String studentId;
    @SerializedName("student_name")             public String studentName;
    @SerializedName("course")                   public String course;
    @SerializedName("college")                  public String college;
    @SerializedName("address")                  public String address;
    @SerializedName("contact_number")           public String contactNumber;
    @SerializedName("emergency_contact")        public String emergencyContact;
    @SerializedName("emergency_address")        public String emergencyAddress;
    @SerializedName("emergency_contact_number") public String emergencyContactNumber;

    @Override
    public String toString() {
        return "ID=" + studentId + ", Name=" + studentName +
                ", Course=" + course + ", College=" + college;
    }
}