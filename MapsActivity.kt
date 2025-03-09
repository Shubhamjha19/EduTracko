@file:Suppress("DEPRECATION")

package com.example.safeedutrack

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.safeedutrack.network.AuthHelper
import com.example.safeedutrack.network.SupabaseService
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MapsActivity : FragmentActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var geofencingClient: GeofencingClient
    private lateinit var geofenceHelper: GeofenceHelper
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var movingMarker: Marker? = null
    private lateinit var authHelper: AuthHelper
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)

        geofencingClient = LocationServices.getGeofencingClient(this)
        geofenceHelper = GeofenceHelper(this)
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        authHelper = AuthHelper(this)
        notificationHelper = NotificationHelper(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        //  val bnnCollege = LatLng(19.28493235362456, 73.05533516285809)
        val bnnCollege= LatLng(19.287804591028536, 73.0803118844574)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(bnnCollege, 19f))
        mMap.addMarker(MarkerOptions().position(bnnCollege).title("Located at BNN College"))

        // Add Circle and Geofence for BNN College
        addCircle(bnnCollege, GEOFENCE_RADIUS)
        addGeofence(bnnCollege, GEOFENCE_RADIUS)

        // Enable user location
        enableUserLocation()

        // Start location updates for moving marker
        startLocationUpdates()
    }

    private var hasShownLocationRationale = false
    private var hasShownBackgroundRationale = false

    private fun enableUserLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        } else {
            if (!hasShownLocationRationale && ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                hasShownLocationRationale = true // Set flag to true
                showPermissionRationale(
                    "Location Permission Required",
                    "This app requires location permissions to provide geofencing and real-time location updates.",
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    FINE_LOCATION_ACCESS_REQUEST_CODE
                )
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    FINE_LOCATION_ACCESS_REQUEST_CODE
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                if (!hasShownBackgroundRationale && ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                    hasShownBackgroundRationale = true // Set flag to true
                    showPermissionRationale(
                        "Background Location Permission Required",
                        "To monitor geofences while the app is in the background, this permission is needed.",
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                        BACKGROUND_LOCATION_ACCESS_REQUEST_CODE
                    )
                } else {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                        BACKGROUND_LOCATION_ACCESS_REQUEST_CODE
                    )
                }
            }
        }
    }

    private fun showPermissionRationale(title: String, message: String, permission: String, requestCode: Int) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Grant") { _, _ ->
                ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Permission Denied. Some features may not work.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            FINE_LOCATION_ACCESS_REQUEST_CODE -> {
                hasShownLocationRationale = false // Reset the flag
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    enableUserLocation()
                } else {
                    Toast.makeText(this, "Location permission denied. Map features limited.", Toast.LENGTH_SHORT).show()
                }
            }
            BACKGROUND_LOCATION_ACCESS_REQUEST_CODE -> {
                hasShownBackgroundRationale = false // Reset the flag
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Background location permission granted.", Toast.LENGTH_SHORT).show()
                    // Navigate to MapsActivity after permission is granted
                    val intent = Intent(this, MapsActivity::class.java)
                    startActivity(intent)
                    finish() // Optionally, finish the current activity
                } else {
                    Toast.makeText(this, "Background location permission denied. Geofencing limited.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }



    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 1000
            fastestInterval = 500
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl("https://xdfyutgivtoadckozjbc.supabase.co")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val supabaseService: SupabaseService = retrofit.create(SupabaseService::class.java)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                val userLatLng = LatLng(location.latitude, location.longitude)

                if (movingMarker == null) {
                    movingMarker = mMap.addMarker(
                        MarkerOptions()
                            .position(userLatLng)
                            .title("User's Current Location")
                            .icon(BitmapDescriptorFactory.fromBitmap(getBitmapFromVectorDrawable(this@MapsActivity, R.drawable.user_moving_icon)))
                    )
                } else {
                    movingMarker?.position = userLatLng
                }

                mMap.animateCamera(CameraUpdateFactory.newLatLng(userLatLng))

                val sharedPreferences = getSharedPreferences("LoginPrefs", MODE_PRIVATE)
                val userEmail = sharedPreferences.getString("userEmail", null) // Fetch user email

                if (userEmail.isNullOrEmpty()) {
                    Log.e(TAG, "User email not found. Skipping location update.")
                    return
                }

                Log.d(TAG, "User email retrieved: $userEmail. Checking location redundancy before storing...")

                CoroutineScope(Dispatchers.IO).launch {
                    val success = authHelper.storeUserLocation(
                        this@MapsActivity,
                        supabaseService,
                        location.latitude,
                        location.longitude
                    )
                    if (success) {
                        Log.d(TAG, "New location stored successfully for user: $userEmail")
                    } else {
                        Log.d(TAG, "Location unchanged. No update needed for user: $userEmail")
                    }
                }
            }
        }

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }



    private fun getBitmapFromVectorDrawable(context: Context, drawableId: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(context, drawableId) ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        // Set the bounds of the drawable
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)

        // Scale the drawable dimensions
        val scaleFactor = 3 // Change this value to 2, 3, or 4 based on the required scaling
        val width = drawable.intrinsicWidth * scaleFactor
        val height = drawable.intrinsicHeight * scaleFactor

        // Create a bitmap with scaled dimensions
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Scale the canvas
        canvas.scale(scaleFactor.toFloat(), scaleFactor.toFloat())

        // Draw the drawable onto the scaled canvas
        drawable.draw(canvas)

        return bitmap
    }


    @SuppressLint("MissingPermission")
    private fun addGeofence(latLng: LatLng, radius: Float) {
        val geofence = geofenceHelper.getGeofence(
            GEOFENCE_ID,
            latLng,
            radius,
            Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL or Geofence.GEOFENCE_TRANSITION_EXIT
        )
        val geofencingRequest = geofenceHelper.getGeofencingRequest(geofence)
        val pendingIntent = geofenceHelper.createPendingIntent()

        geofencingClient.addGeofences(geofencingRequest, pendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "Geofence added successfully.")
                Toast.makeText(this, "Geofence added for BNN College.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                val errorMessage = geofenceHelper.getErrorString(e)
                Log.e(TAG, "Failed to add geofence: $errorMessage")
                Toast.makeText(this, "Error: $errorMessage", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addCircle(latLng: LatLng, radius: Float) {
        val circleOptions = CircleOptions()
            .center(latLng)
            .radius(radius.toDouble())
            .strokeColor(Color.argb(255, 255, 0, 0))
            .fillColor(Color.argb(50, 255, 0, 0))
            .strokeWidth(4f)
        mMap.addCircle(circleOptions)
    }



    override fun onDestroy() {
        super.onDestroy()
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }



    companion object {
        private const val GEOFENCE_RADIUS = 50f
        private const val GEOFENCE_ID = "GEOFENCE_BNN_COLLEGE"

        private const val FINE_LOCATION_ACCESS_REQUEST_CODE = 10001
        private const val BACKGROUND_LOCATION_ACCESS_REQUEST_CODE = 10002
        private const val NOTIFICATION_REQUEST_CODE = 10003

        private const val NOTIFICATION_CHANNEL_ID = "DEVICE_STATE_CHANNEL"

        private const val TAG = "MapsActivity"
    }
}
