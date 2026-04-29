package com.example.mbsiss;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ViolationLogAdapter
        extends RecyclerView.Adapter<ViolationLogAdapter.ViewHolder> {

    private final List<ViolationLogData> list;

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
        holder.tvName.setText(item.studentName != null ? item.studentName : "Unknown");
        holder.tvId.setText("ID: " + (item.studentId != null ? item.studentId : "—"));
        holder.tvViolation.setText(item.violationName != null ? item.violationName : "—");
        holder.tvDate.setText(item.recordedAt != null ? item.recordedAt : "—");
        holder.tvSeverity.setText(item.severity != null ? item.severity : "—");

        // Color severity badge
        if (item.severity != null) {
            switch (item.severity) {
                case "Grave":
                    holder.tvSeverity.setBackgroundColor(0xFFBA1A1A); break;
                case "Major":
                    holder.tvSeverity.setBackgroundColor(0xFFE65100); break;
                default:
                    holder.tvSeverity.setBackgroundColor(0xFFF9A825); break;
            }
        }
    }

    @Override
    public int getItemCount() { return list.size(); }

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