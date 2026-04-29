package com.example.mbsiss;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ViolationFragment extends Fragment {

    private static final String TAG = "ViolationFragment";

    // Search & Student Card
    private EditText etSearch;
    private Button btnScanToFind;
    private LinearLayout cardStudent;
    private TextView tvStudentName, tvStudentId, tvStudentCourse;

    // Violation Selection
    private LinearLayout layoutViolationSelector;
    private Spinner spinnerCategory, spinnerViolation;
    private TextView tvSeverityBadge;
    private EditText etNotes;
    private Button btnSubmitViolation;

    // Log
    private RecyclerView recyclerLog;
    private ViolationLogAdapter logAdapter;
    private List<ViolationLogData> logList = new ArrayList<>();

    // Data
    private List<ViolationTypeData> allViolationTypes = new ArrayList<>();
    private Map<String, List<ViolationTypeData>> categorizedTypes = new LinkedHashMap<>();
    private StudentData selectedStudent = null;
    private ViolationTypeData selectedViolationType = null;

    public ViolationFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_violation, container, false);

        etSearch             = view.findViewById(R.id.etSearch);
        btnScanToFind        = view.findViewById(R.id.btnScanToFind);
        cardStudent          = view.findViewById(R.id.cardStudent);
        tvStudentName        = view.findViewById(R.id.tvStudentName);
        tvStudentId          = view.findViewById(R.id.tvStudentId);
        tvStudentCourse      = view.findViewById(R.id.tvStudentCourse);
        layoutViolationSelector = view.findViewById(R.id.layoutViolationSelector);
        spinnerCategory      = view.findViewById(R.id.spinnerCategory);
        spinnerViolation     = view.findViewById(R.id.spinnerViolation);
        tvSeverityBadge      = view.findViewById(R.id.tvSeverityBadge);
        etNotes              = view.findViewById(R.id.etNotes);
        btnSubmitViolation   = view.findViewById(R.id.btnSubmitViolation);
        recyclerLog          = view.findViewById(R.id.recyclerLog);

        recyclerLog.setLayoutManager(new LinearLayoutManager(requireContext()));
        logAdapter = new ViolationLogAdapter(logList);
        recyclerLog.setAdapter(logAdapter);

        loadViolationTypes();
        loadRecentLogs();
        setupSearch();
        setupScanButton();
        setupSubmitButton();

        return view;
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (s.length() >= 2) searchStudent(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void searchStudent(String query) {
        ApiService api = RetrofitClient.getInstance().create(ApiService.class);
        api.searchStudents(query).enqueue(new Callback<StudentListResponse>() {
            @Override
            public void onResponse(@NonNull Call<StudentListResponse> call,
                                   @NonNull Response<StudentListResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().success
                        && !response.body().data.isEmpty()) {
                    showStudentCard(response.body().data.get(0));
                }
            }
            @Override
            public void onFailure(@NonNull Call<StudentListResponse> call,
                                  @NonNull Throwable t) {
                Log.e(TAG, "Search failed: " + t.getMessage());
            }
        });
    }

    private void setupScanButton() {
        btnScanToFind.setOnClickListener(v -> {
            // Navigate to CameraFragment for scanning
            // After scan, student data is returned here
            Toast.makeText(requireContext(),
                    "Switch to Camera tab to scan ID first.",
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void showStudentCard(StudentData student) {
        selectedStudent = student;
        tvStudentName.setText(student.studentName != null ? student.studentName : "Unknown");
        tvStudentId.setText("ID: " + (student.studentId != null ? student.studentId : "—"));
        tvStudentCourse.setText(
                (student.course != null ? student.course : "") +
                        (student.college != null ? " | " + student.college : "")
        );
        cardStudent.setVisibility(View.VISIBLE);
        layoutViolationSelector.setVisibility(View.VISIBLE);
    }

    private void loadViolationTypes() {
        ApiService api = RetrofitClient.getInstance().create(ApiService.class);
        api.getViolationTypes().enqueue(new Callback<ViolationTypeListResponse>() {
            @Override
            public void onResponse(@NonNull Call<ViolationTypeListResponse> call,
                                   @NonNull Response<ViolationTypeListResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().success) {
                    allViolationTypes = response.body().data;
                    buildCategoryMap();
                    setupCategorySpinner();
                }
            }
            @Override
            public void onFailure(@NonNull Call<ViolationTypeListResponse> call,
                                  @NonNull Throwable t) {
                Log.e(TAG, "Failed to load violation types: " + t.getMessage());
            }
        });
    }

    private void buildCategoryMap() {
        categorizedTypes.clear();
        for (ViolationTypeData vt : allViolationTypes) {
            if (!categorizedTypes.containsKey(vt.category)) {
                categorizedTypes.put(vt.category, new ArrayList<>());
            }
            categorizedTypes.get(vt.category).add(vt);
        }
    }

    private void setupCategorySpinner() {
        List<String> categories = new ArrayList<>(categorizedTypes.keySet());
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                categories
        );
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(catAdapter);

        spinnerCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v,
                                       int position, long id) {
                String selectedCat = categories.get(position);
                updateViolationSpinner(categorizedTypes.get(selectedCat));
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateViolationSpinner(List<ViolationTypeData> types) {
        if (types == null) return;
        List<String> names = new ArrayList<>();
        for (ViolationTypeData vt : types) names.add(vt.name);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                names
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerViolation.setAdapter(adapter);

        spinnerViolation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v,
                                       int position, long id) {
                selectedViolationType = types.get(position);
                updateSeverityBadge(selectedViolationType.severity);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateSeverityBadge(String severity) {
        tvSeverityBadge.setText("Severity: " + severity);
        switch (severity) {
            case "Grave":
                tvSeverityBadge.setBackgroundColor(0xFFBA1A1A);
                break;
            case "Major":
                tvSeverityBadge.setBackgroundColor(0xFFE65100);
                break;
            default:
                tvSeverityBadge.setBackgroundColor(0xFFF9A825);
                break;
        }
    }

    private void setupSubmitButton() {
        btnSubmitViolation.setOnClickListener(v -> {
            if (selectedStudent == null) {
                Toast.makeText(requireContext(),
                        "Please find a student first.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedViolationType == null) {
                Toast.makeText(requireContext(),
                        "Please select a violation.", Toast.LENGTH_SHORT).show();
                return;
            }
            String notes = etNotes.getText().toString().trim();
            ViolationRequest request = new ViolationRequest(
                    selectedStudent.studentId,
                    selectedViolationType.id,
                    notes
            );
            submitViolation(request);
        });
    }

    private void submitViolation(ViolationRequest request) {
        ApiService api = RetrofitClient.getInstance().create(ApiService.class);
        api.saveViolation(request).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse> call,
                                   @NonNull Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().success) {
                    Toast.makeText(requireContext(),
                            "Violation recorded!", Toast.LENGTH_SHORT).show();
                    etNotes.setText("");
                    cardStudent.setVisibility(View.GONE);
                    layoutViolationSelector.setVisibility(View.GONE);
                    selectedStudent = null;
                    selectedViolationType = null;
                    etSearch.setText("");
                    loadRecentLogs();
                } else {
                    Toast.makeText(requireContext(),
                            "Failed to save. Try again.", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse> call,
                                  @NonNull Throwable t) {
                Toast.makeText(requireContext(),
                        "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadRecentLogs() {
        ApiService api = RetrofitClient.getInstance().create(ApiService.class);
        api.getViolations("").enqueue(new Callback<ViolationListResponse>() {
            @Override
            public void onResponse(@NonNull Call<ViolationListResponse> call,
                                   @NonNull Response<ViolationListResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().success) {
                    logList.clear();
                    logList.addAll(response.body().data);
                    logAdapter.notifyDataSetChanged();
                }
            }
            @Override
            public void onFailure(@NonNull Call<ViolationListResponse> call,
                                  @NonNull Throwable t) {
                Log.e(TAG, "Failed to load logs: " + t.getMessage());
            }
        });
    }
}