package com.mylabour;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SearchView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private DatabaseReference mDatabase;
    private RecyclerView recyclerView;
    private LabourAdapter adapter;
    private List<Labour> labourList;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private String nodeKey;
    private ActivityResultLauncher<String> signaturePickerLauncher;
    private String currentSignatureBase64;
    private String currentProfilePhotoBase64;
    private com.google.android.material.imageview.ShapeableImageView ivDialogSignature;
    private com.google.android.material.imageview.ShapeableImageView ivEditProfile;
    private View btnRemoveSignature;
    private ActivityResultLauncher<String> profilePhotoPickerLauncher;
    private static final String UPDATE_CHANNEL_ID = "app_update_channel";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createUpdateNotificationChannel();

        signaturePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
                    Bitmap resized = resizeBitmap(bitmap, 400);
                    currentSignatureBase64 = encodeToBase64(resized);
                    if (ivDialogSignature != null) {
                        ivDialogSignature.setImageBitmap(resized);
                        ivDialogSignature.setVisibility(View.VISIBLE);
                    }
                    if (btnRemoveSignature != null) {
                        btnRemoveSignature.setVisibility(View.VISIBLE);
                    }
                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Error picking signature", e);
                }
            }
        });

        profilePhotoPickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
                    Bitmap resized = resizeBitmap(bitmap, 500);
                    currentProfilePhotoBase64 = encodeToBase64(resized);
                    if (ivEditProfile != null) {
                        ivEditProfile.setImageBitmap(resized);
                    }
                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Error picking profile photo", e);
                }
            }
        });

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        String userEmail = currentUser.getEmail();
        nodeKey = (userEmail != null) ? userEmail.replace(".", ",") : currentUser.getUid();
        mDatabase = FirebaseDatabase.getInstance().getReference("labours").child(nodeKey);

        recyclerView = findViewById(R.id.recycler_view);
        progressBar = findViewById(R.id.progress_bar);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);

        swipeRefreshLayout.setOnRefreshListener(this::fetchLabours);
        swipeRefreshLayout.setColorSchemeResources(R.color.purple_500);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        labourList = new ArrayList<>();
        adapter = new LabourAdapter(labourList, nodeKey, labour -> {
            Intent intent = new Intent(MainActivity.this, LabourDetailActivity.class);
            intent.putExtra("labour", labour);
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);

        SearchView searchView = findViewById(R.id.search_view);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.filter(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.filter(newText);
                return false;
            }
        });

        findViewById(R.id.iv_profile).setOnClickListener(v -> showProfileDialog(currentUser));

        android.content.SharedPreferences userPrefs = getSharedPreferences("UserProfile_" + nodeKey, MODE_PRIVATE);
        String customPhotoBase64 = userPrefs.getString("user_photo", null);
        com.google.android.material.imageview.ShapeableImageView ivProfileToolbar = findViewById(R.id.iv_profile);

        if (customPhotoBase64 != null) {
            byte[] decodedString = Base64.decode(customPhotoBase64, Base64.DEFAULT);
            Bitmap decodedByte = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            ivProfileToolbar.setImageBitmap(decodedByte);
        } else if (currentUser.getPhotoUrl() != null) {
            Glide.with(this)
                    .load(currentUser.getPhotoUrl())
                    .placeholder(R.drawable.ic_person)
                    .into(ivProfileToolbar);
        }

        fetchLabours();

        FloatingActionButton fab = findViewById(R.id.fab_add_labour);
        fab.setOnClickListener(view -> showAddLabourDialog());

        requestRequiredPermissions();
        checkForUpdates();
    }

    private void requestRequiredPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(android.Manifest.permission.CAMERA);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), 100);
        }
    }

    private void checkForUpdates() {
        new Thread(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/Hamraj37/My-Labour/releases/latest");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
                connection.setRequestProperty("User-Agent", "My-Labour-App");
                connection.connect();

                if (connection.getResponseCode() == 200) {
                    InputStream responseBody = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody));
                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }

                    JSONObject jsonObject = new JSONObject(result.toString());
                    String latestVersion = jsonObject.getString("tag_name"); // e.g., "v1.0.0"
                    String releaseNotes = jsonObject.optString("body", "New version available.");
                    
                    // Format release notes to show clearly in dialog
                    // Usually GitHub release notes contain markdown like "* Commit message"
                    // We'll clean it up slightly if needed
                    final String formattedNotes = releaseNotes.replace("### Changes in this release", "").trim();

                    String downloadUrl = null;
                    JSONArray assets = jsonObject.getJSONArray("assets");
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        if (asset.getString("name").endsWith(".apk")) {
                            downloadUrl = asset.getString("browser_download_url");
                            break;
                        }
                    }

                    if (downloadUrl == null) {
                        downloadUrl = jsonObject.getString("html_url");
                    }

                    final String finalDownloadUrl = downloadUrl;

                    // Clean the version strings (removing 'v' if present) for comparison
                    String latestClean = latestVersion.replace("v", "");
                    String currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;

                    if (currentVersion != null) {
                        String currentClean = currentVersion.replace("v", "").trim();
                        if (!latestClean.equals(currentClean)) {
                            runOnUiThread(() -> {
                                showUpdateDialog(latestVersion, finalDownloadUrl, formattedNotes);
                                showUpdateNotification(latestVersion);
                            });
                        }
                    }
                } else {
                    android.util.Log.e("UpdateCheck", "Server returned: " + connection.getResponseCode());
                }
            } catch (Exception e) {
                android.util.Log.e("UpdateCheck", "Error checking for updates", e);
            }
        }).start();
    }

    private void showUpdateDialog(String version, String url, String notes) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_update, null);
        builder.setView(dialogView);

        TextView tvVersion = dialogView.findViewById(R.id.tv_update_version);
        TextView tvNotes = dialogView.findViewById(R.id.tv_update_notes);
        View layoutRecentChanges = dialogView.findViewById(R.id.layout_recent_changes);
        View layoutProgress = dialogView.findViewById(R.id.layout_progress);
        ProgressBar progressBar = dialogView.findViewById(R.id.progress_bar_update);
        TextView tvProgressPercent = dialogView.findViewById(R.id.tv_progress_percent);
        View btnUpdateNow = dialogView.findViewById(R.id.btn_update_now);
        View tvUpdateLater = dialogView.findViewById(R.id.tv_update_later);

        tvVersion.setText(getString(R.string.version_format, version));
        tvNotes.setText(notes);

        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        
        // Make background transparent for rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        dialog.show();

        tvUpdateLater.setOnClickListener(v -> dialog.dismiss());

        btnUpdateNow.setOnClickListener(v -> {
            if (url.endsWith(".apk")) {
                layoutRecentChanges.setVisibility(View.GONE);
                layoutProgress.setVisibility(View.VISIBLE);
                btnUpdateNow.setEnabled(false);

                new UpdateManager(this).downloadAndInstall(url, new UpdateManager.OnProgressListener() {
                    @Override
                    public void onProgress(int progress) {
                        progressBar.setProgress(progress);
                        tvProgressPercent.setText(getString(R.string.percent_format, progress));
                    }

                    @Override
                    public void onComplete() {
                        dialog.dismiss();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                        layoutRecentChanges.setVisibility(View.VISIBLE);
                        layoutProgress.setVisibility(View.GONE);
                        btnUpdateNow.setEnabled(true);
                    }
                });
            } else {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
                dialog.dismiss();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void fetchLabours() {
        if (!swipeRefreshLayout.isRefreshing()) {
            progressBar.setVisibility(View.VISIBLE);
        }
        mDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                labourList.clear();
                for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                    Labour labour = postSnapshot.getValue(Labour.class);
                    labourList.add(labour);
                }
                adapter.setLabourList(labourList);
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(MainActivity.this, R.string.failed_load_data, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddLabourDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_labour, null);
        builder.setView(dialogView);

        EditText etName = dialogView.findViewById(R.id.et_name);
        EditText etEmail = dialogView.findViewById(R.id.et_email);
        EditText etNumber = dialogView.findViewById(R.id.et_number);
        EditText etAddress = dialogView.findViewById(R.id.et_address);
        EditText etInitialAdvance = dialogView.findViewById(R.id.et_initial_advance);
        View btnSave = dialogView.findViewById(R.id.btn_save_labour);
        View tvCancel = dialogView.findViewById(R.id.tv_cancel_labour);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String number = etNumber.getText().toString().trim();
            String address = etAddress.getText().toString().trim();
            String advanceStr = etInitialAdvance.getText().toString().trim();
            double initialAdvance = advanceStr.isEmpty() ? 0 : Double.parseDouble(advanceStr);

            if (!name.isEmpty()) {
                addLabourToFirebase(name, email, number, address, initialAdvance);
                dialog.dismiss();
            } else {
                Toast.makeText(MainActivity.this, R.string.please_enter_name, Toast.LENGTH_SHORT).show();
            }
        });

        tvCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void addLabourToFirebase(String name, String email, String number, String address, double initialAdvance) {
        String id = mDatabase.push().getKey();
        Labour labour = new Labour(id, name, email, number, address);
        labour.initialAdvance = initialAdvance;

        if (id != null) {
            mDatabase.child(id).setValue(labour)
                    .addOnSuccessListener(aVoid -> Toast.makeText(MainActivity.this, R.string.labour_added_success, Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Toast.makeText(MainActivity.this, R.string.labour_added_failure, Toast.LENGTH_SHORT).show());
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
        image.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] b = baos.toByteArray();
        return Base64.encodeToString(b, Base64.DEFAULT);
    }

    private void showProfileDialog(FirebaseUser user) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_profile, null);
        builder.setView(dialogView);

        com.google.android.material.imageview.ShapeableImageView ivProfile = dialogView.findViewById(R.id.iv_dialog_profile);
        TextView tvName = dialogView.findViewById(R.id.tv_dialog_name);
        TextView tvEmail = dialogView.findViewById(R.id.tv_dialog_email);
        TextView tvCompanyName = dialogView.findViewById(R.id.tv_company_name);
        TextView tvCompanyAddress = dialogView.findViewById(R.id.tv_company_address);
        TextView tvCompanyPhones = dialogView.findViewById(R.id.tv_company_phones);
        com.google.android.material.button.MaterialButton btnManageCompany = dialogView.findViewById(R.id.btn_manage_company);
        com.google.android.material.button.MaterialButton btnEditProfile = dialogView.findViewById(R.id.btn_edit_profile);
        com.google.android.material.button.MaterialButton btnAbout = dialogView.findViewById(R.id.btn_dialog_about);
        com.google.android.material.button.MaterialButton btnPrivacy = dialogView.findViewById(R.id.btn_dialog_privacy);
        View btnLogout = dialogView.findViewById(R.id.btn_dialog_logout);
        View tvClose = dialogView.findViewById(R.id.tv_dialog_close);
        TextView tvVersion = dialogView.findViewById(R.id.tv_dialog_version);

        android.content.SharedPreferences userPrefs = getSharedPreferences("UserProfile_" + nodeKey, MODE_PRIVATE);
        String customName = userPrefs.getString("user_name", user.getDisplayName());
        String customPhotoBase64 = userPrefs.getString("user_photo", null);

        tvName.setText(java.util.Objects.requireNonNullElse(customName, getString(R.string.default_user_name)));

        String email = user.getEmail();
        String phone = user.getPhoneNumber();
        tvEmail.setText(java.util.Objects.requireNonNullElseGet(email, () -> java.util.Objects.requireNonNullElse(phone, getString(R.string.default_user_name))));

        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            tvVersion.setText(getString(R.string.version_format, versionName));
        } catch (Exception e) {
            tvVersion.setVisibility(View.GONE);
        }

        if (customPhotoBase64 != null) {
            byte[] decodedString = Base64.decode(customPhotoBase64, Base64.DEFAULT);
            Bitmap decodedByte = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            ivProfile.setImageBitmap(decodedByte);
        } else if (user.getPhotoUrl() != null) {
            Glide.with(this)
                    .load(user.getPhotoUrl())
                    .placeholder(R.drawable.ic_person)
                    .into(ivProfile);
        }

        // Load Company Details from SharedPreferences
        android.content.SharedPreferences prefs = getSharedPreferences("CompanyPrefs_" + nodeKey, MODE_PRIVATE);
        String companyName = prefs.getString("company_name", "");
        String companyAddress = prefs.getString("company_address", "");
        String companyPhone = prefs.getString("company_phone", "");
        String companyPhone2 = prefs.getString("company_phone_2", "");

        if (!companyName.isEmpty()) {
            tvCompanyName.setText(companyName);
            tvCompanyName.setVisibility(View.VISIBLE);
            
            if (!companyAddress.isEmpty()) {
                tvCompanyAddress.setText(companyAddress);
                tvCompanyAddress.setVisibility(View.VISIBLE);
            } else {
                tvCompanyAddress.setVisibility(View.GONE);
            }

            StringBuilder phones = new StringBuilder();
            if (!companyPhone.isEmpty()) phones.append(companyPhone);
            if (!companyPhone2.isEmpty()) {
                if (phones.length() > 0) phones.append(" / ");
                phones.append(companyPhone2);
            }
            
            if (phones.length() > 0) {
                tvCompanyPhones.setText(phones.toString());
                tvCompanyPhones.setVisibility(View.VISIBLE);
            } else {
                tvCompanyPhones.setVisibility(View.GONE);
            }

            btnManageCompany.setText(R.string.edit_company_details);
        } else {
            tvCompanyName.setVisibility(View.GONE);
            tvCompanyAddress.setVisibility(View.GONE);
            tvCompanyPhones.setVisibility(View.GONE);
            btnManageCompany.setText(R.string.add_company_details);
        }

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnEditProfile.setOnClickListener(v -> {
            dialog.dismiss();
            showEditProfileDialog(user);
        });

        btnManageCompany.setOnClickListener(v -> {
            dialog.dismiss();
            showEditCompanyDialog();
        });

        btnAbout.setOnClickListener(v -> {
            dialog.dismiss();
            showAboutDialog();
        });

        btnPrivacy.setOnClickListener(v -> {
            dialog.dismiss();
            showPrivacyDialog();
        });

        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
            dialog.dismiss();
        });

        tvClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showEditCompanyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_company, null);
        builder.setView(dialogView);

        EditText etCompanyName = dialogView.findViewById(R.id.et_company_name);
        EditText etCompanyPhone = dialogView.findViewById(R.id.et_company_phone);
        EditText etCompanyPhone2 = dialogView.findViewById(R.id.et_company_phone_2);
        EditText etCompanyAddress = dialogView.findViewById(R.id.et_company_address);
        ivDialogSignature = dialogView.findViewById(R.id.iv_company_signature);
        View btnPickSignature = dialogView.findViewById(R.id.btn_pick_signature);
        btnRemoveSignature = dialogView.findViewById(R.id.btn_remove_signature);
        
        View btnSave = dialogView.findViewById(R.id.btn_save_company);
        View tvCancel = dialogView.findViewById(R.id.tv_cancel_company);
        
        android.content.SharedPreferences prefs = getSharedPreferences("CompanyPrefs_" + nodeKey, MODE_PRIVATE);
        etCompanyName.setText(prefs.getString("company_name", ""));
        etCompanyPhone.setText(prefs.getString("company_phone", ""));
        etCompanyPhone2.setText(prefs.getString("company_phone_2", ""));
        etCompanyAddress.setText(prefs.getString("company_address", ""));
        
        currentSignatureBase64 = prefs.getString("company_signature", null);
        if (currentSignatureBase64 != null && !currentSignatureBase64.isEmpty()) {
            byte[] decodedString = Base64.decode(currentSignatureBase64, Base64.DEFAULT);
            Bitmap decodedByte = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            ivDialogSignature.setImageBitmap(decodedByte);
            ivDialogSignature.setVisibility(View.VISIBLE);
            btnRemoveSignature.setVisibility(View.VISIBLE);
        }

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnPickSignature.setOnClickListener(v -> signaturePickerLauncher.launch("image/*"));
        
        btnRemoveSignature.setOnClickListener(v -> {
            currentSignatureBase64 = null;
            ivDialogSignature.setVisibility(View.GONE);
            btnRemoveSignature.setVisibility(View.GONE);
        });

        btnSave.setOnClickListener(v -> {
            String name = etCompanyName.getText().toString().trim();
            String phone = etCompanyPhone.getText().toString().trim();
            String phone2 = etCompanyPhone2.getText().toString().trim();
            String address = etCompanyAddress.getText().toString().trim();
            
            prefs.edit()
                .putString("company_name", name)
                .putString("company_phone", phone)
                .putString("company_phone_2", phone2)
                .putString("company_address", address)
                .putString("company_signature", currentSignatureBase64)
                .apply();
                
            Toast.makeText(this, "Company details saved locally", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        tvCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showAboutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_about, null);
        builder.setView(dialogView);

        TextView tvVersion = dialogView.findViewById(R.id.tv_about_version);
        View btnGithub = dialogView.findViewById(R.id.btn_github);
        View btnClose = dialogView.findViewById(R.id.btn_about_close);

        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            tvVersion.setText(getString(R.string.version_format, versionName));
        } catch (Exception e) {
            tvVersion.setVisibility(View.GONE);
        }

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnGithub.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Hamraj37/My-Labour"));
            startActivity(intent);
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showEditProfileDialog(FirebaseUser user) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_profile, null);
        builder.setView(dialogView);

        ivEditProfile = dialogView.findViewById(R.id.iv_edit_profile);
        com.google.android.material.floatingactionbutton.FloatingActionButton fabPickPhoto = dialogView.findViewById(R.id.fab_pick_photo);
        EditText etName = dialogView.findViewById(R.id.et_edit_name);
        View btnSave = dialogView.findViewById(R.id.btn_save_profile);
        View tvCancel = dialogView.findViewById(R.id.tv_cancel_edit_profile);

        android.content.SharedPreferences userPrefs = getSharedPreferences("UserProfile_" + nodeKey, MODE_PRIVATE);
        String currentName = userPrefs.getString("user_name", user.getDisplayName());
        currentProfilePhotoBase64 = userPrefs.getString("user_photo", null);

        etName.setText(currentName);
        if (currentProfilePhotoBase64 != null) {
            byte[] decodedString = Base64.decode(currentProfilePhotoBase64, Base64.DEFAULT);
            Bitmap decodedByte = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            ivEditProfile.setImageBitmap(decodedByte);
        } else if (user.getPhotoUrl() != null) {
            Glide.with(this)
                    .load(user.getPhotoUrl())
                    .placeholder(R.drawable.ic_person)
                    .into(ivEditProfile);
        }

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        fabPickPhoto.setOnClickListener(v -> profilePhotoPickerLauncher.launch("image/*"));

        btnSave.setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(this, R.string.name_cannot_empty, Toast.LENGTH_SHORT).show();
                return;
            }

            userPrefs.edit()
                    .putString("user_name", newName)
                    .putString("user_photo", currentProfilePhotoBase64)
                    .apply();

            // Also update the toolbar profile image if it exists in MainActivity
            if (currentProfilePhotoBase64 != null) {
                byte[] decodedString = Base64.decode(currentProfilePhotoBase64, Base64.DEFAULT);
                Bitmap decodedByte = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                ((com.google.android.material.imageview.ShapeableImageView) findViewById(R.id.iv_profile)).setImageBitmap(decodedByte);
            }

            Toast.makeText(this, R.string.profile_updated, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        tvCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showPrivacyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_privacy, null);
        builder.setView(dialogView);

        View btnMore = dialogView.findViewById(R.id.btn_privacy_more);
        View btnClose = dialogView.findViewById(R.id.btn_privacy_close);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnMore.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Hamraj37/My-Labour/blob/master/PRIVACY_POLICY.md"));
            startActivity(intent);
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void createUpdateNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    UPDATE_CHANNEL_ID,
                    "App Updates",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for new app versions");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void showUpdateNotification(String version) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, UPDATE_CHANNEL_ID)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(getString(R.string.new_update_available))
                .setContentText(getString(R.string.update_notification_content, version))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(2, builder.build());
    }
}
