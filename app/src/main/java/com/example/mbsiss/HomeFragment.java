package com.example.mbsiss;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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

    // masterList holds ALL violations from the server (never filtered directly).
    // filteredList is what the adapter sees — filtered by category AND search text.
    private List<ViolationLogData> masterList   = new ArrayList<>();
    private List<ViolationLogData> filteredList = new ArrayList<>();

    private ChipGroup chipGroup;
    private EditText  etSearch;
    private TextView  tvTotalViolations, tvTotalStudents;

    // Track the currently selected category so search can re-apply it
    private String currentCategory = "All";

    public HomeFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        recyclerView      = view.findViewById(R.id.recyclerViolations);
        chipGroup         = view.findViewById(R.id.chipGroup);
        etSearch          = view.findViewById(R.id.etHomeSearch);
        tvTotalViolations = view.findViewById(R.id.tvTotalViolations);
        tvTotalStudents   = view.findViewById(R.id.tvTotalStudents);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        // Adapter works on filteredList — only this list is mutated for display
        adapter = new ViolationLogAdapter(filteredList);
        recyclerView.setAdapter(adapter);

        setupChips();
        setupSearch();
        loadViolations("All");

        return view;
    }

    // ─── Chip filter setup ────────────────────────────────────────────────────

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
            chip.setOnClickListener(v -> {
                currentCategory = cat;
                // Re-apply both category filter and current search text
                applyFilters(etSearch.getText().toString());
            });
            chipGroup.addView(chip);
        }
    }

    // ─── Search wiring ────────────────────────────────────────────────────────

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Re-filter every time the user types or deletes a character
                applyFilters(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    // ─── Combined filter: category + search text ──────────────────────────────

    /**
     * Filters masterList by the current chip category AND the search query,
     * then updates filteredList and notifies the adapter.
     *
     * Search matches against:
     *   - Student name
     *   - Student ID number
     *   - Violation description / type
     *
     * Both filters are case-insensitive.
     */
    private void applyFilters(String query) {
        String lowerQuery = query.trim().toLowerCase();

        filteredList.clear();

        for (ViolationLogData item : masterList) {
            // ── Category filter ────────────────────────────────────────────────
            boolean categoryMatch = currentCategory.equals("All")
                    || (item.category != null
                    && item.category.equalsIgnoreCase(currentCategory));

            if (!categoryMatch) continue;

            // ── Search filter ──────────────────────────────────────────────────
            // If search box is empty, all category-matched items pass through
            if (lowerQuery.isEmpty()) {
                filteredList.add(item);
                continue;
            }

            boolean searchMatch =
                    (item.studentName   != null && item.studentName.toLowerCase().contains(lowerQuery))   ||
                            (item.studentId     != null && item.studentId.toLowerCase().contains(lowerQuery))     ||
                            (item.violationName     != null && item.violationName.toLowerCase().contains(lowerQuery))     ||
                            (item.category      != null && item.category.toLowerCase().contains(lowerQuery));

            if (searchMatch) filteredList.add(item);
        }

        adapter.notifyDataSetChanged();

        // Update the counter to reflect currently visible items
        tvTotalViolations.setText(String.valueOf(filteredList.size()));
    }

    // ─── API load ─────────────────────────────────────────────────────────────

    /**
     * Loads violations from the server into masterList, then re-applies
     * the current filters so the displayed list stays consistent.
     *
     * NOTE: We pass an empty category string to always fetch ALL records
     * from the server. Category filtering is done locally in applyFilters()
     * so the guard can switch chips and type searches without a new API call.
     */
    private void loadViolations(String category) {
        ApiService api = RetrofitClient.getInstance().create(ApiService.class);
        api.getViolations("")   // always fetch all — filter locally
                .enqueue(new Callback<ViolationListResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ViolationListResponse> call,
                                           @NonNull Response<ViolationListResponse> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().success) {
                            masterList.clear();
                            masterList.addAll(response.body().data);

                            // Count unique students from the full master list
                            long uniqueStudents = masterList.stream()
                                    .map(v -> v.studentId)
                                    .filter(id -> id != null && !id.isEmpty())
                                    .distinct()
                                    .count();
                            tvTotalStudents.setText(String.valueOf(uniqueStudents));

                            // Apply current filters to populate filteredList
                            applyFilters(etSearch.getText().toString());
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