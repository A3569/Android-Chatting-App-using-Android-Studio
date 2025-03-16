package com.example.chatapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.chatapp.databinding.ActivityRegisterBinding
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.database.FirebaseDatabase
import java.util.concurrent.TimeUnit

// Activity handling user registration using phone number authentication
class RegisterActivity : AppCompatActivity() {

    // View binding for accessing UI elements without findViewById
    private lateinit var binding: ActivityRegisterBinding
    
    // Firebase Authentication instance for phone authentication
    private lateinit var auth: FirebaseAuth
    
    // Stores the verification ID received from Firebase after sending SMS
    private var storedVerificationId: String? = null
    
    // Tag for logging messages from this activity
    private val tag = "RegisterActivity"
    
    // Reference to Firebase Realtime Database for user data operations
    private val database = FirebaseDatabase.getInstance().reference

    // Called when the activity is first created
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Call parent implementation first
        super.onCreate(savedInstanceState)
        
        // Initialize view binding to access UI elements
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Authentication
        auth = FirebaseAuth.getInstance()

        // Configure registration button click listener
        binding.btnRegister.setOnClickListener {
            // Extract and trim input values
            val username = binding.etUsername.text.toString().trim()
            val phoneNumber = binding.etPhoneNumber.text.toString().trim()
            
            // Validate username - show error if empty
            if (username.isEmpty()) {
                binding.tilUsername.error = "Username is required"
                return@setOnClickListener
            } else {
                // Clear error if username is valid
                binding.tilUsername.error = null
            }
            
            // Validate phone number - show error if empty
            if (phoneNumber.isEmpty()) {
                Toast.makeText(this, "Please enter your phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Show loading indicator and disable register button
            binding.progressBar.visibility = View.VISIBLE
            binding.btnRegister.isEnabled = false
            binding.btnRegister.text = "Please wait..."
            
            // Format the phone number to ensure proper international format
            val formattedPhoneNumber = formatPhoneNumber(phoneNumber)
            
            // First check if the phone number is already registered
            checkIfPhoneNumberExists(formattedPhoneNumber) { isAlreadyRegistered ->
                if (isAlreadyRegistered) {
                    // Phone number is already registered - display error UI
                    runOnUiThread {
                        // Hide loading indicator
                        binding.progressBar.visibility = View.GONE
                        
                        // Re-enable registration button
                        binding.btnRegister.isEnabled = true
                        binding.btnRegister.text = "Register"
                        
                        // Show error in the phone number field
                        binding.etPhoneNumber.error = "This phone number is already registered"
                        
                        // Show a more detailed error dialog with login option
                        val errorDialog = AlertDialog.Builder(this)
                            .setTitle("Registration Error")
                            .setMessage("This phone number is already associated with an account. Please use a different number or try to login instead.")
                            .setPositiveButton("OK", null)
                            .setNeutralButton("Go to Login") { _, _ ->

                                // Navigate to login screen if user selects this option
                                startActivity(Intent(this, LoginActivity::class.java))
                                finish()
                            }
                            .create()
                        
                        errorDialog.show()
                    }
                } else {
                    // Phone number is not registered, proceed with registration
                    runOnUiThread {
                        registerUserWithPhone(formattedPhoneNumber, username)
                    }
                }
            }
        }

        // Configure login text click listener to navigate to login screen
        binding.tvLogin.setOnClickListener {
            finish()
        }
    }

    // Checks if a phone number is already registered in the database
    private fun checkIfPhoneNumberExists(phoneNumber: String, callback: (Boolean) -> Unit) {
        // Query the phone-to-users node to see if this phone number exists
        database.child("phone-to-users").child(phoneNumber.replace("+", ""))
            .get()
            .addOnSuccessListener { snapshot ->

                // Log the result for debugging
                Log.d(tag, "Phone number lookup result: exists=${snapshot.exists()}, value=${snapshot.value}")
                
                // Return result through callback - true if phone exists in database
                callback(snapshot.exists())
            }
            .addOnFailureListener { e ->

                // Log error for debugging
                Log.e(tag, "Error checking if phone number exists: ${e.message}")
                
                // In case of error, assume not registered to allow user to attempt registration
                callback(false)
            }
    }

    // Formats a phone number to ensure it has proper international format
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

    // Callbacks for Firebase phone authentication process
    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        // Called when verification is automatically completed on the device
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            Log.d(tag, "onVerificationCompleted")
            
            // Get username from the input field
            val username = binding.etUsername.text.toString().trim()
            
            // Automatically sign in since verification is already complete
            signInWithPhoneAuthCredential(credential, username)
        }

        // Called when verification fails due to various reasons
        override fun onVerificationFailed(e: FirebaseException) {
            // Log error for debugging purposes
            Log.e(tag, "Verification failed: ${e.message}", e)
            
            // Display user-friendly error message based on the error type
            val errorMessage = when {
                // App not properly configured with Firebase
                e.message?.contains("app is not authorized") == true || 
                e.message?.contains("SHA-1") == true -> 
                    "App authentication failed. Please make sure you're using the official app version."
                
                // Firebase billing issues
                e.message?.contains("BILLING_NOT_ENABLED") == true -> 
                    "SMS verification is currently unavailable. Please try again later or contact support."
                
                // SMS quota exceeded
                e.message?.contains("quota") == true -> 
                    "SMS quota exceeded. Please try again later."
                
                // Phone number blocked due to too many attempts
                e.message?.contains("blocked") == true -> 
                    "This phone number has been temporarily blocked due to too many attempts. Please try again later."
                
                // Invalid phone number format
                e.message?.contains("invalid") == true -> 
                    "Please enter a valid phone number with country code."
                
                // Generic fallback error message
                else -> "Verification failed: ${e.message}"
            }
            
            // Show error UI indicator on phone field
            binding.etPhoneNumber.error = "Error with this number"
            
            // Log detailed error for debugging
            Log.e(tag, "Firebase Auth Error Details: ${e.javaClass.simpleName}, Message: ${e.message}")
            
            // Show error toast with user-friendly message
            Toast.makeText(this@RegisterActivity, errorMessage, Toast.LENGTH_LONG).show()
            
            // Reset the form when an invalid phone number is detected
            resetRegistrationForm()
        }
        
        // Called when verification code is successfully sent to the provided phone number
        override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
            // Log verification ID for debugging
            Log.d(tag, "onCodeSent: $verificationId")
            
            // Store verification ID for later use
            storedVerificationId = verificationId
            
            // Show success message for code sent
            Toast.makeText(this@RegisterActivity, "Verification code sent successfully!", Toast.LENGTH_SHORT).show()
            
            // Pass verification ID to VerifyOtpActivity for OTP verification
            val intent = Intent(this@RegisterActivity, VerifyOtpActivity::class.java)
            intent.putExtra("VERIFICATION_ID", verificationId)
            intent.putExtra("PHONE_NUMBER", binding.etPhoneNumber.text.toString().trim())
            startActivity(intent)
        }
    }

    // Resets the registration form UI after validation failure
    @SuppressLint("SetTextI18n")
    private fun resetRegistrationForm() {
        // Clear the phone number field if it contains an invalid number
        if (binding.etPhoneNumber.error != null) {
            binding.etPhoneNumber.setText("")
            binding.etPhoneNumber.requestFocus()
        }
        
        // Reset the button state to enabled with original text
        binding.btnRegister.isEnabled = true
        binding.btnRegister.text = "Register"
    }

    // Initiates the phone registration process with Firebase
    @SuppressLint("SetTextI18n")
    private fun registerUserWithPhone(phoneNumber: String, username: String) {
        Log.d(tag, "Attempting to register with phone number: $phoneNumber and username: $username")
        
        // Validate phone number format before sending to Firebase
        if (!isValidPhoneNumber(phoneNumber)) {
            // Show error if format is invalid
            binding.etPhoneNumber.error = "Invalid phone number format"
            Toast.makeText(this, "Please enter a valid phone number with country code", Toast.LENGTH_SHORT).show()
            resetRegistrationForm()
            return
        }
        
        // Update UI to show progress
        binding.btnRegister.isEnabled = false
        binding.btnRegister.text = "Checking phone number..."
        
        try {
            // Double-check if the phone number is already registered
            checkIfPhoneNumberExists(phoneNumber) { isAlreadyRegistered ->
                if (isAlreadyRegistered) {
                    // Phone number is already registered - display error UI
                    runOnUiThread {
                        // Hide loading indicator
                        binding.progressBar.visibility = View.GONE
                        
                        // Re-enable registration button
                        binding.btnRegister.isEnabled = true
                        binding.btnRegister.text = "Register"
                        
                        // Show error in the phone number field
                        binding.etPhoneNumber.error = "This phone number is already registered"
                        
                        // Show a more detailed error dialog with login option
                        val errorDialog = AlertDialog.Builder(this)
                            .setTitle("Registration Error")
                            .setMessage("This phone number is already associated with an account. Please use a different number or try to login instead.")
                            .setPositiveButton("OK", null)
                            .setNeutralButton("Go to Login") { _, _ ->
                            
                                // Navigate to login screen if user selects this option
                                startActivity(Intent(this, LoginActivity::class.java))
                                finish()
                            }
                            .create()
                        
                        errorDialog.show()
                    }
                } else {
                    // Phone number is not registered, proceed with verification
                    binding.btnRegister.text = "Sending verification code..."
                    
                    // Configure Firebase phone auth options
                    val options = PhoneAuthOptions.newBuilder(auth)
                        .setPhoneNumber(phoneNumber)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(this)
                        .setCallbacks(callbacks)
                        .build()
                    
                    // Start phone verification process
                    PhoneAuthProvider.verifyPhoneNumber(options)
                }
            }
        } catch (e: Exception) {
            // Handle any unexpected errors during registration process
            Log.e(tag, "Error in registerUserWithPhone: ${e.message}", e)
            Toast.makeText(this, "Registration error: ${e.message}", Toast.LENGTH_LONG).show()
            resetRegistrationForm()
        }
    }

    // Validates the phone number format
    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        // Phone number must start with + and have at least 8 digits after country code
        return phoneNumber.startsWith("+") && 
               phoneNumber.length >= 8 && 
               phoneNumber.substring(1).all { it.isDigit() }
    }

    // Signs in the user with the provided phone auth credential
    @SuppressLint("SetTextI18n")
    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential, username: String) {
        // Attempt to sign in with the credential
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Redirect to MainActivity since verification is complete
                    val intent = Intent(this, MainActivity::class.java)
                    intent.putExtra("USERNAME", username)
                    startActivity(intent)
                    finish()
                } else {
                    // Handle sign-in failure
                    val exception = task.exception
                    Log.e(tag, "Verification failed: ${exception?.message}", exception)
                    Toast.makeText(this, "Verification failed: ${exception?.message}", Toast.LENGTH_SHORT).show()
                    
                    // Reset button state
                    binding.btnRegister.isEnabled = true
                    binding.btnRegister.text = "Register"
                }
            }
    }
}
