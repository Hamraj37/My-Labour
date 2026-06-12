package com.mylabour;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class LabourAdapter extends RecyclerView.Adapter<LabourAdapter.LabourViewHolder> {

    private List<Labour> labourList;

    public LabourAdapter(List<Labour> labourList) {
        this.labourList = labourList;
    }

    @NonNull
    @Override
    public LabourViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_labour, parent, false);
        return new LabourViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LabourViewHolder holder, int position) {
        Labour labour = labourList.get(position);
        holder.tvName.setText(labour.name);
        holder.tvNumber.setText(labour.number);
    }

    @Override
    public int getItemCount() {
        return labourList.size();
    }

    public static class LabourViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvNumber;

        public LabourViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_labour_name);
            tvNumber = itemView.findViewById(R.id.tv_labour_number);
        }
    }
}
