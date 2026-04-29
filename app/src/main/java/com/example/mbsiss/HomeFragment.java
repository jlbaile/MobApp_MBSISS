package com.example.mbsiss;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";

    private RecyclerView recyclerView;
    private ViolationLogAdapter adapter;
    private List<ViolationLogData> violationList = new ArrayList<>();
    private ChipGroup chipGroup;
    private TextView tvTotalViolations, tvTotalStudents;

    public HomeFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        recyclerView      = view.findViewById(R.id.recyclerViolations);
        chipGroup         = view.findViewById(R.id.chipGroup);
        tvTotalViolations = view.findViewById(R.id.tvTotalViolations);
        tvTotalStudents   = view.findViewById(R.id.tvTotalStudents);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ViolationLogAdapter(violationList);
        recyclerView.setAdapter(adapter);

        setupChips();
        loadViolations("All");

        return view;
    }

    private void setupChips() {
        String[] categories = {
                "All", "ID & Uniform", "Safety & Security",
                "Substance & Behavioral", "Visitor & Residence"
        };
        for (String cat : categories) {
            Chip chip = new Chip(requireContext());
            chip.setText(cat);
            chip.setCheckable(true);
            chip.setChecked(cat.equals("All"));
            chip.setOnClickListener(v -> loadViolations(cat));
            chipGroup.addView(chip);
        }
    }

    private void loadViolations(String category) {
        ApiService api = RetrofitClient.getInstance().create(ApiService.class);
        api.getViolations(category.equals("All") ? "" : category)
                .enqueue(new Callback<ViolationListResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ViolationListResponse> call,
                                           @NonNull Response<ViolationListResponse> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().success) {
                            violationList.clear();
                            violationList.addAll(response.body().data);
                            adapter.notifyDataSetChanged();
                            tvTotalViolations.setText(
                                    String.valueOf(violationList.size())
                            );
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ViolationListResponse> call,
                                          @NonNull Throwable t) {
                        Log.e(TAG, "Failed to load violations: " + t.getMessage());
                    }
                });
    }
}