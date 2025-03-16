package com.example.chatapp.util

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.database.DatabaseException
import java.net.UnknownHostException

// Utility object for handling and displaying errors throughout the chat application
object ErrorHandler {
    // Tag for logcat messages to easily identify logs from this class
    private const val TAG = "ErrorHandler"
    
    //Handles exceptions by logging them and displaying a user-friendly toast message
    fun handleException(context: Context, exception: Exception, operation: String = "Operation") {
        // Log the error with full stack trace for debugging purposes
        Log.e(TAG, "$operation failed: ${exception.message}", exception)
        
        // Convert the exception to a user-friendly message based on its type
        val errorMessage = when (exception) {
            // Authentication errors
            is FirebaseAuthInvalidUserException -> 
                "User not found. Please check your email or register."
            
            is FirebaseAuthInvalidCredentialsException -> 
                "Invalid credentials. Please check your email and password."
            
            is FirebaseAuthUserCollisionException -> 
                "An account with this email already exists."
            
            // Network errors
            is FirebaseNetworkException, is UnknownHostException -> 
                "Network error. Please check your internet connection."
            
            // Database errors
            is DatabaseException -> 
                "Database error: ${exception.message}"
            
            // Generic Firebase errors
            is FirebaseException -> 
                "Firebase error: ${exception.message}"
            
            // Fallback for any other exceptions
            else -> 
                "$operation failed: ${exception.message}"
        }
        
        // Display the error message to the user as a toast notification
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
    }
    
    // Displays an error dialog with a title, message, and OK button
    fun showErrorDialog(context: Context, title: String, message: String) {
        // Log the error dialog for debugging and monitoring
        Log.e(TAG, "Error dialog: $title - $message")
        
        // Build and show an AlertDialog with the provided details
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
} 