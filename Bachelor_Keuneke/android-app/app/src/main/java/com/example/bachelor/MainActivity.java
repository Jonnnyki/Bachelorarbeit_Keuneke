package com.example.bachelor;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestStoragePermissions();
    }

    /**
     * Method to request network permission if not granted on installation.
     */
    private void requestStoragePermissions() {
        // Creating a string array with he permissions i want to request
        String[] permissions = {Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE};

        // Checking if the the permissions were granted, if not set boolean false and exit loop
        boolean permissionGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionGranted = false;
                break;
            }
        }

        // If any permission wasn't granted, request permissions for all permissions in the array
        if (!permissionGranted) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * Launches the labeling activity.
     *
     * @param v The button in the view that is used to launch the activity.
     */
    public void launchLabeling(View v) {
        Intent intent = new Intent(this, Labeling.class);
        startActivity(intent);
    }

    /**
     * Launches the learning activity.
     *
     * @param v The button in the view that is used to launch the activity.
     */
    public void launchAnalyzing(View v) {
        //if permissons granted, launch activity.
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            showPermissionDialog();
            return;
        }

        Intent intent = new Intent(this, AnalyzingLabeling.class);
        startActivity(intent);
    }

    /**
     * Shows an alertdialog to inform the user about the required permissions.
     * Requests permissions when confirming.
     */
    private void showPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permission required")
                .setMessage("Permissions are needed" +
                        " for the basic function of the app.")
                .setPositiveButton("OK", (dialog, which) -> requestStoragePermissions())
                .create()
                .show();
    }
}