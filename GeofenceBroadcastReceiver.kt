package com.example.safeedutrack

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.safeedutrack.network.AuthHelper
import com.example.safeedutrack.network.SupabaseService
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


class GeofenceBroadcastReceiver : BroadcastReceiver() {


    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://yoururl.supabase.co") // Replace with your Supabase project URL
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val supabaseService: SupabaseService = retrofit.create(SupabaseService::class.java)


    @SuppressLint("LongLogTag")
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "BroadcastReceiver triggered.")
        Toast.makeText(context, "Geofence triggered", Toast.LENGTH_SHORT).show()

        val notificationHelper = NotificationHelper(context)

        // Obtain the geofencing event
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        // Check for errors
        if (geofencingEvent == null || geofencingEvent.hasError()) {
            val errorCode = geofencingEvent?.errorCode ?: GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE
            handleGeofenceError(context, errorCode)
            return
        }
        Log.d(TAG, "GeofencingEvent: $geofencingEvent")

        // Get triggering geofences and transition type
        val geofenceList = geofencingEvent.triggeringGeofences
        if (geofenceList.isNullOrEmpty()) {
            Log.e(TAG, "No triggering geofences found.")
            Toast.makeText(context, "No triggering geofences found.", Toast.LENGTH_SHORT).show()
            return
        }

        // Log all triggered geofences
        geofenceList.forEach { geofence ->
            Log.d(TAG, "Triggered Geofence: ${geofence.requestId}")
        }

        val transitionType = geofencingEvent.geofenceTransition

        // Handle specific geofence transitions
        handleGeofenceTransition(context, notificationHelper, transitionType, geofenceList)
    }

    @SuppressLint("LongLogTag")
    private fun handleGeofenceError(context: Context, errorCode: Int) {
        val authHelper = AuthHelper(context)

        when (errorCode) {
            GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> {
                Log.e(TAG, "Geofence service is not available.")
                Toast.makeText(context, "Geofence service is not available.", Toast.LENGTH_SHORT).show()
                CoroutineScope(Dispatchers.IO).launch {
                    val isSuccess = authHelper.storeUserNotification(context, supabaseService, "Geofence_Service", "Geofence service is not available.")

                    // Optionally, log or handle the success/failure response
                    if (isSuccess) {
                        Log.d("NotificationHelper", "Notification successfully stored in Supabase.")
                    } else {
                        Log.e("NotificationHelper", "Failed to store notification in Supabase.")
                    }
                }
            }
            GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES -> {
                Log.e(TAG, "Too many geofences registered.")
                Toast.makeText(context, "Too many geofences registered.", Toast.LENGTH_SHORT).show()
                CoroutineScope(Dispatchers.IO).launch {
                    val isSuccess = authHelper.storeUserNotification(context, supabaseService, "Geofence_overloaded", "Too many geofences registered.")

                    // Optionally, log or handle the success/failure response
                    if (isSuccess) {
                        Log.d("NotificationHelper", "Notification successfully stored in Supabase.")
                    } else {
                        Log.e("NotificationHelper", "Failed to store notification in Supabase.")
                    }
                }
            }
            GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS -> {
                Log.e(TAG, "Too many pending intents provided.")
                Toast.makeText(context, "Too many pending intents provided.", Toast.LENGTH_SHORT).show()
                CoroutineScope(Dispatchers.IO).launch {
                    val isSuccess = authHelper.storeUserNotification(context, supabaseService, "Geofence_overloaded_PendingIntents", "Too many pending intents provided.")

                    // Optionally, log or handle the success/failure response
                    if (isSuccess) {
                        Log.d("NotificationHelper", "Notification successfully stored in Supabase.")
                    } else {
                        Log.e("NotificationHelper", "Failed to store notification in Supabase.")
                    }
                }
            }
            else -> {
                Log.e(TAG, "Unknown geofence error occurred. Error code: $errorCode")
                Toast.makeText(context, "Unknown geofence error occurred. Error code: $errorCode", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val geofenceStates = mutableMapOf<String, GeofenceState>()

    private enum class GeofenceState {
        NONE,      // No active cycle (or reset after exit)
        ENTERED,   // ENTER event received
        DWELLING   // DWELL event received after enter
    }

    private fun handleGeofenceTransition(
        context: Context,
        notificationHelper: NotificationHelper,
        transitionType: Int,
        geofenceList: List<Geofence>
    ) {
        val TAG = "GeofenceTransition"
        // Create your AuthHelper instance once.
        val authHelper = AuthHelper(context)

        geofenceList.forEach { geofence ->
            val geofenceId = geofence.requestId
            // Retrieve the current state; default to NONE if not found.
            val currentState = geofenceStates[geofenceId] ?: GeofenceState.NONE

            when (transitionType) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> {
                    // Process ENTER only if no active cycle is in progress.
                    if (currentState == GeofenceState.NONE) {
                        Log.d(TAG, "🚀 [ENTER] Geofence: $geofenceId -> Current state: $currentState. Processing ENTER event!")
                        // Immediately update state to indicate an ENTER event.
                        geofenceStates[geofenceId] = GeofenceState.ENTERED

                        val transitionMessage = "You have entered the geofenced area: $geofenceId"
                        val notificationTitle = "Geofence Entered"

                        CoroutineScope(Dispatchers.IO).launch {
                            // Retrieve the user email from LoginPrefs.
                            val loginPrefs = context.getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
                            val userEmail = loginPrefs.getString("userEmail", null)
                            if (userEmail.isNullOrEmpty()) {
                                Log.e(TAG, "❌ [ENTER] Geofence: $geofenceId -> Missing user email in LoginPrefs. Skipping ENTER notification!")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "User email not found. Please log in.", Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }
                            Log.d(TAG, "🎯 [ENTER] Fetched user email: $userEmail for geofence: $geofenceId")

                            // --- Local duplicate check using TansPrefs ---
                            val tansPrefs = context.getSharedPreferences("TansPrefs", Context.MODE_PRIVATE)
                            val lastTransitionMsg = tansPrefs.getString("lastTransitionMsg", null)
                            if (lastTransitionMsg != null && lastTransitionMsg == transitionMessage) {
                                Log.d(TAG, "⚠️ [ENTER] Duplicate transition message detected in TansPrefs for geofence: $geofenceId. Skipping notification!")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Already inside Geofence!", Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }
                            // --- End local duplicate check ---

                            // Fetch the latest notifications from Supabase.
                            val latestNotifications = authHelper.fetchLatestUserNotificationsFromSupabase(context, supabaseService)
                            val latestNotification = latestNotifications.firstOrNull { it.user_email == userEmail }
                            if (latestNotification != null) {
                                Log.d(TAG, "ℹ️ [ENTER] Latest notification for $userEmail: ${latestNotification.body}")
                            } else {
                                Log.d(TAG, "ℹ️ [ENTER] No previous notification found for $userEmail.")
                            }

                            // Check if the new notification message is the same as the previous one from Supabase.
                            if (latestNotification != null && latestNotification.body == transitionMessage) {
                                Log.d(TAG, "⚠️ [ENTER] Duplicate notification detected for $userEmail via Supabase. Skipping ENTER notification!")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Duplicate ENTER notification for $userEmail; skipped.", Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }

                            // Not a duplicate: send the notification and toast.
                            withContext(Dispatchers.Main) {
                                sendNotificationAndToast(context, notificationHelper, transitionMessage, notificationTitle)
                            }
                            // Immediately store the transition message in TansPrefs.
                            tansPrefs.edit().putString("lastTransitionMsg", transitionMessage).apply()

                            val isSuccess = authHelper.storeUserNotification(
                                context,
                                supabaseService,
                                notificationTitle,
                                transitionMessage
                            )
                            if (isSuccess) {
                                Log.d(TAG, "✅ [ENTER] Notification successfully stored in Supabase for geofence: $geofenceId")
                            } else {
                                Log.e(TAG, "❌ [ENTER] Failed to store notification in Supabase for geofence: $geofenceId")
                            }
                        }
                    } else {
                        Log.d(TAG, "ℹ️ [ENTER] Geofence: $geofenceId -> Current state: $currentState. ENTER event ignored!")
                    }
                }

                Geofence.GEOFENCE_TRANSITION_DWELL -> {
                    // Process DWELL only if a valid ENTER event was previously received.
                    if (currentState == GeofenceState.ENTERED) {
                        Log.d(TAG, "🚀 [DWELL] Geofence: $geofenceId -> Valid ENTER detected. Processing DWELL event!")
                        // Update state to DWELLING.
                        geofenceStates[geofenceId] = GeofenceState.DWELLING

                        val transitionMessage = "You are dwelling within the geofenced area: $geofenceId"
                        val notificationTitle = "Geofence Dwelling"

                        CoroutineScope(Dispatchers.IO).launch {
                            val loginPrefs = context.getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
                            val userEmail = loginPrefs.getString("userEmail", null)
                            if (userEmail.isNullOrEmpty()) {
                                Log.e(TAG, "❌ [DWELL] Geofence: $geofenceId -> Missing user email in LoginPrefs. Skipping DWELL notification!")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "User email not found. Please log in.", Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }
                            Log.d(TAG, "🎯 [DWELL] Fetched user email: $userEmail for geofence: $geofenceId")

                            // --- Local duplicate check using TansPrefs ---
                            val tansPrefs = context.getSharedPreferences("TansPrefs", Context.MODE_PRIVATE)
                            val lastTransitionMsg = tansPrefs.getString("lastTransitionMsg", null)
                            if (lastTransitionMsg != null && lastTransitionMsg == transitionMessage) {
                                Log.d(TAG, "⚠️ [DWELL] Duplicate transition message detected in TansPrefs for geofence: $geofenceId. Skipping notification!")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Dwelling within Geofence!", Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }
                            // --- End local duplicate check ---

                            val latestNotifications = authHelper.fetchLatestUserNotificationsFromSupabase(context, supabaseService)
                            val latestNotification = latestNotifications.firstOrNull { it.user_email == userEmail }
                            if (latestNotification != null) {
                                Log.d(TAG, "ℹ️ [DWELL] Latest notification for $userEmail: ${latestNotification.body}")
                            } else {
                                Log.d(TAG, "ℹ️ [DWELL] No previous notification found for $userEmail.")
                            }
                            if (latestNotification != null && latestNotification.body == transitionMessage) {
                                Log.d(TAG, "⚠️ [DWELL] Duplicate notification detected for $userEmail via Supabase. Skipping DWELL notification!")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Duplicate DWELL notification for $userEmail; skipped.", Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }

                            withContext(Dispatchers.Main) {
                                sendNotificationAndToast(context, notificationHelper, transitionMessage, notificationTitle)
                            }
                            // Store the transition event in TansPrefs.
                            tansPrefs.edit().putString("lastTransitionMsg", transitionMessage).apply()

                            val isSuccess = authHelper.storeUserNotification(
                                context,
                                supabaseService,
                                notificationTitle,
                                transitionMessage
                            )
                            if (isSuccess) {
                                Log.d(TAG, "✅ [DWELL] Notification successfully stored in Supabase for geofence: $geofenceId")
                            } else {
                                Log.e(TAG, "❌ [DWELL] Failed to store notification in Supabase for geofence: $geofenceId")
                            }
                        }
                    } else {
                        Log.d(TAG, "ℹ️ [DWELL] Geofence: $geofenceId -> Current state: $currentState. DWELL event ignored!")
                    }
                }

                Geofence.GEOFENCE_TRANSITION_EXIT -> {
                    // Process EXIT only if the geofence is currently active.
                    if (currentState != GeofenceState.NONE) {
                        Log.d(TAG, "🚀 [EXIT] Geofence: $geofenceId -> Active state: $currentState. Processing EXIT event!")
                        // Reset the state so that a new cycle (ENTER) can be processed later.
                        geofenceStates[geofenceId] = GeofenceState.NONE

                        val transitionMessage = "You have exited the geofenced area: $geofenceId"
                        val notificationTitle = "Geofence Exited"

                        CoroutineScope(Dispatchers.IO).launch {
                            val loginPrefs = context.getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
                            val userEmail = loginPrefs.getString("userEmail", null)
                            if (userEmail.isNullOrEmpty()) {
                                Log.e(TAG, "❌ [EXIT] Geofence: $geofenceId -> Missing user email in LoginPrefs. Skipping EXIT notification!")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "User email not found. Please log in.", Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }
                            Log.d(TAG, "🎯 [EXIT] Fetched user email: $userEmail for geofence: $geofenceId")

                            // --- Local duplicate check using TansPrefs ---
                            val tansPrefs = context.getSharedPreferences("TansPrefs", Context.MODE_PRIVATE)
                            val lastTransitionMsg = tansPrefs.getString("lastTransitionMsg", null)
                            if (lastTransitionMsg != null && lastTransitionMsg == transitionMessage) {
                                Log.d(TAG, "⚠️ [EXIT] Duplicate transition message detected in TansPrefs for geofence: $geofenceId. Skipping notification!")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Geofence already exited!", Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }
                            // --- End local duplicate check ---

                            val latestNotifications = authHelper.fetchLatestUserNotificationsFromSupabase(context, supabaseService)
                            val latestNotification = latestNotifications.firstOrNull { it.user_email == userEmail }
                            if (latestNotification != null) {
                                Log.d(TAG, "ℹ️ [EXIT] Latest notification for $userEmail: ${latestNotification.body}")
                            } else {
                                Log.d(TAG, "ℹ️ [EXIT] No previous notification found for $userEmail.")
                            }
                            if (latestNotification != null && latestNotification.body == transitionMessage) {
                                Log.d(TAG, "⚠️ [EXIT] Duplicate notification detected for $userEmail via Supabase. Skipping EXIT notification!")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Duplicate EXIT notification for $userEmail; skipped.", Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }

                            withContext(Dispatchers.Main) {
                                sendNotificationAndToast(context, notificationHelper, transitionMessage, notificationTitle)
                            }
                            // Store the transition event in TansPrefs.
                            tansPrefs.edit().putString("lastTransitionMsg", transitionMessage).apply()

                            val isSuccess = authHelper.storeUserNotification(
                                context,
                                supabaseService,
                                notificationTitle,
                                transitionMessage
                            )
                            if (isSuccess) {
                                Log.d(TAG, "✅ [EXIT] Notification successfully stored in Supabase for geofence: $geofenceId")
                            } else {
                                Log.e(TAG, "❌ [EXIT] Failed to store notification in Supabase for geofence: $geofenceId")
                            }
                        }
                    } else {
                        Log.d(TAG, "ℹ️ [EXIT] Geofence: $geofenceId -> Already in state NONE. EXIT event ignored!")
                    }
                }

                else -> {
                    Log.w(TAG, "⚠️ [UNKNOWN] Unknown geofence transition type: $transitionType. Event ignored!")
                    return
                }
            }
        }
    }


    private fun sendNotificationAndToast(
        context: Context,
        notificationHelper: NotificationHelper,
        message: String,
        title: String
    ) {
        // Show Toast message
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

        // Send notification
        notificationHelper.sendHighPriorityNotification(
            title,
            message,
            MapsActivity::class.java // You can replace this with any other activity
        )
    }

    companion object {
        private const val TAG = "GeofenceBroadcastReceiver"
    }
}
