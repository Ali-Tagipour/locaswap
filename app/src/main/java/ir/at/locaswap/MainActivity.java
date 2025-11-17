package ir.at.locaswap;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "MainActivity";
    private static final int LOCATION_PERMISSION_CODE = 100;

    private FusedLocationProviderClient fusedLocationClient;
    private TextView tvCurrent, tvTarget, tvAccuracy, tvAltitude, tvSpeed, tvCurrentAddress, tvTargetAddress;
    private Button btnPaste, btnSaved, btnMap, btnLanguage, btnProcess;
    private FloatingActionButton fabMenu;
    private ImageView radarView;

    private volatile boolean isProcessRunning = false;
    private LocationManager locationManager;
    private SharedPreferences prefs;
    private double mockLat = 0, mockLng = 0;
    private double currentLat = 0, currentLng = 0;
    private SensorManager sensorManager;
    private Sensor pressureSensor;

    private Handler mockHandler = new Handler(Looper.getMainLooper());
    private Runnable mockRunnable;

    private final ActivityResultLauncher<Intent> mapLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                try {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        mockLat = result.getData().getDoubleExtra("lat", 0);
                        mockLng = result.getData().getDoubleExtra("lng", 0);
                        updateTargetLocation();
                        saveLocation(mockLat, mockLng);
                        getAddress(mockLat, mockLng, tvTargetAddress);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "mapLauncher error", e);
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
        safeSetupButtons();
        startRadarAnimation();
        setupFabMenu();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                moveTaskToBack(true);
            }
        });
    }

    private void initViews() {
        tvCurrent = findViewById(R.id.tv_current);
        tvTarget = findViewById(R.id.tv_target);
        tvAccuracy = findViewById(R.id.tv_accuracy);
        tvAltitude = findViewById(R.id.tv_altitude);
        tvSpeed = findViewById(R.id.tv_speed);
        tvCurrentAddress = findViewById(R.id.tv_current_address);
        tvTargetAddress = findViewById(R.id.tv_target_address);
        btnPaste = findViewById(R.id.btn_paste);
        btnSaved = findViewById(R.id.btn_saved);
        btnMap = findViewById(R.id.btn_map);
        btnLanguage = findViewById(R.id.btn_language);
        btnProcess = findViewById(R.id.btn_process);
        fabMenu = findViewById(R.id.fab_menu);
        radarView = findViewById(R.id.radar_view);
    }

    private void initServices() {
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        pressureSensor = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) : null;
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }

    private void safeSetupButtons() {
        btnPaste.setOnClickListener(v -> {
            try { showCoordinateDialog(); }
            catch (Exception e) { handleClickException("paste", e); }
        });

        btnSaved.setOnClickListener(v -> {
            try { showSavedLocations(); }
            catch (Exception e) { handleClickException("saved", e); }
        });

        btnMap.setOnClickListener(v -> {
            try { openMap(); }
            catch (Exception e) { handleClickException("map", e); }
        });

        btnLanguage.setOnClickListener(v -> {
            try { changeLanguage(); }
            catch (Exception e) { handleClickException("language", e); }
        });

        btnProcess.setOnClickListener(v -> {
            try { toggleMock(); }
            catch (Exception e) { handleClickException("process", e); }
        });
    }

    private void handleClickException(String source, Exception e) {
        Log.e(TAG, "Button click error: " + source, e);
        Toast.makeText(this, "خطا در اجرای عملیات: " + source, Toast.LENGTH_SHORT).show();
    }

    private void setupFabMenu() {
        fabMenu.setOnClickListener(v -> {
            try { showFabMenu(); }
            catch (Exception e) { handleClickException("fab", e); }
        });
    }

    private void showFabMenu() {
        PopupMenu popup = new PopupMenu(this, fabMenu);
        popup.getMenuInflater().inflate(R.menu.menu_main, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            try {
                if (id == R.id.menu_about) startActivity(new Intent(this, AboutActivity.class));
                else if (id == R.id.menu_policy) startActivity(new Intent(this, RulesActivity.class));
                else if (id == R.id.menu_help) startActivity(new Intent(this, HelpActivity.class));
            } catch (Exception e) {
                handleClickException("menu_item", e);
            }
            return true;
        });
        popup.show();
    }

    private void showCoordinateDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_coords, null);
        EditText etLat = view.findViewById(R.id.et_lat);
        EditText etLng = view.findViewById(R.id.et_lng);

        new AlertDialog.Builder(this)
                .setTitle("وارد کردن مختصات")
                .setView(view)
                .setPositiveButton("ذخیره", (d, w) -> {
                    String latStr = etLat.getText().toString().trim();
                    String lngStr = etLng.getText().toString().trim();
                    if (!latStr.isEmpty() && !lngStr.isEmpty()) {
                        try {
                            mockLat = Double.parseDouble(latStr);
                            mockLng = Double.parseDouble(lngStr);
                            updateTargetLocation();
                            saveLocation(mockLat, mockLng);
                            getAddress(mockLat, mockLng, tvTargetAddress);
                        } catch (Exception e) {
                            Log.e(TAG, "parse coords", e);
                            Toast.makeText(this, "فرمت اشتباه!", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("لغو", null)
                .show();
    }

    private void showSavedLocations() {
        try {
            Set<String> set = new HashSet<>(prefs.getStringSet("saved_locations", new HashSet<>()));
            if (set.isEmpty()) {
                Toast.makeText(this, "هیچ لوکیشنی ذخیره نشده", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] items = set.toArray(new String[0]);
            new AlertDialog.Builder(this)
                    .setTitle("لوکیشن‌های ذخیره‌شده")
                    .setItems(items, (d, i) -> {
                        try {
                            String[] coords = items[i].split(",\\s*");
                            mockLat = Double.parseDouble(coords[0]);
                            mockLng = Double.parseDouble(coords[1]);
                            updateTargetLocation();
                            getAddress(mockLat, mockLng, tvTargetAddress);
                        } catch (Exception ex) {
                            Log.e(TAG, "parse saved coords", ex);
                            Toast.makeText(this, "خطا در خواندن مختصات", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("پاک کردن همه", (d, i) -> {
                        prefs.edit().remove("saved_locations").apply();
                        Toast.makeText(this, "همه پاک شدند", Toast.LENGTH_SHORT).show();
                    })
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "showSavedLocations", e);
            Toast.makeText(this, "خطا در نمایش ذخیره‌ها", Toast.LENGTH_SHORT).show();
        }
    }

    private void openMap() {
        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra("current_lat", currentLat);
        intent.putExtra("current_lng", currentLng);
        mapLauncher.launch(intent);
    }

    private void changeLanguage() {
        try {
            String current = Locale.getDefault().getLanguage();
            String newLang = current.equals("fa") ? "en" : "fa";
            Locale locale = new Locale(newLang);
            Locale.setDefault(locale);
            Configuration config = new Configuration();
            config.setLocale(locale);
            getResources().updateConfiguration(config, getResources().getDisplayMetrics());
            recreate();
        } catch (Exception e) {
            Log.e(TAG, "changeLanguage failed", e);
            Toast.makeText(this, "عدم تغییر زبان", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveLocation(double lat, double lng) {
        String entry = String.format(Locale.US, "%.6f, %.6f", lat, lng);
        Set<String> set = new HashSet<>(prefs.getStringSet("saved_locations", new HashSet<>()));
        set.add(entry);
        prefs.edit().putStringSet("saved_locations", set).apply();
    }

    private void loadLastTarget() {
        try {
            mockLat = Double.longBitsToDouble(prefs.getLong("mock_lat", 0));
            mockLng = Double.longBitsToDouble(prefs.getLong("mock_lng", 0));
            if (mockLat != 0 || mockLng != 0) updateTargetLocation();
        } catch (Exception e) {
            Log.e(TAG, "loadLastTarget", e);
        }
    }

    private void updateTargetLocation() {
        try {
            tvTarget.setText(String.format(Locale.US, "هدف: %.6f, %.6f", mockLat, mockLng));
            prefs.edit()
                    .putLong("mock_lat", Double.doubleToRawLongBits(mockLat))
                    .putLong("mock_lng", Double.doubleToRawLongBits(mockLng))
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "updateTargetLocation", e);
        }
    }

    private void toggleMock() {
        isProcessRunning = !isProcessRunning;
        updateProcessButtonText();

        if (isProcessRunning) {
            if (!isMockLocationAllowedForApp()) {
                showMockLocationWarning();
                isProcessRunning = false;
                updateProcessButtonText();
                return;
            }
            startMockLocation();
        } else {
            stopMockLocation();
        }
    }

    private void updateProcessButtonText() {
        if (btnProcess != null) {
            btnProcess.setText(isProcessRunning ? getString(R.string.stop) : getString(R.string.start));
        }
    }

    private boolean isMockLocationAllowedForApp() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
                if (appOps == null) return false;
                int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, android.os.Process.myUid(), getPackageName());
                return mode == AppOpsManager.MODE_ALLOWED;
            } else {
                String val = Settings.Secure.getString(getContentResolver(), Settings.Secure.ALLOW_MOCK_LOCATION);
                return "1".equals(val);
            }
        } catch (Exception e) {
            Log.e(TAG, "isMockLocationAllowedForApp", e);
            return false;
        }
    }

    private void showMockLocationWarning() {
        new AlertDialog.Builder(this)
                .setTitle("Mock Location غیرفعال است")
                .setMessage("برای فعال‌سازی:\n1. به تنظیمات → درباره گوشی → شماره ساخت (۷ بار بزنید)\n2. گزینه‌های توسعه‌دهنده → برنامه Mock Location → LocaSwap")
                .setPositiveButton("باز کردن تنظیمات", (d, w) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)))
                .setNegativeButton("لغو", null)
                .show();
    }

    private void startMockLocation() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "مجوز مکان لازم است!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (locationManager.getProvider(LocationManager.GPS_PROVIDER) != null) {
                try { locationManager.removeTestProvider(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ProviderProperties properties = new ProviderProperties.Builder()
                        .setAccuracy(ProviderProperties.ACCURACY_FINE)
                        .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                        .build();
                try { locationManager.addTestProvider(LocationManager.GPS_PROVIDER, properties); } catch (Exception ignored) {}
            } else {
                try {
                    locationManager.addTestProvider(
                            LocationManager.GPS_PROVIDER,
                            false, false, false, false, true, true, true, 1, 1
                    );
                } catch (Exception ignored) {}
            }

            try { locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true); } catch (Exception ignored) {}

            final Location mock = new Location(LocationManager.GPS_PROVIDER);
            mock.setLatitude(mockLat);
            mock.setLongitude(mockLng);
            mock.setAccuracy(5f);
            mock.setSpeed(0f);
            mock.setBearing(0f);
            mock.setTime(System.currentTimeMillis());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                mock.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            }

            mockRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!isProcessRunning) return;
                    try { locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mock); } catch (Exception ignored) {}
                    mockHandler.postDelayed(this, 1000);
                }
            };
            mockHandler.post(mockRunnable);

            runOnUiThread(() -> Toast.makeText(this, "موقعیت جعلی فعال شد!", Toast.LENGTH_SHORT).show());

        } catch (Exception e) {
            Log.e(TAG, "startMockLocation", e);
            runOnUiThread(() -> {
                Toast.makeText(this, "خطا: Developer Options → Mock Location App → LocaSwap", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            });
        }
    }

    private void stopMockLocation() {
        try {
            isProcessRunning = false;
            if (mockRunnable != null) mockHandler.removeCallbacks(mockRunnable);
            try { locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false); } catch (Exception ignored) {}
            try {
                if (locationManager.getProvider(LocationManager.GPS_PROVIDER) != null) {
                    locationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
                }
            } catch (Exception ignored) {}
            updateProcessButtonText();
        } catch (Exception e) {
            Log.e(TAG, "stopMockLocation", e);
        }
    }

    // ------------------------ Radar animation fixes ------------------------
    private void startRadarAnimation() {
        // safe: empty for now
    }
    private void stopRadarAnimation() {
        // safe: empty
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
        try {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (requestCode == LOCATION_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            }
        } catch (Exception e) {
            Log.e(TAG, "onRequestPermissionsResult", e);
        }
    }

    private void getCurrentLocation() {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                try {
                    if (location != null) {
                        currentLat = location.getLatitude();
                        currentLng = location.getLongitude();
                        tvCurrent.setText(String.format(Locale.US, "فعلی: %.6f, %.6f", currentLat, currentLng));
                        tvAccuracy.setText("دقت: " + (int) location.getAccuracy() + " متر");
                        tvSpeed.setText("سرعت: " + String.format("%.1f", location.getSpeed() * 3.6) + " km/h");
                        getAddress(currentLat, currentLng, tvCurrentAddress);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "getLastLocation callback", e);
                }
            }).addOnFailureListener(e -> Log.e(TAG, "getLastLocation failure", e));
        } catch (Exception e) {
            Log.e(TAG, "getCurrentLocation", e);
        }
    }

    private void getAddress(double lat, double lng, TextView tv) {
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> list = geocoder.getFromLocation(lat, lng, 1);
                runOnUiThread(() -> {
                    try { tv.setText(list != null && !list.isEmpty() ? list.get(0).getAddressLine(0) : "آدرس ناموجود"); }
                    catch (Exception e) { tv.setText("آدرس ناموجود"); }
                });
            } catch (Exception e) {
                Log.e(TAG, "geocoder error", e);
                runOnUiThread(() -> tv.setText("آدرس ناموجود (اینترنت لازم نیست)"));
            }
        }).start();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        try {
            if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
                float pressure = event.values[0];
                float altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure);
                tvAltitude.setText(String.format(Locale.US, "ارتفاع: %.1f متر", altitude));
            }
        } catch (Exception e) {
            Log.e(TAG, "onSensorChanged", e);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onResume() {
        super.onResume();
        try { if (pressureSensor != null) sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL); }
        catch (Exception e) { Log.e(TAG, "onResume sensor register", e); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { sensorManager.unregisterListener(this); }
        catch (Exception e) { Log.e(TAG, "onPause sensor unregister", e); }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopMockLocation();
        stopRadarAnimation();
        super.onDestroy();
    }
}
