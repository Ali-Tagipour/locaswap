package ir.at.locaswap;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.*;
import android.location.provider.ProviderProperties;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.*;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final int LOCATION_PERMISSION_CODE = 100;
    private FusedLocationProviderClient fusedLocationClient;
    private TextView tvCurrent, tvTarget, tvAccuracy, tvAltitude, tvSpeed, tvCurrentAddress, tvTargetAddress;
    private Button btnPaste, btnSaved, btnMap, btnLanguage, btnProcess;
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
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }

    private void setupButtons() {
        btnPaste.setOnClickListener(v -> showCoordinateDialog());
        btnSaved.setOnClickListener(v -> showSavedLocations());
        btnMap.setOnClickListener(v -> openMap());
        btnLanguage.setOnClickListener(v -> changeLanguage());
        btnProcess.setOnClickListener(v -> toggleMock());
    }

    private void setupFabMenu() {
        fabMenu.setOnClickListener(v -> showFabMenu());
    }

    private void showFabMenu() {
        PopupMenu popup = new PopupMenu(this, fabMenu);
        popup.getMenuInflater().inflate(R.menu.menu_main, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_about) startActivity(new Intent(this, AboutActivity.class));
            else if (id == R.id.menu_policy) startActivity(new Intent(this, RulesActivity.class));
            else if (id == R.id.menu_help) startActivity(new Intent(this, HelpActivity.class));
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
                            Toast.makeText(this, "فرمت اشتباه!", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("لغو", null)
                .show();
    }

    private void showSavedLocations() {
        Set<String> set = prefs.getStringSet("saved_locations", new HashSet<>());
        if (set.isEmpty()) {
            Toast.makeText(this, "هیچ لوکیشنی ذخیره نشده", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = set.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("لوکیشن‌های ذخیره‌شده")
                .setItems(items, (d, i) -> {
                    String[] coords = items[i].split(", ");
                    mockLat = Double.parseDouble(coords[0]);
                    mockLng = Double.parseDouble(coords[1]);
                    updateTargetLocation();
                    getAddress(mockLat, mockLng, tvTargetAddress);
                })
                .setNegativeButton("پاک کردن همه", (d, i) -> {
                    prefs.edit().remove("saved_locations").apply();
                    Toast.makeText(this, "همه پاک شدند", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void openMap() {
        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra("current_lat", currentLat);
        intent.putExtra("current_lng", currentLng);
        mapLauncher.launch(intent);
    }

    private void changeLanguage() {
        String current = Locale.getDefault().getLanguage();
        String newLang = current.equals("fa") ? "en" : "fa";
        Locale locale = new Locale(newLang);
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        recreate();
    }

    private void saveLocation(double lat, double lng) {
        String entry = String.format(Locale.US, "%.6f, %.6f", lat, lng);
        Set<String> set = new HashSet<>(prefs.getStringSet("saved_locations", new HashSet<>()));
        set.add(entry);
        prefs.edit().putStringSet("saved_locations", set).apply();
    }

    private void loadLastTarget() {
        mockLat = Double.longBitsToDouble(prefs.getLong("mock_lat", 0));
        mockLng = Double.longBitsToDouble(prefs.getLong("mock_lng", 0));
        if (mockLat != 0 && mockLng != 0) updateTargetLocation();
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
        btnProcess.setText(isProcessRunning ? "توقف" : "شروع");

        if (isProcessRunning) {
            if (!isMockLocationEnabled()) {
                showMockLocationWarning();
                isProcessRunning = false;
                btnProcess.setText("شروع");
                return;
            }
            startMockLocation();
        } else {
            stopMockLocation();
            moveTaskToBack(true);
        }
    }

    private boolean isMockLocationEnabled() {
        try {
            return Settings.Secure.getInt(getContentResolver(), "mock_location") == 1;
        } catch (Exception e) {
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

    // ------------------------
    // MOCK LOCATION (FIXED)
    // ------------------------
    private void startMockLocation() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "مجوز مکان لازم است!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            try {
                locationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
            } catch (Exception ignored) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                // ------------------------
                // ANDROID 12+ (API 31+)
                // SupportsAltitude/Speed حذف شده‌اند
                // ------------------------
                ProviderProperties properties = new ProviderProperties.Builder()
                        .setAccuracy(ProviderProperties.ACCURACY_FINE)
                        .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                        // ❌ این سه تا در API جدید وجود ندارند
                        // .setSupportsAltitude(...)
                        // .setSupportsSpeed(...)
                        // .setSupportsBearing(...)
                        .build();

                locationManager.addTestProvider(
                        LocationManager.GPS_PROVIDER,
                        properties
                );

            } else {

                // ------------------------
                // ANDROID 11- (API 30-)
                // اینجا متدها موجودند
                // ------------------------
                locationManager.addTestProvider(
                        LocationManager.GPS_PROVIDER,
                        false,   // requiresNetwork
                        false,   // requiresSatellite
                        false,   // requiresCell
                        false,   // hasMonetaryCost
                        true,    // supportsAltitude
                        true,    // supportsSpeed
                        true,    // supportsBearing
                        1,       // powerRequirement
                        1        // accuracy
                );
            }

            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);

            Location mock = new Location(LocationManager.GPS_PROVIDER);
            mock.setLatitude(mockLat);
            mock.setLongitude(mockLng);
            mock.setAccuracy(5f);
            mock.setSpeed(0f);
            mock.setBearing(0f);
            mock.setTime(System.currentTimeMillis());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                mock.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            }

            new Thread(() -> {
                while (isProcessRunning) {
                    try {
                        locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mock);
                    } catch (Exception ignored) {}
                    try { Thread.sleep(1000); } catch (Exception ignored) {}
                }
            }).start();

            runOnUiThread(() ->
                    Toast.makeText(this, "موقعیت جعلی فعال شد!", Toast.LENGTH_SHORT).show()
            );

        } catch (Exception e) {
            runOnUiThread(() -> {
                Toast.makeText(this, "خطا: Developer Options → Mock Location App → LocaSwap", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            });
        }
    }


    private void stopMockLocation() {
        try {
            isProcessRunning = false;
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false);
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
        } catch (Exception ignored) {}
    }


    private void startRadarAnimation() {
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 360f);
        anim.setDuration(18000);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.addUpdateListener(a -> radarView.setRotation((Float) a.getAnimatedValue()));
        anim.start();
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
            }
        });
    }

    private void getAddress(double lat, double lng, TextView tv) {
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> list = geocoder.getFromLocation(lat, lng, 1);
                runOnUiThread(() -> tv.setText(list != null && !list.isEmpty() ? list.get(0).getAddressLine(0) : "آدرس ناموجود"));
            } catch (Exception e) {
                runOnUiThread(() -> tv.setText("آدرس ناموجود (اینترنت لازم نیست)"));
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

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onResume() {
        super.onResume();
        if (pressureSensor != null) sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) {
            stopMockLocation();
        }
        super.onDestroy();
    }
}
