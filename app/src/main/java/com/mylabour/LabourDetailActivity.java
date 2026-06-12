package com.mylabour;

import android.os.Bundle;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LabourDetailActivity extends AppCompatActivity {

    private List<CalendarAdapter.CalendarDay> days;
    private CalendarAdapter adapter;
    private TextView tvFullDay, tvHalfDay, tvAbsent, tvTotalAmount, tvWage;
    private DatabaseReference mAttendanceRef, mLabourRef;
    private String labourId;
    private double dailyWage;
    private String yearMonth; // e.g., "2023_10"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_labour_detail);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        Labour labour = (Labour) getIntent().getSerializableExtra("labour");

        if (labour != null) {
            labourId = labour.id;
            dailyWage = labour.dailyAmount;
            TextView tvName = findViewById(R.id.tv_detail_name);
            TextView tvNumber = findViewById(R.id.tv_detail_number);
            TextView tvEmail = findViewById(R.id.tv_detail_email);
            TextView tvAddress = findViewById(R.id.tv_detail_address);
            tvWage = findViewById(R.id.tv_detail_wage);
            tvFullDay = findViewById(R.id.tv_full_day_count);
            tvHalfDay = findViewById(R.id.tv_half_day_count);
            tvAbsent = findViewById(R.id.tv_absent_count);
            tvTotalAmount = findViewById(R.id.tv_total_amount);

            tvName.setText(labour.name);
            tvNumber.setText(labour.number);
            tvEmail.setText(labour.email != null && !labour.email.isEmpty() ? labour.email : "N/A");
            tvAddress.setText(labour.address != null && !labour.address.isEmpty() ? labour.address : "N/A");
            tvWage.setText("₹" + formatAmount(dailyWage));

            setupFirebase();
            setupCustomCalendar();
            fetchAttendanceData();

            findViewById(R.id.layout_edit_wage).setOnClickListener(v -> showEditWageDialog());
        }
    }

    private void setupFirebase() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String userEmail = currentUser.getEmail();
            String nodeKey = (userEmail != null) ? userEmail.replace(".", ",") : currentUser.getUid();
            
            Calendar cal = Calendar.getInstance();
            yearMonth = cal.get(Calendar.YEAR) + "_" + (cal.get(Calendar.MONTH) + 1);
            
            mLabourRef = FirebaseDatabase.getInstance().getReference("labours")
                    .child(nodeKey)
                    .child(labourId);
            
            mAttendanceRef = mLabourRef.child("attendance")
                    .child(yearMonth);
        }
    }

    private void showEditWageDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Daily Wage");
        
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(formatAmount(dailyWage));
        builder.setView(input);

        builder.setPositiveButton("Update", (dialog, which) -> {
            String newWageStr = input.getText().toString().trim();
            if (!newWageStr.isEmpty()) {
                double newWage = Double.parseDouble(newWageStr);
                updateDailyWage(newWage);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void updateDailyWage(double newWage) {
        if (mLabourRef != null) {
            mLabourRef.child("dailyAmount").setValue(newWage)
                    .addOnSuccessListener(aVoid -> {
                        dailyWage = newWage;
                        tvWage.setText("₹" + formatAmount(dailyWage));
                        updateSummary();
                        Toast.makeText(this, "Wage updated", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to update wage", Toast.LENGTH_SHORT).show());
        }
    }

    private void fetchAttendanceData() {
        if (mAttendanceRef == null) return;

        mAttendanceRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Map<String, String> attendanceMap = new HashMap<>();
                for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                    attendanceMap.put(postSnapshot.getKey(), postSnapshot.getValue(String.class));
                }

                // Update days list with data from Firebase
                for (CalendarAdapter.CalendarDay day : days) {
                    if (day.dayNumber != 0) {
                        String status = attendanceMap.get(String.valueOf(day.dayNumber));
                        day.status = (status != null) ? status : "Default";
                    }
                }
                adapter.notifyDataSetChanged();
                updateSummary();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(LabourDetailActivity.this, "Failed to load attendance", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveAttendanceToFirebase(int day, String status) {
        if (mAttendanceRef == null) return;
        
        if (status.equals("Default")) {
            mAttendanceRef.child(String.valueOf(day)).removeValue();
        } else {
            mAttendanceRef.child(String.valueOf(day)).setValue(status);
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

        days = new ArrayList<>();
        
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1;
        int daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int i = 0; i < firstDayOfWeek; i++) {
            days.add(new CalendarAdapter.CalendarDay(0, ""));
        }

        for (int i = 1; i <= daysInMonth; i++) {
            days.add(new CalendarAdapter.CalendarDay(i, "Default"));
        }

        adapter = new CalendarAdapter(this, days);
        calendarGrid.setAdapter(adapter);

        calendarGrid.setOnItemClickListener((parent, view, position, id) -> {
            CalendarAdapter.CalendarDay day = days.get(position);
            if (day.dayNumber != 0) {
                showStatusDialog(day, position);
                
                tvSelectedDate.setText(day.dayNumber + " " + tvMonthYear.getText());
                updateStatusDisplay(day, tvDayStatus);
            }
        });
    }

    private void showStatusDialog(CalendarAdapter.CalendarDay day, int position) {
        String[] options = {"Double Full Day", "Full Day + Half", "Full Day", "Half Day", "Absent", "Reset"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Mark Attendance - Day " + day.dayNumber);
        builder.setItems(options, (dialog, which) -> {
            String newStatus;
            if (which == 5) {
                newStatus = "Default";
            } else {
                newStatus = options[which];
            }
            
            day.status = newStatus;
            saveAttendanceToFirebase(day.dayNumber, newStatus);
            
            adapter.notifyDataSetChanged();
            updateSummary();
            
            TextView tvDayStatus = findViewById(R.id.tv_day_status);
            updateStatusDisplay(day, tvDayStatus);
        });
        builder.show();
    }

    private void updateStatusDisplay(CalendarAdapter.CalendarDay day, TextView tvDayStatus) {
        tvDayStatus.setText(day.status.equals("Default") ? "Tap to mark" : day.status);
        switch (day.status) {
            case "Double Full Day": tvDayStatus.setTextColor(android.graphics.Color.parseColor("#1B5E20")); break;
            case "Full Day + Half": tvDayStatus.setTextColor(android.graphics.Color.parseColor("#2E7D32")); break;
            case "Full Day": tvDayStatus.setTextColor(android.graphics.Color.parseColor("#2E7D32")); break;
            case "Half Day": tvDayStatus.setTextColor(android.graphics.Color.parseColor("#EF6C00")); break;
            case "Absent": tvDayStatus.setTextColor(android.graphics.Color.parseColor("#C62828")); break;
            default: tvDayStatus.setTextColor(android.graphics.Color.parseColor("#6200EE")); break;
        }
    }

    private void updateSummary() {
        int fullCount = 0;
        int halfCount = 0;
        int absentCount = 0;
        double totalWorkUnits = 0;
        
        for (CalendarAdapter.CalendarDay day : days) {
            switch (day.status) {
                case "Double Full Day":
                    fullCount += 2;
                    totalWorkUnits += 2.0;
                    break;
                case "Full Day + Half":
                    fullCount += 1;
                    halfCount += 1;
                    totalWorkUnits += 1.5;
                    break;
                case "Full Day":
                    fullCount += 1;
                    totalWorkUnits += 1.0;
                    break;
                case "Half Day":
                    halfCount += 1;
                    totalWorkUnits += 0.5;
                    break;
                case "Absent":
                    absentCount++;
                    break;
            }
        }
        
        tvFullDay.setText(String.valueOf(fullCount));
        tvHalfDay.setText(String.valueOf(halfCount));
        tvAbsent.setText(String.valueOf(absentCount));
        
        double totalEarnings = totalWorkUnits * dailyWage;
        tvTotalAmount.setText("₹" + formatAmount(totalEarnings));
    }

    private String formatAmount(double amount) {
        if (amount == (long) amount) {
            return String.format("%d", (long) amount);
        } else {
            return String.format("%s", amount);
        }
    }
}
