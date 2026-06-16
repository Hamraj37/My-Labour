package com.mylabour;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.google.android.material.card.MaterialCardView;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;

public class CalendarAdapter extends BaseAdapter {
    private final Context context;
    private final List<CalendarDay> days;
    private final LayoutInflater inflater;

    public CalendarAdapter(Context context, List<CalendarDay> days) {
        this.context = context;
        this.days = days;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return days.size();
    }

    @Override
    public Object getItem(int position) {
        return days.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_calendar_day, parent, false);
        }

        CalendarDay day = days.get(position);
        TextView tvDay = convertView.findViewById(R.id.tv_day_number);
        MaterialCardView card = convertView.findViewById(R.id.card_day_background);
        View dot = convertView.findViewById(R.id.dot_indicator);

        if (day.dayNumber == 0) {
            tvDay.setText("");
            card.setCardBackgroundColor(Color.TRANSPARENT);
            card.setStrokeWidth(0);
            dot.setVisibility(View.GONE);
        } else {
            tvDay.setText(String.valueOf(day.dayNumber));
            dot.setVisibility(View.GONE);

            if (day.isToday) {
                card.setStrokeColor(Color.parseColor("#1976D2")); // Blue border for today
                card.setStrokeWidth(2 * (int) context.getResources().getDisplayMetrics().density);
            } else {
                card.setStrokeWidth(0);
            }
            
            // Set colors based on status
            String status = day.status;
            if (Objects.equals(status, "Double Full Day") || Objects.equals(status, "2x") || (status != null && status.endsWith("x") && !status.contains(".5"))) {
                card.setCardBackgroundColor(Color.parseColor("#A5D6A7")); // Stronger Green
                tvDay.setTextColor(Color.parseColor("#1B5E20"));
            } else if (Objects.equals(status, "Full Day + Half") || (status != null && status.endsWith("x") && status.contains(".5"))) {
                card.setCardBackgroundColor(Color.parseColor("#E8F5E9")); // Green
                tvDay.setTextColor(Color.parseColor("#2E7D32"));
                dot.setVisibility(View.VISIBLE); // Dot to indicate partial/overtime
            } else if (Objects.equals(status, "Full Day")) {
                card.setCardBackgroundColor(Color.parseColor("#E8F5E9")); // Light Green
                tvDay.setTextColor(Color.parseColor("#2E7D32"));
            } else if (Objects.equals(status, "Half Day")) {
                card.setCardBackgroundColor(Color.parseColor("#FFF3E0")); // Light Orange
                tvDay.setTextColor(Color.parseColor("#EF6C00"));
            } else if (Objects.equals(status, "Absent")) {
                card.setCardBackgroundColor(Color.parseColor("#FFEBEE")); // Light Red
                tvDay.setTextColor(Color.parseColor("#C62828"));
            } else {
                card.setCardBackgroundColor(Color.TRANSPARENT);
                tvDay.setTextColor(Color.BLACK);
            }
        }

        return convertView;
    }

    public static class CalendarDay {
        public int dayNumber;
        public String status;
        public boolean isToday;

        public CalendarDay(int dayNumber, String status) {
            this(dayNumber, status, false);
        }

        public CalendarDay(int dayNumber, String status, boolean isToday) {
            this.dayNumber = dayNumber;
            this.status = status;
            this.isToday = isToday;
        }
    }
}
