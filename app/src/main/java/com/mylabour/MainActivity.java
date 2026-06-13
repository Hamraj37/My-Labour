package com.mylabour;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SearchView;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        String userEmail = currentUser.getEmail();
        String nodeKey = (userEmail != null) ? userEmail.replace(".", ",") : currentUser.getUid();
        mDatabase = FirebaseDatabase.getInstance().getReference("labours").child(nodeKey);

        recyclerView = findViewById(R.id.recycler_view);
        progressBar = findViewById(R.id.progress_bar);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        labourList = new ArrayList<>();
        adapter = new LabourAdapter(labourList, labour -> {
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

        if (currentUser.getPhotoUrl() != null) {
            Glide.with(this)
                    .load(currentUser.getPhotoUrl())
                    .placeholder(R.drawable.ic_person)
                    .into((com.google.android.material.imageview.ShapeableImageView) findViewById(R.id.iv_profile));
        }

        fetchLabours();

        FloatingActionButton fab = findViewById(R.id.fab_add_labour);
        fab.setOnClickListener(view -> showAddLabourDialog());

        checkForUpdates();
    }

    private void checkForUpdates() {
        new Thread(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/Hamraj37/My-Labour/releases/latest");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
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
                        String currentClean = currentVersion.replace("v", "");
                        if (!latestClean.equals(currentClean)) {
                            runOnUiThread(() -> showUpdateDialog(latestVersion, finalDownloadUrl, releaseNotes));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
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
        progressBar.setVisibility(View.VISIBLE);
        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                labourList.clear();
                for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                    Labour labour = postSnapshot.getValue(Labour.class);
                    labourList.add(labour);
                }
                adapter.setLabourList(labourList);
                progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this, "Failed to load data.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddLabourDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_labour, null);
        builder.setView(dialogView);

        EditText etName = dialogView.findViewById(R.id.et_name);
        EditText etEmail = dialogView.findViewById(R.id.et_email);
        EditText etNumber = dialogView.findViewById(R.id.et_number);
        EditText etAddress = dialogView.findViewById(R.id.et_address);
        EditText etInitialAdvance = dialogView.findViewById(R.id.et_initial_advance);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String number = etNumber.getText().toString().trim();
            String address = etAddress.getText().toString().trim();
            String advanceStr = etInitialAdvance.getText().toString().trim();
            double initialAdvance = advanceStr.isEmpty() ? 0 : Double.parseDouble(advanceStr);

            if (!name.isEmpty()) {
                addLabourToFirebase(name, email, number, address, initialAdvance);
            } else {
                Toast.makeText(MainActivity.this, "Please enter a name", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void addLabourToFirebase(String name, String email, String number, String address, double initialAdvance) {
        String id = mDatabase.push().getKey();
        Labour labour = new Labour(id, name, email, number, address);
        labour.initialAdvance = initialAdvance;

        if (id != null) {
            mDatabase.child(id).setValue(labour)
                    .addOnSuccessListener(aVoid -> Toast.makeText(MainActivity.this, "Labour added successfully", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Failed to add labour", Toast.LENGTH_SHORT).show());
        }
    }

    private void showProfileDialog(FirebaseUser user) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_profile, null);
        builder.setView(dialogView);

        com.google.android.material.imageview.ShapeableImageView ivProfile = dialogView.findViewById(R.id.iv_dialog_profile);
        TextView tvName = dialogView.findViewById(R.id.tv_dialog_name);
        TextView tvEmail = dialogView.findViewById(R.id.tv_dialog_email);

        tvName.setText(user.getDisplayName() != null ? user.getDisplayName() : "User");
        tvEmail.setText(user.getEmail());

        if (user.getPhotoUrl() != null) {
            Glide.with(this)
                    .load(user.getPhotoUrl())
                    .placeholder(R.drawable.ic_person)
                    .into(ivProfile);
        }

        builder.setPositiveButton(R.string.logout, (dialog, which) -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
        builder.setNegativeButton(R.string.close, (dialog, which) -> dialog.dismiss());
        builder.show();
    }
}
