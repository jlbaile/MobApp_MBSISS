package com.example.mbsiss;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.mbsiss.databinding.ActivityMainBinding;

/**
 * MainActivity — updated with session guard.
 *
 * CHANGE from original:
 *   Added SessionManager check at the top of onCreate().
 *   If no session exists (not logged in), redirect to LoginActivity immediately.
 *   Everything else is unchanged.
 */
public class MainActivity extends AppCompatActivity {

    ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── Session guard ──────────────────────────────────────────────────
        // If the user is not logged in, send them to LoginActivity.
        // This also handles the case where the OS restores MainActivity
        // from the back stack after the session was cleared.
        SessionManager session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        // ── End session guard ──────────────────────────────────────────────

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Default fragment on launch
        replaceFragment(new HomeFragment());

        binding.bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.home) {
                replaceFragment(new HomeFragment());
            } else if (itemId == R.id.camera) {
                replaceFragment(new CameraFragment());
            } else if (itemId == R.id.violation) {
                replaceFragment(new ViolationFragment());
            } else if (itemId == R.id.setting) {
                replaceFragment(new SettingFragment());
            }
            return true;
        });
    }

    private void replaceFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, fragment);
        fragmentTransaction.commit();
    }
}