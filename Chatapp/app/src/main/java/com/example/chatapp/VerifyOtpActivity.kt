package com.example.chatapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.*
import com.google.firebase.FirebaseException
import com.google.firebase.database.FirebaseDatabase
import java.util.concurrent.TimeUnit
import com.example.chatapp.util.Constants

// Activity for handling OTP (One-Time Password) verification
class VerifyOtpActivity : AppCompatActivity() {

    // UI elements for OTP verification
    private lateinit var editTextOtp: EditText
    private lateinit var buttonVerify: Button
    private lateinit var buttonResend: Button
    private lateinit var resendTimer: TextView
    private lateinit var progressBarVerify: ProgressBar
    
    // Firebase Authentication instance for phone verification
    private lateinit var auth: FirebaseAuth
    
    // Verification ID received from RegisterActivity, used to verify OTP
    private lateinit var verificationId: String
    
    // Phone number being verified
    private lateinit var phoneNumber: String
    
    // Tag for logging messages from this activity
    private val tag = "VerifyOtpActivity"
    
    // Timer to enforce cooldown period between OTP resend attempts
    private var countDownTimer: CountDownTimer? = null
    
    // Duration in seconds to wait before allowing resend
    private val resendWaitTimeInSeconds = 30L

    // Called when the activity is first created
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_otp)
        
        // Log activity creation for debugging
        Log.d(tag, "VerifyOtpActivity created")

        try {
            // Initialize Firebase Authentication instance
            auth = FirebaseAuth.getInstance()

            // Get data passed from RegisterActivity via intent
            phoneNumber = intent.getStringExtra("PHONE_NUMBER") ?: ""
            verificationId = intent.getStringExtra("VERIFICATION_ID") ?: ""
            val username = intent.getStringExtra("USERNAME") ?: ""
            
            // Log received data for debugging
            Log.d(tag, "Received phone number: $phoneNumber")
            Log.d(tag, "Received verification ID: ${if (verificationId.isEmpty()) "empty" else "exists"}")
            Log.d(tag, "Received username: $username")

            // Verify that required data is present
            if (verificationId.isEmpty()) {
                // Missing verification ID - can't proceed
                Log.e(tag, "Verification ID is missing")
                Toast.makeText(this, "Error: Verification ID is missing. Please try again.", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            // Initialize UI component references
            editTextOtp = findViewById(R.id.editTextOtp)
            buttonVerify = findViewById(R.id.buttonVerify)
            buttonResend = findViewById(R.id.buttonResend)
            resendTimer = findViewById(R.id.tvResendTimer)
            progressBarVerify = findViewById(R.id.progressBarVerify)

            // Set up verify button click listener
            buttonVerify.setOnClickListener {
                verifyOtp(username)
            }
            
            // Set up resend button click listener
            buttonResend.setOnClickListener {
                if (phoneNumber.isNotEmpty()) {
                    // Show progress and disable button during resend operation
                    progressBarVerify.visibility = View.VISIBLE
                    buttonResend.isEnabled = false
                    buttonResend.text = "Sending..."
                    
                    // Initiate OTP resend process
                    resendOtp(phoneNumber)
                } else {
                    // Phone number missing - can't resend
                    Toast.makeText(this, "Phone number is missing. Please go back and try again.", Toast.LENGTH_SHORT).show()
                }
            }
            
            // Start the countdown timer right away to prevent immediate resend
            startResendTimer()
        } catch (e: Exception) {
            // Handle any unexpected initialization errors
            Log.e(tag, "Error during VerifyOtpActivity creation: ${e.message}", e)
            Toast.makeText(this, "An error occurred: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    // Starts or restarts the countdown timer for OTP resend cooldown
    private fun startResendTimer() {
        // Disable resend button during countdown
        buttonResend.isEnabled = false
        resendTimer.visibility = View.VISIBLE
        
        // Cancel any existing timer to prevent multiple timers running
        countDownTimer?.cancel()
        
        // Create a new countdown timer for the resend cooldown period
        countDownTimer = object : CountDownTimer(resendWaitTimeInSeconds * 1000, 1000) {
            // Update UI every second with remaining time
            @SuppressLint("SetTextI18n")
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                resendTimer.text = "Resend in ${secondsRemaining}s"
            }

            // Enable resend button when timer finishes
            @SuppressLint("SetTextI18n")
            override fun onFinish() {
                buttonResend.isEnabled = true
                buttonResend.text = "Resend OTP"
                resendTimer.visibility = View.GONE
            }
        }.start()
    }

    // Verifies the OTP entered by the user
    @SuppressLint("SetTextI18n")
    private fun verifyOtp(username: String = "") {
        // Get OTP entered by user and trim whitespace
        val otp = editTextOtp.text.toString().trim()
        
        if (otp.isNotEmpty()) {
            // Validate OTP is 6 digits as required by Firebase
            if (otp.length < 6) {
                Toast.makeText(this, "Please enter a valid 6-digit OTP", Toast.LENGTH_SHORT).show()
                return
            }

            // Update UI to show verification in progress
            progressBarVerify.visibility = View.VISIBLE
            buttonVerify.isEnabled = false
            buttonVerify.text = "Verifying..."
            
            // Log verification attempt for debugging
            Log.d(tag, "Attempting to verify OTP: $otp with verification ID: $verificationId")
            
            try {
                // Create Firebase phone auth credential from verification ID and OTP
                val credential = PhoneAuthProvider.getCredential(verificationId, otp)
                
                // Attempt to sign in with the credential
                signInWithPhoneAuthCredential(credential, username)
            } catch (e: Exception) {
                // Handle errors creating credential or signing in
                Log.e(tag, "Error creating credential: ${e.message}", e)
                Toast.makeText(this, "Verification error: ${e.message}", Toast.LENGTH_SHORT).show()
                
                // Reset UI to allow another attempt
                progressBarVerify.visibility = View.GONE
                buttonVerify.isEnabled = true
                buttonVerify.text = "Verify"
            }
        } else {
            // No OTP entered - show error
            Toast.makeText(this, "Please enter the OTP", Toast.LENGTH_SHORT).show()
        }
    }

    // Signs in with Firebase using phone auth credential
    @SuppressLint("SetTextI18n")
    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential, username: String = "") {
        Log.d(tag, "Signing in with phone auth credential")
        
        // Attempt Firebase sign-in with the credential
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Authentication successful
                    Log.d(tag, "signInWithCredential:success")
                    
                    // Get user ID from the authenticated user
                    val userId = auth.currentUser?.uid
                    if (userId != null) {
                        // Create or update user record in database
                        createOrUpdateUserInDatabase(userId, phoneNumber, username)
                    } else {
                        // User ID is unexpectedly null despite successful auth
                        Log.e(tag, "User ID is null after successful auth")
                        Toast.makeText(this, "Authentication successful!", Toast.LENGTH_SHORT).show()
                        
                        // Reset UI state
                        progressBarVerify.visibility = View.GONE
                        buttonVerify.isEnabled = true
                        buttonVerify.text = "Verify"
                        
                        // Navigate to MainActivity as fallback
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                } else {
                    // Authentication failed
                    val exception = task.exception
                    Log.e(tag, "signInWithCredential:failure", exception)
                    Toast.makeText(this, "Incorrect OTP. Please try again.", Toast.LENGTH_SHORT).show()
                    
                    // Reset UI to allow another attempt
                    progressBarVerify.visibility = View.GONE
                    buttonVerify.isEnabled = true
                    buttonVerify.text = "Verify"
                }
            }
    }

    // Creates a new user record in the database or updates existing record
    private fun createOrUpdateUserInDatabase(userId: String, phoneNumber: String, username: String = "") {
        // Get reference to Firebase database
        val database = FirebaseDatabase.getInstance().reference
        
        // Check if user already exists in database
        database.child("users").child(userId).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    // User doesn't exist, create a new record
                    val finalUsername = username.ifEmpty { "User_${phoneNumber.takeLast(4)}" }
                    
                    // Create user data map with default values
                    val user = hashMapOf(
                        "uid" to userId,
                        "username" to finalUsername,
                        "phoneNumber" to phoneNumber,
                        "profileImageUrl" to Constants.DEFAULT_PROFILE_IMAGE,
                        "status" to "Available",
                        "lastSeen" to System.currentTimeMillis()
                    )
                    
                    // Create the user record in Firebase
                    database.child("users").child(userId).setValue(user)
                        .addOnSuccessListener {
                            Log.d(tag, "User record created successfully")
                            
                            // Create phone-to-user mapping for preventing duplicate registrations
                            val formattedPhone = phoneNumber.replace("+", "")
                            database.child("phone-to-users").child(formattedPhone).setValue(userId)
                                .addOnSuccessListener {
                                    Log.d(tag, "Phone-to-user mapping created successfully")
                                    
                                    // Display success message for new user registration
                                    Toast.makeText(this, "Registration successful! Welcome to Chatapp.", Toast.LENGTH_LONG).show()
                                    
                                    // Navigate to profile setup for new users
                                    val intent = Intent(this, ProfileActivity::class.java)
                                    intent.putExtra("IS_NEW_USER", true)
                                    startActivity(intent)
                                    finish()
                                }
                                .addOnFailureListener { e ->

                                    // Mapping creation failed, but continue with registration
                                    Log.e(tag, "Failed to create phone-to-user mapping: ${e.message}")
                                    
                                    // Still display success message
                                    Toast.makeText(this, "Registration successful! Welcome to Chatapp.", Toast.LENGTH_LONG).show()
                                    
                                    // Continue to profile setup anyway
                                    val intent = Intent(this, ProfileActivity::class.java)
                                    intent.putExtra("IS_NEW_USER", true)
                                    startActivity(intent)
                                    finish()
                                }
                        }
                        .addOnFailureListener { e ->

                            // User record creation failed
                            Log.e(tag, "Error creating user record: ${e.message}")
                            
                            // Display generic success for authentication only
                            Toast.makeText(this, "Authentication successful!", Toast.LENGTH_SHORT).show()
                            
                            // Navigate to main activity as fallback even if profile creation fails
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        }
                } else {
                    // User exists, just update last seen timestamp
                    database.child("users").child(userId)
                        .child("lastSeen").setValue(System.currentTimeMillis())
                    
                    // Display welcome back message for returning users
                    Toast.makeText(this, "Login successful! Welcome back.", Toast.LENGTH_SHORT).show()
                    
                    // Navigate to main activity for returning users
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            }
            .addOnFailureListener { e ->

                // Database query failed
                Log.e(tag, "Error checking if user exists: ${e.message}")
                
                // Display generic success message
                Toast.makeText(this, "Authentication successful!", Toast.LENGTH_SHORT).show()
                
                // Navigate to main activity as fallback
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
    }

    // Callbacks for Firebase phone authentication status changes
    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        // Called when verification is automatically completed on the device
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            Log.d(tag, "onVerificationCompleted")
            
            // Automatically sign in since verification is complete
            signInWithPhoneAuthCredential(credential)
        }

        // Called when verification fails due to various reasons
        @SuppressLint("SetTextI18n")
        override fun onVerificationFailed(e: FirebaseException) {
            // Log error for debugging purposes
            Log.e(tag, "Verification failed: ${e.message}", e)
            
            // Reset UI to enable retry
            progressBarVerify.visibility = View.GONE
            buttonResend.isEnabled = true
            buttonResend.text = "Resend OTP"
            
            // Determine user-friendly error message based on error type
            val errorMessage = when {
                // Phone number blocked due to too many attempts
                e.message?.contains("blocked") == true -> 
                    "This phone number has been temporarily blocked due to too many attempts. Please try again later."
                
                // Invalid phone number format
                e.message?.contains("invalid") == true -> 
                    "Please enter a valid phone number with country code."
                
                // Generic fallback error message
                else -> "Verification failed: ${e.message}"
            }
            
            // Display error message to user
            Toast.makeText(this@VerifyOtpActivity, errorMessage, Toast.LENGTH_SHORT).show()
        }

        // Called when a new verification code is successfully sent to the phone
        override fun onCodeSent(newVerificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
            // Log successful code sending for debugging
            Log.d(tag, "New code sent to $phoneNumber")
            
            // Update verification ID for the new code
            verificationId = newVerificationId
            
            // Hide progress indicator
            progressBarVerify.visibility = View.GONE
            
            // Reset the resend cooldown timer and UI
            startResendTimer()
            
            // Notify user of successful resend
            Toast.makeText(this@VerifyOtpActivity, "OTP resent successfully! Check your messages.", Toast.LENGTH_SHORT).show()
        }
    }

    // Requests Firebase to resend a verification code to the phone number
    @SuppressLint("SetTextI18n")
    private fun resendOtp(phoneNumber: String) {
        Log.d(tag, "Resending OTP to $phoneNumber")
        try {
            // Configure the phone auth options
            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(30L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(callbacks)
                .build()
            
            // Request Firebase to send a new verification code
            PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (e: Exception) {
            // Handle any errors during resend attempt
            Log.e(tag, "Error resending OTP: ${e.message}", e)
            Toast.makeText(this, "Failed to resend OTP: ${e.message}", Toast.LENGTH_SHORT).show()
            
            // Reset UI to allow another attempt
            progressBarVerify.visibility = View.GONE
            buttonResend.isEnabled = true
            buttonResend.text = "Resend OTP"
        }
    }
    
    // Called when the activity is being destroyed
    override fun onDestroy() {
        super.onDestroy()
        
        // Cancel countdown timer to avoid memory leaks
        countDownTimer?.cancel()
    }
}
