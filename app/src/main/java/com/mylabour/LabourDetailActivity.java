package com.mylabour;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.print.PageRange;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import java.io.FileInputStream;
import java.io.OutputStream;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
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
import java.util.Random;
import java.nio.charset.StandardCharsets;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import java.security.MessageDigest;

public class LabourDetailActivity extends AppCompatActivity {

    private List<CalendarAdapter.CalendarDay> days;
    private CalendarAdapter adapter;
    private TextView tvFullDay, tvHalfDay, tvAbsent, tvTotalAmount, tvWage, tvPreviousDue, tvPaidAmount, tvDueAmount, tvAdvanceAmount, tvPrevDueLabel, tvUniqueCode;
    private View layoutPreviousDue, dividerPrevDue, layoutPaidAmount, dividerPaid, layoutDueAmount, layoutAdvanceAmount, dividerTotal, fabSetPaid, fabLock;
    private android.widget.LinearLayout layoutPaymentsList;
    private View tvPaymentHistoryLabel, cardPaymentHistory;
    private DatabaseReference mAttendanceRef, mLabourRef, mBaseAttendanceRef, mPaymentsRef;
    private ValueEventListener attendanceListener;
    private final List<Payment> paymentList = new ArrayList<>();
    private String labourId;
    private double dailyWage;
    private Calendar currentCalendar;
    private String nodeKey;
    private double totalEarnings = 0;
    private double previousMonthDue = 0;
    private double paidAmountForMonth = 0;
    private int currentFullCount = 0, currentHalfCount = 0, currentAbsentCount = 0;
    private double currentGrossTotal = 0, currentBalance = 0;
    private Labour currentLabour;
    private DataSnapshot allAttendanceSnapshot;
    private ActivityResultLauncher<String> avatarPickerLauncher;
    private ActivityResultLauncher<Void> cameraLauncher;
    private View layoutCompanyHeader, dividerCompanyHeader, layoutSignatureExport, progressLoading;
    private TextView tvHeaderCompanyName, tvHeaderCompanyAddress, tvHeaderCompanyPhones;
    private ImageView ivDetailSignature;
    private com.google.android.material.imageview.ShapeableImageView ivDetailAvatar;
    private ActivityResultLauncher<ScanOptions> qrScannerLauncher;
    private boolean isMonthLocked = false;
    private String lastLockedStr = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_labour_detail);

        layoutCompanyHeader = findViewById(R.id.layout_company_header);
        dividerCompanyHeader = findViewById(R.id.divider_company_header);
        tvHeaderCompanyName = findViewById(R.id.tv_header_company_name);
        tvHeaderCompanyAddress = findViewById(R.id.tv_header_company_address);
        tvHeaderCompanyPhones = findViewById(R.id.tv_header_company_phones);
        
        layoutSignatureExport = findViewById(R.id.layout_signature_export);
        ivDetailSignature = findViewById(R.id.iv_detail_signature);
        progressLoading = findViewById(R.id.progress_loading);
        
        ivDetailAvatar = findViewById(R.id.iv_detail_avatar);
        
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String email = user.getEmail();
            nodeKey = (email != null) ? email.replace(".", ",") : user.getUid();
        }
        
        loadCompanyHeader();
        loadSignature();
        loadAvatarLocally();

        avatarPickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    Bitmap resized = resizeBitmap(bitmap, 400);
                    String base64 = encodeToBase64(resized);
                    saveAvatarLocally(base64);
                    ivDetailAvatar.setImageBitmap(resized);
                    ivDetailAvatar.setPadding(0, 0, 0, 0);
                    ivDetailAvatar.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                } catch (Exception e) {
                    android.util.Log.e("LabourDetail", "Failed to load image", e);
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                }
            }
        });

        cameraLauncher = registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
            if (bitmap != null) {
                Bitmap resized = resizeBitmap(bitmap, 400);
                String base64 = encodeToBase64(resized);
                saveAvatarLocally(base64);
                ivDetailAvatar.setImageBitmap(resized);
                ivDetailAvatar.setPadding(0, 0, 0, 0);
                ivDetailAvatar.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            }
        });

        qrScannerLauncher = registerForActivityResult(new ScanContract(), result -> {
            if (result.getContents() != null) {
                String scannedData = result.getContents();
                handleScanResult(scannedData);
            }
        });

        findViewById(R.id.btn_change_photo).setOnClickListener(v -> showPhotoOptionsDialog());

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
            tvPrevDueLabel = findViewById(R.id.tv_prev_due_label);
            layoutPreviousDue = findViewById(R.id.layout_previous_due);
            dividerPrevDue = findViewById(R.id.divider_prev_due);
            tvPaidAmount = findViewById(R.id.tv_paid_amount);
            tvDueAmount = findViewById(R.id.tv_due_amount);
            layoutPaidAmount = findViewById(R.id.layout_paid_amount);
            dividerPaid = findViewById(R.id.divider_paid);
            layoutDueAmount = findViewById(R.id.layout_due_amount);
            tvAdvanceAmount = findViewById(R.id.tv_advance_amount);
            layoutAdvanceAmount = findViewById(R.id.layout_advance_amount);
            dividerTotal = findViewById(R.id.divider_total);
            fabSetPaid = findViewById(R.id.fab_set_paid);
            fabLock = findViewById(R.id.fab_lock);
            layoutPaymentsList = findViewById(R.id.layout_payments_list);
            tvPaymentHistoryLabel = findViewById(R.id.tv_payment_history_label);
            cardPaymentHistory = findViewById(R.id.card_payment_history);
            tvUniqueCode = findViewById(R.id.tv_unique_code);

            tvName.setText(labour.name);
            String number = labour.number;
            if (number != null && !number.startsWith("+91")) {
                number = "+91" + number;
            }
            final String finalNumber = number;
            tvNumber.setText(finalNumber);
            tvEmail.setText(labour.email != null && !labour.email.isEmpty() ? labour.email : "N/A");
            tvAddress.setText(labour.address != null && !labour.address.isEmpty() ? labour.address : "N/A");
            tvWage.setText(getString(R.string.wage_format, formatAmount(dailyWage)));
            
            currentCalendar = Calendar.getInstance();
            updateMonthlyUniqueCodeDisplay();

            setupFirebase();
            setupCustomCalendar();
            fetchAttendanceData();
            fetchPreviousMonthDue();

            findViewById(R.id.layout_edit_wage).setOnClickListener(v -> showEditWageDialog());
            findViewById(R.id.layout_copy_phone).setOnClickListener(v -> copyToClipboard(finalNumber));
            findViewById(R.id.btn_prev_month).setOnClickListener(v -> changeMonth(-1));
            findViewById(R.id.btn_next_month).setOnClickListener(v -> changeMonth(1));
            
            findViewById(R.id.fab_set_paid).setOnClickListener(v -> showSetPaidDialog());
            fabLock.setOnClickListener(v -> toggleMonthLock());
            findViewById(R.id.fab_print).setOnClickListener(v -> {
                if (currentLabour != null) {
                    progressLoading.setVisibility(View.VISIBLE);
                    v.postDelayed(() -> {
                        File file = generatePdf(currentLabour);
                        if (file != null) printPdf(file);
                        progressLoading.setVisibility(View.GONE);
                    }, 100);
                }
            });
            findViewById(R.id.fab_share).setOnClickListener(v -> {
                if (currentLabour != null) {
                    progressLoading.setVisibility(View.VISIBLE);
                    v.postDelayed(() -> {
                        File file = generatePdf(currentLabour);
                        if (file != null) shareReportOptions(file, currentLabour);
                        progressLoading.setVisibility(View.GONE);
                    }, 100);
                }
            });

            findViewById(R.id.fab_scan_qr).setOnClickListener(v -> startScanning());
        }
    }

    private void showMoreMenu(View v) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_menu_labour, null);
        builder.setView(dialogView);

        View menuEdit = dialogView.findViewById(R.id.menu_edit);
        View menuToggleCompany = dialogView.findViewById(R.id.menu_toggle_company);
        com.google.android.material.materialswitch.MaterialSwitch switchCompany = dialogView.findViewById(R.id.switch_company);
        View menuToggleSignature = dialogView.findViewById(R.id.menu_toggle_signature);
        com.google.android.material.materialswitch.MaterialSwitch switchSignature = dialogView.findViewById(R.id.switch_signature);
        View menuDelete = dialogView.findViewById(R.id.menu_delete);
        View menuClose = dialogView.findViewById(R.id.menu_close);

        android.content.SharedPreferences prefs = getSharedPreferences("CompanyPrefs_" + nodeKey, MODE_PRIVATE);
        boolean showCompany = prefs.getBoolean("show_company_header", true);
        switchCompany.setChecked(showCompany);
        
        boolean showSignature = prefs.getBoolean("show_signature", true);
        switchSignature.setChecked(showSignature);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        menuEdit.setOnClickListener(view -> {
            dialog.dismiss();
            showEditLabourDialog();
        });

        menuToggleCompany.setOnClickListener(view -> {
            boolean isChecked = !switchCompany.isChecked();
            switchCompany.setChecked(isChecked);
            prefs.edit().putBoolean("show_company_header", isChecked).apply();
            loadCompanyHeader();
        });

        menuToggleSignature.setOnClickListener(view -> {
            boolean isChecked = !switchSignature.isChecked();
            switchSignature.setChecked(isChecked);
            prefs.edit().putBoolean("show_signature", isChecked).apply();
            loadSignature();
        });

        menuDelete.setOnClickListener(view -> {
            dialog.dismiss();
            showDeleteConfirmation();
        });

        menuClose.setOnClickListener(view -> dialog.dismiss());

        dialog.show();
    }

    private void showEditLabourDialog() {
        if (currentLabour == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_labour, null);
        builder.setView(dialogView);

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        android.widget.EditText etName = dialogView.findViewById(R.id.et_name);
        android.widget.EditText etEmail = dialogView.findViewById(R.id.et_email);
        android.widget.EditText etNumber = dialogView.findViewById(R.id.et_number);
        android.widget.EditText etAddress = dialogView.findViewById(R.id.et_address);
        View layoutInitialAdvance = dialogView.findViewById(R.id.layout_initial_advance);
        View btnSave = dialogView.findViewById(R.id.btn_save_labour);
        View tvCancel = dialogView.findViewById(R.id.tv_cancel_labour);

        tvTitle.setText(R.string.edit_labour_details);
        etName.setText(currentLabour.name);
        etEmail.setText(currentLabour.email);
        etNumber.setText(currentLabour.number);
        etAddress.setText(currentLabour.address);
        
        // Hide initial advance for editing as it might cause confusion with attendance balance
        layoutInitialAdvance.setVisibility(View.GONE);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String number = etNumber.getText().toString().trim();
            String address = etAddress.getText().toString().trim();

            if (!name.isEmpty()) {
                // Keep existing initialAdvance
                updateLabourDetails(name, email, number, address, currentLabour.initialAdvance);
                dialog.dismiss();
            } else {
                Toast.makeText(this, R.string.name_empty_error, Toast.LENGTH_SHORT).show();
            }
        });

        tvCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void startScanning() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan a QR Code");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        options.setCaptureActivity(CaptureActivityPortrait.class);
        qrScannerLauncher.launch(options);
    }

    private void handleScanResult(String data) {
        if (data.startsWith("upi://")) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(data));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "No UPI app found", Toast.LENGTH_SHORT).show();
            }
        } else {
            verifyScannedCode(data);
        }
    }

    private void verifyScannedCode(String scannedData) {
        if (scannedData == null || scannedData.isEmpty()) return;

        // Try to extract the unique code from the multi-line QR data
        String uniqueCodeToVerify = "";
        String[] lines = scannedData.split("\n");
        for (String line : lines) {
            if (line.startsWith("Unique Code: ")) {
                uniqueCodeToVerify = line.substring("Unique Code: ".length()).trim();
                break;
            }
        }
        
        // If "Unique Code:" prefix not found, use the first line or raw data (backward compatibility)
        if (uniqueCodeToVerify.isEmpty()) {
            uniqueCodeToVerify = scannedData.startsWith("#") ? scannedData.substring(1) : scannedData;
        }

        final String finalCode = uniqueCodeToVerify;
        if (progressLoading != null) progressLoading.setVisibility(View.VISIBLE);
        
        // Check in Firebase
        if (mAttendanceRef != null) {
            mAttendanceRef.child("monthlyUniqueCode").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (progressLoading != null) progressLoading.setVisibility(View.GONE);
                    String firebaseCode = snapshot.getValue(String.class);
                    
                    if (finalCode.equals(firebaseCode)) {
                        showVerificationDialog(true, scannedData, finalCode);
                    } else {
                        // Check if it matches the locally generated one (fallback)
                        if (finalCode.equals(getMonthlyUniqueCode())) {
                             showVerificationDialog(true, scannedData, finalCode);
                        } else {
                             showVerificationDialog(false, scannedData, finalCode);
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    if (progressLoading != null) progressLoading.setVisibility(View.GONE);
                    Toast.makeText(LabourDetailActivity.this, "Verification error", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            if (progressLoading != null) progressLoading.setVisibility(View.GONE);
            if (finalCode.equals(getMonthlyUniqueCode())) {
                showVerificationDialog(true, scannedData, finalCode);
            } else {
                showVerificationDialog(false, scannedData, finalCode);
            }
        }
    }

    private void showVerificationDialog(boolean success, String fullData, String code) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_verification, null);
        builder.setView(dialogView);

        ImageView ivStatus = dialogView.findViewById(R.id.iv_verification_status);
        TextView tvTitle = dialogView.findViewById(R.id.tv_verification_title);
        TextView tvSubtitle = dialogView.findViewById(R.id.tv_verification_subtitle);
        TextView tvDetails = dialogView.findViewById(R.id.tv_verification_details);
        com.google.android.material.button.MaterialButton btnOk = dialogView.findViewById(R.id.btn_verification_ok);

        if (success) {
            ivStatus.setImageResource(android.R.drawable.ic_dialog_info);
            ivStatus.setColorFilter(Color.parseColor("#2E7D32")); 
            tvTitle.setText("Report Verified ✓");
            tvSubtitle.setText("This document is AUTHENTIC");
            tvSubtitle.setTextColor(Color.parseColor("#2E7D32"));
            
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
            String[] lines = fullData.split("\n");
            
            for (String line : lines) {
                if (line.equals("VERIFY REPORT")) continue;
                
                boolean fieldMatches = checkFieldMatch(line);
                int start = sb.length();
                sb.append(line).append(fieldMatches ? " ✓" : " ✗").append("\n");
                
                int color = fieldMatches ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828");
                sb.setSpan(new android.text.style.ForegroundColorSpan(color), 
                         start, sb.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            // Add warning if month is currently unlocked
            if (!isMonthLocked) {
                int start = sb.length();
                sb.append("\n⚠️ WARNING: This month is currently UNLOCKED. The data in this printed report might have been modified after printing.");
                sb.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#EF6C00")), // Orange warning color
                         start, sb.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                         start, sb.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            tvDetails.setText(sb);
        } else {
            ivStatus.setImageResource(android.R.drawable.ic_dialog_alert);
            ivStatus.setColorFilter(Color.parseColor("#C62828")); 
            tvTitle.setText("Verification Failed ✗");
            tvSubtitle.setText("Data mismatch detected");
            tvSubtitle.setTextColor(Color.parseColor("#C62828"));
            
            tvDetails.setText("The scanned QR code does not match the digital records for " + 
                (currentLabour != null ? currentLabour.name : "this labourer") + ".\n\n" +
                "Scanned Code: #" + code + "\n\n" +
                "Please ensure you are scanning the correct report or check if the code has been updated.");
        }

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnOk.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private boolean checkFieldMatch(String line) {
        if (currentLabour == null) return true;
        
        try {
            if (line.startsWith("Name: ")) {
                return line.substring(6).trim().equalsIgnoreCase(currentLabour.name);
            }
            if (line.startsWith("Phone: ")) {
                return line.substring(7).trim().contains(currentLabour.number);
            }
            if (line.startsWith("Attendance: ")) {
                String expected = currentFullCount + "F, " + currentHalfCount + "H, " + currentAbsentCount + "A";
                return line.substring(12).trim().equals(expected);
            }
            if (line.startsWith("Month Earn: ₹")) {
                return line.substring(13).trim().equals(formatAmount(totalEarnings));
            }
            if (line.startsWith("Gross Total: ₹")) {
                return line.substring(14).trim().equals(formatAmount(currentGrossTotal));
            }
            if (line.startsWith("Total Paid: ₹")) {
                return line.substring(13).trim().equals(formatAmount(paidAmountForMonth));
            }
            if (line.startsWith("Net Due: ₹")) {
                return line.substring(10).trim().equals(formatAmount(currentBalance));
            }
            if (line.startsWith("Net Advance: ₹")) {
                return line.substring(14).trim().equals(formatAmount(Math.abs(currentBalance)));
            }
            if (line.startsWith("Unique Code: ")) {
                return line.substring(13).trim().equals(getMonthlyUniqueCode());
            }
            if (line.startsWith("Locked on: ")) {
                // If we have a local lastLockedStr, we can try to match it
                if (lastLockedStr != null && !lastLockedStr.isEmpty()) {
                    return line.trim().equals(lastLockedStr.replace(" | ", "").trim());
                }
                return true;
            }
        } catch (Exception ignored) {}
        
        return true; // Default for non-critical or formatting lines
    }

    private void updateLabourDetails(String name, String email, String number, String address, double initialAdvance) {
        if (mLabourRef != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("name", name);
            updates.put("email", email);
            updates.put("number", number);
            updates.put("address", address);
            updates.put("initialAdvance", initialAdvance);

            mLabourRef.updateChildren(updates).addOnSuccessListener(aVoid -> 
                Toast.makeText(this, R.string.details_updated, Toast.LENGTH_SHORT).show()
            );
        }
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_labour_title)
                .setMessage(R.string.delete_labour_msg)
                .setPositiveButton(R.string.delete, (dialog, which) -> deleteLabour())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showPhotoOptionsDialog() {
        String[] options = {"Take Photo", "Choose from Gallery"};
        new AlertDialog.Builder(this)
                .setTitle("Change Photo")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            cameraLauncher.launch(null);
                        } else {
                            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, 101);
                        }
                    } else {
                        avatarPickerLauncher.launch("image/*");
                    }
                })
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

    private File generatePdf(Labour labour) {
        View view = findViewById(R.id.content_to_export);
        View btnChangePhoto = findViewById(R.id.btn_change_photo);
        View bottomSpacer = findViewById(R.id.bottom_spacer);
        GridView calendarGrid = findViewById(R.id.calendar_grid);
        
        // Temporarily hide UI elements not needed in PDF
        if (btnChangePhoto != null) btnChangePhoto.setVisibility(View.INVISIBLE);
        if (bottomSpacer != null) bottomSpacer.setVisibility(View.GONE);
        
        // Re-measure view after hiding elements to get correct height
        view.measure(View.MeasureSpec.makeMeasureSpec(view.getWidth(), View.MeasureSpec.EXACTLY),
                     View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int width = view.getWidth();
        int height = view.getMeasuredHeight();

        if (width <= 0 || height <= 0) {
            Toast.makeText(this, "Content not ready yet", Toast.LENGTH_SHORT).show();
            // Restore visibility if we return early
            if (btnChangePhoto != null) btnChangePhoto.setVisibility(View.VISIBLE);
            if (bottomSpacer != null) bottomSpacer.setVisibility(View.VISIBLE);
            return null;
        }

        // Hide delete buttons in payment history
        List<View> deleteButtons = new ArrayList<>();
        for (int i = 0; i < layoutPaymentsList.getChildCount(); i++) {
            View itemView = layoutPaymentsList.getChildAt(i);
            View btnDelete = itemView.findViewById(R.id.btn_delete_payment);
            if (btnDelete != null && btnDelete.getVisibility() == View.VISIBLE) {
                btnDelete.setVisibility(View.INVISIBLE);
                deleteButtons.add(btnDelete);
            }
        }

        // Hide current day border (today highlight)
        Map<com.google.android.material.card.MaterialCardView, Integer> todayCards = new HashMap<>();
        if (calendarGrid != null) {
            for (int i = 0; i < calendarGrid.getChildCount(); i++) {
                View child = calendarGrid.getChildAt(i);
                com.google.android.material.card.MaterialCardView card = child.findViewById(R.id.card_day_background);
                if (card != null && card.getStrokeWidth() > 0) {
                    todayCards.put(card, card.getStrokeWidth());
                    card.setStrokeWidth(0);
                }
            }
        }

        PdfDocument document = new PdfDocument();
        int pageHeight = (int) (width * 1.414); // A4 aspect ratio
        int footerHeight = 80;
        int contentHeightPerPage = pageHeight - footerHeight;
        
        int totalPages = (int) Math.ceil((double) height / contentHeightPerPage);

        Paint paint = new Paint();
        paint.setColor(Color.GRAY);
        paint.setTextSize(12);
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);

        // Generate verification data
        int fullDays = Integer.parseInt(tvFullDay.getText().toString());
        int halfDays = Integer.parseInt(tvHalfDay.getText().toString());
        int absent = Integer.parseInt(tvAbsent.getText().toString());
        
        double grossTotal = totalEarnings + previousMonthDue;
        double balance = grossTotal - paidAmountForMonth;
        
        String monthlyUniqueCode = getMonthlyUniqueCode();
        
        android.content.SharedPreferences prefs = getSharedPreferences("CompanyPrefs_" + nodeKey, MODE_PRIVATE);
        String companyName = prefs.getString("company_name", "");
        String companyPhone = prefs.getString("company_phone", "");

        StringBuilder qrDataBuilder = new StringBuilder();
        qrDataBuilder.append("VERIFY REPORT\n");
        if (!companyName.isEmpty()) {
            qrDataBuilder.append("From: ").append(companyName).append("\n");
        }
        if (!companyPhone.isEmpty()) {
            qrDataBuilder.append("Co. Phone: ").append(companyPhone).append("\n");
        }
        
        SimpleDateFormat monthYearSdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        qrDataBuilder.append("Month: ").append(monthYearSdf.format(currentCalendar.getTime())).append("\n");

        qrDataBuilder.append("Name: ").append(labour.name).append("\n");
        if (labour.number != null && !labour.number.isEmpty()) {
            qrDataBuilder.append("Phone: ").append(labour.number).append("\n");
        }
        if (labour.email != null && !labour.email.isEmpty()) {
            qrDataBuilder.append("Email: ").append(labour.email).append("\n");
        }
        if (labour.address != null && !labour.address.isEmpty()) {
            qrDataBuilder.append("Addr: ").append(labour.address).append("\n");
        }
        qrDataBuilder.append("Attendance: ").append(fullDays).append("F, ")
                     .append(halfDays).append("H, ")
                     .append(absent).append("A\n");
        
        if (previousMonthDue != 0) {
            String label = previousMonthDue > 0 ? "Prev Due: " : "Prev Adv: ";
            qrDataBuilder.append(label).append("₹").append(formatAmount(Math.abs(previousMonthDue))).append("\n");
        }
        
        qrDataBuilder.append("Month Earn: ₹").append(formatAmount(totalEarnings)).append("\n");
        qrDataBuilder.append("Gross Total: ₹").append(formatAmount(grossTotal)).append("\n");
        qrDataBuilder.append("Total Paid: ₹").append(formatAmount(paidAmountForMonth)).append("\n");
        
        if (balance > 0) {
            qrDataBuilder.append("Net Due: ₹").append(formatAmount(balance)).append("\n");
        } else if (balance < 0) {
            qrDataBuilder.append("Net Advance: ₹").append(formatAmount(Math.abs(balance))).append("\n");
        } else {
            qrDataBuilder.append("Status: Settled\n");
        }
        
        qrDataBuilder.append("Unique Code: ").append(monthlyUniqueCode);
        if (lastLockedStr != null && !lastLockedStr.isEmpty()) {
            qrDataBuilder.append("\n").append(lastLockedStr.replace(" | ", ""));
        }
        
        Bitmap qrBitmap = generateQRCode(qrDataBuilder.toString(), 512);

        for (int i = 0; i < totalPages; i++) {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(width, pageHeight, i + 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            // Draw the view segment
            canvas.save();
            canvas.translate(0, -i * contentHeightPerPage);
            view.draw(canvas);
            canvas.restore();

            // Add white background for the footer area to avoid content overlap
            Paint bgPaint = new Paint();
            bgPaint.setColor(Color.WHITE);
            canvas.drawRect(0, contentHeightPerPage, width, pageHeight, bgPaint);

            // Add footer text
            String footerText = "Powered by My Labour - Page " + (i + 1) + " of " + totalPages;
            canvas.drawText(footerText, width / 2f, pageHeight - (footerHeight / 2f) + 5, paint);

            // Draw QR Code on the last page, on the left side
            if (i == totalPages - 1 && qrBitmap != null) {
                int qrSize = 140; 
                int margin = 35;
                int pageTopOffset = i * contentHeightPerPage;
                
                float drawX = margin; // Fixed to left side
                float drawY;
                
                if (layoutSignatureExport.getVisibility() == View.VISIBLE) {
                    // Align vertically with the signature block area
                    int sigTop = layoutSignatureExport.getTop();
                    int sigHeight = layoutSignatureExport.getHeight();
                    drawY = (sigTop - pageTopOffset) + (sigHeight - qrSize) / 2f;
                } else {
                    // If signature is not visible, place it towards the bottom left
                    drawY = (height - pageTopOffset) - qrSize - margin - 20;
                }
                
                // Final safety bounds to ensure it's on page and above footer
                drawY = Math.max(margin, Math.min(drawY, contentHeightPerPage - qrSize - 50));
                
                Rect destRect = new Rect((int)drawX, (int)drawY, (int)(drawX + qrSize), (int)(drawY + qrSize));
                canvas.drawBitmap(qrBitmap, null, destRect, null);
                
                Paint qrLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                qrLabelPaint.setColor(Color.parseColor("#757575")); // Material Grey 600
                qrLabelPaint.setTextSize(9);
                qrLabelPaint.setTextAlign(Paint.Align.CENTER);
                qrLabelPaint.setFakeBoldText(true);
                canvas.drawText("Verify Report", drawX + qrSize / 2f, drawY + qrSize + 15, qrLabelPaint);
                
                qrLabelPaint.setTextSize(8);
                qrLabelPaint.setFakeBoldText(false);
                canvas.drawText("#" + monthlyUniqueCode, drawX + qrSize / 2f, drawY + qrSize + 26, qrLabelPaint);
            }

            document.finishPage(page);
        }

        // Restore UI elements
        if (btnChangePhoto != null) btnChangePhoto.setVisibility(View.VISIBLE);
        if (bottomSpacer != null) bottomSpacer.setVisibility(View.VISIBLE);
        for (View btn : deleteButtons) {
            btn.setVisibility(View.VISIBLE);
        }
        
        // Restore current day border
        for (Map.Entry<com.google.android.material.card.MaterialCardView, Integer> entry : todayCards.entrySet()) {
            entry.getKey().setStrokeWidth(entry.getValue());
        }

        File cachePath = new File(getCacheDir(), "reports");
        if (!cachePath.exists() && !cachePath.mkdirs()) {
            android.util.Log.e("LabourDetail", "Failed to create cache directory");
        }
        File file = new File(cachePath, "Labour_Report_" + labour.name.replace(" ", "_") + ".pdf");
        
        try {
            document.writeTo(new FileOutputStream(file));
            document.close();
            return file;
        } catch (IOException e) {
            android.util.Log.e("LabourDetail", "Failed to generate PDF", e);
            Toast.makeText(this, "Failed to generate PDF", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private void printPdf(File file) {
        PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        try {
            PrintDocumentAdapter printAdapter = new PrintDocumentAdapter() {
                @Override
                public void onWrite(PageRange[] pages, ParcelFileDescriptor destination, CancellationSignal cancellationSignal, WriteResultCallback callback) {
                    try (InputStream input = new FileInputStream(file);
                         OutputStream output = new FileOutputStream(destination.getFileDescriptor())) {
                        byte[] buf = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = input.read(buf)) > 0) {
                            output.write(buf, 0, bytesRead);
                        }
                        callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes, CancellationSignal cancellationSignal, LayoutResultCallback callback, Bundle extras) {
                    if (cancellationSignal.isCanceled()) {
                        callback.onLayoutCancelled();
                        return;
                    }
                    PrintDocumentInfo pdi = new PrintDocumentInfo.Builder(file.getName()).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build();
                    callback.onLayoutFinished(pdi, true);
                }
            };
            printManager.print("Labour Report - " + file.getName(), printAdapter, null);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to print", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareReportOptions(File file, Labour labour) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_share, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        dialogView.findViewById(R.id.btn_share_whatsapp).setOnClickListener(v -> {
            shareToWhatsApp(file, labour.number);
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.btn_share_email).setOnClickListener(v -> {
            shareToEmail(file, labour.email);
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.btn_share_other).setOnClickListener(v -> {
            shareFileGeneric(file);
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.btn_close_share).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
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
        if (nodeKey != null) {
            mLabourRef = FirebaseDatabase.getInstance().getReference("labours")
                    .child(nodeKey)
                    .child(labourId);
            mLabourRef.keepSynced(true);

            mLabourRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    currentLabour = snapshot.getValue(Labour.class);
                    if (currentLabour != null) {
                        if (currentLabour.uniqueCode == null || currentLabour.uniqueCode.isEmpty()) {
                            String code = labourId.length() > 4 ? labourId.substring(labourId.length() - 4).toUpperCase() : labourId.toUpperCase();
                            mLabourRef.child("uniqueCode").setValue(code);
                        }
                        updateHeaderUI();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });
            
            mBaseAttendanceRef = mLabourRef.child("attendance");
            fetchPreviousMonthDue();
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

        updateMonthlyUniqueCodeDisplay();

        loadAvatarLocally();
    }

    private void updateMonthlyUniqueCodeDisplay() {
        if (tvUniqueCode != null) {
            tvUniqueCode.setText("#" + getMonthlyUniqueCode() + lastLockedStr);
        }
    }

    private String getMonthlyUniqueCode() {
        String baseCode;
        if (currentLabour != null && currentLabour.uniqueCode != null) {
            baseCode = currentLabour.uniqueCode;
        } else if (labourId != null) {
            baseCode = labourId.length() > 4 ? labourId.substring(labourId.length() - 4).toUpperCase() : labourId.toUpperCase();
        } else {
            baseCode = "LB";
        }
        
        SimpleDateFormat sdf = new SimpleDateFormat("MMyy", Locale.getDefault());
        String datePart = sdf.format(currentCalendar.getTime());
        
        // Generate a stable 4-digit random number based on labourId and date
        long seed = ((labourId != null ? labourId.hashCode() : 0L) + datePart.hashCode());
        Random r = new Random(seed);
        int randomPart = 1000 + r.nextInt(9000);
        
        return baseCode + "-" + randomPart + "-" + datePart;
    }

    private void saveAvatarLocally(String base64) {
        getSharedPreferences("LabourPhotos_" + nodeKey, MODE_PRIVATE).edit()
                .putString("photo_" + labourId, base64)
                .apply();
        Toast.makeText(this, "Photo saved locally", Toast.LENGTH_SHORT).show();
    }

    private void loadAvatarLocally() {
        String base64 = getSharedPreferences("LabourPhotos_" + nodeKey, MODE_PRIVATE)
                .getString("photo_" + labourId, null);
        if (base64 != null && !base64.isEmpty()) {
            try {
                byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                ivDetailAvatar.setImageBitmap(decodedByte);
                ivDetailAvatar.setPadding(0, 0, 0, 0);
                ivDetailAvatar.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            } catch (Exception e) {
                setDefaultAvatar();
            }
        } else {
            setDefaultAvatar();
        }
    }

    private void setDefaultAvatar() {
        ivDetailAvatar.setImageResource(R.drawable.ic_person);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        ivDetailAvatar.setPadding(padding, padding, padding, padding);
        ivDetailAvatar.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
    }

    private void updateAttendanceRef() {
        if (mAttendanceRef != null && attendanceListener != null) {
            mAttendanceRef.removeEventListener(attendanceListener);
        }
        String yearMonth = currentCalendar.get(Calendar.YEAR) + "_" + (currentCalendar.get(Calendar.MONTH) + 1);
        mAttendanceRef = mBaseAttendanceRef.child(yearMonth);
        mPaymentsRef = mAttendanceRef.child("payments");
    }

    private void changeMonth(int offset) {
        currentCalendar.add(Calendar.MONTH, offset);
        lastLockedStr = "";
        updateAttendanceRef();
        setupCustomCalendar();
        fetchAttendanceData();
        calculateAndDisplayPreviousDue();
        updateMonthlyUniqueCodeDisplay();
    }

    private void showEditWageDialog() {
        if (isMonthLocked) {
            Toast.makeText(this, "Month is locked. Cannot change wage.", Toast.LENGTH_SHORT).show();
            return;
        }
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
                        tvWage.setText(getString(R.string.wage_format, formatAmount(dailyWage)));
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
                Double legacyPaidAmount = null;
                paymentList.clear();
                isMonthLocked = false;
                lastLockedStr = "";
                
                for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                    String key = postSnapshot.getKey();
                    if (key == null) continue;
                    
                    switch (key) {
                        case "isLocked":
                            isMonthLocked = postSnapshot.getValue(Boolean.class) != null && postSnapshot.getValue(Boolean.class);
                            break;
                        case "monthlyUniqueCode":
                            // We can use the stored one if needed, but for now we just ensure it exists
                            break;
                        case "lastLocked": {
                            Object value = postSnapshot.getValue();
                            if (value instanceof Number) {
                                long lastLock = ((Number) value).longValue();
                                SimpleDateFormat lastLockSdf = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());
                                lastLockedStr = " | Locked on: " + lastLockSdf.format(new java.util.Date(lastLock));
                                updateMonthlyUniqueCodeDisplay();
                            }
                            break;
                        }
                        case "monthlyWage": {
                            Object value = postSnapshot.getValue();
                            if (value instanceof Number) {
                                wageForMonth = ((Number) value).doubleValue();
                            }
                            break;
                        }
                        case "paidAmount": {
                            Object value = postSnapshot.getValue();
                            if (value instanceof Number) {
                                legacyPaidAmount = ((Number) value).doubleValue();
                            }
                            break;
                        }
                        case "payments":
                            for (DataSnapshot paymentSnapshot : postSnapshot.getChildren()) {
                                Payment p = paymentSnapshot.getValue(Payment.class);
                                if (p != null) {
                                    p.id = paymentSnapshot.getKey();
                                    paymentList.add(p);
                                }
                            }
                            break;
                        default: {
                            Object value = postSnapshot.getValue();
                            if (value instanceof String) {
                                attendanceMap.put(key, (String) value);
                            }
                            break;
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
                tvWage.setText(getString(R.string.wage_format, formatAmount(dailyWage)));

                if (!snapshot.hasChild("monthlyUniqueCode") && currentLabour != null) {
                    mAttendanceRef.child("monthlyUniqueCode").setValue(getMonthlyUniqueCode());
                }

                double totalPaidFromList = 0;
                for (Payment p : paymentList) totalPaidFromList += p.amount;
                
                paidAmountForMonth = (legacyPaidAmount != null) ? legacyPaidAmount + totalPaidFromList : totalPaidFromList;

                // Update days list with data from Firebase
                for (CalendarAdapter.CalendarDay day : days) {
                    if (day.dayNumber != 0) {
                        String status = attendanceMap.get(String.valueOf(day.dayNumber));
                        day.status = (status != null) ? status : "Default";
                    }
                }
                adapter.notifyDataSetChanged();
                displayPaymentHistory();
                updateSummary();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(LabourDetailActivity.this, "Failed to load attendance", Toast.LENGTH_SHORT).show();
            }
        };
        mAttendanceRef.addValueEventListener(attendanceListener);
    }

    private void displayPaymentHistory() {
        layoutPaymentsList.removeAllViews();
        if (paymentList.isEmpty()) {
            tvPaymentHistoryLabel.setVisibility(View.GONE);
            cardPaymentHistory.setVisibility(View.GONE);
            return;
        }

        tvPaymentHistoryLabel.setVisibility(View.VISIBLE);
        cardPaymentHistory.setVisibility(View.VISIBLE);

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

        // Sort payments by timestamp descending
        paymentList.sort((p1, p2) -> Long.compare(p2.timestamp, p1.timestamp));

        for (Payment payment : paymentList) {
            View view = LayoutInflater.from(layoutPaymentsList.getContext()).inflate(R.layout.item_payment, layoutPaymentsList, false);
            TextView tvDate = view.findViewById(R.id.tv_payment_date);
            TextView tvTime = view.findViewById(R.id.tv_payment_time);
            TextView tvAmount = view.findViewById(R.id.tv_payment_amount);
            View btnDelete = view.findViewById(R.id.btn_delete_payment);

            tvDate.setText(dateFormat.format(payment.timestamp));
            tvTime.setText(timeFormat.format(payment.timestamp));
            tvAmount.setText(getString(R.string.wage_format, formatAmount(payment.amount)));

            btnDelete.setOnClickListener(v -> deletePayment(payment));

            layoutPaymentsList.addView(view);
        }
    }

    private void deletePayment(Payment payment) {
        if (isMonthLocked) {
            Toast.makeText(this, "Month is locked. Cannot delete payments.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete Payment")
                .setMessage("Are you sure you want to delete this payment of ₹" + formatAmount(payment.amount) + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (mPaymentsRef != null && payment.id != null) {
                        mPaymentsRef.child(payment.id).removeValue()
                                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Payment deleted", Toast.LENGTH_SHORT).show());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
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
                        tvWage.setText(getString(R.string.wage_format, formatAmount(dailyWage)));
                        // Set it as base wage for the labour
                        mLabourRef.child("baseWage").setValue(lastWage);
                        updateSummary();
                        return;
                    }
                }
                dailyWage = 0;
                tvWage.setText(getString(R.string.wage_format, formatAmount(dailyWage)));
                updateSummary();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                dailyWage = 0;
                tvWage.setText(getString(R.string.wage_format, formatAmount(dailyWage)));
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

    private void updateLastLocked() {
        if (mAttendanceRef != null) {
            mAttendanceRef.child("lastLocked").setValue(System.currentTimeMillis());
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

        Calendar today = Calendar.getInstance();
        boolean isCurrentMonth = (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                                 cal.get(Calendar.MONTH) == today.get(Calendar.MONTH));

        for (int i = 0; i < firstDayOfWeek; i++) {
            days.add(new CalendarAdapter.CalendarDay(0, ""));
        }

        for (int i = 1; i <= daysInMonth; i++) {
            boolean isToday = isCurrentMonth && (i == today.get(Calendar.DAY_OF_MONTH));
            days.add(new CalendarAdapter.CalendarDay(i, "Default", isToday));
        }

        adapter = new CalendarAdapter(calendarGrid.getContext(), days);
        calendarGrid.setAdapter(adapter);

        calendarGrid.setOnItemClickListener((parent, view, position, id) -> {
            if (isMonthLocked) {
                Toast.makeText(this, "This month is locked and cannot be edited.", Toast.LENGTH_SHORT).show();
                return;
            }
            CalendarAdapter.CalendarDay day = days.get(position);
            if (day.dayNumber != 0) {
                showStatusDialog(day);
            }
        });
    }

    private void toggleMonthLock() {
        if (isMonthLocked) {
            new AlertDialog.Builder(this)
                    .setTitle("Unlock Month")
                    .setMessage("Are you sure you want to unlock this month for editing?")
                    .setPositiveButton("Unlock", (d, w) -> {
                        if (mAttendanceRef != null) {
                            mAttendanceRef.child("isLocked").setValue(false);
                            mAttendanceRef.child("lastLocked").removeValue();
                            Toast.makeText(this, "Month unlocked", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Lock Month")
                    .setMessage("Are you sure you want to LOCK this month? Further edits will be disabled until unlocked.")
                    .setPositiveButton("Lock", (d, w) -> {
                        if (mAttendanceRef != null) {
                            mAttendanceRef.child("isLocked").setValue(true);
                            updateLastLocked();
                            Toast.makeText(this, "Month locked", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void showStatusDialog(CalendarAdapter.CalendarDay day) {
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
        
        currentFullCount = fullCount;
        currentHalfCount = halfCount;
        currentAbsentCount = absentCount;
        
        totalEarnings = totalWorkUnits * dailyWage;
        currentGrossTotal = totalEarnings + previousMonthDue;
        currentBalance = currentGrossTotal - paidAmountForMonth;

        tvTotalAmount.setText(getString(R.string.wage_format, formatAmount(currentGrossTotal)));
        tvPaidAmount.setText(getString(R.string.wage_format, formatAmount(paidAmountForMonth)));

        if (currentBalance > 0) {
            tvDueAmount.setText(getString(R.string.wage_format, formatAmount(currentBalance)));
            layoutDueAmount.setVisibility(View.VISIBLE);
            layoutAdvanceAmount.setVisibility(View.GONE);
        } else if (currentBalance < 0) {
            tvAdvanceAmount.setText(getString(R.string.wage_format, formatAmount(Math.abs(currentBalance))));
            layoutDueAmount.setVisibility(View.GONE);
            layoutAdvanceAmount.setVisibility(View.VISIBLE);
        } else {
            tvDueAmount.setText(getString(R.string.wage_format, "0"));
            layoutDueAmount.setVisibility(View.VISIBLE);
            layoutAdvanceAmount.setVisibility(View.GONE);
        }

        if (paidAmountForMonth > 0) {
            layoutPaidAmount.setVisibility(View.VISIBLE);
            dividerPaid.setVisibility(View.VISIBLE);
        } else {
            layoutPaidAmount.setVisibility(View.GONE);
            dividerPaid.setVisibility(View.GONE);
        }

        if (currentBalance > 0) {
            layoutDueAmount.setVisibility(View.VISIBLE);
        } else {
            layoutDueAmount.setVisibility(View.GONE);
        }
        fabSetPaid.setVisibility(isMonthLocked ? View.GONE : View.VISIBLE);
        
        if (fabLock instanceof com.google.android.material.floatingactionbutton.FloatingActionButton) {
            com.google.android.material.floatingactionbutton.FloatingActionButton fab = (com.google.android.material.floatingactionbutton.FloatingActionButton) fabLock;
            if (isMonthLocked) {
                fab.setImageResource(android.R.drawable.ic_partial_secure);
                fab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#E8F5E9")));
                fab.setImageTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#2E7D32")));
            } else {
                fab.setImageResource(android.R.drawable.ic_lock_idle_lock);
                fab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FFEBEE")));
                fab.setImageTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#C62828")));
            }
        }

        // Update month display to show lock status
        TextView tvMonthYear = findViewById(R.id.tv_month_year);
        if (tvMonthYear != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
            String monthName = sdf.format(currentCalendar.getTime());
            if (isMonthLocked) {
                tvMonthYear.setText(monthName + " 🔒");
            } else {
                tvMonthYear.setText(monthName);
            }
        }

        if (paidAmountForMonth > 0 || (previousMonthDue < 0 && Math.abs(previousMonthDue) > 0.01)) {
            dividerTotal.setVisibility(View.VISIBLE);
        } else {
            dividerTotal.setVisibility(View.GONE);
        }
    }

    private void showSetPaidDialog() {
        if (isMonthLocked) {
            Toast.makeText(this, "Month is locked. Cannot add payments.", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Payment");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Enter amount");
        builder.setView(input);

        builder.setPositiveButton("Add", (dialog, which) -> {
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
        if (mPaymentsRef != null) {
            String id = mPaymentsRef.push().getKey();
            if (id != null) {
                Payment payment = new Payment(id, amount, System.currentTimeMillis());
                mPaymentsRef.child(id).setValue(payment)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(this, "Payment added successfully", Toast.LENGTH_SHORT).show();
                        });
            }
        }
    }

    private void fetchPreviousMonthDue() {
        mBaseAttendanceRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allAttendanceSnapshot = snapshot;
                calculateAndDisplayPreviousDue();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void calculateAndDisplayPreviousDue() {
        if (allAttendanceSnapshot == null) {
            updateSummary();
            return;
        }

        double cumulativeBalance = 0;
        if (currentLabour != null) {
            cumulativeBalance -= currentLabour.initialAdvance;
        }

        List<String> monthKeys = new ArrayList<>();
        for (DataSnapshot monthSnap : allAttendanceSnapshot.getChildren()) {
            String key = monthSnap.getKey();
            if (key != null && key.contains("_")) {
                monthKeys.add(key);
            }
        }

        monthKeys.sort((k1, k2) -> {
            String[] parts1 = k1.split("_");
            String[] parts2 = k2.split("_");
            try {
                int y1 = Integer.parseInt(parts1[0]);
                int m1 = Integer.parseInt(parts1[1]);
                int y2 = Integer.parseInt(parts2[0]);
                int m2 = Integer.parseInt(parts2[1]);
                if (y1 != y2) return Integer.compare(y1, y2);
                return Integer.compare(m1, m2);
            } catch (Exception e) {
                return 0;
            }
        });

        String currentMonthKey = currentCalendar.get(Calendar.YEAR) + "_" + (currentCalendar.get(Calendar.MONTH) + 1);

        for (String key : monthKeys) {
            if (key.equals(currentMonthKey)) break;

            DataSnapshot monthSnap = allAttendanceSnapshot.child(key);
            double monthWorkUnits = 0;
            double monthWage = 0;
            double monthPaid = 0;

            // Try to get monthly wage, fallback to base wage
            Object wageObj = monthSnap.child("monthlyWage").getValue();
            if (wageObj instanceof Number) {
                monthWage = ((Number) wageObj).doubleValue();
            } else if (currentLabour != null) {
                monthWage = currentLabour.baseWage;
            }

            // Get paid amount (legacy + payments list)
            Object paidObj = monthSnap.child("paidAmount").getValue();
            if (paidObj instanceof Number) {
                monthPaid += ((Number) paidObj).doubleValue();
            }
            
            DataSnapshot paymentsSnap = monthSnap.child("payments");
            for (DataSnapshot pSnap : paymentsSnap.getChildren()) {
                Payment p = pSnap.getValue(Payment.class);
                if (p != null) monthPaid += p.amount;
            }

            // Calculate work units
            for (DataSnapshot daySnap : monthSnap.getChildren()) {
                String dKey = daySnap.getKey();
                if (dKey != null && !dKey.equals("monthlyWage") && !dKey.equals("paidAmount") && !dKey.equals("payments")) {
                    Object status = daySnap.getValue();
                    if (status instanceof String) {
                        monthWorkUnits += calculateWorkUnits((String) status);
                    }
                }
            }

            cumulativeBalance += (monthWorkUnits * monthWage) - monthPaid;
        }

        previousMonthDue = cumulativeBalance;

        if (previousMonthDue > 0) {
            tvPreviousDue.setText(getString(R.string.wage_format, formatAmount(previousMonthDue)));
            tvPreviousDue.setTextColor(Color.parseColor("#C62828")); // Red for due
            tvPrevDueLabel.setText("Prev. Due");
            layoutPreviousDue.setVisibility(View.VISIBLE);
            dividerPrevDue.setVisibility(View.VISIBLE);
        } else if (previousMonthDue < 0) {
            tvPreviousDue.setText(getString(R.string.wage_format, formatAmount(Math.abs(previousMonthDue))));
            tvPreviousDue.setTextColor(Color.parseColor("#2E7D32")); // Green for advance
            tvPrevDueLabel.setText("Prev. Advance");
            layoutPreviousDue.setVisibility(View.VISIBLE);
            dividerPrevDue.setVisibility(View.VISIBLE);
        } else {
            layoutPreviousDue.setVisibility(View.GONE);
            dividerPrevDue.setVisibility(View.GONE);
        }

        updateSummary();
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
            return String.format(Locale.getDefault(), "%d", (long) amount);
        } else {
            return String.valueOf(amount);
        }
    }

    private void loadCompanyHeader() {
        android.content.SharedPreferences prefs = getSharedPreferences("CompanyPrefs_" + nodeKey, MODE_PRIVATE);
        boolean showCompany = prefs.getBoolean("show_company_header", true);
        
        String name = prefs.getString("company_name", "");
        String address = prefs.getString("company_address", "");
        String phone1 = prefs.getString("company_phone", "");
        String phone2 = prefs.getString("company_phone_2", "");

        if (!name.isEmpty() && showCompany) {
            tvHeaderCompanyName.setText(name);
            tvHeaderCompanyAddress.setText(address);
            
            String phoneText = "";
            if (!phone1.isEmpty()) {
                phoneText = "Phone: " + phone1;
                if (!phone2.isEmpty()) {
                    phoneText += " / " + phone2;
                }
            }
            tvHeaderCompanyPhones.setText(phoneText);
            
            layoutCompanyHeader.setVisibility(View.VISIBLE);
            dividerCompanyHeader.setVisibility(View.VISIBLE);
            tvHeaderCompanyAddress.setVisibility(address.isEmpty() ? View.GONE : View.VISIBLE);
            tvHeaderCompanyPhones.setVisibility(phoneText.isEmpty() ? View.GONE : View.VISIBLE);
        } else {
            layoutCompanyHeader.setVisibility(View.GONE);
            dividerCompanyHeader.setVisibility(View.GONE);
        }
    }

    private void loadSignature() {
        android.content.SharedPreferences prefs = getSharedPreferences("CompanyPrefs_" + nodeKey, MODE_PRIVATE);
        String signatureBase64 = prefs.getString("company_signature", null);
        boolean showSignature = prefs.getBoolean("show_signature", true);
        
        if (signatureBase64 != null && !signatureBase64.isEmpty() && showSignature) {
            try {
                byte[] decodedString = Base64.decode(signatureBase64, Base64.DEFAULT);
                Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                ivDetailSignature.setImageBitmap(bitmap);
                layoutSignatureExport.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                layoutSignatureExport.setVisibility(View.GONE);
            }
        } else {
            layoutSignatureExport.setVisibility(View.GONE);
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

    private Bitmap generateQRCode(String text, int size) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H);
            
            BitMatrix bitMatrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            
            int[] rect = bitMatrix.getEnclosingRectangle();
            if (rect == null) return null;
            int qrLeft = rect[0];
            int qrTop = rect[1];
            int qrWidth = rect[2];
            int qrHeight = rect[3];

            // Finder pattern is 7 modules wide. Find pixel width of 7 modules.
            int pixelWidth7Modules = 0;
            while (pixelWidth7Modules < qrWidth && bitMatrix.get(qrLeft + pixelWidth7Modules, qrTop)) {
                pixelWidth7Modules++;
            }
            float mSize = (float) pixelWidth7Modules / 7.0f;
            float eyeSize = 7 * mSize;

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);

            Bitmap logo = BitmapFactory.decodeResource(getResources(), R.drawable.logo);

            // Draw QR dots, skipping the finder pattern areas
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (bitMatrix.get(x, y)) {
                        boolean isEye = (x >= qrLeft && x < qrLeft + eyeSize && y >= qrTop && y < qrTop + eyeSize) ||
                                (x >= qrLeft + qrWidth - eyeSize && x < qrLeft + qrWidth && y >= qrTop && y < qrTop + eyeSize) ||
                                (x >= qrLeft && x < qrLeft + eyeSize && y >= qrTop + qrHeight - eyeSize && y < qrTop + qrHeight);
                        
                        if (!isEye) {
                            bitmap.setPixel(x, y, Color.BLACK);
                        }
                    }
                }
            }

            // Draw Styled Eyes (Finder Patterns)
            drawStyledEye(canvas, qrLeft, qrTop, mSize); // Top-left
            drawStyledEye(canvas, qrLeft + qrWidth - eyeSize, qrTop, mSize); // Top-right
            drawStyledEye(canvas, qrLeft, qrTop + qrHeight - eyeSize, mSize); // Bottom-left

            // Draw Logo in the center
            if (logo != null) {
                int logoSize = (int) (size * 0.22); // Solid logo size
                Bitmap scaledLogo = Bitmap.createScaledBitmap(logo, logoSize, logoSize, true);
                
                float centerX = width / 2f;
                float centerY = height / 2f;
                
                // Draw white background for logo to make it clear
                Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                whitePaint.setColor(Color.WHITE);
                float bgSize = logoSize + (mSize * 2);
                canvas.drawRoundRect(new RectF(centerX - bgSize/2f, centerY - bgSize/2f, centerX + bgSize/2f, centerY + bgSize/2f), mSize * 2, mSize * 2, whitePaint);
                
                canvas.drawBitmap(scaledLogo, centerX - logoSize / 2f, centerY - logoSize / 2f, null);
            }

            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    private void drawStyledEye(Canvas canvas, float x, float y, float mSize) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float eyeSize = 7 * mSize;

        // Outer square
        paint.setColor(Color.BLACK);
        canvas.drawRoundRect(new RectF(x, y, x + eyeSize, y + eyeSize), mSize * 2, mSize * 2, paint);

        // White inner square
        paint.setColor(Color.WHITE);
        float innerOffset = mSize;
        float innerSize = 5 * mSize;
        canvas.drawRoundRect(new RectF(x + innerOffset, y + innerOffset, x + innerOffset + innerSize, y + innerOffset + innerSize), mSize * 1.5f, mSize * 1.5f, paint);

        // Center pupil
        paint.setColor(Color.BLACK);
        float pupilOffset = 2 * mSize;
        float pupilSize = 3 * mSize;
        canvas.drawRoundRect(new RectF(x + pupilOffset, y + pupilOffset, x + pupilOffset + pupilSize, y + pupilOffset + pupilSize), mSize, mSize, paint);
    }
}
