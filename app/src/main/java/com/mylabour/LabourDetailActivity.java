package com.mylabour;

import android.os.Bundle;
import android.widget.GridView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class LabourDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_labour_detail);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        Labour labour = (Labour) getIntent().getSerializableExtra("labour");

        if (labour != null) {
            TextView tvName = findViewById(R.id.tv_detail_name);
            TextView tvNumber = findViewById(R.id.tv_detail_number);
            TextView tvEmail = findViewById(R.id.tv_detail_email);
            TextView tvAddress = findViewById(R.id.tv_detail_address);
            TextView tvFullDay = findViewById(R.id.tv_full_day_count);
            TextView tvHalfDay = findViewById(R.id.tv_half_day_count);
            TextView tvAbsent = findViewById(R.id.tv_absent_count);

            tvName.setText(labour.name);
            tvNumber.setText(labour.number);
            tvEmail.setText(labour.email != null && !labour.email.isEmpty() ? labour.email : "N/A");
            tvAddress.setText(labour.address != null && !labour.address.isEmpty() ? labour.address : "N/A");

            // Dummy data for summary
            tvFullDay.setText("20");
            tvHalfDay.setText("2");
            tvAbsent.setText("1");

            setupCustomCalendar();
        }
    }

    private void setupCustomCalendar() {
        TextView tvMonthYear = findViewById(R.id.tv_month_year);
        GridView calendarGrid = findViewById(R.id.calendar_grid);
        TextView tvSelectedDate = findViewById(R.id.tv_selected_date);
        TextView tvDayStatus = findViewById(R.id.tv_day_status);

        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        tvMonthYear.setText(sdf.format(calendar.getTime()));

        List<CalendarAdapter.CalendarDay> days = new ArrayList<>();
        
        // Calculate days for current month
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1;
        int daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);

        // Add empty spaces for previous month's tail
        for (int i = 0; i < firstDayOfWeek; i++) {
            days.add(new CalendarAdapter.CalendarDay(0, ""));
        }

        // Add days with dummy status
        for (int i = 1; i <= daysInMonth; i++) {
            String status = "Default";
            if (i % 7 == 0) status = "Absent";
            else if (i % 5 == 0) status = "Half Day";
            else status = "Full Day";
            
            days.add(new CalendarAdapter.CalendarDay(i, status));
        }

        CalendarAdapter adapter = new CalendarAdapter(this, days);
        calendarGrid.setAdapter(adapter);

        calendarGrid.setOnItemClickListener((parent, view, position, id) -> {
            CalendarAdapter.CalendarDay day = days.get(position);
            if (day.dayNumber != 0) {
                tvSelectedDate.setText(day.dayNumber + " " + tvMonthYear.getText());
                tvDayStatus.setText(day.status);
                
                // Update status color
                switch (day.status) {
                    case "Full Day": tvDayStatus.setTextColor(android.graphics.Color.parseColor("#2E7D32")); break;
                    case "Half Day": tvDayStatus.setTextColor(android.graphics.Color.parseColor("#EF6C00")); break;
                    case "Absent": tvDayStatus.setTextColor(android.graphics.Color.parseColor("#C62828")); break;
                }
            }
        });
    }
}
