package com.example.safeedutrack

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.safeedutrack.databinding.ActivityAdminMapBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import android.app.Activity
import android.graphics.Color
import com.example.safeedutrack.network.AuthHelper
import com.example.safeedutrack.network.SupabaseService
import com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.Marker
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AdminMap : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityAdminMapBinding
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://xdfyutgivtoadckozjbc.supabase.co") // Replace with your Supabase project URL
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val supabaseService: SupabaseService = retrofit.create(SupabaseService::class.java)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAdminMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        requestLocationPermission(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        val bnnCollege = LatLng(19.28493235362456, 73.05533516285809)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(bnnCollege, 19f))
        mMap.animateCamera(CameraUpdateFactory.newLatLng(bnnCollege))
        // Add Circle and Geofence for BNN College with a proper check
        addCircle(bnnCollege, 50f)



        // Enable user location if permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }


        // Call the function to display all users on the map
        displayAllUsersOnMap(this, supabaseService)

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


    private fun displayAllUsersOnMap(context: Context, supabaseService: SupabaseService) {
        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            Log.e("Permission", "Location permission is not granted.")
            return
        }

        val locationRequest = LocationRequest.Builder(1000L)
            .setMinUpdateIntervalMillis(500L)
            .setPriority(PRIORITY_HIGH_ACCURACY)
            .build()

        val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
        val userMarkers = mutableMapOf<String, Marker>()  // Track user markers

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                location?.let {
                    val currentLatLng = LatLng(location.latitude, location.longitude)


                    // Fetch and update all users' locations in real time
                    CoroutineScope(Dispatchers.Main).launch {
                        val authHelper = AuthHelper(context)
                        val users = authHelper.fetchLatestUserLocationsFromSupabase(context, supabaseService)

                        // Remove old markers before adding new ones
                        for ((_, marker) in userMarkers) {
                            marker.remove()
                        }
                        userMarkers.clear()

                        if (users.isNotEmpty()) {
                            for (user in users) {
                                val userLatLng = LatLng(user.latitude, user.longitude)

                                // Add new marker for each user
                                val marker = mMap.addMarker(
                                    MarkerOptions()
                                        .position(userLatLng)
                                        .title("User: ${user.user_email}")
                                        .snippet("User Email: ${user.user_email}")
                                        .icon(BitmapDescriptorFactory.fromBitmap(getBitmapFromVectorDrawable(context, R.drawable.user_moving_icon)))
                                )
                                if (marker != null) {
                                    userMarkers[user.user_email] = marker  // Use user_email as key
                                }

                            }
                        } else {
                            Log.e("AdminMap", "No users found in Supabase.")
                        }
                    }
                }
            }
        }

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }


    fun requestLocationPermission(context: Context) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                context as Activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Show an explanation to the user why you need the permission
            Log.e("Permission", "Location permission is required for real-time updates.")
        } else {
            // Request the permission
            ActivityCompat.requestPermissions(context, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
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
}

