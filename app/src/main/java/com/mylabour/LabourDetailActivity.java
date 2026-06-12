package com.mylabour;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.FileProvider;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import android.util.Base64;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
    private TextView tvFullDay, tvHalfDay, tvAbsent, tvTotalAmount, tvWage, tvPreviousDue, tvPaidAmount, tvDueAmount;
    private View layoutPreviousDue, dividerPrevDue, layoutPaidAmount, dividerPaid, layoutDueAmount, dividerTotal, fabSetPaid;
    private DatabaseReference mAttendanceRef, mLabourRef, mBaseAttendanceRef;
    private ValueEventListener attendanceListener;
    private String labourId;
    private double dailyWage;
    private Calendar currentCalendar;
    private String nodeKey;
    private double totalEarnings = 0;
    private double previousMonthDue = 0;
    private double paidAmountForMonth = 0;
    private Labour currentLabour;
    private ActivityResultLauncher<String> headerImagePickerLauncher;
    private android.widget.ImageView ivHeaderImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_labour_detail);

        ivHeaderImage = findViewById(R.id.iv_header_image);
        loadHeaderImage();

        headerImagePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    // Resize to a reasonable size for header
                    Bitmap resized = resizeBitmap(bitmap, 800);
                    String base64 = encodeToBase64(resized);
                    saveHeaderImage(base64);
                    ivHeaderImage.setImageBitmap(resized);
                    ivHeaderImage.setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                }
            }
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_more).setOnClickListener(this::showMoreMenu);

        Labour labour = (Labour) getIntent().getSerializableExtra("labour");
        currentLabour = labour;

        if (labour != null) {
            labourId = labour.id;
            dailyWage = 0;
            TextView tvName = findViewById(R.id.tv_detail_name);
            TextView tvNumber = findViewById(R.id.tv_detail_number);
            TextView tvEmail = findViewById(R.id.tv_detail_email);
            TextView tvAddress = findViewById(R.id.tv_detail_address);
            tvWage = findViewById(R.id.tv_detail_wage);
            tvFullDay = findViewById(R.id.tv_full_day_count);
            tvHalfDay = findViewById(R.id.tv_half_day_count);
            tvAbsent = findViewById(R.id.tv_absent_count);
            tvTotalAmount = findViewById(R.id.tv_total_amount);
            tvPreviousDue = findViewById(R.id.tv_previous_due);
            layoutPreviousDue = findViewById(R.id.layout_previous_due);
            dividerPrevDue = findViewById(R.id.divider_prev_due);
            tvPaidAmount = findViewById(R.id.tv_paid_amount);
            tvDueAmount = findViewById(R.id.tv_due_amount);
            layoutPaidAmount = findViewById(R.id.layout_paid_amount);
            dividerPaid = findViewById(R.id.divider_paid);
            layoutDueAmount = findViewById(R.id.layout_due_amount);
            dividerTotal = findViewById(R.id.divider_total);
            fabSetPaid = findViewById(R.id.fab_set_paid);

            tvName.setText(labour.name);
            String number = labour.number;
            if (number != null && !number.startsWith("+91")) {
                number = "+91" + number;
            }
            final String finalNumber = number;
            tvNumber.setText(finalNumber);
            tvEmail.setText(labour.email != null && !labour.email.isEmpty() ? labour.email : "N/A");
            tvAddress.setText(labour.address != null && !labour.address.isEmpty() ? labour.address : "N/A");
            tvWage.setText("₹" + formatAmount(dailyWage));

            currentCalendar = Calendar.getInstance();
            setupFirebase();
            setupCustomCalendar();
            fetchAttendanceData();
            fetchPreviousMonthDue();

            findViewById(R.id.layout_edit_wage).setOnClickListener(v -> showEditWageDialog());
            findViewById(R.id.layout_copy_phone).setOnClickListener(v -> copyToClipboard(finalNumber));
            findViewById(R.id.btn_prev_month).setOnClickListener(v -> changeMonth(-1));
            findViewById(R.id.btn_next_month).setOnClickListener(v -> changeMonth(1));
            
            findViewById(R.id.fab_set_paid).setOnClickListener(v -> showSetPaidDialog());
            findViewById(R.id.fab_share).setOnClickListener(v -> {
                if (currentLabour != null) {
                    generateAndSharePdf(currentLabour);
                }
            });
        }
    }

    private void showMoreMenu(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.getMenuInflater().inflate(R.menu.menu_labour_detail, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_delete) {
                showDeleteConfirmation();
                return true;
            } else if (id == R.id.action_edit) {
                showEditLabourDialog();
                return true;
            } else if (id == R.id.action_change_header) {
                headerImagePickerLauncher.launch("image/*");
                return true;
            } else if (id == R.id.action_remove_header) {
                removeHeaderImage();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showEditLabourDialog() {
        if (currentLabour == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Labour Details");
        
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_labour, null);
        builder.setView(dialogView);

        android.widget.EditText etName = dialogView.findViewById(R.id.et_name);
        android.widget.EditText etEmail = dialogView.findViewById(R.id.et_email);
        android.widget.EditText etNumber = dialogView.findViewById(R.id.et_number);
        android.widget.EditText etAddress = dialogView.findViewById(R.id.et_address);

        etName.setText(currentLabour.name);
        etEmail.setText(currentLabour.email);
        etNumber.setText(currentLabour.number);
        etAddress.setText(currentLabour.address);

        builder.setPositiveButton("Update", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String number = etNumber.getText().toString().trim();
            String address = etAddress.getText().toString().trim();

            if (!name.isEmpty()) {
                updateLabourDetails(name, email, number, address);
            } else {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void updateLabourDetails(String name, String email, String number, String address) {
        if (mLabourRef != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("name", name);
            updates.put("email", email);
            updates.put("number", number);
            updates.put("address", address);

            mLabourRef.updateChildren(updates).addOnSuccessListener(aVoid -> {
                Toast.makeText(this, "Details updated successfully", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Labour")
                .setMessage("Are you sure you want to delete this labour and all attendance data? This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteLabour())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteLabour() {
        if (mLabourRef != null) {
            mLabourRef.removeValue().addOnSuccessListener(aVoid -> {
                Toast.makeText(this, "Labour deleted successfully", Toast.LENGTH_SHORT).show();
                finish();
            }).addOnFailureListener(e -> {
                Toast.makeText(this, "Failed to delete labour", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void generateAndSharePdf(Labour labour) {
        View view = findViewById(R.id.content_to_export);
        
        int width = view.getWidth();
        int height = view.getHeight();

        if (width <= 0 || height <= 0) {
            Toast.makeText(this, "Content not ready yet", Toast.LENGTH_SHORT).show();
            return;
        }

        PdfDocument document = new PdfDocument();
        int footerHeight = 60;
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(width, height + footerHeight, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);

        Canvas canvas = page.getCanvas();
        view.draw(canvas);

        // Add footer text
        Paint paint = new Paint();
        paint.setColor(Color.GRAY);
        paint.setTextSize(14);
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        
        canvas.drawText("Labour Report - " + labour.name, width / 2f, height + (footerHeight / 2f) + 10, paint);

        document.finishPage(page);

        File cachePath = new File(getCacheDir(), "reports");
        if (!cachePath.exists()) cachePath.mkdirs();
        File file = new File(cachePath, "Labour_Report_" + labour.name.replace(" ", "_") + ".pdf");
        
        try {
            document.writeTo(new FileOutputStream(file));
            document.close();
            shareReportOptions(file, labour);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to generate PDF", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareReportOptions(File file, Labour labour) {
        String[] options = {"Share via WhatsApp", "Share via Email", "Other Share Options"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Share Report with " + labour.name);
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    shareToWhatsApp(file, labour.number);
                    break;
                case 1:
                    shareToEmail(file, labour.email);
                    break;
                case 2:
                    shareFileGeneric(file);
                    break;
            }
        });
        builder.show();
    }

    private void shareToWhatsApp(File file, String phoneNumber) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
        
        // Format phone number for WhatsApp (remove non-digits)
        String cleanNumber = phoneNumber.replaceAll("\\D+", "");
        if (!cleanNumber.startsWith("91") && cleanNumber.length() == 10) {
            cleanNumber = "91" + cleanNumber;
        }
        
        intent.putExtra("jid", cleanNumber + "@s.whatsapp.net");
        intent.setPackage("com.whatsapp");
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show();
            shareFileGeneric(file);
        }
    }

    private void shareToEmail(File file, String email) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{email});
        intent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Work Attendance Report");
        intent.putExtra(android.content.Intent.EXTRA_TEXT, "Please find attached the attendance and wage report.");
        intent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        
        startActivity(android.content.Intent.createChooser(intent, "Send Email"));
    }

    private void shareFileGeneric(File file) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(android.content.Intent.createChooser(intent, "Share Report"));
    }

    private void setupFirebase() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String userEmail = currentUser.getEmail();
            nodeKey = (userEmail != null) ? userEmail.replace(".", ",") : currentUser.getUid();
            
            mLabourRef = FirebaseDatabase.getInstance().getReference("labours")
                    .child(nodeKey)
                    .child(labourId);

            mLabourRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    currentLabour = snapshot.getValue(Labour.class);
                    if (currentLabour != null) {
                        updateHeaderUI();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });
            
            mBaseAttendanceRef = mLabourRef.child("attendance");
            updateAttendanceRef();
        }
    }

    private void updateHeaderUI() {
        TextView tvName = findViewById(R.id.tv_detail_name);
        TextView tvNumber = findViewById(R.id.tv_detail_number);
        TextView tvEmail = findViewById(R.id.tv_detail_email);
        TextView tvAddress = findViewById(R.id.tv_detail_address);

        tvName.setText(currentLabour.name);
        String number = currentLabour.number;
        if (number != null && !number.startsWith("+91")) {
            number = "+91" + number;
        }
        tvNumber.setText(number);
        tvEmail.setText(currentLabour.email != null && !currentLabour.email.isEmpty() ? currentLabour.email : "N/A");
        tvAddress.setText(currentLabour.address != null && !currentLabour.address.isEmpty() ? currentLabour.address : "N/A");
    }

    private void updateAttendanceRef() {
        if (mAttendanceRef != null && attendanceListener != null) {
            mAttendanceRef.removeEventListener(attendanceListener);
        }
        String yearMonth = currentCalendar.get(Calendar.YEAR) + "_" + (currentCalendar.get(Calendar.MONTH) + 1);
        mAttendanceRef = mBaseAttendanceRef.child(yearMonth);
    }

    private void changeMonth(int offset) {
        currentCalendar.add(Calendar.MONTH, offset);
        updateAttendanceRef();
        setupCustomCalendar();
        fetchAttendanceData();
        fetchPreviousMonthDue();
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
        if (mAttendanceRef != null) {
            mAttendanceRef.child("monthlyWage").setValue(newWage)
                    .addOnSuccessListener(aVoid -> {
                        dailyWage = newWage;
                        tvWage.setText("₹" + formatAmount(dailyWage));
                        // Also update base wage for the labour
                        mLabourRef.child("baseWage").setValue(newWage);
                        updateSummary();
                        Toast.makeText(this, "Wage updated and set as base", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to update wage", Toast.LENGTH_SHORT).show());
        }
    }

    private void fetchAttendanceData() {
        if (mAttendanceRef == null) return;

        attendanceListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Map<String, String> attendanceMap = new HashMap<>();
                Double wageForMonth = null;
                Double paidAmount = null;
                for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                    String key = postSnapshot.getKey();
                    if (key == null) continue;
                    
                    if (key.equals("monthlyWage")) {
                        Object value = postSnapshot.getValue();
                        if (value instanceof Number) {
                            wageForMonth = ((Number) value).doubleValue();
                        }
                    } else if (key.equals("paidAmount")) {
                        Object value = postSnapshot.getValue();
                        if (value instanceof Number) {
                            paidAmount = ((Number) value).doubleValue();
                        }
                    } else {
                        Object value = postSnapshot.getValue();
                        if (value instanceof String) {
                            attendanceMap.put(key, (String) value);
                        }
                    }
                }

                if (wageForMonth != null) {
                    dailyWage = wageForMonth;
                } else if (currentLabour != null && currentLabour.baseWage > 0) {
                    dailyWage = currentLabour.baseWage;
                } else {
                    fetchAndSetBaseWageFromLastMonth();
                    return;
                }
                tvWage.setText("₹" + formatAmount(dailyWage));

                paidAmountForMonth = (paidAmount != null) ? paidAmount : 0;

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
        };
        mAttendanceRef.addValueEventListener(attendanceListener);
    }

    private void fetchAndSetBaseWageFromLastMonth() {
        Calendar lastMonth = (Calendar) currentCalendar.clone();
        lastMonth.add(Calendar.MONTH, -1);
        String lastMonthKey = lastMonth.get(Calendar.YEAR) + "_" + (lastMonth.get(Calendar.MONTH) + 1);

        mBaseAttendanceRef.child(lastMonthKey).child("monthlyWage").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    double lastWage = 0;
                    Object value = snapshot.getValue();
                    if (value instanceof Long) {
                        lastWage = ((Long) value).doubleValue();
                    } else if (value instanceof Double) {
                        lastWage = (Double) value;
                    }

                    if (lastWage > 0) {
                        dailyWage = lastWage;
                        tvWage.setText("₹" + formatAmount(dailyWage));
                        // Set it as base wage for the labour
                        mLabourRef.child("baseWage").setValue(lastWage);
                        updateSummary();
                        return;
                    }
                }
                dailyWage = 0;
                tvWage.setText("₹" + formatAmount(dailyWage));
                updateSummary();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                dailyWage = 0;
                tvWage.setText("₹" + formatAmount(dailyWage));
                updateSummary();
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

        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        tvMonthYear.setText(sdf.format(currentCalendar.getTime()));

        days = new ArrayList<>();
        
        Calendar cal = (Calendar) currentCalendar.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1;
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

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
            }
        });
    }

    private void showStatusDialog(CalendarAdapter.CalendarDay day, int position) {
        String[] options = {"5x", "4.5x", "4x", "3.5x", "3x", "2.5x", "2x", "1.5x", "Full Day", "Half Day", "Absent", "Reset"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Mark Attendance - Day " + day.dayNumber);
        builder.setItems(options, (dialog, which) -> {
            String newStatus;
            if (which == options.length - 1) {
                newStatus = "Default";
            } else {
                newStatus = options[which];
            }
            
            day.status = newStatus;
            saveAttendanceToFirebase(day.dayNumber, newStatus);
            
            adapter.notifyDataSetChanged();
            updateSummary();
        });
        builder.show();
    }

    private void updateSummary() {
        int fullCount = 0;
        int halfCount = 0;
        int absentCount = 0;
        double totalWorkUnits = 0;
        
        for (CalendarAdapter.CalendarDay day : days) {
            String status = day.status;
            if (status == null || status.equals("Default")) continue;

            if (status.equals("Double Full Day") || status.equals("2x")) {
                fullCount += 2;
                totalWorkUnits += 2.0;
            } else if (status.equals("Full Day + Half") || status.equals("1.5x")) {
                fullCount += 1;
                halfCount += 1;
                totalWorkUnits += 1.5;
            } else if (status.equals("Full Day")) {
                fullCount += 1;
                totalWorkUnits += 1.0;
            } else if (status.equals("Half Day")) {
                halfCount += 1;
                totalWorkUnits += 0.5;
            } else if (status.equals("Absent")) {
                absentCount++;
            } else if (status.endsWith("x")) {
                try {
                    double val = Double.parseDouble(status.replace("x", ""));
                    totalWorkUnits += val;
                    fullCount += (int) val;
                    if (val % 1 != 0) {
                        halfCount += 1;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        
        tvFullDay.setText(String.valueOf(fullCount));
        tvHalfDay.setText(String.valueOf(halfCount));
        tvAbsent.setText(String.valueOf(absentCount));
        
        totalEarnings = totalWorkUnits * dailyWage;
        double grossTotal = totalEarnings + previousMonthDue;
        double balance = grossTotal - paidAmountForMonth;

        tvTotalAmount.setText("₹" + formatAmount(grossTotal));
        tvPaidAmount.setText("₹" + formatAmount(paidAmountForMonth));
        tvDueAmount.setText("₹" + formatAmount(balance));

        if (paidAmountForMonth > 0) {
            layoutPaidAmount.setVisibility(View.VISIBLE);
            dividerPaid.setVisibility(View.VISIBLE);
        } else {
            layoutPaidAmount.setVisibility(View.GONE);
            dividerPaid.setVisibility(View.GONE);
        }

        if (balance > 0) {
            layoutDueAmount.setVisibility(View.VISIBLE);
            fabSetPaid.setVisibility(View.VISIBLE);
        } else {
            layoutDueAmount.setVisibility(View.GONE);
            fabSetPaid.setVisibility(View.GONE);
        }

        if (paidAmountForMonth > 0 || balance > 0) {
            dividerTotal.setVisibility(View.VISIBLE);
        } else {
            dividerTotal.setVisibility(View.GONE);
        }
    }

    private void showSetPaidDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set Amount Paid");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(formatAmount(paidAmountForMonth));
        builder.setView(input);

        builder.setPositiveButton("Set", (dialog, which) -> {
            String paidStr = input.getText().toString().trim();
            if (!paidStr.isEmpty()) {
                double paidVal = Double.parseDouble(paidStr);
                savePaidAmountToFirebase(paidVal);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void savePaidAmountToFirebase(double amount) {
        if (mAttendanceRef != null) {
            mAttendanceRef.child("paidAmount").setValue(amount)
                    .addOnSuccessListener(aVoid -> {
                        paidAmountForMonth = amount;
                        updateSummary();
                        Toast.makeText(this, "Paid amount updated", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void fetchPreviousMonthDue() {
        Calendar prevMonth = (Calendar) currentCalendar.clone();
        prevMonth.add(Calendar.MONTH, -1);
        String yearMonth = prevMonth.get(Calendar.YEAR) + "_" + (prevMonth.get(Calendar.MONTH) + 1);
        
        mBaseAttendanceRef.child(yearMonth).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                double prevWorkUnits = 0;
                double prevWage = 0;
                double prevPaid = 0;
                
                for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                    String key = postSnapshot.getKey();
                    if (key == null) continue;

                    if (key.equals("monthlyWage")) {
                        Object value = postSnapshot.getValue();
                        if (value instanceof Number) {
                            prevWage = ((Number) value).doubleValue();
                        }
                    } else if (key.equals("paidAmount")) {
                        Object value = postSnapshot.getValue();
                        if (value instanceof Number) {
                            prevPaid = ((Number) value).doubleValue();
                        }
                    } else {
                        Object value = postSnapshot.getValue();
                        if (value instanceof String) {
                            prevWorkUnits += calculateWorkUnits((String) value);
                        }
                    }
                }
                
                previousMonthDue = (prevWorkUnits * prevWage) - prevPaid;
                if (previousMonthDue < 0) previousMonthDue = 0;

                tvPreviousDue.setText("₹" + formatAmount(previousMonthDue));
                
                if (previousMonthDue > 0) {
                    layoutPreviousDue.setVisibility(View.VISIBLE);
                    dividerPrevDue.setVisibility(View.VISIBLE);
                } else {
                    layoutPreviousDue.setVisibility(View.GONE);
                    dividerPrevDue.setVisibility(View.GONE);
                }

                updateSummary();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private double calculateWorkUnits(String status) {
        if (status == null || status.equals("Default")) return 0;
        if (status.equals("Double Full Day") || status.equals("2x")) return 2.0;
        if (status.equals("Full Day + Half") || status.equals("1.5x")) return 1.5;
        if (status.equals("Full Day")) return 1.0;
        if (status.equals("Half Day")) return 0.5;
        if (status.endsWith("x")) {
            try {
                return Double.parseDouble(status.replace("x", ""));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }


    private void copyToClipboard(String text) {
        if (text == null || text.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Labour Phone Number", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Copied to clipboard: " + text, Toast.LENGTH_SHORT).show();
        }
    }

    private String formatAmount(double amount) {
        if (amount == (long) amount) {
            return String.format("%d", (long) amount);
        } else {
            return String.format("%s", amount);
        }
    }

    private void saveHeaderImage(String base64) {
        getSharedPreferences("Settings", MODE_PRIVATE).edit()
                .putString("header_image_base64", base64)
                .apply();
    }

    private void removeHeaderImage() {
        getSharedPreferences("Settings", MODE_PRIVATE).edit()
                .remove("header_image_base64")
                .apply();
        ivHeaderImage.setImageDrawable(null);
        ivHeaderImage.setVisibility(View.GONE);
        Toast.makeText(this, "Header image removed", Toast.LENGTH_SHORT).show();
    }

    private void loadHeaderImage() {
        String base64 = getSharedPreferences("Settings", MODE_PRIVATE)
                .getString("header_image_base64", null);
        if (base64 != null) {
            byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            ivHeaderImage.setImageBitmap(decodedByte);
            ivHeaderImage.setVisibility(View.VISIBLE);
        } else {
            ivHeaderImage.setVisibility(View.GONE);
        }
    }

    private Bitmap resizeBitmap(Bitmap image, int maxSize) {
        int width = image.getWidth();
        int height = image.getHeight();

        float bitmapRatio = (float) width / (float) height;
        if (bitmapRatio > 1) {
            width = maxSize;
            height = (int) (width / bitmapRatio);
        } else {
            height = maxSize;
            width = (int) (height * bitmapRatio);
        }
        return Bitmap.createScaledBitmap(image, width, height, true);
    }

    private String encodeToBase64(Bitmap image) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        byte[] b = baos.toByteArray();
        return Base64.encodeToString(b, Base64.DEFAULT);
    }
}
