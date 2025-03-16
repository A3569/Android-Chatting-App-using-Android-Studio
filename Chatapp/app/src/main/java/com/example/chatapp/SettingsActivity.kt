package com.example.chatapp

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.chatapp.databinding.ActivitySettingsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage

// Activity for managing user settings and account preferences
class SettingsActivity : AppCompatActivity() {

    // View binding for accessing UI elements without findViewById
    private lateinit var binding: ActivitySettingsBinding
    
    // Reference to Firebase Realtime Database for storing user settings
    private lateinit var database: DatabaseReference
    
    // Name for the local shared preferences file
    private val prefsName = "ChatAppPrefs"
    
    // Keys for storing specific settings in shared preferences and Firebase
    private val keyNotifications = "notifications_enabled"
    private val keyPrivacy = "privacy_enabled"

    // Called when the activity is first created
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize view binding to access UI elements
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase database reference
        database = FirebaseDatabase.getInstance().reference

        // Set up toolbar with back button and title
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        // Load saved settings from both local storage and Firebase
        loadSettings()

        // Set up save button click listener to store settings
        binding.buttonSave.setOnClickListener {
            saveSettings()
        }

        // Set up delete account button click listener with confirmation dialog
        binding.btnDeleteAccount.setOnClickListener {
            showDeleteAccountConfirmation()
        }
    }

    // Loads user settings from local shared preferences and Firebase
    private fun loadSettings() {
        // Get local preferences from device storage
        val sharedPrefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        
        // Retrieve local settings with defaults (notifications on, privacy off)
        val notificationsEnabled = sharedPrefs.getBoolean(keyNotifications, true)
        val privacyEnabled = sharedPrefs.getBoolean(keyPrivacy, false)

        // Update UI switches with local values first
        binding.switchNotifications.isChecked = notificationsEnabled
        binding.switchPrivacy.isChecked = privacyEnabled

        // Try to load from Firebase if user is authenticated (these will override local settings)
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            // Query Firebase for user-specific settings
            database.child("users")
                .child(currentUser.uid)
                .child("settings")
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        // Only update UI if values are present in Firebase
                        snapshot.child(keyNotifications).getValue(Boolean::class.java)?.let {
                            binding.switchNotifications.isChecked = it
                        }
                        snapshot.child(keyPrivacy).getValue(Boolean::class.java)?.let {
                            binding.switchPrivacy.isChecked = it
                        }
                    }
                }
        }
    }

    // Saves user settings to both local shared preferences and Firebase
    private fun saveSettings() {
        try {
            // Get current values from UI switches
            val notificationsEnabled = binding.switchNotifications.isChecked
            val privacyEnabled = binding.switchPrivacy.isChecked

            // Save to local shared preferences first
            val sharedPrefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val editor = sharedPrefs.edit()
            editor.putBoolean(keyNotifications, notificationsEnabled)
            editor.putBoolean(keyPrivacy, privacyEnabled)
            editor.apply()

            // Save to Firebase if user is authenticated
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                // Create a map of settings to update in Firebase
                val settingsMap = mapOf(
                    keyNotifications to notificationsEnabled,
                    keyPrivacy to privacyEnabled
                )

                // Update Firebase with settings values
                database.child("users")
                    .child(currentUser.uid)
                    .child("settings")
                    .updateChildren(settingsMap)
                    .addOnSuccessListener {
                        // Show success message and close activity if save is successful
                        Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { _ ->

                        // If Firebase save fails, notify user that settings were only saved locally
                        Toast.makeText(this, "Cloud save failed, but settings saved locally", Toast.LENGTH_SHORT).show()
                        finish()
                    }
            } else {
                // User not authenticated, only saved locally - show appropriate message
                Toast.makeText(this, "Settings saved locally", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            // Handle any unexpected errors during save process
            Toast.makeText(this, "Error saving settings: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Shows a confirmation dialog before deleting account
    private fun showDeleteAccountConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_account_title)
            .setMessage(R.string.delete_account_message)
            .setPositiveButton(R.string.confirm) { _, _ ->

                // If user confirms deletion, proceed with account deletion process
                deleteUserAccount()
            }
            .setNegativeButton(R.string.cancel, null)
            .setCancelable(true)
            .show()
    }

    // Initiates the account deletion process
    private fun deleteUserAccount() {
        // Get current authenticated user, return if not signed in
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userId = currentUser.uid

        // Show progress message to user
        Toast.makeText(this, "Deleting account, please wait...", Toast.LENGTH_SHORT).show()

        // Delete user profile data from database
        database.child("users").child(userId).removeValue()
            .addOnCompleteListener { userDeletionTask ->
                if (userDeletionTask.isSuccessful) {
                    // Delete user's profile image from storage
                    val storageRef = FirebaseStorage.getInstance().reference
                    storageRef.child("profile_images/$userId").delete()
                        .addOnCompleteListener {
                            // Continue regardless of profile image deletion success
                            deleteUserChatsAndMessages(userId)
                        }
                } else {
                    // Show error if user data deletion fails
                    Toast.makeText(this, "Failed to delete user data: ${userDeletionTask.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Deletes user's chat data and messages from Firebase
    private fun deleteUserChatsAndMessages(userId: String) {
        // Get list of all the user's chats
        database.child("user-chats").child(userId).get()
            .addOnSuccessListener { userChatsSnapshot ->
                // For each chat, collect chat IDs to process message deletion
                val deleteChats = mutableListOf<String>()
                
                // Check if the user has any chats
                if (userChatsSnapshot.exists() && userChatsSnapshot.hasChildren()) {
                    // Collect all chat IDs the user is participating in
                    for (chatSnapshot in userChatsSnapshot.children) {
                        val chatId = chatSnapshot.child("chatId").getValue(String::class.java)
                        chatId?.let { deleteChats.add(it) }
                    }
                }
                
                // Delete the user's chats reference
                database.child("user-chats").child(userId).removeValue()
                    .addOnCompleteListener {
                        // Delete user's messages from all chats
                        for (chatId in deleteChats) {
                            database.child("chats").child(chatId).child("messages")
                                .get()
                                .addOnSuccessListener { messagesSnapshot ->

                                    // Check each message in the chat
                                    for (messageSnapshot in messagesSnapshot.children) {
                                        // Only delete messages sent by this user
                                        val senderId = messageSnapshot.child("senderId").getValue(String::class.java)
                                        if (senderId == userId) {
                                            messageSnapshot.ref.removeValue()
                                        }
                                    }
                                }
                        }
                        
                        // Delete the user's Firebase Auth account
                        deleteAuthAccount()
                    }
            }
            .addOnFailureListener {
                // If we can't get user chats, still try to delete auth account
                deleteAuthAccount()
            }
    }

    // Deletes the Firebase Authentication account for the user
    private fun deleteAuthAccount() {
        // Get current authenticated user, return if not signed in
        val user = FirebaseAuth.getInstance().currentUser ?: return
        
        // Delete the Firebase Auth account
        user.delete()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Show success message to user
                    Toast.makeText(this, "Account deleted successfully", Toast.LENGTH_LONG).show()
                    
                    // Clear all local shared preferences
                    getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().apply()
                    
                    // Navigate to login screen, clearing activity stack
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    // If deletion fails, notify the user with error details
                    Toast.makeText(this, "Failed to delete account: ${task.exception?.message}", 
                        Toast.LENGTH_LONG).show()
                }
            }
    }

    // Handles action bar item clicks, specifically the back button
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            // Handle back button press by finishing activity
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
