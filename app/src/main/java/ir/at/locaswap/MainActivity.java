package ir.at.locaswap;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_CODE = 100;
    private FusedLocationProviderClient fusedLocationClient;
    private TextView tvLatitude, tvLongitude, tvSpeed, tvError;
    private Button btnProcess, btnShowMap, btnLanguage;
    private boolean isProcessRunning = false;
    private boolean isMapVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        requestLocationPermission();
        setupButtons();
    }

    private void initViews() {
        tvLatitude = findViewById(R.id.tv_latitude);
        tvLongitude = findViewById(R.id.tv_longitude);
        tvSpeed = findViewById(R.id.tv_speed);
        tvError = findViewById(R.id.tv_error);
        btnProcess = findViewById(R.id.btn_process);
        btnShowMap = findViewById(R.id.btn_show_map);
        btnLanguage = findViewById(R.id.btn_language);
    }

    private void setupButtons() {
        findViewById(R.id.btn_change_location).setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.change_location) + " (Use Mock Location)", Toast.LENGTH_SHORT).show());

        btnShowMap.setOnClickListener(v -> {
            isMapVisible = !isMapVisible;
            btnShowMap.setText(isMapVisible ? R.string.hide_map : R.string.show_map);
            Toast.makeText(this, isMapVisible ? "Map shown (use external app)" : "Map hidden", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_add_move).setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.add_move), Toast.LENGTH_SHORT).show());

        btnProcess.setOnClickListener(v -> {
            isProcessRunning = !isProcessRunning;
            btnProcess.setText(isProcessRunning ? R.string.stop_process : R.string.start_process);
            Toast.makeText(this, isProcessRunning ? "Process started" : "Process stopped", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_help).setOnClickListener(v -> showDialog(R.string.help, R.string.help_content));
        findViewById(R.id.btn_about).setOnClickListener(v -> showDialog(R.string.about, R.string.about_content));
        findViewById(R.id.btn_rules).setOnClickListener(v -> showDialog(R.string.rules, R.string.rules_content));

        btnLanguage.setOnClickListener(v -> toggleLanguage());
    }

    private void toggleLanguage() {
        Locale current = getResources().getConfiguration().locale;
        String newLang = current.getLanguage().equals("fa") ? "en" : "fa";
        Locale locale = new Locale(newLang);
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        recreate(); // بازسازی صفحه برای اعمال زبان
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_CODE);
        } else {
            getLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLocation();
            } else {
                showError(getString(R.string.error_location));
            }
        }
    }

    private void getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        tvLatitude.setText(String.format("%s: %.6f", getString(R.string.latitude), location.getLatitude()));
                        tvLongitude.setText(String.format("%s: %.6f", getString(R.string.longitude), location.getLongitude()));
                        tvSpeed.setText(String.format("%s: %.2f m/s", getString(R.string.speed), location.getSpeed()));
                        tvError.setVisibility(TextView.GONE);
                    } else {
                        showError(getString(R.string.error_location));
                    }
                })
                .addOnFailureListener(e -> showError("Error: " + e.getMessage()));
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(TextView.VISIBLE);
    }

    private void showDialog(int titleRes, int contentRes) {
        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setMessage(contentRes)
                .setPositiveButton(R.string.ok, null)
                .show();
    }
}