package com.omarica.walkme;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.indooratlas.android.sdk.IALocation;
import com.indooratlas.android.sdk.IALocationListener;
import com.indooratlas.android.sdk.IALocationManager;
import com.indooratlas.android.sdk.IALocationRequest;
import com.indooratlas.android.sdk.IARegion;
import com.indooratlas.android.sdk.resources.IAFloorPlan;
import com.indooratlas.android.sdk.resources.IALatLng;
import com.indooratlas.android.sdk.resources.IALocationListenerSupport;
import com.indooratlas.android.sdk.resources.IAResourceManager;
import com.indooratlas.android.sdk.resources.IAResult;
import com.indooratlas.android.sdk.resources.IAResultCallback;
import com.indooratlas.android.sdk.resources.IATask;
import com.indooratlas.android.wayfinding.IARoutingLeg;
import com.indooratlas.android.wayfinding.IAWayfinder;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import com.squareup.picasso.Target;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;

public class MainActivity extends FragmentActivity implements LocationListener,
        GoogleMap.OnMapClickListener, SensorEventListener {
    private static final int MY_PERMISSION_ACCESS_FINE_LOCATION = 42;

    private static final String TAG = "WalkMe";

    private static final int REQ_CODE_SPEECH_INPUT = 100;
    /* used to decide when bitmap should be downscaled */
    private static final int MAX_DIMENSION = 2048;
    private static final float HUE_IABLUE = 200.0f;

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private Circle mCircle;
    private IARegion mOverlayFloorPlan = null;
    private GroundOverlay mGroundOverlay = null;
    private IALocationManager mIALocationManager;
    private IAResourceManager mResourceManager;
    private IATask<IAFloorPlan> mFetchFloorPlanTask;
    private Target mLoadTarget;
    private boolean mCameraPositionNeedsUpdating = true; // update on first location
    private boolean mShowIndoorLocation = false;

    private IAWayfinder mWayfinder;
    private LatLng mLocation;

    private LatLng mDestination;
    private Marker mDestinationMarker;

    private Polyline mPath;
    private Polyline mPathCurrent;
    private IARoutingLeg[] mCurrentRoute;

    private Integer mFloor;
    private LatLng currentLocation;
    private Marker mMarker;

    private int currentDegree = 0;
    private FirebaseDatabase database;
    private DatabaseReference myRef;
    private DatabaseReference degreeRef;

    // device sensor manager

    private SensorManager mSensorManager;
    /**
     * Listener that handles location change events.
     */
    private IALocationListener mListener = new IALocationListenerSupport() {

        /**
         * Location changed, move marker and camera position.
         */
        @Override
        public void onLocationChanged(IALocation location) {


            mLocation = new LatLng(location.getLatitude(), location.getLongitude());

            Log.d(TAG, "new location received with coordinates: " + location.getLatitude()
                    + "," + location.getLongitude());


            drawMarker();

            if (mMap == null) {
                //location received before map is initialized, ignoring update here
                return;
            }


            mFloor = location.getFloorLevel();
            if (mWayfinder != null) {
                mWayfinder.setLocation(mLocation.latitude, mLocation.longitude, mFloor);
            }
            updateRoute();

            if (mShowIndoorLocation) {
                showLocationCircle(mLocation, location.getAccuracy());
            }

            // our camera position needs updating if location has significantly changed
            if (mCameraPositionNeedsUpdating) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mLocation, 17.5f));
                mCameraPositionNeedsUpdating = false;
            }
        }
    };
    /**
     * Listener that changes overlay if needed
     */
    private IARegion.Listener mRegionListener = new IARegion.Listener() {
        @Override
        public void onEnterRegion(IARegion region) {
            if (region.getType() == IARegion.TYPE_FLOOR_PLAN) {
                final String newId = region.getId();
                // Are we entering a new floor plan or coming back the floor plan we just left?
                if (mGroundOverlay == null || !region.equals(mOverlayFloorPlan)) {
                    mCameraPositionNeedsUpdating = true; // entering new fp, need to move camera
                    if (mGroundOverlay != null) {
                        mGroundOverlay.remove();
                        mGroundOverlay = null;
                    }
                    mOverlayFloorPlan = region; // overlay will be this (unless error in loading)
                    fetchFloorPlan(newId);
                } else {
                    mGroundOverlay.setTransparency(0.0f);
                }

                mShowIndoorLocation = true;
                showInfo("Showing IndoorAtlas SDK\'s location output");
            }
            showInfo("Enter " + (region.getType() == IARegion.TYPE_VENUE
                    ? "VENUE "
                    : "FLOOR_PLAN ") + region.getId());
        }

        @Override
        public void onExitRegion(IARegion region) {
            if (mGroundOverlay != null) {
                // Indicate we left this floor plan but leave it there for reference
                // If we enter another floor plan, this one will be removed and another one loaded
                mGroundOverlay.setTransparency(0.5f);
            }

            mShowIndoorLocation = false;
            showInfo("Exit " + (region.getType() == IARegion.TYPE_VENUE
                    ? "VENUE "
                    : "FLOOR_PLAN ") + region.getId());
        }

    };


    HashMap<String, LatLng> poiMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        poiMap = new HashMap<>();

        database = FirebaseDatabase.getInstance();
        myRef = database.getReference("building").child("engineering").child("floor").child("2");
        degreeRef = database.getReference();
        myRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                for (DataSnapshot i : dataSnapshot.getChildren()) {

                    double lat = 0;
                    double lng = 0;
                    for (DataSnapshot j : i.getChildren()) {

                        if (j.getKey().toString().equals("lat")) {
                            lat = (double) j.getValue();
                        } else {
                            lng = (double) j.getValue();
                        }
                    }
                    LatLng latLng = new LatLng(lat, lng);
                    poiMap.put(i.getKey(), latLng);
                }


            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // prevent the screen going to sleep while app is on foreground
        findViewById(android.R.id.content).setKeepScreenOn(true);

        // instantiate IALocationManager and IAResourceManager
        mIALocationManager = IALocationManager.create(this);
        mResourceManager = IAResourceManager.create(this);

        // Request GPS locations
        if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSION_ACCESS_FINE_LOCATION);
            return;
        }

        startListeningPlatformLocations();

        String graphJSON = loadGraphJSON();
        if (graphJSON == null) {
            Toast.makeText(this, "Could not find wayfinding_graph.json from raw " +
                    "resources folder. Cannot do wayfinding.", Toast.LENGTH_LONG).show();
        } else {
            mWayfinder = IAWayfinder.create(this, graphJSON);
        }


        // navigateTo(25.09210937,55.15636251);
    }

    private void navigateTo(double lat, double lng) {
        mWayfinder.setLocation(currentLocation.latitude, currentLocation.longitude, 3);

        mWayfinder.setDestination(lat, lng, 3);
        mCurrentRoute = mWayfinder.getRoute();


    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {

            startVoiceInput();

        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                boolean isCorrect;
                if (resultCode == RESULT_OK && null != data) {
                    ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    //Toast.makeText(this, result.get(0), Toast.LENGTH_SHORT).show();

                    isCorrect = poiMap.containsKey(result.get(0));
                    Toast.makeText(this, isCorrect + " " + result.get(0), Toast.LENGTH_SHORT).show();

                    /*
                    if (mWayfinder != null) {
                        mWayfinder.setDestination(poiMap.get(result.get(0)).latitude, poiMap.get(result.get(0)).longitude, mFloor);
                    }
                    Log.d(TAG, "Set destination: (" + mDestination.latitude + ", " +
                            mDestination.longitude + "), floor=" + mFloor);

                    updateRoute(); */
                    LatLng point = poiMap.get(result.get(0));

                    if (mMap != null) {



                        mDestination = point;
                        if (mDestinationMarker == null) {
                            mDestinationMarker = mMap.addMarker(new MarkerOptions()
                                    .position(point)
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
                        } else {
                            mDestinationMarker.setPosition(point);
                        }
                        if (mWayfinder != null) {
                            mWayfinder.setDestination(point.latitude, point.longitude, mFloor);
                        }
                        Log.d(TAG, "Set destination: (" + mDestination.latitude + ", " +
                                mDestination.longitude + "), floor=" + mFloor);

                        updateRoute();
                    }
                }
                break;
            }

        }
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Hello, How can I help you?");
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {

        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // remember to clean up after ourselves
        mIALocationManager.destroy();
        if (mWayfinder != null) {
            mWayfinder.close();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // for the system's orientation sensor registered listeners

        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME);

        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMap();
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            mMap.setMyLocationEnabled(false);
        }

        // start receiving location updates & monitor region changes
        mIALocationManager.requestLocationUpdates(IALocationRequest.create(), mListener);
        mIALocationManager.registerRegionListener(mRegionListener);

        mMap.setOnMapClickListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // unregister location & region changes
        mIALocationManager.removeLocationUpdates(mListener);
        mIALocationManager.registerRegionListener(mRegionListener);
    }

    private BitmapDescriptor bitmapDescriptorFromVector(Context context, int vectorResId) {
        Drawable vectorDrawable = ContextCompat.getDrawable(context, vectorResId);
        vectorDrawable.setBounds(0, 0, vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight());
        Bitmap bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private void drawMarker(){

        if (mMarker == null) {
            if (mMap != null) {
                mMarker = mMap.addMarker(new MarkerOptions().position(mLocation)
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.map_blue_dot))
                         .anchor(0.5f, 0.5f)
                        .rotation(currentDegree)
                        .flat(true));
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mLocation, 17.0f));
            }
        } else {
            mMarker.setPosition(mLocation);
            mMarker.setRotation(currentDegree);
        }

    }

    private void showLocationCircle(LatLng center, double accuracyRadius) {
        if (mCircle == null) {
            // location can received before map is initialized, ignoring those updates
            if (mMap != null) {
                mCircle = mMap.addCircle(new CircleOptions()
                        .center(center)
                        .radius(accuracyRadius)
                        .fillColor(0x801681FB)
                        .strokeColor(0x800A78DD)
                        .zIndex(1.0f)
                        .visible(true)
                        .strokeWidth(5.0f));
            }
        } else {
            // move existing markers position to received location
            mCircle.setCenter(center);
            mCircle.setRadius(accuracyRadius);
        }
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSION_ACCESS_FINE_LOCATION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startListeningPlatformLocations();
                }
                break;
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (!mShowIndoorLocation) {
            Log.d(TAG, "new LocationService location received with coordinates: " + location.getLatitude()
                    + "," + location.getLongitude());

            showLocationCircle(
                    currentLocation = new LatLng(location.getLatitude(), location.getLongitude()),
                    location.getAccuracy());
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    /**
     * Sets bitmap of floor plan as ground overlay on Google Maps
     */
    private void setupGroundOverlay(IAFloorPlan floorPlan, Bitmap bitmap) {

        if (mGroundOverlay != null) {
            mGroundOverlay.remove();
        }

        if (mMap != null) {
            BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmap);
            IALatLng iaLatLng = floorPlan.getCenter();
            LatLng center = new LatLng(iaLatLng.latitude, iaLatLng.longitude);
            GroundOverlayOptions fpOverlay = new GroundOverlayOptions()
                    .image(bitmapDescriptor)
                    .zIndex(0.0f)
                    .position(center, floorPlan.getWidthMeters(), floorPlan.getHeightMeters())
                    .bearing(floorPlan.getBearing());

            mGroundOverlay = mMap.addGroundOverlay(fpOverlay);
        }
    }

    /**
     * Download floor plan using Picasso library.
     */
    private void fetchFloorPlanBitmap(final IAFloorPlan floorPlan) {

        final String url = floorPlan.getUrl();

        if (mLoadTarget == null) {
            mLoadTarget = new Target() {

                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                    Log.d(TAG, "onBitmap loaded with dimensions: " + bitmap.getWidth() + "x"
                            + bitmap.getHeight());
                    setupGroundOverlay(floorPlan, bitmap);
                }

                @Override
                public void onPrepareLoad(Drawable placeHolderDrawable) {
                    // N/A
                }

                @Override
                public void onBitmapFailed(Drawable placeHolderDraweble) {
                    showInfo("Failed to load bitmap");
                    mOverlayFloorPlan = null;
                }
            };
        }

        RequestCreator request = Picasso.with(this).load(url);

        final int bitmapWidth = floorPlan.getBitmapWidth();
        final int bitmapHeight = floorPlan.getBitmapHeight();

        if (bitmapHeight > MAX_DIMENSION) {
            request.resize(0, MAX_DIMENSION);
        } else if (bitmapWidth > MAX_DIMENSION) {
            request.resize(MAX_DIMENSION, 0);
        }

        request.into(mLoadTarget);
    }


    /**
     * Fetches floor plan data from IndoorAtlas server.
     */
    private void fetchFloorPlan(String id) {

        // if there is already running task, cancel it
        cancelPendingNetworkCalls();

        final IATask<IAFloorPlan> task = mResourceManager.fetchFloorPlanWithId(id);

        task.setCallback(new IAResultCallback<IAFloorPlan>() {

            @Override
            public void onResult(IAResult<IAFloorPlan> result) {

                if (result.isSuccess() && result.getResult() != null) {
                    // retrieve bitmap for this floor plan metadata
                    fetchFloorPlanBitmap(result.getResult());
                } else {
                    // ignore errors if this task was already canceled
                    if (!task.isCancelled()) {
                        // do something with error
                        showInfo("Loading floor plan failed: " + result.getError());
                        mOverlayFloorPlan = null;
                    }
                }
            }
        }, Looper.getMainLooper()); // deliver callbacks using main looper

        // keep reference to task so that it can be canceled if needed
        mFetchFloorPlanTask = task;

    }

    /**
     * Helper method to cancel current task if any.
     */
    private void cancelPendingNetworkCalls() {
        if (mFetchFloorPlanTask != null && !mFetchFloorPlanTask.isCancelled()) {
            mFetchFloorPlanTask.cancel();
        }
    }

    private void showInfo(String text) {
        final Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), text,
                Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(R.string.button_close, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                snackbar.dismiss();
            }
        });
        snackbar.show();
    }

    private void startListeningPlatformLocations() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null && (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
        }
    }

    /**
     * Load "wayfinding_graph.json" from raw resources folder of the app module
     *
     * @return
     */
    private String loadGraphJSON() {
        try {
            Resources res = getResources();
            int resourceIdentifier = res.getIdentifier("wayfinding_graph", "raw", this.getPackageName());
            InputStream in_s = res.openRawResource(resourceIdentifier);

            byte[] b = new byte[in_s.available()];
            in_s.read(b);
            return new String(b);
        } catch (Exception e) {
            Log.e(TAG, "Could not find wayfinding_graph.json from raw resources folder");
            return null;
        }

    }

    @Override
    public void onMapClick(LatLng point) {
        if (mMap != null) {



            mDestination = point;
            if (mDestinationMarker == null) {
                mDestinationMarker = mMap.addMarker(new MarkerOptions()
                        .position(point)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
            } else {
                mDestinationMarker.setPosition(point);
            }
            if (mWayfinder != null) {
                mWayfinder.setDestination(point.latitude, point.longitude, mFloor);
            }
            Log.d(TAG, "Set destination: (" + mDestination.latitude + ", " +
                    mDestination.longitude + "), floor=" + mFloor);

            updateRoute();
        }
    }


    private void updateRoute() {
        if (mLocation == null || mDestination == null || mWayfinder == null) {
            return;
        }

        Log.d(TAG, "Updating the wayfinding route");

        mCurrentRoute = mWayfinder.getRoute();
        if (mCurrentRoute == null || mCurrentRoute.length == 0) {
            // Wrong credentials or invalid wayfinding graph
            return;
        }
        if (mPath != null) {
            // Remove old path if any
            clearOldPath();
        }


        visualizeRoute(mCurrentRoute);
    }

    /**
     * Clear the visualizations for the wayfinding paths
     */
    private void clearOldPath() {
        mPath.remove();
        mPathCurrent.remove();
    }

    /**
     * Visualize the IndoorAtlas Wayfinding path on top of the Google Maps.
     *
     * @param legs Array of IARoutingLeg objects returned from IAWayfinder.getRoute()
     */
    private void visualizeRoute(IARoutingLeg[] legs) {
        // optCurrent will contain the wayfinding path in the current floor and opt will contain the
        // whole path, including parts in other floors.
        PolylineOptions opt = new PolylineOptions();
        PolylineOptions optCurrent = new PolylineOptions();
        Log.d("TAG",legs.length+"");

        for (IARoutingLeg leg : legs) {
            opt.add(new LatLng(leg.getBegin().getLatitude(), leg.getBegin().getLongitude()));
            if (leg.getBegin().getFloor() == mFloor && leg.getEnd().getFloor() == mFloor) {
                optCurrent.add(
                        new LatLng(leg.getBegin().getLatitude(), leg.getBegin().getLongitude()));
                optCurrent.add(
                        new LatLng(leg.getEnd().getLatitude(), leg.getEnd().getLongitude()));
            }
        }
        optCurrent.color(Color.RED);
        if (legs.length > 0) {
            IARoutingLeg leg = legs[legs.length - 1];
            opt.add(new LatLng(leg.getEnd().getLatitude(), leg.getEnd().getLongitude()));
        }

        // Here wayfinding path in different floor than current location is visualized in blue and
        // path in current floor is visualized in red
        Log.d("TAG",opt.getPoints().get(0).toString()+"");

       // mMap.addPolyline(new PolylineOptions().add(new LatLng(mLocation.latitude,mLocation.longitude)).add(new LatLng(mDestination.latitude,mDestination.longitude)));
        mPath = mMap.addPolyline(opt);
        mPathCurrent = mMap.addPolyline(optCurrent);
    }


    @Override
    public void onSensorChanged(final SensorEvent sensorEvent) {

        currentDegree = (int) sensorEvent.values[0];
        degreeRef.child("currentDegree").setValue(currentDegree);

     /*   final Handler handlerwayfinding-graph-4.jsonwayfinding-graph-5.json = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Do something after 5s = 5000ms
                currentDegree = (int) sensorEvent.values[0];
                degreeRef.child("currentDegree").setValue(currentDegree);
            }
        }, 5000); */

       /* if(mLocation!=null)
        drawMarker(); */
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
