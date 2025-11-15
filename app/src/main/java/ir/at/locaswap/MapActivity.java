package ir.at.locaswap;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;

public class MapActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private double currentLat, currentLng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        currentLat = getIntent().getDoubleExtra("current_lat", 35.6892);
        currentLng = getIntent().getDoubleExtra("current_lng", 51.3890);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        LatLng start = new LatLng(currentLat, currentLng);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(start, 15));

        mMap.setOnMapClickListener(latLng -> {
            Intent result = new Intent();
            result.putExtra("lat", latLng.latitude);
            result.putExtra("lng", latLng.longitude);
            setResult(RESULT_OK, result);
            finish();
        });
    }
}