package com.example.chatapp.util

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import com.example.chatapp.model.Contact
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.*

// Utility class for handling device contacts and finding matches with app users
object ContactsUtil {
    // Tag for logging purposes
    private const val TAG = "ContactsUtil"
    
    // Read device contacts and find matches with app users
    fun findContactsUsingApp(context: Context, callback: (List<Contact>) -> Unit) {
        // Launch in background thread using IO dispatcher to avoid blocking the UI
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get all contacts from the device that have phone numbers
                val deviceContacts = getDeviceContacts(context)
                Log.d(TAG, "Found ${deviceContacts.size} device contacts")
                
                // If no contacts were found, return an empty list immediately
                if (deviceContacts.isEmpty()) {
                    // Switch to Main dispatcher to call the callback on the main thread
                    withContext(Dispatchers.Main) {
                        callback(emptyList())
                    }
                    return@launch
                }
                
                // Create a map with normalized phone numbers as keys for efficient lookup
                val phoneMap = deviceContacts.associateBy { normalizePhoneNumber(it.phoneNumber) }
                Log.d(TAG, "Created phone map with ${phoneMap.size} entries")
                
                // Get reference to Firebase database for querying users
                val database = FirebaseDatabase.getInstance().reference
                
                try {
                    // Use a completion latch to ensure we don't return prematurely
                    withContext(Dispatchers.IO) {
                        // Create a CompletableDeferred to handle async completion
                        val latch = CompletableDeferred<List<Contact>>()
                        
                        // First try to find matches using the optimized phone-to-users mapping
                        database.child("phone-to-users").addListenerForSingleValueEvent(object : ValueEventListener {
                            
                            // Called when phone mapping data is available from Firebase
                            override fun onDataChange(phoneMappingSnapshot: DataSnapshot) {
                                // Try to find matches using the phone-to-users mapping first
                                val matchPhaseOne = findMatchesUsingPhoneMapping(deviceContacts, phoneMappingSnapshot)
                                
                                // If we found matches using the mapping, return them right away
                                if (matchPhaseOne.isNotEmpty()) {
                                    latch.complete(matchPhaseOne)
                                    return
                                }
                                
                                // If no matches found with phone-to-users mapping, fall back to users node
                                database.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
                                    
                                    // Called when user data is available from Firebase
                                    override fun onDataChange(snapshot: DataSnapshot) {
                                        try {
                                            Log.d(TAG, "Firebase query successful, received snapshot with ${snapshot.childrenCount} users")
                                            
                                            // List to store all matching contacts
                                            val matchingContacts = mutableListOf<Contact>()
                                            
                                            // Iterate through all users in the database
                                            for (userSnapshot in snapshot.children) {
                                                try {
                                                    // Get the phone number of this Firebase user
                                                    val phoneNumber = userSnapshot.child("phoneNumber").getValue(String::class.java) ?: ""
                                                    if (phoneNumber.isNotEmpty()) {
                                                        // Normalize the phone number for consistent comparison
                                                        val normalizedPhone = normalizePhoneNumber(phoneNumber)
                                                        
                                                        // Check if this Firebase user's phone matches any device contact
                                                        val matchingContact = phoneMap[normalizedPhone]
                                                        
                                                        // If we found a match, create a Contact object with combined data
                                                        if (matchingContact != null) {
                                                            // Get additional user data from Firebase
                                                            val userId = userSnapshot.child("uid").getValue(String::class.java) ?: continue
                                                            val username = userSnapshot.child("username").getValue(String::class.java) ?: continue
                                                            val profileImageUrl = userSnapshot.child("profileImageUrl").getValue(String::class.java) ?: ""
                                                            val status = userSnapshot.child("status").getValue(String::class.java) ?: "Available"
                                                            
                                                            // Create a contact with both device and app info
                                                            matchingContacts.add(
                                                                Contact(
                                                                    id = userId,
                                                                    name = matchingContact.name,
                                                                    phoneNumber = matchingContact.phoneNumber,
                                                                    username = username,
                                                                    profileImageUrl = profileImageUrl,
                                                                    status = status
                                                                )
                                                            )
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "Error processing user: ${e.message}")
                                                }
                                            }
                                            
                                            // Try alternative matching strategies with different phone formats
                                            val additionalMatches = findMatchesWithAlternativeFormats(deviceContacts, snapshot)
                                            
                                            // Add any additional matches not already in the list
                                            additionalMatches.forEach { contact ->

                                                // Check if this contact is already in our list to avoid duplicates
                                                if (matchingContacts.none { it.id == contact.id }) {
                                                    matchingContacts.add(contact)
                                                }
                                            }
                                            
                                            Log.d(TAG, "Found ${matchingContacts.size} matching contacts")

                                            // Complete the deferred with the list of matching contacts
                                            latch.complete(matchingContacts)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error processing Firebase data: ${e.message}", e)

                                            // Return empty list in case of error
                                            latch.complete(emptyList())
                                        }
                                    }
                                    
                                    // Called when Firebase query is cancelled
                                    override fun onCancelled(error: DatabaseError) {
                                        Log.e(TAG, "Firebase query cancelled: ${error.message}")
                                        
                                        // Return empty list if query is cancelled
                                        latch.complete(emptyList())
                                    }
                                })
                            }
                            
                            // Called when Firebase phone mapping query is cancelled
                            override fun onCancelled(error: DatabaseError) {
                                Log.e(TAG, "Firebase phone mapping query cancelled: ${error.message}")
                                // Fall back to direct lookup if the mapping query fails
                                findMatchesDirectly(database, deviceContacts, latch)
                            }
                        })
                        
                        // Wait for the result with a timeout
                        try {
                            // Set a timeout to prevent waiting forever
                            val result = withTimeoutOrNull(10000) { latch.await() }
                            
                            // Switch to Main dispatcher to call callback on UI thread
                            withContext(Dispatchers.Main) {
                                callback(result ?: emptyList())
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error waiting for Firebase result: ${e.message}", e)
                            
                            // Ensure we call callback even if there's an error
                            withContext(Dispatchers.Main) {
                                callback(emptyList())
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during Firebase lookup: ${e.message}", e)

                    // Ensure we call callback even if there's an error
                    withContext(Dispatchers.Main) {
                        callback(emptyList())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Global exception in findContactsUsingApp: ${e.message}", e)

                // Ensure we call callback even if there's an error
                withContext(Dispatchers.Main) {
                    callback(emptyList())
                }
            }
        }
    }
    
    // Find matches using the phone-to-users mapping in Firebase
    private fun findMatchesUsingPhoneMapping(deviceContacts: List<Contact>, phoneMappingSnapshot: DataSnapshot): List<Contact> {
        val matchingContacts = mutableListOf<Contact>()
        val database = FirebaseDatabase.getInstance().reference
        
        try {
            // Check each device contact against the phone-to-users mapping
            for (contact in deviceContacts) {
                // Try different formats of the phone number to increase match chances
                val phoneFormats = getPhoneNumberFormats(contact.phoneNumber)
                
                // Check each format against the phone-to-users mapping
                for (phoneFormat in phoneFormats) {
                    // Look up the user ID associated with this phone format
                    val userId = phoneMappingSnapshot.child(phoneFormat).getValue(String::class.java)
                    
                    if (!userId.isNullOrEmpty()) {
                        // If we found a user ID, get their details synchronously
                        val userSnapshot = try {
                            val dataSnapshot = Tasks.await(database.child("users").child(userId).get())
                            dataSnapshot
                        } catch (e: Exception) {
                            Log.e(TAG, "Error getting user data for $userId: ${e.message}")
                            null
                        }
                        
                        // Extract user details and create a Contact object
                        userSnapshot?.let {
                            val username = it.child("username").getValue(String::class.java) ?: return@let
                            val profileImageUrl = it.child("profileImageUrl").getValue(String::class.java) ?: ""
                            val status = it.child("status").getValue(String::class.java) ?: "Available"
                            
                            matchingContacts.add(
                                Contact(
                                    id = userId,
                                    name = contact.name,
                                    phoneNumber = contact.phoneNumber,
                                    username = username,
                                    profileImageUrl = profileImageUrl,
                                    status = status
                                )
                            )
                        }
                        
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in findMatchesUsingPhoneMapping: ${e.message}", e)
        }
        
        return matchingContacts
    }
    
    // Fall back to finding matches directly from the users node
    private fun findMatchesDirectly(database: DatabaseReference, deviceContacts: List<Contact>, latch: CompletableDeferred<List<Contact>>) {
        database.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
            
            // Called when user data is available from Firebase
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val matchingContacts = mutableListOf<Contact>()
                    
                    // Create a map for efficient lookup
                    val phoneMap = deviceContacts.associateBy { normalizePhoneNumber(it.phoneNumber) }
                    
                    // Iterate through all users in the database
                    for (userSnapshot in snapshot.children) {
                        try {
                            // Get the phone number of this Firebase user
                            val phoneNumber = userSnapshot.child("phoneNumber").getValue(String::class.java) ?: ""
                            if (phoneNumber.isNotEmpty()) {
                                // Normalize the phone number for consistent comparison
                                val normalizedPhone = normalizePhoneNumber(phoneNumber)
                                
                                // Check if this Firebase user's phone matches any device contact
                                val matchingContact = phoneMap[normalizedPhone]
                                
                                // If we found a match, create a Contact object with combined data
                                if (matchingContact != null) {
                                    // Get additional user data from Firebase
                                    val userId = userSnapshot.child("uid").getValue(String::class.java) ?: continue
                                    val username = userSnapshot.child("username").getValue(String::class.java) ?: continue
                                    val profileImageUrl = userSnapshot.child("profileImageUrl").getValue(String::class.java) ?: ""
                                    val status = userSnapshot.child("status").getValue(String::class.java) ?: "Available"
                                    
                                    // Create a contact with both device and app info
                                    matchingContacts.add(
                                        Contact(
                                            id = userId,
                                            name = matchingContact.name,
                                            phoneNumber = matchingContact.phoneNumber,
                                            username = username,
                                            profileImageUrl = profileImageUrl,
                                            status = status
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing user directly: ${e.message}")
                        }
                    }
                    
                    Log.d(TAG, "Found ${matchingContacts.size} matching contacts directly")
                    
                    // Complete the deferred with the list of matching contacts
                    latch.complete(matchingContacts)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in direct matching: ${e.message}", e)
                    latch.complete(emptyList())
                }
            }
            
            // Called when Firebase query is cancelled
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Direct Firebase query cancelled: ${error.message}")
                latch.complete(emptyList())
            }
        })
    }
    
    // Find matches with alternative phone number formats
    private fun findMatchesWithAlternativeFormats(deviceContacts: List<Contact>, snapshot: DataSnapshot): List<Contact> {
        val matchingContacts = mutableListOf<Contact>()
        
        try {
            // For each user in Firebase
            for (userSnapshot in snapshot.children) {
                // Get the phone number of this Firebase user
                val firebasePhone = userSnapshot.child("phoneNumber").getValue(String::class.java) ?: ""
                if (firebasePhone.isEmpty()) continue
                
                // Get user details from Firebase
                val userId = userSnapshot.child("uid").getValue(String::class.java) ?: continue
                val username = userSnapshot.child("username").getValue(String::class.java) ?: continue
                val profileImageUrl = userSnapshot.child("profileImageUrl").getValue(String::class.java) ?: ""
                val status = userSnapshot.child("status").getValue(String::class.java) ?: "Available"
                
                // Get all possible formats of the Firebase phone number
                val firebaseFormats = getPhoneNumberFormats(firebasePhone)
                
                // For each device contact
                for (contact in deviceContacts) {
                    // Get all possible formats of the contact phone number
                    val contactFormats = getPhoneNumberFormats(contact.phoneNumber)
                    
                    // Check for matches between the two sets of formats
                    for (firebaseFormat in firebaseFormats) {
                        // If any format matches between Firebase and device contact
                        if (contactFormats.contains(firebaseFormat)) {
                            // Create a contact with both device and app info
                            matchingContacts.add(
                                Contact(
                                    id = userId,
                                    name = contact.name,
                                    phoneNumber = contact.phoneNumber,
                                    username = username,
                                    profileImageUrl = profileImageUrl,
                                    status = status
                                )
                            )
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in alternative format matching: ${e.message}", e)
        }
        
        return matchingContacts
    }
    
    // Get all possible formats of a phone number for matching
    private fun getPhoneNumberFormats(phoneNumber: String): Set<String> {
        val formats = mutableSetOf<String>()

        // Start with a normalized version of the phone number
        val normalized = normalizePhoneNumber(phoneNumber)
        
        // Add the normalized format as the base format
        formats.add(normalized)
        
        // Without the + sign if present
        if (normalized.startsWith("+")) {
            formats.add(normalized.substring(1))
        }
        
        // For numbers that might be in international format but missing the +
        if (normalized.length > 10 && !normalized.startsWith("+")) {
            formats.add("+$normalized")
        }
        
        // For US/CA/MX numbers, try with and without country code
        if (normalized.startsWith("+1") && normalized.length == 12) {
            formats.add(normalized.substring(2))
        } else if (normalized.length == 10) {
            formats.add("+1$normalized")
        }
        
        // For UK numbers
        if (normalized.startsWith("+44") && normalized.length >= 12) {
            formats.add(normalized.substring(3))
            formats.add("0${normalized.substring(3)}")
        } else if (normalized.startsWith("0") && normalized.length == 11) {
            formats.add("+44${normalized.substring(1)}")
        }
        
        // Last 9 or 10 digits for any cases where only local part matches
        if (normalized.length >= 10) {
            formats.add(normalized.takeLast(10))
        }
        if (normalized.length >= 9) {
            formats.add(normalized.takeLast(9))
        }
        
        return formats
    }
    
    // Get all contacts with phone numbers from the device
    @SuppressLint("Range")
    private fun getDeviceContacts(context: Context): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val contentResolver: ContentResolver = context.contentResolver
        
        // Specify which columns we want to retrieve from the contacts provider
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        
        // Query the contacts provider for all contacts with phone numbers
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        
        // Use the cursor safely with Kotlin's 'use' function to ensure it's closed
        cursor?.use {
            val phoneSet = HashSet<String>()
            
            // Iterate through all contacts in the cursor
            while (it.moveToNext()) {
                // Get contact details from the cursor columns
                val id = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
                val name = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                val phoneNumber = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                
                // Normalize the phone number for deduplication
                val normalizedPhone = normalizePhoneNumber(phoneNumber)
                
                // Skip duplicate phone numbers and very short numbers
                if (!phoneSet.contains(normalizedPhone) && normalizedPhone.length >= 6) {
                    phoneSet.add(normalizedPhone)
                    contacts.add(Contact(id = id, name = name, phoneNumber = phoneNumber))
                }
            }
        }
        
        return contacts
    }
    
    // Normalize phone number for comparison
    private fun normalizePhoneNumber(phoneNumber: String): String {
        // Remove all characters except digits and the + sign
        return phoneNumber.replace(Regex("[^0-9+]"), "")
    }
} 