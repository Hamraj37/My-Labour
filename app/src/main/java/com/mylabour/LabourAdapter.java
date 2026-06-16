package com.mylabour;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class LabourAdapter extends RecyclerView.Adapter<LabourAdapter.LabourViewHolder> {

    private List<Labour> labourList;
    private List<Labour> labourListFull;
    private final OnLabourClickListener listener;
    private final String nodeKey;

    public interface OnLabourClickListener {
        void onLabourClick(Labour labour);
    }

    public LabourAdapter(List<Labour> labourList, String nodeKey, OnLabourClickListener listener) {
        this.labourList = labourList;
        this.labourListFull = new ArrayList<>(labourList);
        this.nodeKey = nodeKey;
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
        holder.bind(labour, nodeKey);
    }

    @Override
    public int getItemCount() {
        return labourList.size();
    }

    public static class LabourViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvNumber;
        com.google.android.material.imageview.ShapeableImageView ivAvatar;
        OnLabourClickListener listener;

        public LabourViewHolder(@NonNull View itemView, OnLabourClickListener listener) {
            super(itemView);
            this.listener = listener;
            tvName = itemView.findViewById(R.id.tv_labour_name);
            tvNumber = itemView.findViewById(R.id.tv_labour_number);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
        }

        public void bind(Labour labour, String nodeKey) {
            tvName.setText(labour.name);
            tvNumber.setText(labour.number);
            
            String photoBase64 = itemView.getContext()
                    .getSharedPreferences("LabourPhotos_" + nodeKey, Context.MODE_PRIVATE)
                    .getString("photo_" + labour.id, null);

            if (photoBase64 != null && !photoBase64.isEmpty()) {
                try {
                    byte[] decodedString = Base64.decode(photoBase64, Base64.DEFAULT);
                    Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                    ivAvatar.setImageBitmap(decodedByte);
                    ivAvatar.setPadding(0, 0, 0, 0);
                    ivAvatar.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    ivAvatar.setImageTintList(null);
                } catch (Exception e) {
                    setDefaultAvatar();
                }
            } else {
                setDefaultAvatar();
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onLabourClick(labour);
                }
            });
        }

        private void setDefaultAvatar() {
            ivAvatar.setImageResource(R.drawable.ic_person);
            int padding = (int) (10 * itemView.getContext().getResources().getDisplayMetrics().density);
            ivAvatar.setPadding(padding, padding, padding, padding);
            ivAvatar.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
            
            // Restore default tint
            Context context = itemView.getContext();
            android.util.TypedValue typedValue = new android.util.TypedValue();
            context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true);
            ivAvatar.setImageTintList(android.content.res.ColorStateList.valueOf(typedValue.data));
        }
    }
}
