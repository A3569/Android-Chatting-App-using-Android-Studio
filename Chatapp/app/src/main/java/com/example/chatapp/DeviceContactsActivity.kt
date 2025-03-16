package com.example.chatapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chatapp.adapter.ContactAdapter
import com.example.chatapp.databinding.ActivityDeviceContactsBinding
import com.example.chatapp.model.Contact
import com.example.chatapp.util.ContactsUtil
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

// Activity that displays device contacts who are also using the app
class DeviceContactsActivity : AppCompatActivity() {
    
    // View binding for accessing UI elements without findViewById
    private lateinit var binding: ActivityDeviceContactsBinding
    
    // Adapter for the RecyclerView that displays contacts
    private lateinit var contactAdapter: ContactAdapter
    
    // List to store all contacts that use the app
    private val contactsList = mutableListOf<Contact>()
    
    // List to store filtered contacts (based on search)
    private val filteredList = mutableListOf<Contact>()
    
    // Tag for logging messages from this activity
    private val tag = "DeviceContactsActivity"
    
    // Firebase Authentication instance for user authentication
    private lateinit var auth: FirebaseAuth
    
    // Flag to track if search is currently active
    private var isSearchActive = false
    
    // Companion object for static constants used in this activity
    companion object {
        // Request code for contacts permission
        private const val CONTACTS_PERMISSION_REQUEST = 123
    }
    
    // Called when the activity is first created
    override fun onCreate(savedInstanceState: Bundle?) {
        // Call parent implementation first
        super.onCreate(savedInstanceState)
        
        try {
            // Initialize view binding to access UI elements
            binding = ActivityDeviceContactsBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            // Log activity creation for debugging
            Log.d(tag, "Activity created, setting up UI")
            
            // Initialize Firebase Authentication
            auth = FirebaseAuth.getInstance()
            
            // Check if user is logged in, redirect to login if not
            if (auth.currentUser == null) {
                Log.e(tag, "User is not authenticated. Redirecting to login")
                Toast.makeText(this, "Please log in to access contacts", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return
            }
            
            // Log current user info for debugging purposes
            auth.currentUser?.let {
                Log.d(tag, "Firebase user authenticated: ${it.uid}, phone: ${it.phoneNumber}")
            }
            
            // Configure the toolbar with back button and title
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "Contacts"
            
            // Set up the RecyclerView and its adapter for displaying contacts
            try {
                // Initialize adapter with click listener for contact selection
                contactAdapter = ContactAdapter(filteredList) { contact ->

                    // When contact is clicked, check if chat exists and navigate
                    checkExistingChatAndNavigate(contact)
                }
                
                // Configure the RecyclerView with layout manager and adapter
                binding.rvContacts.apply {
                    layoutManager = LinearLayoutManager(this@DeviceContactsActivity)
                    adapter = contactAdapter
                }
                
                Log.d(tag, "RecyclerView setup complete")
            } catch (e: Exception) {
                // Log and show error if RecyclerView setup fails
                Log.e(tag, "Error setting up RecyclerView: ${e.message}", e)
                Toast.makeText(this, "Error initializing contacts list: ${e.message}", Toast.LENGTH_LONG).show()
            }
            
            // Set up search functionality with query listener
            binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                // Handle search submission (not used but required by interface)
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return false
                }
                
                // Handle text changes in search view, filtering results in real-time
                override fun onQueryTextChange(newText: String?): Boolean {
                    try {
                        // Update search state flag based on whether query is empty
                        isSearchActive = !newText.isNullOrEmpty()

                        // Filter contacts based on search text
                        filterContacts(newText)
                    } catch (e: Exception) {
                        Log.e(tag, "Error filtering contacts: ${e.message}", e)
                    }
                    return true
                }
            })
            
            // Set up retry button click listener for when contact loading fails
            binding.btnRetry.setOnClickListener {
                if (hasContactsPermission()) {
                    // If we have permission, show loading state and retry
                    showLoadingState()
                    loadContacts()
                } else {
                    requestContactsPermission()
                }
            }
            
            // Set up button to show all app users when device contacts can't be loaded
            binding.btnShowAllUsers.setOnClickListener {
                startActivity(Intent(this, NewChatActivity::class.java))
                finish()
            }
            
            // Check if we have contacts permission and proceed accordingly
            if (hasContactsPermission()) {
                // If permission granted, show loading state and load contacts
                showLoadingState()
                loadContacts()
            } else {
                requestContactsPermission()
            }
        } catch (e: Exception) {
            // Catch any unexpected errors in initialization
            Log.e(tag, "Error initializing activity: ${e.message}", e)
            Toast.makeText(this, "Error initializing: ${e.message}", Toast.LENGTH_LONG).show()
            showErrorState("Error initializing contacts. Please try again.")
        }
    }
    
    // Shows the loading state in the UI
    private fun showLoadingState() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvNoContacts.visibility = View.GONE
        binding.btnRetry.visibility = View.GONE
        binding.btnShowAllUsers.visibility = View.GONE
        binding.rvContacts.visibility = View.GONE
    }
    
    // Shows the error state in the UI
    private fun showErrorState(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.tvNoContacts.visibility = View.VISIBLE
        binding.tvNoContacts.text = message
        binding.btnRetry.visibility = View.VISIBLE
        binding.btnShowAllUsers.visibility = View.VISIBLE
        binding.rvContacts.visibility = View.GONE
    }
    
    // Shows the empty state in the UI when no contacts are found
    private fun showEmptyState(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.tvNoContacts.visibility = View.VISIBLE
        binding.tvNoContacts.text = message
        binding.btnRetry.visibility = View.GONE
        binding.btnShowAllUsers.visibility = View.VISIBLE
        binding.rvContacts.visibility = View.GONE
    }
    
    // Shows the contacts list in the UI
    private fun showContactsState() {
        binding.progressBar.visibility = View.GONE
        binding.tvNoContacts.visibility = View.GONE
        binding.btnRetry.visibility = View.GONE
        binding.btnShowAllUsers.visibility = View.GONE
        binding.rvContacts.visibility = View.VISIBLE
    }
    
    // Checks if the app has permission to read contacts
    private fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    // Requests permission to read contacts from the user
    private fun requestContactsPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_CONTACTS),
            CONTACTS_PERMISSION_REQUEST
        )
    }
    
    // Handles the result of permission requests
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        // Call parent implementation first
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        // Check if this is the result for our contacts permission request
        if (requestCode == CONTACTS_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, show loading state and load contacts
                showLoadingState()
                loadContacts()
            } else {
                // Permission denied, show error state with explanation
                showErrorState("Contacts permission is required to see your device contacts. You can still view all app users.")
            }
        }
    }
    
    // Loads contacts from the device and filters those who use the app
    @SuppressLint("NotifyDataSetChanged", "SetTextI18n")
    private fun loadContacts() {
        try {
            // Ensure loading state is shown
            if (binding.progressBar.visibility != View.VISIBLE) {
                showLoadingState()
            }
            
            Log.d(tag, "Starting to load contacts")
            
            // Verify user is authenticated before proceeding
            if (auth.currentUser == null) {
                Log.e(tag, "Firebase user not authenticated when trying to load contacts")
                showErrorState("You must be logged in to access contacts. You can still view all app users.")
                return
            }
            
            // Get the current user's phone number to filter out self from contacts
            val firebaseDb = FirebaseDatabase.getInstance().reference
            firebaseDb.child("users").child(auth.currentUser!!.uid)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    // Called when data is successfully retrieved
                    override fun onDataChange(snapshot: DataSnapshot) {
                        // Get current user's phone number from Firebase
                        val currentUserPhoneNumber = snapshot.child("phoneNumber").getValue(String::class.java) ?: ""
                        Log.d(tag, "Current user phone number: $currentUserPhoneNumber")
                        
                        // Continue with contacts loading now that we have user's phone
                        continueLoadingContacts(currentUserPhoneNumber)
                    }
                    
                    // Called if the database operation is cancelled
                    override fun onCancelled(error: DatabaseError) {
                        Log.e(tag, "Error fetching current user data: ${error.message}")
                        // Continue loading contacts without filtering out self
                        continueLoadingContacts("")
                    }
                })
        } catch (e: Exception) {
            // Handle any unexpected errors
            Log.e(tag, "Error in loadContacts: ${e.message}", e)
            showErrorState("Error loading contacts: ${e.message}")
        }
    }
    
    // Continues the contact loading process after getting the current user's phone number
    @SuppressLint("NotifyDataSetChanged")
    private fun continueLoadingContacts(currentUserPhoneNumber: String) {
        // Set a timeout in case contact loading takes too long
        CoroutineScope(Dispatchers.Main).launch {
            // Wait 15 seconds before checking if still loading
            if (binding.progressBar.visibility == View.VISIBLE) {
                // If still loading after timeout, show a toast to inform user
                Toast.makeText(
                    this@DeviceContactsActivity,
                    "Still looking for contacts... Please wait or try again later.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        
        // Use ContactsUtil to find contacts who are using the app
        ContactsUtil.findContactsUsingApp(applicationContext) { contacts ->
            try {
                // Update UI on the main thread since callback might be on background thread
                runOnUiThread {
                    try {
                        // Clear existing contacts list
                        contactsList.clear()
                        
                        // Filter out the current user from contacts
                        val filteredContacts = if (currentUserPhoneNumber.isNotEmpty()) {
                            contacts.filter { contact -> 
                                val normalizedContactPhone = contact.phoneNumber.replace("+", "").replace(" ", "")
                                val normalizedUserPhone = currentUserPhoneNumber.replace("+", "").replace(" ", "")

                                // Exclude contact if either phone number ends with the other
                                !normalizedContactPhone.endsWith(normalizedUserPhone) && !normalizedUserPhone.endsWith(normalizedContactPhone)
                            }
                        } else {
                            // If we don't have current user's phone, use all contacts
                            contacts
                        }
                        
                        // Log how many contacts were filtered out
                        Log.d(tag, "Filtered out ${contacts.size - filteredContacts.size} contacts (including self)")
                        
                        // Add filtered contacts to our list
                        contactsList.addAll(filteredContacts)
                        
                        // Initialize filtered list with all contacts
                        filteredList.clear()
                        filteredList.addAll(contactsList)
                        
                        // Notify adapter that data has changed
                        contactAdapter.notifyDataSetChanged()
                        
                        // Update UI based on whether contacts were found
                        if (contactsList.isEmpty()) {
                            if (isSearchActive) {
                                // If search is active and no results, show empty search message
                                showEmptyState("No contacts found matching your search.")
                            } else {
                                // If no contacts found at all, show empty state
                                showEmptyState("No contacts found using this app. You can view all app users instead.")
                            }
                        } else {
                            // Contacts found, show them
                            showContactsState()
                        }
                    } catch (e: Exception) {
                        // Handle errors when updating UI
                        Log.e(tag, "Error updating UI with contacts: ${e.message}", e)
                        showErrorState("Error displaying contacts: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                // Handle errors in the callback itself
                Log.e(tag, "Error in contacts callback: ${e.message}", e)
                showErrorState("Error processing contacts: ${e.message}")
            }
        }
    }
    
    // Filters contacts based on search query
    @SuppressLint("NotifyDataSetChanged", "SetTextI18n")
    private fun filterContacts(query: String?) {
        try {
            // Clear the current filtered list
            filteredList.clear()
            
            if (query.isNullOrEmpty()) {
                // If no search query, show all contacts
                filteredList.addAll(contactsList)
                
                // Update UI based on whether any contacts exist
                if (contactsList.isEmpty()) {
                    showEmptyState("No contacts found using this app. You can view all app users instead.")
                } else {
                    showContactsState()
                }
            } else {
                // Convert query to lowercase for case-insensitive comparison
                val searchQuery = query.lowercase(Locale.getDefault())
                
                // Filter contacts whose name, phone number, or username contains the query
                val results = contactsList.filter {
                    it.name.lowercase(Locale.getDefault()).contains(searchQuery) ||
                            it.phoneNumber.contains(searchQuery) ||
                            it.username.lowercase(Locale.getDefault()).contains(searchQuery)
                }
                
                // Add filtered results to the list
                filteredList.addAll(results)
                
                // Update UI based on search results
                if (filteredList.isEmpty()) {
                    showEmptyState("No contacts found matching \"$query\".")
                } else {
                    showContactsState()
                }
            }
            
            // Notify adapter that data has changed
            contactAdapter.notifyDataSetChanged()
        } catch (e: Exception) {
            // Handle errors during filtering
            Log.e(tag, "Error filtering contacts: ${e.message}", e)
            Toast.makeText(this, "Error filtering contacts: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Checks if a chat already exists with the selected contact
    private fun checkExistingChatAndNavigate(contact: Contact) {
        // Get current user or return if not authenticated
        val currentUser = auth.currentUser ?: return
        val database = FirebaseDatabase.getInstance().reference
        
        // Show loading indicator
        binding.progressBar.visibility = View.VISIBLE
        
        Log.d(tag, "Checking for existing chat with contact: ${contact.name}, ID: ${contact.id}")
        
        // Query Firebase to check for existing chats with this contact
        database.child("user-chats").child(currentUser.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Variable to store existing chat ID if found
                    var existingChatId: String? = null
                    
                    // Iterate through all chats to find one with this contact
                    for (chatSnapshot in snapshot.children) {
                        // Get participants list for this chat
                        val participants = chatSnapshot.child("participants").getValue(object : com.google.firebase.database.GenericTypeIndicator<List<String>>() {})
                        
                        // If participants include selected contact, we found a match
                        if (participants != null && participants.contains(contact.id)) {
                            existingChatId = chatSnapshot.child("chatId").getValue(String::class.java)
                            Log.d(tag, "Found existing chat with ID: $existingChatId")
                            break
                        }
                    }
                    
                    // Hide loading indicator
                    binding.progressBar.visibility = View.GONE
                    
                    if (existingChatId != null) {
                        // Chat exists, navigate to it
                        Log.d(tag, "Redirecting to existing chat with ${contact.name}")
                        Toast.makeText(this@DeviceContactsActivity, "Opening existing chat with ${contact.name}", Toast.LENGTH_SHORT).show()
                        
                        // Create intent with chat ID and receiver ID
                        val intent = Intent(this@DeviceContactsActivity, ChatActivity::class.java).apply {
                            putExtra("CHAT_ID", existingChatId)
                            putExtra("RECEIVER_ID", contact.id)
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        // No existing chat, start a new one
                        Log.d(tag, "Creating new chat with ${contact.name}")
                        
                        // Create intent with just receiver ID (ChatActivity will create chat)
                        val intent = Intent(this@DeviceContactsActivity, ChatActivity::class.java).apply {
                            putExtra("RECEIVER_ID", contact.id)
                        }
                        startActivity(intent)
                        finish()
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    // Hide loading indicator
                    binding.progressBar.visibility = View.GONE
                    
                    // Log and show error
                    Log.e(tag, "Error checking for existing chat: ${error.message}")
                    Toast.makeText(
                        this@DeviceContactsActivity,
                        "Failed to open chat: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
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