package com.example.chatapp.util

import android.app.Activity
import android.graphics.Color
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.recyclerview.widget.RecyclerView

// Utility class to help with rendering optimizations and performance improvements
object RenderingHelper {
    // Tag for identifying log messages from this class
    private const val TAG = "RenderingHelper"
    
    // Set up optimized rendering for an activity
    fun setupOptimizedRendering(activity: Activity) {
        try {
            // Set window background to white instead of transparent
            activity.window.decorView.setBackgroundColor(Color.WHITE)
            
            // Enable hardware acceleration for the window
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
            
            // Log successful completion for debugging purposes
            Log.d(TAG, "Optimized rendering setup completed for ${activity.javaClass.simpleName}")
        } catch (e: Exception) {
            // Log any errors that occur during setup
            Log.e(TAG, "Error setting up optimized rendering: ${e.message}")
        }
    }
    
    // Enable hardware acceleration for views
    fun enableHardwareAcceleration(rootView: View, vararg recyclerViews: RecyclerView) {
        try {
            // LAYER_TYPE_HARDWARE tells Android to use the GPU to render this view hierarchy
            rootView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            // Apply hardware acceleration to each RecyclerView passed to this method
            recyclerViews.forEach { recyclerView ->

                // Enable hardware acceleration on each RecyclerView
                recyclerView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                
                // Optimize RecyclerView for animations by removing change animation duration
                recyclerView.itemAnimator?.changeDuration = 0
            }
            
            // Log successful completion for debugging
            Log.d(TAG, "Hardware acceleration enabled for views")
        } catch (e: Exception) {
            // Log any errors that occur during the process
            Log.e(TAG, "Error enabling hardware acceleration: ${e.message}")
        }
    }
} 