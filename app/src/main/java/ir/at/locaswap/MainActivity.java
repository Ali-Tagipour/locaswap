package ir.at.locaswap;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
    private Button btnProcess, btnShowMap, btnLanguage, btnAddLocation;
    private boolean isProcessRunning = false;
    private LocationManager locationManager;
    private SharedPreferences prefs;
    private double mockLat = 0;
    private double mockLng = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        initViews();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        requestLocationPermission();
        setupButtons();

        // بازگردانی مختصات ذخیره‌شده
        mockLat = Double.longBitsToDouble(prefs.getLong("mock_lat", 0));
        mockLng = Double.longBitsToDouble(prefs.getLong("mock_lng", 0));
    }

    private void initViews() {
        tvLatitude = findViewById(R.id.tv_latitude);
        tvLongitude = findViewById(R.id.tv_longitude);
        tvSpeed = findViewById(R.id.tv_speed);
        tvError = findViewById(R.id.tv_error);
        btnProcess = findViewById(R.id.btn_process);
        btnShowMap = findViewById(R.id.btn_show_map);
        btnLanguage = findViewById(R.id.btn_language);
        btnAddLocation = findViewById(R.id.btn_add_move);
    }

    private void setupButtons() {
        btnAddLocation.setOnClickListener(v -> showLocationDialog());

        btnProcess.setOnClickListener(v -> toggleProcess());

        btnLanguage.setOnClickListener(v -> toggleLanguage());
    }

    private void showLocationDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_location, null);
        EditText etLat = dialogView.findViewById(R.id.et_lat);
        EditText etLng = dialogView.findViewById(R.id.et_lng);

        // نمایش مختصات فعلی
        etLat.setText(String.valueOf(mockLat));
        etLng.setText(String.valueOf(mockLng));

        new AlertDialog.Builder(this)
                .setTitle("انتخاب مختصات")
                .setView(dialogView)
                .setPositiveButton("تأیید", (dialog, which) -> {
                    try {
                        mockLat = Double.parseDouble(etLat.getText().toString());
                        mockLng = Double.parseDouble(etLng.getText().toString());

                        // ذخیره با longBitsToDouble
                        prefs.edit()
                                .putLong("mock_lat", Double.doubleToRawLongBits(mockLat))
                                .putLong("mock_lng", Double.doubleToRawLongBits(mockLng))
                                .apply();

                        Toast.makeText(this, "مختصات ذخیره شد: " + mockLat + ", " + mockLng, Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "خطا در ورودی", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("لغو", null)
                .show();
    }

    private void toggleProcess() {
        isProcessRunning = !isProcessRunning;
        btnProcess.setText(isProcessRunning ? R.string.stop_process : R.string.start_process);

        if (isProcessRunning) {
            startMockLocation();
        } else {
            stopMockLocation();
        }
    }

    private void startMockLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            // استفاده از پارامترهای boolean به جای ProviderProperties
            locationManager.addTestProvider(
                    LocationManager.GPS_PROVIDER,
                    false,  // requiresNetwork
                    false,  // requiresSatellite
                    false,  // requiresCell
                    false,  // hasMonetaryCost
                    true,   // supportsAltitude
                    true,   // supportsSpeed
                    true,   // supportsBearing
                    1,      // powerRequirement (1 = LOW, 2 = MEDIUM, 3 = HIGH)
                    1       // accuracy (1 = FINE, 2 = COARSE)
            );

            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);

            Location mock = new Location(LocationManager.GPS_PROVIDER);
            mock.setLatitude(mockLat);
            mock.setLongitude(mockLng);
            mock.setAccuracy(5f);
            mock.setTime(System.currentTimeMillis());
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                mock.setElapsedRealtimeNanos(System.nanoTime());
            }

            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mock);
            Toast.makeText(this, "Mock Location فعال شد", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "لطفاً Mock Location را در Developer Options فعال کنید", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        }
    }

    private void stopMockLocation() {
        try {
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
            Toast.makeText(this, "Mock Location متوقف شد", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void toggleLanguage() {
        String currentLang = Locale.getDefault().getLanguage();
        String newLang = "fa".equals(currentLang) ? "en" : "fa";
        Locale locale = new Locale(newLang);
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        recreate();
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
        if (requestCode == LOCATION_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLocation();
        } else {
            showError(getString(R.string.error_location));
        }
    }

    private void getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        tvLatitude.setText(String.format(Locale.getDefault(), "%s: %.6f", getString(R.string.latitude), location.getLatitude()));
                        tvLongitude.setText(String.format(Locale.getDefault(), "%s: %.6f", getString(R.string.longitude), location.getLongitude()));
                        tvSpeed.setText(String.format(Locale.getDefault(), "%s: %.2f m/s", getString(R.string.speed), location.getSpeed()));
                        tvError.setVisibility(TextView.GONE);
                    } else {
                        showError(getString(R.string.loading));
                    }
                })
                .addOnFailureListener(e -> showError("Error: " + e.getMessage()));
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(TextView.VISIBLE);
    }
}