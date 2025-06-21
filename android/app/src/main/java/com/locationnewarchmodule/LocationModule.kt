package com.locationnewarchmodule

import android.content.Intent
import android.location.Location
import android.util.Log
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import java.lang.ref.WeakReference

@ReactModule(name = LocationModule.NAME)
class LocationModule(reactContext: ReactApplicationContext) : 
    ReactContextBaseJavaModule(reactContext), PermissionListener {

    companion object {
        const val NAME = "LocationModule"
        private const val TAG = "LocationModule_NewArch"
        private const val PERMISSION_REQUEST_CODE = 1001
        private var instance: WeakReference<LocationModule>? = null
        private var listenerCount = AtomicInteger(0)
        
        // Debug configuration
        private const val DEBUG_MODE = true // Set to true for debug logging
        
        // Simplified event system
        private const val MAX_EVENT_QUEUE_SIZE = 10
        private val eventQueue = ArrayDeque<EventData>(MAX_EVENT_QUEUE_SIZE)
        private val queueLock = java.util.concurrent.locks.ReentrantLock()
        private var isProcessingQueue = AtomicBoolean(false)
        
        // Data structures
        data class EventData(
            val eventName: String,
            val params: WritableMap,
            val timestamp: Long
        )
        
        fun getInstance(): LocationModule? = instance?.get()
        
        fun sendLocationUpdate(locationData: WritableMap) {
            if (DEBUG_MODE) {
                // Check if this is idle time data
                val isIdleTimeData = locationData.hasKey("type") && locationData.getString("type") == "idle_time_only"
                
                if (isIdleTimeData) {
                    Log.d(TAG, "🕐 [NewArch] Processing IDLE TIME DATA for React Native")
                    Log.d(TAG, "   📊 Data Type: Idle Time Only Update")
                    Log.d(TAG, "   🕐 Total Idle Time: ${locationData.getDouble("idleTime")}ms (${locationData.getDouble("idleTime")/1000}s)")
                    Log.d(TAG, "   🏠 Outside Visit Idle: ${locationData.getDouble("outsideVisitIdleTime")}ms (${locationData.getDouble("outsideVisitIdleTime")/1000}s)")
                    Log.d(TAG, "   🎯 Is Outside Visit Tracking: ${locationData.getBoolean("isOutsideVisitTracking")}")
                    Log.d(TAG, "   📅 Last Location Update: ${locationData.getDouble("lastLocationUpdateTime")}")
                    Log.d(TAG, "   ⚡ Idle Threshold: ${locationData.getDouble("idleThreshold")}ms")
                } else {
                    Log.d(TAG, "📍 [NewArch] Processing LOCATION DATA for React Native")
                    Log.d(TAG, "   📍 Coordinates: (${locationData.getDouble("latitude")}, ${locationData.getDouble("longitude")})")
                    Log.d(TAG, "   🎯 Accuracy: ${locationData.getDouble("accuracy")}m")
                    Log.d(TAG, "   🏃 Speed: ${locationData.getDouble("speed")}m/s")
                    Log.d(TAG, "   ⏰ Timestamp: ${locationData.getDouble("timestamp")}")
                    Log.d(TAG, "   🔧 Provider: ${locationData.getString("provider")}")
                }
            }
            
            instance?.get()?.sendEvent("onLocationUpdate", locationData)
            if (DEBUG_MODE) Log.d(TAG, "✅ [NewArch] Location/Idle data sent to React Native via event system")
        }
        
        // Memory management
        fun cleanupMemory() {
            try {
                queueLock.lock()
                try {
                    eventQueue.clear()
                } finally {
                    queueLock.unlock()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ [NewArch] Error during memory cleanup", e)
            }
        }
    }

    // Permission request callback
    private var permissionPromise: Promise? = null
    private var pendingPermissions = mutableListOf<String>()

    init {
        instance = WeakReference(this)
        if (DEBUG_MODE) Log.d(TAG, "🔧 [NewArch] LocationModule initialized")
    }

    override fun getName() = NAME

    // React Native methods
    @ReactMethod
    fun startLocationTracking(promise: Promise) {
        if (DEBUG_MODE) Log.d(TAG, "🚀 [NewArch] startLocationTracking called")
        
        try {
            val currentActivity = currentActivity
            if (currentActivity == null) {
                if (DEBUG_MODE) Log.e(TAG, "❌ [NewArch] No activity available for startLocationTracking")
                promise.reject("NO_ACTIVITY", "No activity available")
                return
            }

            if (DEBUG_MODE) Log.d(TAG, "🔍 [NewArch] Checking permissions for startLocationTracking")

            // Check if we have the required permissions
            val hasFineLocation = ContextCompat.checkSelfPermission(
                currentActivity, 
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            val hasCoarseLocation = ContextCompat.checkSelfPermission(
                currentActivity, 
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            val hasForegroundServiceLocation = if (android.os.Build.VERSION.SDK_INT >= 34) {
                ContextCompat.checkSelfPermission(
                    currentActivity,
                    "android.permission.FOREGROUND_SERVICE_LOCATION"
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Not needed on lower API levels
            }

            if (DEBUG_MODE) {
                Log.d(TAG, "🔐 [NewArch] Permission check results:")
                Log.d(TAG, "   - ACCESS_FINE_LOCATION: ${if (hasFineLocation) "✅ GRANTED" else "❌ DENIED"}")
                Log.d(TAG, "   - ACCESS_COARSE_LOCATION: ${if (hasCoarseLocation) "✅ GRANTED" else "❌ DENIED"}")
                Log.d(TAG, "   - FOREGROUND_SERVICE_LOCATION: ${if (hasForegroundServiceLocation) "✅ GRANTED" else "❌ DENIED"}")
                Log.d(TAG, "   - API Level: ${android.os.Build.VERSION.SDK_INT}")
            }

            // Accept either fine or coarse location permission
            if ((!hasFineLocation && !hasCoarseLocation) || !hasForegroundServiceLocation) {
                if (DEBUG_MODE) Log.e(TAG, "❌ [NewArch] Missing required permissions for location tracking")
                val error = Arguments.createMap().apply {
                    putString("error", "Missing required permissions. Please request permissions first.")
                    putInt("code", -2)
                }
                promise.reject("PERMISSION_ERROR", "Missing required permissions", error)
                return
            }

            if (DEBUG_MODE) Log.d(TAG, "✅ [NewArch] All permissions granted, creating service intent")

            val intent = Intent(reactApplicationContext, LocationService::class.java)
            intent.action = "START_TRACKING"
            
            // Use startForegroundService for API 26+ and startService for older versions
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                if (DEBUG_MODE) Log.d(TAG, "🚀 [NewArch] Using startForegroundService (API 26+)")
                reactApplicationContext.startForegroundService(intent)
            } else {
                if (DEBUG_MODE) Log.d(TAG, "🚀 [NewArch] Using startService (API < 26)")
                reactApplicationContext.startService(intent)
            }
            
            if (DEBUG_MODE) Log.d(TAG, "✅ [NewArch] Service intent sent successfully")
            
            val result = Arguments.createMap().apply {
                putString("status", "success")
                putString("permission", "granted")
                putString("message", "Location tracking started")
            }
            promise.resolve(result)
            
            if (DEBUG_MODE) Log.d(TAG, "✅ [NewArch] startLocationTracking completed successfully")
        } catch (e: Exception) {
            if (DEBUG_MODE) Log.e(TAG, "❌ [NewArch] Error in startLocationTracking: ${e.message}", e)
            val error = Arguments.createMap().apply {
                putString("error", e.message ?: "Failed to start tracking")
                putInt("code", -1)
            }
            promise.reject("LOCATION_ERROR", e.message, error)
        }
    }

    @ReactMethod
    fun stopLocationTracking(promise: Promise) {
        if (DEBUG_MODE) Log.d(TAG, "🛑 [NewArch] stopLocationTracking called")
        
        try {
            val intent = Intent(reactApplicationContext, LocationService::class.java)
            intent.action = "STOP_TRACKING"
            
            if (DEBUG_MODE) Log.d(TAG, "🛑 [NewArch] Creating stop service intent")
            
            // Use startForegroundService for API 26+ and startService for older versions
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                if (DEBUG_MODE) Log.d(TAG, "🛑 [NewArch] Using startForegroundService for stop (API 26+)")
                reactApplicationContext.startForegroundService(intent)
            } else {
                if (DEBUG_MODE) Log.d(TAG, "🛑 [NewArch] Using startService for stop (API < 26)")
                reactApplicationContext.startService(intent)
            }
            
            if (DEBUG_MODE) Log.d(TAG, "✅ [NewArch] Stop service intent sent successfully")
            promise.resolve(true)
            
            if (DEBUG_MODE) Log.d(TAG, "✅ [NewArch] stopLocationTracking completed successfully")
        } catch (e: Exception) {
            if (DEBUG_MODE) Log.e(TAG, "❌ [NewArch] Error in stopLocationTracking: ${e.message}", e)
            val error = Arguments.createMap().apply {
                putString("error", e.message ?: "Failed to stop tracking")
                putInt("code", -1)
            }
            promise.reject("LOCATION_ERROR", e.message, error)
        }
    }

    @ReactMethod
    fun getLastLocation(promise: Promise) {
        if (DEBUG_MODE) Log.d(TAG, "📍 [NewArch] getLastLocation called")
        
        try {
            // Get actual last location from service companion object
            val lastLocation = LocationService.getLastKnownLocation()
            
            if (lastLocation != null) {
                if (DEBUG_MODE) Log.d(TAG, "📍 [NewArch] Found actual last location")
                
                val locationData = Arguments.createMap().apply {
                    putDouble("latitude", lastLocation.latitude)
                    putDouble("longitude", lastLocation.longitude)
                    putDouble("accuracy", lastLocation.accuracy.toDouble())
                    putDouble("altitude", lastLocation.altitude)
                    putDouble("speed", lastLocation.speed.toDouble())
                    putDouble("speedAccuracy", 0.0)
                    putDouble("heading", 0.0)
                    putDouble("timestamp", lastLocation.time.toDouble())
                    putDouble("displacement", 0.0)
                    putString("updateReason", "manual")
                    putBoolean("isSameLocation", false)
                    putBoolean("isBelowDistanceThreshold", false)
                    putDouble("totalIdleTimeBelowThreshold", 0.0)
                    putBoolean("isCurrentlyIdle", false)
                    putDouble("outsideVist_Total_IdleTime", 0.0)
                    putBoolean("isOutsideVisitTracking", true)
                }
                
                if (DEBUG_MODE) Log.d(TAG, "✅ [NewArch] Actual location data created successfully")
                promise.resolve(locationData)
            } else {
                if (DEBUG_MODE) Log.w(TAG, "⚠️ [NewArch] No last location available, returning null")
                promise.resolve(null)
            }
            
            if (DEBUG_MODE) Log.d(TAG, "✅ [NewArch] getLastLocation completed successfully")
        } catch (e: Exception) {
            if (DEBUG_MODE) Log.e(TAG, "❌ [NewArch] Error in getLastLocation: ${e.message}", e)
            val error = Arguments.createMap().apply {
                putString("error", e.message ?: "Failed to get location")
                putInt("code", -1)
            }
            promise.reject("LOCATION_ERROR", e.message, error)
        }
    }

    @ReactMethod
    fun requestLocationPermissions(promise: Promise) {
        if (DEBUG_MODE) Log.d(TAG, "🔐 [NewArch] requestLocationPermissions called")
        
        try {
            val currentActivity = currentActivity
            if (currentActivity == null) {
                if (DEBUG_MODE) Log.e(TAG, "❌ [NewArch] No activity available for permission request")
                promise.reject("NO_ACTIVITY", "No activity available to request permissions")
                return
            }

            if (currentActivity !is PermissionAwareActivity) {
                if (DEBUG_MODE) Log.e(TAG, "❌ [NewArch] Activity does not support permission requests")
                promise.reject("NO_PERMISSION_ACTIVITY", "Activity does not support permission requests")
                return
            }

            if (DEBUG_MODE) Log.d(TAG, "🔍 [NewArch] Checking current permission status")

            // Check if we already have the required permissions
            val hasFineLocation = ContextCompat.checkSelfPermission(
                currentActivity, 
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            val hasCoarseLocation = ContextCompat.checkSelfPermission(
                currentActivity, 
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            val hasForegroundServiceLocation = if (android.os.Build.VERSION.SDK_INT >= 34) {
                ContextCompat.checkSelfPermission(
                    currentActivity,
                    "android.permission.FOREGROUND_SERVICE_LOCATION"
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Not needed on lower API levels
            }

            if (DEBUG_MODE) {
                Log.d(TAG, "🔐 [NewArch] Current permission status:")
                Log.d(TAG, "   - ACCESS_FINE_LOCATION: ${if (hasFineLocation) "✅ GRANTED" else "❌ DENIED"}")
                Log.d(TAG, "   - ACCESS_COARSE_LOCATION: ${if (hasCoarseLocation) "✅ GRANTED" else "❌ DENIED"}")
                Log.d(TAG, "   - FOREGROUND_SERVICE_LOCATION: ${if (hasForegroundServiceLocation) "✅ GRANTED" else "❌ DENIED"}")
                Log.d(TAG, "   - API Level: ${android.os.Build.VERSION.SDK_INT}")
            }

            // Accept either fine or coarse location permission
            if ((hasFineLocation || hasCoarseLocation) && hasForegroundServiceLocation) {
                if (DEBUG_MODE) Log.d(TAG, "✅ [NewArch] All permissions already granted")
                // All permissions already granted
                val result = Arguments.createMap().apply {
                    putString("status", "granted")
                    putString("accuracy", if (hasFineLocation) "full" else "reduced")
                    putInt("accuracyStatus", if (hasFineLocation) 1 else 2)
                    putString("message", "All permissions already granted")
                }
                promise.resolve(result)
                return
            }

            if (DEBUG_MODE) Log.d(TAG, "🔐 [NewArch] Some permissions missing, preparing to request")

            // Store the promise for the callback
            permissionPromise = promise

            // Prepare permissions to request
            pendingPermissions.clear()
            
            if (!hasFineLocation && !hasCoarseLocation) {
                // Request fine location first, fallback to coarse if needed
                pendingPermissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
                if (DEBUG_MODE) Log.d(TAG, "🔐 [NewArch] Will request: ACCESS_FINE_LOCATION")
            }
            if (android.os.Build.VERSION.SDK_INT >= 34 && !hasForegroundServiceLocation) {
                pendingPermissions.add("android.permission.FOREGROUND_SERVICE_LOCATION")
                if (DEBUG_MODE) Log.d(TAG, "🔐 [NewArch] Will request: FOREGROUND_SERVICE_LOCATION")
            }

            if (pendingPermissions.isNotEmpty()) {
                if (DEBUG_MODE) Log.d(TAG, "🔐 [NewArch] Requesting ${pendingPermissions.size} permissions: ${pendingPermissions.joinToString(", ")}")
                
                // Request permissions
                currentActivity.requestPermissions(
                    pendingPermissions.toTypedArray(),
                    PERMISSION_REQUEST_CODE,
                    this
                )
                
                if (DEBUG_MODE) Log.d(TAG, "🔐 [NewArch] Permission request sent to activity")
            } else {
                if (DEBUG_MODE) Log.d(TAG, "✅ [NewArch] No permissions to request, all granted")
                val result = Arguments.createMap().apply {
                    putString("status", "granted")
                    putString("accuracy", if (hasFineLocation) "full" else "reduced")
                    putInt("accuracyStatus", if (hasFineLocation) 1 else 2)
                    putString("message", "All permissions granted")
                }
                promise.resolve(result)
            }
        } catch (e: Exception) {
            if (DEBUG_MODE) Log.e(TAG, "❌ [NewArch] Error in requestLocationPermissions: ${e.message}", e)
            val error = Arguments.createMap().apply {
                putString("error", e.message ?: "Failed to request permissions")
                putInt("code", -1)
            }
            promise.reject("PERMISSION_ERROR", e.message, error)
        }
    }

    @ReactMethod
    fun checkAccuracyAuthorization(promise: Promise) {
        if (DEBUG_MODE) Log.d(TAG, "🎯 [NewArch] checkAccuracyAuthorization called")
        
        try {
            val currentActivity = currentActivity
            if (currentActivity == null) {
                if (DEBUG_MODE) Log.e(TAG, "❌ [NewArch] No activity available for accuracy check")
                promise.reject("NO_ACTIVITY", "No activity available")
                return
            }

            val hasFineLocation = ContextCompat.checkSelfPermission(
                currentActivity, 
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            val accuracyStatus = if (hasFineLocation) 1 else 2 // 1 = full, 2 = reduced
            
            if (DEBUG_MODE) {
                Log.d(TAG, "🎯 [NewArch] Accuracy authorization check:")
                Log.d(TAG, "   - ACCESS_FINE_LOCATION: ${if (hasFineLocation) "✅ GRANTED" else "❌ DENIED"}")
                Log.d(TAG, "   - Accuracy Status: $accuracyStatus")
            }
            
            promise.resolve(accuracyStatus)
        } catch (e: Exception) {
            if (DEBUG_MODE) Log.e(TAG, "❌ [NewArch] Error in checkAccuracyAuthorization: ${e.message}", e)
            val error = Arguments.createMap().apply {
                putString("error", e.message ?: "Failed to check accuracy")
                putInt("code", -1)
            }
            promise.reject("ACCURACY_ERROR", e.message, error)
        }
    }

    @ReactMethod
    fun requestAccuracyAuthorization(promise: Promise) {
        if (DEBUG_MODE) Log.d(TAG, "🎯 [NewArch] requestAccuracyAuthorization called")
        
        try {
            // For now, return full accuracy if we have fine location permission
            val currentActivity = currentActivity
            if (currentActivity == null) {
                if (DEBUG_MODE) Log.e(TAG, "❌ [NewArch] No activity available for accuracy request")
                promise.reject("NO_ACTIVITY", "No activity available")
                return
            }

            val hasFineLocation = ContextCompat.checkSelfPermission(
                currentActivity, 
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            val accuracyStatus = if (hasFineLocation) 1 else 2 // 1 = full, 2 = reduced
            
            if (DEBUG_MODE) {
                Log.d(TAG, "🎯 [NewArch] Accuracy authorization request:")
                Log.d(TAG, "   - ACCESS_FINE_LOCATION: ${if (hasFineLocation) "✅ GRANTED" else "❌ DENIED"}")
                Log.d(TAG, "   - Accuracy Status: $accuracyStatus")
            }
            
            promise.resolve(accuracyStatus)
        } catch (e: Exception) {
            if (DEBUG_MODE) Log.e(TAG, "❌ [NewArch] Error in requestAccuracyAuthorization: ${e.message}", e)
            val error = Arguments.createMap().apply {
                putString("error", e.message ?: "Failed to request accuracy")
                putInt("code", -1)
            }
            promise.reject("ACCURACY_ERROR", e.message, error)
        }
    }

    @ReactMethod
    fun addListener(eventName: String) {
        if (DEBUG_MODE) Log.d(TAG, "👂 [NewArch] addListener called for event: $eventName")
        listenerCount.incrementAndGet()
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        if (DEBUG_MODE) Log.d(TAG, "👂 [NewArch] removeListeners called with count: $count")
        val currentCount = listenerCount.get()
        val newCount = maxOf(0, currentCount - count)
        listenerCount.set(newCount)
    }

    // Helper to send events to JS
    private fun sendEvent(eventName: String, params: WritableMap?) {
        if (DEBUG_MODE) Log.d(TAG, "📤 [NewArch] sendEvent called for event: $eventName")
        
        try {
            if (listenerCount.get() <= 0) {
                if (DEBUG_MODE) Log.d(TAG, "🔇 [NewArch] No listeners registered, skipping event: $eventName")
                return
            }
            
            reactApplicationContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(eventName, params)
            
            if (DEBUG_MODE) Log.d(TAG, "✅ [NewArch] Event sent successfully: $eventName")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [NewArch] Error sending event: $eventName", e)
        }
    }

    // Method to send errors from the service
    fun sendLocationError(errorData: WritableMap) {
        if (DEBUG_MODE) Log.d(TAG, "❌ [NewArch] sendLocationError called")
        sendEvent("onLocationError", errorData)
    }

    // Method to send permission changes from the service
    fun sendPermissionChange(permissionData: WritableMap) {
        if (DEBUG_MODE) Log.d(TAG, "🔐 [NewArch] sendPermissionChange called")
        sendEvent("onLocationPermissionChanged", permissionData)
    }
    
    // Cleanup
    override fun invalidate() {
        if (DEBUG_MODE) Log.d(TAG, "🧹 [NewArch] invalidate called")
        super.invalidate()
        
        try {
            cleanupMemory()
            instance = null
        } catch (e: Exception) {
            Log.e(TAG, "❌ [NewArch] Error during module cleanup", e)
        }
    }

    // Permission request callback
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (DEBUG_MODE) {
                Log.d(TAG, "🔐 [NewArch] Permission result received for request code: $requestCode")
                Log.d(TAG, "🔐 [NewArch] Permissions: ${permissions.joinToString(", ")}")
                Log.d(TAG, "🔐 [NewArch] Grant results: ${grantResults.joinToString(", ")}")
            }
            
            // Check if all permissions were granted
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            
            if (allGranted) {
                if (DEBUG_MODE) Log.d(TAG, "✅ [NewArch] All permissions granted successfully")
                
                // Check which location permission was granted
                val hasFineLocation = permissions.contains(android.Manifest.permission.ACCESS_FINE_LOCATION) &&
                    grantResults[permissions.indexOf(android.Manifest.permission.ACCESS_FINE_LOCATION)] == PackageManager.PERMISSION_GRANTED
                
                val result = Arguments.createMap().apply {
                    putString("status", "granted")
                    putString("accuracy", if (hasFineLocation) "full" else "reduced")
                    putInt("accuracyStatus", if (hasFineLocation) 1 else 2)
                    putString("message", "All permissions granted successfully")
                }
                permissionPromise?.resolve(result)
                if (DEBUG_MODE) Log.d(TAG, "✅ [NewArch] Permission promise resolved successfully")
            } else {
                if (DEBUG_MODE) Log.d(TAG, "❌ [NewArch] Some permissions denied")
                
                // Check which specific permissions were denied
                val deniedPermissions = mutableListOf<String>()
                for (i in permissions.indices) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        deniedPermissions.add(permissions[i])
                        if (DEBUG_MODE) Log.d(TAG, "❌ [NewArch] Permission denied: ${permissions[i]}")
                    } else {
                        if (DEBUG_MODE) Log.d(TAG, "✅ [NewArch] Permission granted: ${permissions[i]}")
                    }
                }
                
                val result = Arguments.createMap().apply {
                    putString("status", "denied")
                    putString("accuracy", "none")
                    putInt("accuracyStatus", 0)
                    putString("message", "Permissions denied: ${deniedPermissions.joinToString(", ")}")
                }
                permissionPromise?.reject("PERMISSION_ERROR", "Some permissions were denied", result)
                if (DEBUG_MODE) Log.d(TAG, "❌ [NewArch] Permission promise rejected")
            }
            permissionPromise = null
            if (DEBUG_MODE) Log.d(TAG, "🔐 [NewArch] Permission callback completed")
            return true // We handled the permission result
        }
        if (DEBUG_MODE) Log.d(TAG, "🔐 [NewArch] Permission result not for our request code: $requestCode")
        return false // We didn't handle this permission result
    }
} 