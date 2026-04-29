package com.example.mbsiss;

import android.os.Bundle;import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.example.mbsiss.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    // Fixed typo from ActivitmainBinding to ActivityMainBinding
    ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set the default fragment (Home) when the app starts
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

    // Helper method to swap fragments
    private void replaceFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, fragment);
        fragmentTransaction.commit();
    }
}