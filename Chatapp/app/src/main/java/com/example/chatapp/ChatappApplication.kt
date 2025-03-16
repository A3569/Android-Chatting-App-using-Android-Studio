package com.example.chatapp

import android.util.Log
import androidx.multidex.MultiDexApplication
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.example.chatapp.util.UserPresenceUtil

// Initializes Firebase services and other application-wide components when the app starts
class ChatappApplication : MultiDexApplication() {
    
    // Companion object contains static properties and initializers for the application
    companion object {
        // Tag for logging - helps identify logs from this class
        private const val TAG = "ChatappApplication"
        
        // Flag to track if Firebase persistence has been enabled
        private var persistenceInitialized = false
        
        // Static initializer block that runs when the class is loaded
        init {
            try {
                // Enable Firebase persistence to allow offline data access
                FirebaseDatabase.getInstance().setPersistenceEnabled(true)
                
                // Mark persistence as initialized to avoid duplicate calls
                persistenceInitialized = true
                
                // Log successful initialization for debugging
                Log.d(TAG, "Firebase persistence enabled successfully in static initializer")
            } catch (e: Exception) {
                // Log any errors that occur during persistence setup
                Log.e(TAG, "Error enabling Firebase persistence in static initializer: ${e.message}", e)
            }
        }
    }
    
    // Called when the application is starting
    override fun onCreate() {
        // Always call the superclass implementation first
        super.onCreate()
        
        try {
            // Initialize Firebase SDK with application context
            FirebaseApp.initializeApp(this)
            
            // Double check persistence status - it should already be initialized in the static block
            if (!persistenceInitialized) {
                // Log a warning if persistence isn't already set up
                Log.w(TAG, "Firebase persistence should already be initialized in static block")
            }
            
            // Initialize Firebase App Check for additional security
            try {
                // Get App Check instance
                val firebaseAppCheck = FirebaseAppCheck.getInstance()
                
                // Get the debug token from resources
                val debugToken = getString(R.string.firebase_app_check_debug_token)
                
                // Set the debug token as a system property
                System.setProperty("firebase.appcheck.debug.token", debugToken)
                
                // Install the debug provider for development
                firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                
                // Log successful initialization of App Check
                Log.d(TAG, "Firebase App Check initialized successfully with debug token")
            } catch (e: Exception) {
                // Log App Check initialization errors
                Log.e(TAG, "Error initializing Firebase App Check: ${e.message}", e)
            }
            
            // Log the database URL to help with debugging
            try {
                // Get and log the database URL
                val databaseUrl = FirebaseDatabase.getInstance().reference.toString()
                Log.d(TAG, "Firebase Database URL: $databaseUrl")
            } catch (e: Exception) {
                // Log errors when trying to access the database URL
                Log.e(TAG, "Error getting Firebase database URL: ${e.message}", e)
            }
            
            // Initialize user presence system
            try {
                // Set up the presence monitoring system
                UserPresenceUtil.setupUserPresence()
                
                // Log successful initialization
                Log.d(TAG, "User presence system initialized")
            } catch (e: Exception) {
                // Log errors in presence system setup
                Log.e(TAG, "Error setting up user presence: ${e.message}", e)
            }
            
            // Verify Firebase Storage is accessible
            try {
                // Get a reference to the storage root
                val storageRef = FirebaseStorage.getInstance().reference
                
                // Log successful storage initialization
                Log.d(TAG, "Firebase Storage initialized: $storageRef")
                
                // Verify the storage bucket exists and is configured correctly
                val bucket = FirebaseStorage.getInstance().app.options.storageBucket
                
                // Check if bucket is configured
                if (bucket.isNullOrEmpty()) {
                    // Log error if bucket is missing - image uploads will fail
                    Log.e(TAG, "Firebase Storage bucket is not configured! Uploads will fail")
                } else {
                    // Log bucket name for verification
                    Log.d(TAG, "Firebase Storage bucket: $bucket")
                }
            } catch (e: Exception) {
                // Log storage initialization errors
                Log.e(TAG, "Error initializing Firebase Storage: ${e.message}", e)
            }
            
            // Verify Firebase connection and handle network issues
            try {
                // Reference the special Firebase path that indicates connection status
                FirebaseDatabase.getInstance().getReference(".info/connected")
                    .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                        // Called when the connection state changes
                        override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                            // Extract the boolean connection status
                            val connected = snapshot.getValue(Boolean::class.java) ?: false
                            
                            // Log appropriate message based on connection state
                            if (connected) {
                                Log.d(TAG, "Connected to Firebase Realtime Database")
                            } else {
                                Log.w(TAG, "Not connected to Firebase Realtime Database")
                            }
                        }

                        // Called if the listener is cancelled or encounters an error
                        override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                            // Log error if connection check fails
                            Log.e(TAG, "Error checking Firebase connection: ${error.message}")
                        }
                    })
            } catch (e: Exception) {
                // Log errors with setting up the connection monitor
                Log.e(TAG, "Error setting up Firebase connection listener: ${e.message}", e)
            }
        } catch (e: Exception) {
            // Catch-all for any other Firebase initialization errors
            Log.e(TAG, "Error initializing Firebase: ${e.message}", e)
        }
    }
} 