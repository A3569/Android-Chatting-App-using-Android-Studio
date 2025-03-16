package com.example.chatapp.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

// Utility object for managing user online/offline presence status in the chat app
object UserPresenceUtil {
    
    // Tag for logging from this class
    private const val TAG = "UserPresenceUtil"
    
    // Flag to prevent duplicate initialization
    private var isInitialized = false
    
    // Sets up the user's presence monitoring system
    fun setupUserPresence() {
        try {
            // Check if we've already initialized presence for this session
            if (isInitialized) {
                Log.d(TAG, "User presence already initialized")
                return
            }
            
            // Get the current user from Firebase Authentication
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                // Can't proceed if user isn't authenticated
                Log.w(TAG, "Cannot setup presence for null user")
                return
            }
            
            // Get the user ID for the current authenticated user
            val userId = currentUser.uid
            
            // Get reference to the Firebase database
            val database = FirebaseDatabase.getInstance().reference
            
            // Create a reference to this user's specific status node in the database
            val userStatusDatabaseRef = database.child("users").child(userId)
            
            // Create a reference to Firebase's special ".info/connected" node
            val infoConnectedRef = database.child(".info/connected")
            
            // Add a listener to detect connection state changes
            infoConnectedRef.addValueEventListener(object : ValueEventListener {
                // Called when the connection state changes
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        // Extract boolean connection status from the snapshot
                        val connected = snapshot.getValue(Boolean::class.java) ?: false
                        
                        // Only proceed if we're actually connected
                        if (connected) {
                            // User is online - update their status immediately
                            userStatusDatabaseRef.child("status").setValue("Online")
                            
                            // When this device disconnects, update the last seen time
                            userStatusDatabaseRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)
                            
                            // Set status to "Offline" when user disconnects
                            userStatusDatabaseRef.child("status").onDisconnect().setValue("Offline")
                        }
                    } catch (e: Exception) {
                        // Log any errors but don't crash the app
                        Log.e(TAG, "Error in connected listener: ${e.message}", e)
                    }
                }
                
                // Called when the listener is cancelled
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error in connecting to presence service: ${error.message}", error.toException())
                }
            })
            
            // Mark as initialized to prevent duplicate setup
            isInitialized = true
            Log.d(TAG, "User presence initialized for user $userId")
        } catch (e: Exception) {
            // Catch and log any exceptions during setup
            Log.e(TAG, "Failed to setup user presence: ${e.message}", e)
        }
    }
} 