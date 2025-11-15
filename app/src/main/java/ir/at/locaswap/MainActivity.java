package ir.at.locaswap;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.util.*;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final int LOCATION_PERMISSION_CODE = 100;
    private FusedLocationProviderClient fusedLocationClient;
    private TextView tvCurrent, tvTarget, tvAccuracy, tvAltitude, tvSpeed, tvCurrentAddress, tvTargetAddress;
    private Button btnSaveLocation, btnMap, btnStart;
    private FloatingActionButton fabMenu;
    private ImageView radarView;
    private boolean isProcessRunning = false;
    private LocationManager locationManager;
    private SharedPreferences prefs;
    private double mockLat = 0, mockLng = 0;
    private double currentLat = 0, currentLng = 0;
    private SensorManager sensorManager;
    private Sensor pressureSensor;

    private final ActivityResultLauncher<Intent> mapLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    mockLat = result.getData().getDoubleExtra("lat", 0);
                    mockLng = result.getData().getDoubleExtra("lng", 0);
                    updateTargetLocation();
                    saveLocation(mockLat, mockLng);
                    getAddress(mockLat, mockLng, tvTargetAddress);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initServices();
        loadLastTarget();
        requestLocationPermission();
        setupButtons();
        startRadarAnimation();
        setupFabMenu();
    }

    private void initViews() {
        tvCurrent = findViewById(R.id.tv_current);
        tvTarget = findViewById(R.id.tv_target);
        tvAccuracy = findViewById(R.id.tv_accuracy);
        tvAltitude = findViewById(R.id.tv_altitude);
        tvSpeed = findViewById(R.id.tv_speed);
        tvCurrentAddress = findViewById(R.id.tv_current_address);
        tvTargetAddress = findViewById(R.id.tv_target_address);
        btnSaveLocation = findViewById(R.id.btn_save_location);
        btnMap = findViewById(R.id.btn_map);
        btnStart = findViewById(R.id.btn_start);
        fabMenu = findViewById(R.id.fab_menu);
        radarView = findViewById(R.id.radar_view);
    }

    private void initServices() {
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }

    private void setupButtons() {
        btnSaveLocation.setOnClickListener(v -> showPasteDialog());
        btnMap.setOnClickListener(v -> openMap());
        btnStart.setOnClickListener(v -> toggleMock());
    }

    private void setupFabMenu() {
        fabMenu.setOnClickListener(v -> showFabMenu());
    }

    private void showFabMenu() {
        PopupMenu popup = new PopupMenu(this, fabMenu);
        popup.getMenuInflater().inflate(R.menu.menu_main, popup.getMenu());
        popup.setOnMenuItemClickListener(this::onFabMenuItemClick);
        popup.show();
    }

    private boolean onFabMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_about) {
            startActivity(new Intent(this, AboutActivity.class));
        } else if (id == R.id.menu_policy) {
            startActivity(new Intent(this, RulesActivity.class));
        } else if (id == R.id.menu_help) {
            startActivity(new Intent(this, HelpActivity.class));
        }
        return true;
    }

    private void showPasteDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_paste, null);
        EditText etLat = view.findViewById(R.id.et_lat);
        EditText etLng = view.findViewById(R.id.et_lng);

        new AlertDialog.Builder(this)
                .setTitle("ذخیره لوکیشن")
                .setView(view)
                .setPositiveButton("ذخیره", (d, w) -> {
                    try {
                        mockLat = Double.parseDouble(etLat.getText().toString().trim());
                        mockLng = Double.parseDouble(etLng.getText().toString().trim());
                        if (mockLat != 0 && mockLng != 0) {
                            updateTargetLocation();
                            saveLocation(mockLat, mockLng);
                            getAddress(mockLat, mockLng, tvTargetAddress);
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "فرمت اشتباه! مثال: 35.6892, 51.3890", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("لغو", null)
                .show();
    }

    private void openMap() {
        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra("current_lat", currentLat);
        intent.putExtra("current_lng", currentLng);
        mapLauncher.launch(intent);
    }

    private void saveLocation(double lat, double lng) {
        String entry = String.format(Locale.US, "%.6f, %.6f", lat, lng);
        Set<String> set = new HashSet<>(prefs.getStringSet("saved", new HashSet<>()));
        set.add(entry);
        prefs.edit().putStringSet("saved", set).apply();
    }

    private void loadLastTarget() {
        mockLat = Double.longBitsToDouble(prefs.getLong("mock_lat", 0));
        mockLng = Double.longBitsToDouble(prefs.getLong("mock_lng", 0));
        if (mockLat != 0) updateTargetLocation();
    }

    private void updateTargetLocation() {
        tvTarget.setText(String.format(Locale.US, "هدف: %.6f, %.6f", mockLat, mockLng));
        prefs.edit()
                .putLong("mock_lat", Double.doubleToRawLongBits(mockLat))
                .putLong("mock_lng", Double.doubleToRawLongBits(mockLng))
                .apply();
    }

    private void toggleMock() {
        isProcessRunning = !isProcessRunning;
        btnStart.setText(isProcessRunning ? "توقف" : "شروع");
        if (isProcessRunning) startMockLocation();
        else stopMockLocation();
    }

    // حل کامل ارور ProviderProperties
    @RequiresApi(Build.VERSION_CODES.S)
    private void addTestProviderNew() {
        locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, false, false, false, true, true, true,
                android.location.provider.ProviderProperties.POWER_USAGE_LOW,
                android.location.provider.ProviderProperties.ACCURACY_FINE
        );
    }

    private void addTestProviderOld() {
        locationManager.addTestProvider(LocationManager.GPS_PROVIDER,
                false, false, false, false, true, true, true, 1, 1);
    }

    private void startMockLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "اجازه دسترسی به مکان را بدهید", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // حل ارور: فقط در Android 12+ از ProviderProperties استفاده کن
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                addTestProviderNew();
            } else {
                addTestProviderOld();
            }

            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);

            Location mock = new Location(LocationManager.GPS_PROVIDER);
            mock.setLatitude(mockLat);
            mock.setLongitude(mockLng);
            mock.setAccuracy(5f);
            mock.setTime(System.currentTimeMillis());
            if (Build.VERSION.SDK_INT >= 17) mock.setElapsedRealtimeNanos(System.nanoTime());

            new Thread(() -> {
                while (isProcessRunning) {
                    locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mock);
                    try { Thread.sleep(1000); } catch (Exception ignored) {}
                }
            }).start();

            Toast.makeText(this, "موقعیت جعلی فعال شد!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Developer Options → Mock Location را فعال کنید", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        }
    }

    private void stopMockLocation() {
        try { locationManager.removeTestProvider(LocationManager.GPS_PROVIDER); }
        catch (Exception ignored) {}
    }

    private void startRadarAnimation() {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(18000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            radarView.setRotation(value);
        });
        animator.start();
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_CODE);
        } else {
            getCurrentLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation();
        }
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                currentLat = location.getLatitude();
                currentLng = location.getLongitude();
                tvCurrent.setText(String.format(Locale.US, "فعلی: %.6f, %.6f", currentLat, currentLng));
                tvAccuracy.setText("دقت: " + (int) location.getAccuracy() + " متر");
                tvSpeed.setText("سرعت: " + String.format("%.1f", location.getSpeed() * 3.6) + " km/h");
                getAddress(currentLat, currentLng, tvCurrentAddress);
            } else {
                tvCurrent.setText("در حال بارگذاری...");
            }
        }).addOnFailureListener(e -> {
            tvCurrent.setText("خطا در دریافت مکان");
        });
    }

    private void getAddress(double lat, double lng, TextView tv) {
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> list = geocoder.getFromLocation(lat, lng, 1);
                if (list != null && !list.isEmpty()) {
                    String addr = list.get(0).getAddressLine(0);
                    runOnUiThread(() -> tv.setText(addr));
                } else {
                    runOnUiThread(() -> tv.setText("آدرس ناموجود"));
                }
            } catch (IOException e) {
                runOnUiThread(() -> tv.setText("خطا در اینترنت"));
            }
        }).start();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
            float pressure = event.values[0];
            float altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure);
            tvAltitude.setText(String.format(Locale.US, "ارتفاع: %.1f متر", altitude));
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onResume() {
        super.onResume();
        if (pressureSensor != null) {
            sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }
}