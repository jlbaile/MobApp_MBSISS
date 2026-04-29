package com.example.mbsiss;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {
    @POST("save_student.php")
    Call<ApiResponse> saveStudent(@Body StudentData student);

    @GET("get_student.php")
    Call<StudentListResponse> searchStudents(@Query("query") String query);

    @POST("save_violation.php")
    Call<ApiResponse> saveViolation(@Body ViolationRequest request);

    @GET("get_violations.php")
    Call<ViolationListResponse> getViolations(@Query("category") String category);

    @GET("get_violation_types.php")
    Call<ViolationTypeListResponse> getViolationTypes();
}