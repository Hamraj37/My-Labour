package com.mylabour;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class CalendarAdapter extends BaseAdapter {
    private Context context;
    private List<CalendarDay> days;
    private LayoutInflater inflater;

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
            dot.setVisibility(View.GONE);
        } else {
            tvDay.setText(String.valueOf(day.dayNumber));
            dot.setVisibility(View.GONE);
            
            // Set colors based on status
            switch (day.status) {
                case "Double Full Day":
                    card.setCardBackgroundColor(Color.parseColor("#A5D6A7")); // Stronger Green
                    tvDay.setTextColor(Color.parseColor("#1B5E20"));
                    break;
                case "Full Day + Half":
                    card.setCardBackgroundColor(Color.parseColor("#E8F5E9")); // Green
                    tvDay.setTextColor(Color.parseColor("#2E7D32"));
                    dot.setVisibility(View.VISIBLE); // Orange dot
                    break;
                case "Full Day":
                    card.setCardBackgroundColor(Color.parseColor("#E8F5E9")); // Light Green
                    tvDay.setTextColor(Color.parseColor("#2E7D32"));
                    break;
                case "Half Day":
                    card.setCardBackgroundColor(Color.parseColor("#FFF3E0")); // Light Orange
                    tvDay.setTextColor(Color.parseColor("#EF6C00"));
                    break;
                case "Absent":
                    card.setCardBackgroundColor(Color.parseColor("#FFEBEE")); // Light Red
                    tvDay.setTextColor(Color.parseColor("#C62828"));
                    break;
                default:
                    card.setCardBackgroundColor(Color.TRANSPARENT);
                    tvDay.setTextColor(Color.BLACK);
                    break;
            }
        }

        return convertView;
    }

    public static class CalendarDay {
        public int dayNumber;
        public String status;

        public CalendarDay(int dayNumber, String status) {
            this.dayNumber = dayNumber;
            this.status = status;
        }
    }
}
