package com.example.chatapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.chatapp.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.example.chatapp.util.ErrorHandler
import com.google.firebase.FirebaseException
import com.google.firebase.database.FirebaseDatabase
import java.util.concurrent.TimeUnit

// Activity for user authentication via phone number
class LoginActivity : AppCompatActivity() {
    
    // View binding for accessing UI elements without findViewById
    private lateinit var binding: ActivityLoginBinding
    
    // Firebase Authentication instance for handling user authentication
    private lateinit var auth: FirebaseAuth
    
    // Tag for logging messages from this activity
    private val tag = "LoginActivity"
    
    // Called when the activity is first created
    override fun onCreate(savedInstanceState: Bundle?) {
        // Call parent implementation first
        super.onCreate(savedInstanceState)
        
        try {
            // Initialize view binding to access UI elements
            binding = ActivityLoginBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            // Initialize Firebase Authentication
            try {
                auth = FirebaseAuth.getInstance()
            } catch (e: Exception) {
                // Log and show error if Firebase Auth initialization fails
                Log.e(tag, "Error initializing Firebase Auth: ${e.message}", e)
                ErrorHandler.showErrorDialog(this, "Firebase Error", 
                    "There was an error initializing Firebase. Please restart the app.")
                return
            }
            
            // Check if user is already logged in and navigate if they are
            if (auth.currentUser != null) {
                navigateToMainActivity()
            }
            
            // Set up login button click listener
            binding.btnLogin.setOnClickListener {
                // Get phone number from input field and trim whitespace
                val phoneNumber = binding.etPhoneNumber.text.toString().trim()
                
                // Validate phone number is not empty
                if (phoneNumber.isNotEmpty()) {
                    // Format phone number to ensure it has international format
                    val formattedPhoneNumber = formatPhoneNumber(phoneNumber)
                    
                    // Start login process with formatted number
                    loginUserWithPhone(formattedPhoneNumber)
                } else {
                    // Show error if no phone number is entered
                    Toast.makeText(this, "Please enter your phone number", Toast.LENGTH_SHORT).show()
                }
            }
            
            // Set up register text click listener to navigate to registration
            binding.tvRegister.setOnClickListener {
                startActivity(Intent(this, RegisterActivity::class.java))
            }
        } catch (e: Exception) {
            // Catch-all for any unexpected errors during activity setup
            Log.e(tag, "Error during LoginActivity creation: ${e.message}", e)
            ErrorHandler.showErrorDialog(this, "Error", 
                "An unexpected error occurred. Please restart the app.")
        }
    }
    
    // Helper function to format phone numbers
    private fun formatPhoneNumber(phoneNumber: String): String {
        // If the number already starts with +, assume it's already formatted
        if (phoneNumber.startsWith("+")) {
            return phoneNumber
        }
        
        // Default to adding +44 (UK) if no country code is provided
        if (phoneNumber.length == 10) {
            return "+44$phoneNumber"
        }
        
        // If it's neither starting with + nor exactly 10 digits, add + at the beginning
        return "+$phoneNumber"
    }
    
    // Initiates the phone number authentication process with Firebase
    @SuppressLint("SetTextI18n")
    private fun loginUserWithPhone(phoneNumber: String) {
        // Log the authentication attempt for debugging
        Log.d(tag, "Attempting to login with phone number: $phoneNumber")
        
        try {
            // Configure Firebase phone authentication options
            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    // Called when verification is automatically completed
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        Log.d(tag, "onVerificationCompleted")

                        // Sign in with the credential
                        auth.signInWithCredential(credential)
                            .addOnCompleteListener(this@LoginActivity) { task ->
                                if (task.isSuccessful) {
                                    // Get current user ID
                                    val userId = auth.currentUser?.uid
                                    if (userId != null) {
                                        // Create or update user information in database
                                        createOrUpdateUserInDatabase(userId, phoneNumber)
                                    }
                                    
                                    // Show success message
                                    Toast.makeText(this@LoginActivity, "Login successful! Welcome back.", Toast.LENGTH_SHORT).show()
                                    
                                    // Navigate to the main app screen
                                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                    finish()
                                } else {
                                    // Handle sign-in failure
                                    val exception = task.exception
                                    Log.e(tag, "Login failed: ${exception?.message}", exception)
                                    Toast.makeText(this@LoginActivity, "Login failed: ${exception?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                    }

                    // Called when verification fails due to invalid phone number or other issues
                    override fun onVerificationFailed(e: FirebaseException) {
                        // Log the verification failure
                        Log.e(tag, "Verification failed: ${e.message}", e)
                        
                        // Log detailed error information for debugging
                        Log.e(tag, "Firebase Auth Error Details: ${e.javaClass.simpleName}, Message: ${e.message}")
                        
                        // Show user-friendly error message
                        Toast.makeText(this@LoginActivity, "Invalid phone number", Toast.LENGTH_LONG).show()
                    }

                    // Called when verification code is successfully sent to the phone
                    override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                        // Log the successful code sending
                        Log.d(tag, "onCodeSent: $verificationId")
                        
                        // Show success message to the user
                        Toast.makeText(this@LoginActivity, "Verification code sent successfully!", Toast.LENGTH_SHORT).show()
                        
                        // Navigate to OTP verification screen with necessary data
                        val intent = Intent(this@LoginActivity, VerifyOtpActivity::class.java)
                        intent.putExtra("VERIFICATION_ID", verificationId)
                        intent.putExtra("PHONE_NUMBER", phoneNumber)
                        startActivity(intent)
                    }
                })
                .build()
            
            // Start the phone verification process
            PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (e: Exception) {
            // Handle any unexpected errors during authentication
            Log.e(tag, "Error during phone auth: ${e.message}", e)
            Toast.makeText(this, "Authentication error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Creates a new user record in the database or updates existing record
    private fun createOrUpdateUserInDatabase(userId: String, phoneNumber: String) {
        // Get reference to Firebase database
        val database = FirebaseDatabase.getInstance().reference
        
        // Check if user already exists in the database
        database.child("users").child(userId).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    // Generate a default username based on user ID
                    val username = "User_${userId.substring(0, 5)}"
                    
                    // Create user data map with default values
                    val user = hashMapOf(
                        "uid" to userId,
                        "username" to username,
                        "phoneNumber" to phoneNumber,
                        "profileImageUrl" to "",
                        "status" to "Available",
                        "lastSeen" to System.currentTimeMillis()
                    )
                    
                    // Store user data in Firebase
                    database.child("users").child(userId).setValue(user)
                        .addOnSuccessListener {
                            // Log successful user creation
                            Log.d(tag, "User record created successfully")
                        }
                        .addOnFailureListener { e ->

                            // Log failure to create user record
                            Log.e(tag, "Error creating user record: ${e.message}")
                        }
                } else {
                    // User exists, just update last seen timestamp
                    database.child("users").child(userId)
                        .child("lastSeen").setValue(System.currentTimeMillis())
                }
            }
            .addOnFailureListener { e ->

                // Log any errors checking for user existence
                Log.e(tag, "Error checking if user exists: ${e.message}")
            }
    }
    
    // Navigates to the MainActivity and finishes this activity
    private fun navigateToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
} 
