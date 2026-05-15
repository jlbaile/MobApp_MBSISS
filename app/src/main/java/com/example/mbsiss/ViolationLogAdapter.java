package com.example.mbsiss;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * ViolationLogAdapter — PHT fix v2
 * ──────────────────────────────────
 * ROOT CAUSE of time not changing:
 *   XAMPP/MariaDB on Windows stores timestamps using the SERVER's local
 *   clock — which is already Philippine time (UTC+8). The DB value
 *   "2026-05-02 09:58:10" is already PHT, NOT UTC.
 *   The previous adapter was re-adding +8 hours on top, making it worse.
 *
 * FIX:
 *   Parse the raw DB string as Asia/Manila (PHT) and reformat it into
 *   the friendlier Philippine display format:
 *       May 02, 2026  09:58 PM
 *   No timezone shift is applied — we just reformat the presentation.
 *
 * DISPLAY FORMAT:
 *   "May 02, 2026  09:58 PM"   ← human-readable Philippine style
 */
public class ViolationLogAdapter
        extends RecyclerView.Adapter<ViolationLogAdapter.ViewHolder> {

    private final List<ViolationLogData> list;

    // ── Date formatters ────────────────────────────────────────────────────
    // Input  : "yyyy-MM-dd HH:mm:ss"  (as stored by XAMPP — already PHT)
    // Output : "MMM dd, yyyy  hh:mm a" (e.g. "May 02, 2026  09:58 PM")
    private static final SimpleDateFormat DB_FORMAT;
    private static final SimpleDateFormat DISPLAY_FORMAT;

    static {
        // Parse exactly as Manila time (no shift)
        DB_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        DB_FORMAT.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));

        // Reformat in Manila time — so output is also Manila, no shift
        DISPLAY_FORMAT = new SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.US);
        DISPLAY_FORMAT.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
    }

    public ViolationLogAdapter(List<ViolationLogData> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_violation_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ViolationLogData item = list.get(position);

        // ── Student name (uppercase) ───────────────────────────────────────
        holder.tvName.setText(
                item.studentName != null
                        ? item.studentName.toUpperCase(Locale.US)
                        : "UNKNOWN");

        // ── Student ID ─────────────────────────────────────────────────────
        // Some early scan entries stored garbage like "Birthdate:" as student_id.
        // We show them as-is so the guard knows the record has bad data,
        // but prefix cleanly with "ID: ".
        String displayId = (item.studentId != null && !item.studentId.isEmpty())
                ? item.studentId
                : "—";
        holder.tvId.setText("ID: " + displayId);

        // ── Violation type ─────────────────────────────────────────────────
        holder.tvViolation.setText(
                item.violationName != null ? item.violationName : "—");

        // ── Date/time → Philippine friendly format ─────────────────────────
        holder.tvDate.setText(formatPhilippineDate(item.recordedAt));

        // ── Severity badge ─────────────────────────────────────────────────
        String severity = item.severity != null ? item.severity : "Minor";
        holder.tvSeverity.setText(severity);
        holder.tvSeverity.setBackgroundColor(severityColor(severity));
    }

    @Override
    public int getItemCount() { return list.size(); }

    // ─── Date formatter
    // ───────────────────────────────────────────────────────
    /**
     * Reformats the raw DB string into a readable Philippine date string.
     *
     * Input  (from DB) : "2026-05-02 09:58:10"
     * Output (display) : "May 02, 2026  09:58 AM"
     *
     * Both parse and format use Asia/Manila so no hour shift occurs.
     * If parsing fails the raw string is returned unchanged as a fallback.
     */
    private static String formatPhilippineDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "—";
        try {
            Date date = DB_FORMAT.parse(raw.trim());
            if (date == null) return raw;
            return DISPLAY_FORMAT.format(date);
        } catch (ParseException e) {
            // Return raw value so data is never silently lost
            return raw;
        }
    }

    // ─── Severity colours
    // ─────────────────────────────────────────────────────
    private static int severityColor(String severity) {
        switch (severity) {
            case "Grave":  return 0xFFC0392B; // deep red
            case "Major":  return 0xFFE65100; // orange-red
            default:       return 0xFFF39C12; // amber  (Minor)
        }
    }

    // ─── ViewHolder
    // ───────────────────────────────────────────────────────────
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvId, tvViolation, tvDate, tvSeverity;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName      = itemView.findViewById(R.id.tvLogStudentName);
            tvId        = itemView.findViewById(R.id.tvLogStudentId);
            tvViolation = itemView.findViewById(R.id.tvLogViolation);
            tvDate      = itemView.findViewById(R.id.tvLogDate);
            tvSeverity  = itemView.findViewById(R.id.tvLogSeverity);
        }
    }
}