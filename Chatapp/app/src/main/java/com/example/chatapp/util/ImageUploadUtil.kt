package com.example.chatapp.util

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.example.chatapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import java.util.UUID

// Utility object for handling image uploading and loading operations
object ImageUploadUtil {
    
    // Tag for identifying log messages from this class
    private const val TAG = "ImageUploadUtil"
    
    // Instance of Firebase Storage for all storage operations
    private val storage = FirebaseStorage.getInstance()
    
    // Reference to the root of our Firebase Storage
    private val storageRef = storage.reference
    
    // Uploads an image to Firebase Storage
    fun uploadImage(
        imageUri: Uri,
        path: String,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit,
        maxRetries: Int = 2,
        currentRetry: Int = 0
    ) {
        try {
            // Log the start of the upload attempt with the current retry count
            Log.d(TAG, "Starting upload to path: $path, URI: $imageUri (attempt ${currentRetry + 1})")
            
            // Check if user is authenticated before proceeding
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                // If not authenticated, fail early with a clear error message
                Log.e(TAG, "User is not authenticated")
                onFailure(Exception("You must be logged in to upload files"))
                return
            }
            
            // Log the authenticated user for debugging
            Log.d(TAG, "User is authenticated as ${currentUser.uid}")
            
            // Verify that Firebase Storage is properly configured
            val bucket = storage.app.options.storageBucket
            if (bucket.isNullOrEmpty()) {
                // If bucket is not configured, fail with a clear error
                Log.e(TAG, "Firebase Storage bucket is not configured!")
                onFailure(Exception("Firebase Storage not properly configured"))
                return
            }
            
            // Create a unique filename using UUID to prevent collisions
            val fileName = UUID.randomUUID().toString()
            
            // Create a reference to the specific file location in Firebase Storage
            val fileRef = storageRef.child("$path/$fileName")
            
            // Log the exact storage path for debugging
            Log.d(TAG, "Uploading to: ${fileRef.path}")
            
            // Check if the URI is actually accessible before attempting upload
            try {
                // Try to open an input stream to verify we can read from this URI
                storage.app.applicationContext.contentResolver.openInputStream(imageUri)?.use { 
                    Log.d(TAG, "URI is accessible")
                }
            } catch (e: Exception) {
                // If URI is not accessible, log the error and fail with a user-friendly message
                Log.e(TAG, "Cannot access URI: ${e.message}", e)
                onFailure(Exception("Cannot access selected image. Please try another."))
                return
            }
            
            // Start the actual upload operation
            fileRef.putFile(imageUri)

                // Add a progress listener to track upload progress
                .addOnProgressListener { taskSnapshot ->

                    // Calculate percentage progress for display
                    val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                    Log.d(TAG, "Upload is $progress% done")
                }
                // Add a success listener for the upload operation
                .addOnSuccessListener {
                    // When upload completes, log success and get the download URL
                    Log.d(TAG, "Upload successful, getting download URL...")
                    
                    // Get the download URL for the uploaded file
                    fileRef.downloadUrl
                        .addOnSuccessListener { uri ->

                            // When download URL is available, call the success callback with the URL
                            Log.d(TAG, "Image uploaded successfully: $uri")
                            onSuccess(uri.toString())
                        }
                        .addOnFailureListener { e ->
                        
                            // If we can't get the download URL, log the error
                            Log.e(TAG, "Failed to get download URL: ${e.message}")
                            
                            // Implement retry logic for download URL failure
                            if (currentRetry < maxRetries) {
                                // Log the retry attempt
                                Log.w(TAG, "Retrying to get download URL (${currentRetry + 1}/$maxRetries)")
                                
                                // Try again just for the URL
                                fileRef.downloadUrl
                                    .addOnSuccessListener { uri ->
                                    
                                        // If retry succeeds, call the success callback
                                        Log.d(TAG, "Image URL retrieved on retry: $uri")
                                        onSuccess(uri.toString())
                                    }
                                    .addOnFailureListener { retryError ->

                                        // If retry fails, handle the storage exception
                                        handleStorageException(retryError, onFailure)
                                    }
                            } else {
                                // If we've exceeded max retries, handle the error
                                handleStorageException(e, onFailure)
                            }
                        }
                }
                // Add a failure listener for the upload operation
                .addOnFailureListener { e ->

                    // If upload fails, log the error
                    Log.e(TAG, "Failed to upload image: ${e.message}")
                    
                    // Implement retry logic for upload failure
                    if (currentRetry < maxRetries) {
                        // Log the retry attempt
                        Log.w(TAG, "Retrying upload (${currentRetry + 1}/$maxRetries)")
                        
                        // Wait a moment before retrying to allow potential transient issues to resolve
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            // Recursively call uploadImage with incremented retry count
                            uploadImage(imageUri, path, onSuccess, onFailure, maxRetries, currentRetry + 1)
                        }, 1000)
                    } else {
                        // If we've exceeded max retries, handle the error
                        handleStorageException(e, onFailure)
                    }
                }
        } catch (e: Exception) {
            // Catch any other exceptions that might occur
            Log.e(TAG, "Exception during image upload: ${e.message}")
            onFailure(e)
        }
    }
    
    // Handles FirebaseStorage-specific exceptions with appropriate error messages
    private fun handleStorageException(e: Exception, onFailure: (Exception) -> Unit) {
        // Check if this is a Firebase StorageException
        if (e is StorageException) {
            // Handle different StorageException error codes with specific messages
            when (e.errorCode) {
                StorageException.ERROR_BUCKET_NOT_FOUND -> {
                    // Storage bucket not found
                    Log.e(TAG, "Storage bucket not found. Check Firebase console configuration.")
                    onFailure(Exception("Storage configuration error: Bucket not found"))
                }
                StorageException.ERROR_NOT_AUTHENTICATED -> {
                    // User is not authenticated for this operation
                    Log.e(TAG, "User not authenticated for storage operations")
                    onFailure(Exception("Authentication required for uploads"))
                }
                StorageException.ERROR_NOT_AUTHORIZED -> {
                    // User is authenticated but doesn't have permission
                    Log.e(TAG, "User not authorized for this storage operation")
                    onFailure(Exception("Not authorized to upload files"))
                }
                StorageException.ERROR_QUOTA_EXCEEDED -> {
                    // Storage quota is exceeded
                    Log.e(TAG, "Storage quota exceeded")
                    onFailure(Exception("Storage quota exceeded"))
                }
                else -> {
                    // Handle other storage error codes
                    Log.e(TAG, "Storage error code: ${e.errorCode}")
                    onFailure(e)
                }
            }
        } else {
            onFailure(e)
        }
    }
    
    // Loads an image from a URL into an ImageView
    fun loadImage(context: Context, url: String?, imageView: ImageView) {
        try {
            // Check if the activity is destroyed
            if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) {
                Log.e(TAG, "Error loading image: You cannot start a load for a destroyed activity")
                return
            }
            
            // Check for null/empty URL or special default image constant
            if (url.isNullOrEmpty() || url == Constants.DEFAULT_PROFILE_IMAGE) {
                // For empty URLs or the default marker, use the default profile image
                imageView.setImageResource(R.drawable.default_profile)
            } else {
                // Use Glide to load the image from the URL
                Glide.with(context)
                    .load(url)
                    .placeholder(R.drawable.default_profile)
                    .error(R.drawable.default_profile)
                    .into(imageView)
            }
        } catch (e: Exception) {
            // Log any errors during image loading
            Log.e(TAG, "Error loading image: ${e.message}")
            
            try {
                // Attempt to set the default image in case of error
                imageView.setImageResource(R.drawable.default_profile)
            } catch (e2: Exception) {
                // Handle the case where even setting the default image fails
                Log.e(TAG, "Failed to set default image: ${e2.message}")
            }
        }
    }
} 