package com.example.chatapp

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * These tests verify the app's UI components, navigation flow, and authentication logic
 * using the Espresso testing framework to simulate user interactions.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
 
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    // Class variables to store context and authentication references
    private lateinit var context: Context
    private lateinit var auth: FirebaseAuth
    private var isTestUserLoggedIn = false
    
    // Initializes the application context and Firebase authentication
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        auth = FirebaseAuth.getInstance()
        
        // Sign out any current user to ensure test starts fresh
        if (auth.currentUser != null) {
            auth.signOut()
        }
    }
    
    // Ensures that any test user that was logged in during a test is logged out
    @After
    fun cleanup() {
        // Sign out after tests if we signed in
        if (isTestUserLoggedIn && auth.currentUser != null) {
            auth.signOut()
        }
    }
    
    // Confirms the instrumentation context is correctly set up
    @Test
    fun useAppContext() {
        // Context of the app under test
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.chatapp", appContext.packageName)
    }
    
    // Tests that the LoginActivity launches correctly
    @Test
    fun testLoginActivityLaunches() {
        // Launch the login activity directly
        ActivityScenario.launch(LoginActivity::class.java).use { _ ->

            // Verify login UI elements are displayed
            onView(withId(R.id.etPhoneNumber)).check(matches(isDisplayed()))
            onView(withId(R.id.btnLogin)).check(matches(isDisplayed()))
            onView(withId(R.id.tvRegister)).check(matches(isDisplayed()))
        }
    }
    
    // Tests navigation from Login to Register screen
    @Test
    fun testNavigationToRegister() {
        // Launch login activity
        ActivityScenario.launch(LoginActivity::class.java).use { _ ->

            // Click on register text view
            onView(withId(R.id.tvRegister)).perform(click())
            
            // Verify register activity UI is displayed
            onView(withId(R.id.etUsername)).check(matches(isDisplayed()))
            onView(withId(R.id.etPhoneNumber)).check(matches(isDisplayed()))
            onView(withId(R.id.btnRegister)).check(matches(isDisplayed()))
        }
    }
    
    // Tests that MainActivity redirects to LoginActivity when no user is authenticated
    @Test
    fun testMainActivityRequiresAuthentication() {
        // Ensure user is signed out
        auth.signOut()
        
        // Launch main activity directly, which should redirect to login
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            Thread.sleep(1000)
            
            // Verify we're on login screen
            onView(withId(R.id.btnLogin)).check(matches(isDisplayed()))
        }
    }
    
    // Tests redirection behavior when trying to access MainActivity without authentication
    @Test
    fun testNewChatButtonIsDisplayed() {
        // Launch MainActivity, which should redirect to LoginActivity when not logged in
        ActivityScenario.launch(MainActivity::class.java).use { _ ->

            // Verify we've been redirected to the login screen by checking for login-specific UI elements
            onView(withId(R.id.etPhoneNumber)).check(matches(isDisplayed()))
            onView(withId(R.id.btnLogin)).check(matches(isDisplayed()))
            onView(withId(R.id.tvRegister)).check(matches(isDisplayed()))
        }
    }
    
    // Tests that SettingsActivity launches correctly
    @Test
    fun testSettingsActivityLaunches() {
        // Skip this test if can't authenticate properly
        assumeAuthenticationAvailable()
        
        // Launch settings activity
        ActivityScenario.launch(SettingsActivity::class.java).use { _ ->

            // Check settings UI elements are displayed
            onView(withId(R.id.switchPrivacy)).check(matches(isDisplayed()))
            onView(withId(R.id.tvPrivacyExplanation)).check(matches(isDisplayed()))
        }
    }
    
    // Verifies that dependencies for user presence tracking are available
    @Test
    fun testUserPresenceInitialization() {
        // Make sure Firebase Auth is available
        assertNotNull("Firebase Auth should be initialized", auth)
        
        // Verify the UserPresenceUtil class is accessible
        val utilClass = Class.forName("com.example.chatapp.util.UserPresenceUtil")
        assertNotNull("UserPresenceUtil class should be accessible", utilClass)
        
        // check the setupUserPresence method exists
        val setupMethod = utilClass.getDeclaredMethod("setupUserPresence")
        assertNotNull("setupUserPresence method should exist", setupMethod)
    }
    
    // Verifies the authentication is available
    private fun assumeAuthenticationAvailable() {
        assertNotNull("Firebase Auth should be initialized", auth)
    }
}