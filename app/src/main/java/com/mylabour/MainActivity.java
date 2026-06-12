package com.mylabour;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
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

        Toolbar toolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);

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

        fetchLabours();

        FloatingActionButton fab = findViewById(R.id.fab_add_labour);
        fab.setOnClickListener(view -> showAddLabourDialog());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        
        // Setup Search
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        if (searchView != null) {
            // Tint internal search icon and other elements
            int colorOnSurface = com.google.android.material.color.MaterialColors.getColor(searchView, com.google.android.material.R.attr.colorOnSurface);
            
            ImageView searchIcon = searchView.findViewById(androidx.appcompat.R.id.search_mag_icon);
            if (searchIcon != null) searchIcon.setColorFilter(colorOnSurface);
            
            ImageView closeIcon = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
            if (closeIcon != null) closeIcon.setColorFilter(colorOnSurface);

            android.widget.TextView searchText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
            if (searchText != null) {
                searchText.setTextColor(colorOnSurface);
                searchText.setHintTextColor(colorOnSurface & 0x80FFFFFF); // 50% opacity for hint
            }

            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    if (adapter != null) {
                        adapter.getFilter().filter(newText);
                    }
                    return false;
                }
            });
        }

        // Setup Profile
        MenuItem profileItem = menu.findItem(R.id.action_profile);
        View profileView = profileItem.getActionView();
        if (profileView != null) {
            ImageView ivProfile = profileView.findViewById(R.id.iv_user_profile_menu);
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            
            if (user != null && ivProfile != null) {
                if (user.getPhotoUrl() != null) {
                    ivProfile.setPadding(0, 0, 0, 0);
                    Glide.with(this)
                            .load(user.getPhotoUrl())
                            .circleCrop()
                            .into(ivProfile);
                } else {
                    int padding = (int) (6 * getResources().getDisplayMetrics().density);
                    ivProfile.setPadding(padding, padding, padding, padding);
                }
            }

            profileView.setOnClickListener(v -> showUserProfile(user));
        }

        return true;
    }

    private void showUserProfile(FirebaseUser user) {
        if (user == null) return;
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Profile Settings");
        
        String message = "Name: " + user.getDisplayName() + "\nEmail: " + user.getEmail();
        builder.setMessage(message);

        builder.setPositiveButton("Logout", (dialog, which) -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        builder.setNegativeButton("Close", null);
        builder.show();
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
                adapter.updateList(new ArrayList<>(labourList));
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

        builder.setPositiveButton("Add", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String number = etNumber.getText().toString().trim();
            String address = etAddress.getText().toString().trim();

            if (!name.isEmpty()) {
                addLabourToFirebase(name, email, number, address);
            } else {
                Toast.makeText(MainActivity.this, "Please enter a name", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void addLabourToFirebase(String name, String email, String number, String address) {
        String id = mDatabase.push().getKey();
        Labour labour = new Labour(id, name, email, number, address);

        if (id != null) {
            mDatabase.child(id).setValue(labour)
                    .addOnSuccessListener(aVoid -> Toast.makeText(MainActivity.this, "Labour added successfully", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Failed to add labour", Toast.LENGTH_SHORT).show());
        }
    }
}
