package com.example.chatapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.chatapp.databinding.ActivityProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.example.chatapp.util.ImageUploadUtil
import com.example.chatapp.util.Constants

// Activity for viewing and editing user profile information
class ProfileActivity : AppCompatActivity() {
    
    // View binding for accessing UI elements without findViewById
    private lateinit var binding: ActivityProfileBinding
    
    // URI of the selected image for profile picture
    private var selectedImageUri: Uri? = null
    
    // Tag for logging messages from this activity
    private val tag = "ProfileActivity"
    
    // Adapter for the status dropdown menu
    private lateinit var statusAdapter: ArrayAdapter<String>
    
    // ActivityResultLauncher for handling image selection from gallery
    private val imagePickerLauncher: ActivityResultLauncher<Intent> = 
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            // Check if result is successful and contains data
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                // Store the URI of the selected image
                selectedImageUri = result.data?.data
                
                // Preview the selected image in the profile picture ImageView
                selectedImageUri?.let {
                    binding.ivProfilePic.setImageURI(it)
                }
            }
        }
    
    // ActivityResultLauncher for handling permission requests
    private val requestPermissionLauncher = 
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->

            // Check if all requested permissions were granted
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                // If all permissions granted, open image chooser
                openImageChooser()
            } else {
                // Show message explaining why permission is needed
                Toast.makeText(this, "Storage permission required to select images", Toast.LENGTH_SHORT).show()
            }
        }
    
    // Called when the activity is first created
    override fun onCreate(savedInstanceState: Bundle?) {
        // Call parent implementation first
        super.onCreate(savedInstanceState)
        
        // Initialize view binding to access UI elements
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up the toolbar with back button
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Check if this is a new user registration
        val isNewUser = intent.getBooleanExtra("IS_NEW_USER", false)
        if (isNewUser) {
            // Set different title and instructions for new users
            supportActionBar?.title = "Welcome to ChatApp!"
            Toast.makeText(this, "Please set your username and profile picture", Toast.LENGTH_LONG).show()
        } else {
            // Regular title for existing users
            supportActionBar?.title = "Profile"
        }
        
        // Set up the status dropdown with predefined options
        setupStatusDropdown()
        
        // Load the current user's profile data from Firebase
        loadUserProfile()
        
        // Set up Update Profile button click listener
        binding.btnUpdateProfile.setOnClickListener {
            updateUserProfile()
        }
        
        // Set up Change Photo button click listener
        binding.btnChangePhoto.setOnClickListener {
            checkStoragePermissionAndOpenImageChooser()
        }
        
        // Set up Reset to Default button click listener
        binding.btnResetToDefault.setOnClickListener {
            // Reset to default profile image
            selectedImageUri = null
            
            // Display the default profile image
            binding.ivProfilePic.setImageResource(R.drawable.default_profile)
            
            // Set tag to indicate reset was clicked
            binding.btnResetToDefault.tag = "clicked"
            
            // Show confirmation message to user
            Toast.makeText(this, "Profile image will be reset to default on save", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Sets up the status dropdown menu with predefined status options
    private fun setupStatusDropdown() {
        // Get status options from string array resource
        val statusOptions = resources.getStringArray(R.array.status_options)
        
        // Create adapter for the dropdown with the status options
        statusAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statusOptions)
        
        // Set the adapter to the status dropdown
        (binding.tilStatus.editText as? AutoCompleteTextView)?.setAdapter(statusAdapter)
    }
    
    // Loads the current user's profile data from Firebase
    private fun loadUserProfile() {
        // Get the current authenticated user, return if not signed in
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        
        // Get reference to Firebase database
        val database = FirebaseDatabase.getInstance().reference
        
        // Query the user data for the current user
        database.child("users").child(currentUser.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Extract user data fields from the snapshot
                    val username = snapshot.child("username").getValue(String::class.java) ?: ""
                    val status = snapshot.child("status").getValue(String::class.java) ?: "Online"
                    val profileImageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)
                    val phoneNumber = snapshot.child("phoneNumber").getValue(String::class.java) ?: ""
                    
                    // Populate the UI fields with retrieved data
                    binding.etUsername.setText(username)
                    binding.etPhoneNumber.setText(phoneNumber)
                    
                    // Set the status dropdown value without triggering dropdown expansion
                    (binding.tilStatus.editText as? AutoCompleteTextView)?.setText(status, false)
                    
                    // Load profile image into ImageView using the utility
                    ImageUploadUtil.loadImage(this@ProfileActivity, profileImageUrl, binding.ivProfilePic)
                    
                    // Reset the reset button tag (in case user revisits from another screen)
                    binding.btnResetToDefault.tag = null
                }
                
                override fun onCancelled(error: DatabaseError) {
                    // Show error message if data loading fails
                    Toast.makeText(this@ProfileActivity, "Failed to load profile", Toast.LENGTH_SHORT).show()
                }
            })
    }
    
    // Updates the user's profile information in Firebase
    @SuppressLint("SetTextI18n")
    private fun updateUserProfile() {
        // Get current user or return if not authenticated
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        
        // Get values from input fields
        val username = binding.etUsername.text.toString().trim()
        val status = (binding.tilStatus.editText as? AutoCompleteTextView)?.text.toString().trim()
        
        // Validate username is not empty
        if (username.isEmpty()) {
            binding.tilUsername.error = "Username cannot be empty"
            return
        } else {
            binding.tilUsername.error = null
        }
        
        // Show progress by updating button state
        binding.btnUpdateProfile.isEnabled = false
        binding.btnUpdateProfile.text = "Updating..."
        
        // If reset to default was clicked, use the default profile image
        if (binding.btnResetToDefault.tag == "clicked") {
            Log.d(tag, "Resetting profile image to default")
            updateUserData(currentUser.uid, username, status, Constants.DEFAULT_PROFILE_IMAGE)
            return
        }
        
        // If an image was selected, upload it first before updating profile data
        if (selectedImageUri != null) {
            Log.d(tag, "Starting image upload: $selectedImageUri")
            
            // Use the utility to upload the image to Firebase Storage
            ImageUploadUtil.uploadImage(
                selectedImageUri!!,
                "profile_images/${currentUser.uid}",
                onSuccess = { imageUrl ->

                    // On successful upload, update profile with the new image URL
                    Log.d(tag, "Image uploaded successfully: $imageUrl")
                    updateUserData(currentUser.uid, username, status, imageUrl)
                },
                onFailure = { e ->

                    // On upload failure, show error and reset button state
                    Log.e(tag, "Failed to upload image: ${e.message}", e)
                    Toast.makeText(this, "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.btnUpdateProfile.isEnabled = true
                    binding.btnUpdateProfile.text = "Update Profile"
                }
            )
        } else {
            // No new image selected, just update the other profile data
            updateUserData(currentUser.uid, username, status, null)
        }
    }
    
    // Updates the user data in Firebase with the provided information
    @SuppressLint("SetTextI18s", "SetTextI18n")
    private fun updateUserData(userId: String, username: String, status: String, imageUrl: String?) {
        // Create a map of fields to update
        val updates = HashMap<String, Any>()
        updates["username"] = username
        updates["status"] = status
        
        // Only include the image URL if it was provided
        if (imageUrl != null) {
            updates["profileImageUrl"] = imageUrl
        }
        
        Log.d(tag, "Updating user data: $updates")
        
        // Update the user data in Firebase
        FirebaseDatabase.getInstance().reference
            .child("users").child(userId)
            .updateChildren(updates)
            .addOnCompleteListener { task ->
            
                // Re-enable the update button and restore text
                binding.btnUpdateProfile.isEnabled = true
                binding.btnUpdateProfile.text = "Update Profile"
                
                if (task.isSuccessful) {
                    // Show success message and finish activity
                    Log.d(tag, "Profile updated successfully")
                    Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    // Show error message
                    Log.e(tag, "Failed to update profile: ${task.exception?.message}")
                    Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show()
                }
            }
    }
    
    // Checks if the app has the required permissions to access storage and opens the image chooser if granted
    private fun checkStoragePermissionAndOpenImageChooser() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Checks READ_MEDIA_IMAGES
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                    != PackageManager.PERMISSION_GRANTED) {
                // Request permission if not granted
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES))
            } else {
                // Open image chooser if permission already granted
                openImageChooser()
            }
        } else {
            // Checks READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                // Request permission if not granted
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
            } else {
                // Open image chooser if permission already granted
                openImageChooser()
            }
        }
    }
    
    // Opens the system image chooser to select a profile picture
    private fun openImageChooser() {
        try {
            // Create an intent to pick an image
            val intent = Intent().apply {
                type = "image/*"
                action = Intent.ACTION_GET_CONTENT
            }
            
            Log.d(tag, "Opening image chooser")
            
            // Launch the image chooser using our registered launcher
            imagePickerLauncher.launch(Intent.createChooser(intent, "Select Picture"))
        } catch (e: Exception) {
            // Handle any errors during image chooser launch
            Log.e(tag, "Error opening image chooser: ${e.message}", e)
            Toast.makeText(this, "Failed to open image picker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Handles selection of menu items in the toolbar
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            // Handle back button press
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
} 