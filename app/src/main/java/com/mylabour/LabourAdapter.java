package com.mylabour;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class LabourAdapter extends RecyclerView.Adapter<LabourAdapter.LabourViewHolder> {

    private List<Labour> labourList;
    private List<Labour> labourListFull;
    private OnLabourClickListener listener;

    public interface OnLabourClickListener {
        void onLabourClick(Labour labour);
    }

    public LabourAdapter(List<Labour> labourList, OnLabourClickListener listener) {
        this.labourList = labourList;
        this.labourListFull = new ArrayList<>(labourList);
        this.listener = listener;
    }

    public void setLabourList(List<Labour> labourList) {
        this.labourList = labourList;
        this.labourListFull = new ArrayList<>(labourList);
        notifyDataSetChanged();
    }

    public void filter(String text) {
        List<Labour> filteredList = new ArrayList<>();
        for (Labour item : labourListFull) {
            if (item.name.toLowerCase().contains(text.toLowerCase()) || 
                (item.number != null && item.number.contains(text))) {
                filteredList.add(item);
            }
        }
        this.labourList = filteredList;
        notifyDataSetChanged();
    }



    @NonNull
    @Override
    public LabourViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_labour, parent, false);
        return new LabourViewHolder(view, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull LabourViewHolder holder, int position) {
        Labour labour = labourList.get(position);
        holder.bind(labour);
    }

    @Override
    public int getItemCount() {
        return labourList.size();
    }

    public static class LabourViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvNumber;
        ImageView ivAvatar;
        OnLabourClickListener listener;

        public LabourViewHolder(@NonNull View itemView, OnLabourClickListener listener) {
            super(itemView);
            this.listener = listener;
            tvName = itemView.findViewById(R.id.tv_labour_name);
            tvNumber = itemView.findViewById(R.id.tv_labour_number);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
        }

        public void bind(Labour labour) {
            tvName.setText(labour.name);
            tvNumber.setText(labour.number);
            ivAvatar.setImageResource(R.drawable.ic_person);
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onLabourClick(labour);
                }
            });
        }
    }
}
