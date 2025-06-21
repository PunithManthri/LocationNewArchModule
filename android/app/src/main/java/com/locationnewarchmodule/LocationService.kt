package com.locationnewarchmodule

import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.google.android.gms.location.*
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger
import java.util.Timer
import java.util.TimerTask
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.pow

class LocationService : Service() {
    
    companion object {
        private const val TAG = "LocationService_NewArch"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "location_service_channel"
        private const val DEBUG_MODE = true
        
        // Core configuration constants
        private const val MIN_UPDATE_INTERVAL_MS = 500L
        private const val MAX_UPDATE_INTERVAL_MS = 30000L
        private const val ADAPTIVE_INTERVAL_STEP_MS = 1000L
        private const val MIN_ACCURACY_METERS = 50f
        private const val PREFERRED_ACCURACY_METERS = 25f
        private const val MAX_NETWORK_ACCURACY_METERS = 75f
        private const val MIN_GPS_ACCURACY_METERS = 30f
        private const val PREDICTION_CONFIDENCE_THRESHOLD = 0.75f
        private const val PREDICTION_HORIZON_MS = 5000L
        private const val MOVEMENT_PATTERN_BUFFER_SIZE = 10
        private const val VELOCITY_SMOOTHING_FACTOR = 0.8f
        private const val MIN_DISPLACEMENT_METERS = 2f
        private const val MIN_DISTANCE_FOR_UPDATE = 50f // 50 meters threshold
        private const val FORCE_UPDATE_INTERVAL_MS = 15000L
        private const val MOVEMENT_THRESHOLD_METERS = 1.5f
        private const val STATIONARY_TIME_THRESHOLD_MS = 20000L
        private const val BATTERY_SAVING_MODE_THRESHOLD = 0.15f
        private const val POWER_SAVING_UPDATE_MULTIPLIER = 3.0f
        private const val ULTRA_POWER_SAVING_MODE_THRESHOLD = 0.05f
        private const val ULTRA_POWER_SAVING_MULTIPLIER = 5.0f
        private const val LOCATION_CACHE_SIZE = 20
        private const val CACHE_CLEANUP_INTERVAL_MS = 300000L
        private const val MAX_CACHE_AGE_MS = 600000L
        private const val MAX_CONSECUTIVE_ERRORS = 5
        private const val ERROR_RECOVERY_DELAY_MS = 5000L
        private const val PROVIDER_SWITCH_COOLDOWN_MS = 10000L
        private const val PERFORMANCE_MONITORING_INTERVAL_MS = 60000L
        private const val METRICS_BUFFER_SIZE = 100
        private const val IDLE_UPDATE_INTERVAL_MS = 8000L
        private const val IDLE_ACCUMULATION_THRESHOLD_MS = 5000L
        private val IDLE_TIME_THRESHOLD_MS = 60000L
        
        // Core service state variables
        private var instance: WeakReference<LocationService>? = null
        private var lastKnownLocation: Location? = null
        private var lastUpdateTime: Long = 0
        private var lastLocationTime: Long = 0
        private var lastProviderLogTime: Long = 0
        private var preferredProvider: String? = null
        private var isServiceReady = false
        private var onFirstLocationCallback: ((Location) -> Unit)? = null
        
        // Predictive tracking variables
        private var movementPatternBuffer: MutableList<Location> = mutableListOf()
        private var predictedLocation: Location? = null
        private var predictionConfidence: Float = 0f
        private var velocityVector: Pair<Float, Float> = Pair(0f, 0f)
        private var accelerationVector: Pair<Float, Float> = Pair(0f, 0f)
        private var movementPatternType: MovementPatternType = MovementPatternType.UNKNOWN
        private var adaptiveUpdateInterval: Long = MIN_UPDATE_INTERVAL_MS
        
        // Battery optimization variables
        private var currentBatteryLevel: Float = 1.0f
        private var isPowerSavingMode: Boolean = false
        private var isUltraPowerSavingMode: Boolean = false
        private var powerSavingMultiplier: Float = 1.0f
        
        // Performance monitoring variables
        private lateinit var performanceMetrics: ConcurrentHashMap<String, AtomicLong>
        private var errorCount: AtomicInteger = AtomicInteger(0)
        private var consecutiveErrors: AtomicInteger = AtomicInteger(0)
        private var lastPerformanceReport: Long = 0L
        private var averageLocationAccuracy: Float = 0f
        private var averageUpdateInterval: Long = 0L
        private var totalLocationsProcessed: AtomicLong = AtomicLong(0)
        private var successfulPredictions: AtomicLong = AtomicLong(0)
        private var totalPredictions: AtomicLong = AtomicLong(0)
        
        // Intelligent caching variables
        private var locationCache: MutableList<Location> = mutableListOf()
        private var lastCacheCleanup: Long = 0L
        private var cacheHitRate: Float = 0f
        private var cacheMissRate: Float = 0f
        
        // Adaptive configuration variables
        private var currentAccuracyMode: AccuracyMode = AccuracyMode.BALANCED
        private lateinit var providerPerformanceMap: ConcurrentHashMap<String, ProviderPerformance>
        private var lastProviderSwitch: Long = 0L
        private var adaptiveAccuracyThreshold: Float = PREFERRED_ACCURACY_METERS
        
        // Idle time tracking variables
        private var totalDisplacementSinceLastUpdate: Float = 0f
    private var lastUpdateLocation: Location? = null
        private var totalIdleTimeBelowThreshold: Long = 0L
        private var idleStartTime: Long = 0L
        private var isCurrentlyIdle: Boolean = false
        private var isOutsideVisitTracking: Boolean = true
        private var outsideVisitStartTime: Long = 0L
        private var outsideVist_Total_IdleTime: Long = 0L
    private var lastKnownVisitId: String? = null
        private var lastLocationUpdateTime: Long = 0L
    private var idleTimeMetrics = IdleTimeMetrics()
    
        // Enumerations
        enum class MovementPatternType {
            STATIONARY, WALKING, DRIVING, RUNNING, UNKNOWN
        }
        
        enum class AccuracyMode {
            ULTRA_HIGH, HIGH, BALANCED, POWER_SAVING, ULTRA_POWER_SAVING
        }
        
        // Data classes
        data class ProviderPerformance(
            var accuracy: Float = 0f,
            var reliability: Float = 0f,
            var batteryEfficiency: Float = 0f,
            var lastUpdateTime: Long = 0L,
            var totalUpdates: Long = 0L,
            var successfulUpdates: Long = 0L
        )
        
        data class IdleTimeMetrics(
            var totalIdleTime: Long = 0L,
            var averageIdleDuration: Long = 0L,
            var longestIdlePeriod: Long = 0L,
            var idlePeriodCount: Long = 0L,
            var lastIdleStart: Long = 0L
        )
        
        // Service methods
        fun getInstance(): LocationService? = instance?.get()
        fun getLastKnownLocation(): Location? = lastKnownLocation
        fun isReady(): Boolean = isServiceReady
        
        fun setOnFirstLocationCallback(callback: (Location) -> Unit) {
            onFirstLocationCallback = callback
        }
        
        // Memory management
        fun cleanupMemory() {
            try {
                movementPatternBuffer.clear()
                locationCache.clear()
                providerPerformanceMap.clear()
                performanceMetrics.clear()
                lastKnownLocation = null
                lastUpdateLocation = null
                predictedLocation = null
                onFirstLocationCallback = null
                totalLocationsProcessed.set(0)
                successfulPredictions.set(0)
                totalPredictions.set(0)
                errorCount.set(0)
                consecutiveErrors.set(0)
            } catch (e: Exception) {
                Log.e(TAG, "Error during memory cleanup", e)
            }
        }

        fun hasLocationPermissions(context: android.content.Context): Boolean {
            val fine = android.Manifest.permission.ACCESS_FINE_LOCATION
            val coarse = android.Manifest.permission.ACCESS_COARSE_LOCATION
            val background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) android.Manifest.permission.ACCESS_BACKGROUND_LOCATION else null
            val foregroundServiceLocation = if (Build.VERSION.SDK_INT >= 34) "android.permission.FOREGROUND_SERVICE_LOCATION" else null
            
            val hasFine = ContextCompat.checkSelfPermission(context, fine) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, coarse) == PackageManager.PERMISSION_GRANTED
            val hasBackground = background?.let { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } ?: true
            val hasForegroundServiceLocation = foregroundServiceLocation?.let { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } ?: true
            
            // Accept either fine or coarse location permission
            val hasLocationPermission = hasFine || hasCoarse
            
            if (DEBUG_MODE) {
                Log.d(TAG, "🔐 [Service] Permission check in service context:")
                Log.d(TAG, "   - ACCESS_FINE_LOCATION: ${if (hasFine) "✅ GRANTED" else "❌ DENIED"}")
                Log.d(TAG, "   - ACCESS_COARSE_LOCATION: ${if (hasCoarse) "✅ GRANTED" else "❌ DENIED"}")
                Log.d(TAG, "   - ACCESS_BACKGROUND_LOCATION: ${if (hasBackground) "✅ GRANTED" else "❌ DENIED"}")
                Log.d(TAG, "   - FOREGROUND_SERVICE_LOCATION: ${if (hasForegroundServiceLocation) "✅ GRANTED" else "❌ DENIED"}")
                Log.d(TAG, "   - Has Location Permission: ${if (hasLocationPermission) "✅ YES" else "❌ NO"}")
                Log.d(TAG, "   - Final Result: ${if (hasLocationPermission && hasForegroundServiceLocation) "✅ ALL GRANTED" else "❌ MISSING PERMISSIONS"}")
            }
            
            // For foreground location tracking, we only need location permission and foreground service permission
            // ACCESS_BACKGROUND_LOCATION is only needed for background location tracking
            return hasLocationPermission && hasForegroundServiceLocation
        }
    }

    // Service instance variables
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var isLocationTrackingActive = false
    private var lastValidLocation: Location? = null
    private var isGooglePlayServicesAvailable = false
    
    // Timers
    private var adaptiveUpdateTimer: Timer? = null
    private var performanceMonitorTimer: Timer? = null
    private var batteryMonitorTimer: Timer? = null
    private var cacheCleanupTimer: Timer? = null
    private var errorRecoveryTimer: Timer? = null
    private var idleUpdateTimer: Timer? = null
    
    // Performance variables
    private var locationProcessingStartTime: Long = 0
    private var lastLocationProcessingTime: Long = 0
    private var locationQualityScore: Float = 0f
    private var isInErrorRecoveryMode = false
    private var errorRecoveryAttempts = 0
    private var lastErrorTime: Long = 0
    
    // Constants
    private val NOTIFICATION_CHANNEL_ID = "location_tracking_channel"
    private val MIN_SPEED_THRESHOLD = 0.5f
    private val GPS_SPEED_THRESHOLD = 0.3f
    private val NETWORK_SPEED_THRESHOLD = 1.0f
    
    override fun onCreate() {
        super.onCreate()
        if (DEBUG_MODE) Log.d(TAG, "🔵 LocationService onCreate - Service is being created")
            instance = WeakReference(this)
        isServiceReady = false

        performanceMetrics = ConcurrentHashMap<String, AtomicLong>()
        providerPerformanceMap = ConcurrentHashMap<String, ProviderPerformance>()

        try {
            if (DEBUG_MODE) Log.d(TAG, "🔵 LocationService onCreate - Initializing services")
            initializePerformanceMetrics()
            initializeLocationServices()
            restoreOutsideVisitData()
            startPerformanceMonitoring()
            startBatteryMonitoring()
            startCacheCleanupTimer()
            if (DEBUG_MODE) Log.d(TAG, "🔵 LocationService onCreate - All services initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "🔴 LocationService onCreate - Failed to initialize LocationService", e)
        }
    }
    
    private fun initializePerformanceMetrics() {
        performanceMetrics["totalAccuracy"] = AtomicLong(0)
        performanceMetrics["totalProcessingTime"] = AtomicLong(0)
        performanceMetrics["totalUpdateIntervals"] = AtomicLong(0)
        performanceMetrics["totalErrors"] = AtomicLong(0)
    }
    
    private fun initializeLocationServices() {
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            createNotificationChannel()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize location services", e)
            throw e
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (DEBUG_MODE) Log.d(TAG, "🔵 LocationService onStartCommand - Service is starting with startId: $startId")
        
        val action = intent?.action
        if (DEBUG_MODE) Log.d(TAG, "🔵 LocationService onStartCommand - Intent action: $action")
        
        try {
            when (action) {
                "START_TRACKING" -> {
                    if (DEBUG_MODE) Log.d(TAG, "🔵 LocationService onStartCommand - Starting location tracking")
                    if (!isServiceReady) {
                        if (DEBUG_MODE) Log.d(TAG, "🔵 LocationService onStartCommand - Service not ready, initializing")
                        startForeground(NOTIFICATION_ID, createNotification())
                        isServiceReady = true
                        if (DEBUG_MODE) Log.d(TAG, "🔵 LocationService onStartCommand - Service is now ready, starting location updates")
                        startLocationUpdatesWithRecovery()
                    } else {
                        if (DEBUG_MODE) Log.d(TAG, "🔵 LocationService onStartCommand - Service already ready, restarting location updates")
                        startLocationUpdatesWithRecovery()
                    }
                }
                "STOP_TRACKING" -> {
                    if (DEBUG_MODE) Log.d(TAG, "🔵 LocationService onStartCommand - Stopping location tracking")
                    stopLocationUpdates()
                    stopSelf()
                }
                else -> {
                    if (DEBUG_MODE) Log.d(TAG, "🔵 LocationService onStartCommand - No specific action, initializing service")
                    if (!isServiceReady) {
                        if (DEBUG_MODE) Log.d(TAG, "🔵 LocationService onStartCommand - Service not ready, initializing")
                        startForeground(NOTIFICATION_ID, createNotification())
                        isServiceReady = true
                        if (DEBUG_MODE) Log.d(TAG, "🔵 LocationService onStartCommand - Service is now ready, starting location updates")
                        startLocationUpdatesWithRecovery()
                    } else {
                        if (DEBUG_MODE) Log.d(TAG, "🔵 LocationService onStartCommand - Service already ready")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "🔴 LocationService onStartCommand - Error starting service", e)
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        if (DEBUG_MODE) Log.d(TAG, "🛑 LocationService onDestroy")
        try {
            cleanupAllTimers()
            cleanupLocationListeners()
            cleanupMemory()
            saveOutsideVisitData()
            instance = null
        } catch (e: Exception) {
            Log.e(TAG, "Error during service destruction", e)
        }
        super.onDestroy()
    }
    
    // Adaptive configuration methods
    private fun updateAdaptiveConfiguration() {
        adaptiveAccuracyThreshold = when (currentAccuracyMode) {
            AccuracyMode.ULTRA_HIGH -> PREFERRED_ACCURACY_METERS * 0.5f
            AccuracyMode.HIGH -> PREFERRED_ACCURACY_METERS * 0.75f
            AccuracyMode.BALANCED -> PREFERRED_ACCURACY_METERS
            AccuracyMode.POWER_SAVING -> PREFERRED_ACCURACY_METERS * 1.5f
            AccuracyMode.ULTRA_POWER_SAVING -> PREFERRED_ACCURACY_METERS * 2f
        }
        
        updateLocationRequest()
    }
    
    private fun updateLocationRequest() {
        try {
            stopLocationUpdates()
            
            val locationRequest = LocationRequest.Builder(adaptiveUpdateInterval)
                .setMinUpdateIntervalMillis(adaptiveUpdateInterval)
                .setMinUpdateDistanceMeters(MIN_DISPLACEMENT_METERS)
                .setWaitForAccurateLocation(currentAccuracyMode == AccuracyMode.ULTRA_HIGH)
                .setPriority(when (currentAccuracyMode) {
                    AccuracyMode.ULTRA_HIGH, AccuracyMode.HIGH -> Priority.PRIORITY_HIGH_ACCURACY
                    AccuracyMode.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
                    AccuracyMode.POWER_SAVING, AccuracyMode.ULTRA_POWER_SAVING -> Priority.PRIORITY_LOW_POWER
                })
            .build()
        
            startLocationUpdatesWithRequest(locationRequest)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating location request", e)
        }
    }
    
    // Location update methods
    private fun startLocationUpdatesWithRecovery() {
        if (DEBUG_MODE) Log.d(TAG, "🔵 startLocationUpdatesWithRecovery called")
        try {
            if (!canStartLocationUpdates()) {
                Log.e(TAG, "🔴 startLocationUpdatesWithRecovery - Cannot start location updates")
                return
            }
            
            startAdaptiveUpdateTimer()
            
            val locationRequest = createDefaultLocationRequest()
            if (DEBUG_MODE) Log.d(TAG, "🔵 startLocationUpdatesWithRecovery - Location request created: $locationRequest")
            
            startLocationUpdatesWithRequest(locationRequest)
            
            if (DEBUG_MODE) Log.d(TAG, "🔵 startLocationUpdatesWithRecovery - Location updates started successfully")
                } catch (e: Exception) {
            Log.e(TAG, "🔴 startLocationUpdatesWithRecovery - Failed to start location updates", e)
            handleLocationError(e)
        }
    }
    
    private fun canStartLocationUpdates(): Boolean {
        if (DEBUG_MODE) Log.d(TAG, "🔍 canStartLocationUpdates called")
        
        // Check if Google Play Services are available
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
        
        if (resultCode != ConnectionResult.SUCCESS) {
            if (DEBUG_MODE) Log.e(TAG, "🔴 Google Play Services not available: $resultCode")
            return false
        }
        
        // Check permissions
        if (!hasLocationPermissions(this)) {
            if (DEBUG_MODE) Log.e(TAG, "🔴 Location permissions not granted")
            return false
        }
        
        // Check if FusedLocationClient is initialized
        if (!::fusedLocationClient.isInitialized) {
            if (DEBUG_MODE) Log.e(TAG, "🔴 FusedLocationClient not initialized")
            return false
        }
        
        if (DEBUG_MODE) Log.d(TAG, "✅ canStartLocationUpdates - All checks passed")
        return true
    }
    
    private fun createDefaultLocationRequest(): LocationRequest {
        return LocationRequest.Builder(adaptiveUpdateInterval)
            .setMinUpdateIntervalMillis(adaptiveUpdateInterval)
            .setMinUpdateDistanceMeters(MIN_DISPLACEMENT_METERS)
            .setWaitForAccurateLocation(false)
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .build()
    }
    
    private fun startLocationUpdatesWithRequest(locationRequest: LocationRequest) {
        if (DEBUG_MODE) Log.d(TAG, "🔵 startLocationUpdatesWithRequest called with: $locationRequest")
        try {
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    if (DEBUG_MODE) Log.d(TAG, "🔵 LocationCallback onLocationResult called with ${locationResult.locations.size} locations")
                    
            val currentTime = System.currentTimeMillis()
                    val location = locationResult.locations.lastOrNull()
                    
                    if (location != null) {
                        if (DEBUG_MODE) Log.d(TAG, "🔵 Processing location: $location")
                        
                        if (lastKnownLocation == null) {
                            if (DEBUG_MODE) Log.d(TAG, "🔵 First location received")
                            handleFirstLocation(location, currentTime)
                        } else {
                            if (DEBUG_MODE) Log.d(TAG, "🔵 Processing subsequent location update")
                            processLocationUpdate(location, currentTime)
                        }
                    } else {
                        if (DEBUG_MODE) Log.w(TAG, "⚠️ No valid location in result")
                    }
                }
                
                override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                    if (DEBUG_MODE) Log.d(TAG, "🔵 Location availability changed: ${locationAvailability.isLocationAvailable}")
                }
            }
            
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
            if (DEBUG_MODE) Log.d(TAG, "🔵 startLocationUpdatesWithRequest - Location updates requested successfully")
        } catch (e: Exception) {
            Log.e(TAG, "🔴 startLocationUpdatesWithRequest - Failed to start custom location request", e)
            handleLocationError(e)
        }
    }
    
    private fun stopLocationUpdates() {
        if (DEBUG_MODE) Log.d(TAG, "stopLocationUpdates called")
        try {
            locationCallback?.let { callback ->
                fusedLocationClient.removeLocationUpdates(callback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location updates", e)
        }
    }
    
    private fun processLocationUpdate(location: Location, currentTime: Long) {
        if (DEBUG_MODE) Log.d(TAG, "🔄 processLocationUpdate called with: $location at $currentTime")
        
            try {
                val lastLocation = lastUpdateLocation
            val timeSinceLastUpdate = currentTime - lastUpdateTime
                val shouldForceUpdate = timeSinceLastUpdate >= FORCE_UPDATE_INTERVAL_MS
                
            if (DEBUG_MODE) {
                Log.d(TAG, "⏰ Time Analysis:")
                Log.d(TAG, "   📅 Current Time: $currentTime")
                Log.d(TAG, "   📅 Last Update Time: $lastUpdateTime")
                Log.d(TAG, "   ⏱️ Time Since Last Update: ${timeSinceLastUpdate}ms (${timeSinceLastUpdate/1000}s)")
                Log.d(TAG, "   🔄 Force Update Interval: ${FORCE_UPDATE_INTERVAL_MS}ms")
                Log.d(TAG, "   ⚡ Should Force Update: $shouldForceUpdate")
            }
            
            if (lastLocation != null) {
                val currentDisplacement = lastLocation.distanceTo(location)
                val totalDisplacement = totalDisplacementSinceLastUpdate + currentDisplacement
                
                if (DEBUG_MODE) {
                    Log.d(TAG, "📏 Distance Analysis:")
                    Log.d(TAG, "   📍 Current Displacement: ${currentDisplacement}m")
                    Log.d(TAG, "   📏 Total Displacement: ${totalDisplacement}m")
                    Log.d(TAG, "   🎯 Minimum Distance Threshold: ${MIN_DISTANCE_FOR_UPDATE}m")
                    Log.d(TAG, "   ✅ Above Distance Threshold: ${totalDisplacement >= MIN_DISTANCE_FOR_UPDATE}")
                }
                
                val isBelowThreshold = totalDisplacement < MIN_DISTANCE_FOR_UPDATE
                val isStationary = currentDisplacement < 1.0f
                
                if (DEBUG_MODE) {
                    Log.d(TAG, "🚶 Movement Analysis:")
                    Log.d(TAG, "   🛑 Is Below Threshold: $isBelowThreshold")
                    Log.d(TAG, "   🛑 Is Stationary: $isStationary")
                    Log.d(TAG, "   🏃 Movement Pattern: ${movementPatternType.name}")
                }
                
                if (isBelowThreshold && !shouldForceUpdate) {
                    if (DEBUG_MODE) {
                        Log.d(TAG, "⏭️ SKIPPING LOCATION UPDATE - Stationary/Insufficient Movement")
                        Log.d(TAG, "   📊 Reason: Total displacement (${totalDisplacement}m) < threshold (${MIN_DISTANCE_FOR_UPDATE}m)")
                        Log.d(TAG, "   ⏰ Time since last update: ${timeSinceLastUpdate}ms")
                        Log.d(TAG, "   🔄 Force update not due yet")
                    }
                    
                    updateIdleTimeTracking(currentTime, isBelowThreshold)
                    sendIdleTimeOnlyUpdate(location, currentTime)
                    totalDisplacementSinceLastUpdate = totalDisplacement
                    lastUpdateLocation = location
                    lastLocationUpdateTime = currentTime
                    return
                }
                
                if (isStationary) {
                    if (DEBUG_MODE) {
                        Log.d(TAG, "🛑 STATIONARY DETECTED - Minimal Movement")
                        Log.d(TAG, "   📏 Current Displacement: ${currentDisplacement}m")
                        Log.d(TAG, "   🎯 Stationary Threshold: 1.0m")
                        Log.d(TAG, "   🕐 Idle Time Accumulation: ${totalIdleTimeBelowThreshold}ms")
                    }
                }
                
                totalDisplacementSinceLastUpdate = totalDisplacement
            } else {
                if (DEBUG_MODE) Log.d(TAG, "🆕 First Location Update - No Previous Location")
            }
            
            if (DEBUG_MODE) Log.d(TAG, "✅ PROCEEDING WITH LOCATION UPDATE")
            
            sendLocationToReactNative(location, currentTime, false)
            updatePerformanceMetrics(location, System.currentTimeMillis() - currentTime)
            updateMovementPattern(location)
            
            lastUpdateLocation = location
            lastUpdateTime = currentTime
            lastLocationUpdateTime = currentTime
            lastValidLocation = location
                
            } catch (e: Exception) {
            if (DEBUG_MODE) Log.e(TAG, "❌ Error processing location update", e)
            handleLocationError(e)
        }
    }
    
    private fun handleLocationError(error: Exception) {
        Log.e(TAG, "handleLocationError called", error)
        lastErrorTime = System.currentTimeMillis()
        consecutiveErrors.incrementAndGet()
        errorCount.incrementAndGet()
        performanceMetrics["totalErrors"]?.incrementAndGet()
        
        if (consecutiveErrors.get() >= MAX_CONSECUTIVE_ERRORS) {
            startErrorRecovery()
        }
    }
    
    private fun handleFirstLocation(location: Location, currentTime: Long) {
        if (DEBUG_MODE) Log.d(TAG, "handleFirstLocation called with: $location at $currentTime")
        preferredProvider = location.provider?.lowercase()
        lastLocationTime = currentTime
        lastValidLocation = location
        
        lastUpdateLocation = location
        lastUpdateTime = currentTime
        lastLocationUpdateTime = currentTime
        
        updateMovementPattern(location)
        sendLocationToReactNative(location, currentTime, isFirstLocation = true)
        
        updateLastKnownLocation(location)
        isServiceReady = true
        onFirstLocationCallback?.invoke(location)
        onFirstLocationCallback = null
        
        stopLocationUpdates()
        startOptimizedLocationUpdates()
    }
    
    private fun startOptimizedLocationUpdates() {
        val locationRequest = LocationRequest.Builder(adaptiveUpdateInterval)
            .setMinUpdateIntervalMillis(adaptiveUpdateInterval)
            .setMinUpdateDistanceMeters(MIN_DISPLACEMENT_METERS)
            .setWaitForAccurateLocation(currentAccuracyMode == AccuracyMode.ULTRA_HIGH)
            .setPriority(when (currentAccuracyMode) {
                AccuracyMode.ULTRA_HIGH, AccuracyMode.HIGH -> Priority.PRIORITY_HIGH_ACCURACY
                AccuracyMode.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
                AccuracyMode.POWER_SAVING, AccuracyMode.ULTRA_POWER_SAVING -> Priority.PRIORITY_LOW_POWER
            })
            .build()
        
        startLocationUpdatesWithRequest(locationRequest)
    }
    
    // Predictive methods
    private fun updateMovementPattern(location: Location) {
        if (DEBUG_MODE) Log.d(TAG, "updateMovementPattern called with: $location")
        movementPatternBuffer.add(location)
        if (movementPatternBuffer.size > MOVEMENT_PATTERN_BUFFER_SIZE) {
            movementPatternBuffer.removeAt(0)
        }
        
        if (movementPatternBuffer.size >= 3) {
            analyzeMovementPattern()
            predictNextLocation()
        }
    }
    
    private fun analyzeMovementPattern() {
        if (DEBUG_MODE) Log.d(TAG, "🔍 analyzeMovementPattern called")
        if (movementPatternBuffer.size < 3) {
            if (DEBUG_MODE) Log.d(TAG, "⚠️ Insufficient movement pattern data (${movementPatternBuffer.size}/3)")
            return
        }
        
        val recent = movementPatternBuffer.takeLast(3)
        val speeds = recent.zipWithNext { a, b ->
            val distance = a.distanceTo(b)
            val timeDiff = (b.time - a.time) / 1000f
            if (timeDiff > 0) distance / timeDiff else 0f
        }
        
        val avgSpeed = speeds.average().toFloat()
        
        if (DEBUG_MODE) {
            Log.d(TAG, "📊 Movement Pattern Analysis:")
            Log.d(TAG, "   📏 Recent Locations: ${recent.size}")
            Log.d(TAG, "   🏃 Average Speed: ${avgSpeed}m/s")
            Log.d(TAG, "   📈 Speed Values: ${speeds.joinToString(", ") { "%.2f".format(it) }}")
        }
        
        val previousPattern = movementPatternType
        movementPatternType = when {
            avgSpeed < 0.5f -> MovementPatternType.STATIONARY
            avgSpeed < 2.0f -> MovementPatternType.WALKING
            avgSpeed < 8.0f -> MovementPatternType.RUNNING
            else -> MovementPatternType.DRIVING
        }
        
        if (DEBUG_MODE) {
            Log.d(TAG, "🏃 Movement Pattern Classification:")
            Log.d(TAG, "   📊 Previous Pattern: ${previousPattern.name}")
            Log.d(TAG, "   📊 Current Pattern: ${movementPatternType.name}")
            Log.d(TAG, "   🔄 Pattern Changed: ${previousPattern != movementPatternType}")
        }
        
        if (movementPatternType == MovementPatternType.STATIONARY) {
            if (DEBUG_MODE) {
                Log.d(TAG, "🛑 STATIONARY PATTERN DETECTED")
                Log.d(TAG, "   📏 Average Speed: ${avgSpeed}m/s (below 0.5m/s threshold)")
                Log.d(TAG, "   ⏰ Will use longer update intervals")
                Log.d(TAG, "   🕐 Current Idle Time: ${totalIdleTimeBelowThreshold}ms")
            }
        }
        
        val previousInterval = adaptiveUpdateInterval
        adaptiveUpdateInterval = when (movementPatternType) {
            MovementPatternType.STATIONARY -> (MAX_UPDATE_INTERVAL_MS * powerSavingMultiplier).toLong()
            MovementPatternType.WALKING -> (ADAPTIVE_INTERVAL_STEP_MS * 2 * powerSavingMultiplier).toLong()
            MovementPatternType.RUNNING -> (ADAPTIVE_INTERVAL_STEP_MS * powerSavingMultiplier).toLong()
            MovementPatternType.DRIVING -> (ADAPTIVE_INTERVAL_STEP_MS * powerSavingMultiplier).toLong()
            MovementPatternType.UNKNOWN -> MIN_UPDATE_INTERVAL_MS
        }.coerceIn(MIN_UPDATE_INTERVAL_MS, MAX_UPDATE_INTERVAL_MS)
        
        if (DEBUG_MODE) {
            Log.d(TAG, "⏰ Adaptive Update Interval:")
            Log.d(TAG, "   📊 Previous Interval: ${previousInterval}ms")
            Log.d(TAG, "   📊 Current Interval: ${adaptiveUpdateInterval}ms")
            Log.d(TAG, "   🔄 Interval Changed: ${previousInterval != adaptiveUpdateInterval}")
            Log.d(TAG, "   ⚡ Power Saving Multiplier: ${powerSavingMultiplier}")
        }
    }
    
    private fun predictNextLocation(): Location? {
        if (DEBUG_MODE) Log.d(TAG, "predictNextLocation called")
        if (movementPatternBuffer.size < 3) return null
        
        val recent = movementPatternBuffer.takeLast(3)
        val current = recent.last()
        
        val velocityX = (current.longitude - recent[1].longitude) / ((current.time - recent[1].time) / 1000f)
        val velocityY = (current.latitude - recent[1].latitude) / ((current.time - recent[1].time) / 1000f)
        
        velocityVector = Pair(
            velocityVector.first * (1 - VELOCITY_SMOOTHING_FACTOR) + velocityX.toFloat() * VELOCITY_SMOOTHING_FACTOR,
            velocityVector.second * (1 - VELOCITY_SMOOTHING_FACTOR) + velocityY.toFloat() * VELOCITY_SMOOTHING_FACTOR
        )
        
        val predictionTime = current.time + PREDICTION_HORIZON_MS
        val timeDiff = PREDICTION_HORIZON_MS / 1000f
        
        val predictedLat = current.latitude + velocityVector.second * timeDiff
        val predictedLng = current.longitude + velocityVector.first * timeDiff
        
        val speed = sqrt(velocityVector.first.pow(2) + velocityVector.second.pow(2))
        predictionConfidence = when (movementPatternType) {
            MovementPatternType.STATIONARY -> 0.95f
            MovementPatternType.WALKING -> 0.85f
            MovementPatternType.RUNNING -> 0.75f
            MovementPatternType.DRIVING -> 0.70f
            MovementPatternType.UNKNOWN -> 0.50f
        } * (1 - (speed / 20f).coerceIn(0f, 0.5f))
        
        predictedLocation = Location("predicted").apply {
            latitude = predictedLat
            longitude = predictedLng
            accuracy = current.accuracy * 1.5f
            time = predictionTime
        }
        
        return predictedLocation
    }
    
    // Battery optimization
    private fun updateBatteryOptimization() {
        if (DEBUG_MODE) Log.d(TAG, "updateBatteryOptimization called")
        val batteryThreshold = when {
            currentBatteryLevel <= ULTRA_POWER_SAVING_MODE_THRESHOLD -> {
                isUltraPowerSavingMode = true
                isPowerSavingMode = true
                ULTRA_POWER_SAVING_MULTIPLIER
            }
            currentBatteryLevel <= BATTERY_SAVING_MODE_THRESHOLD -> {
                isUltraPowerSavingMode = false
                isPowerSavingMode = true
                POWER_SAVING_UPDATE_MULTIPLIER
            }
            else -> {
                isUltraPowerSavingMode = false
                isPowerSavingMode = false
                1.0f
            }
        }
        
        powerSavingMultiplier = batteryThreshold
        
        currentAccuracyMode = when {
            isUltraPowerSavingMode -> AccuracyMode.ULTRA_POWER_SAVING
            isPowerSavingMode -> AccuracyMode.POWER_SAVING
            else -> AccuracyMode.BALANCED
        }
    }
    
    // Performance monitoring
    private fun updatePerformanceMetrics(location: Location?, processingTime: Long) {
        if (DEBUG_MODE) Log.d(TAG, "updatePerformanceMetrics called with: $location, processingTime: $processingTime")
        try {
            if (location != null) {
                performanceMetrics["totalAccuracy"]?.addAndGet(location.accuracy.toLong())
            }
            performanceMetrics["totalProcessingTime"]?.addAndGet(processingTime)
            
            val totalProcessed = totalLocationsProcessed.get()
            if (totalProcessed > 0) {
                averageLocationAccuracy = performanceMetrics["totalAccuracy"]?.get()?.toFloat()?.div(totalProcessed) ?: 0f
                averageUpdateInterval = performanceMetrics["totalUpdateIntervals"]?.get() ?: 0L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating performance metrics", e)
        }
    }
    
    // Idle time tracking
    private fun updateIdleTimeTracking(currentTime: Long, isBelowThreshold: Boolean) {
        if (DEBUG_MODE) {
            Log.d(TAG, "⏱️ updateIdleTimeTracking called with currentTime: $currentTime, isBelowThreshold: $isBelowThreshold")
        }
        
        if (isBelowThreshold) {
            if (!isCurrentlyIdle) {
                idleStartTime = currentTime
                isCurrentlyIdle = true
                if (DEBUG_MODE) Log.d(TAG, "🕐 Starting idle period at: $currentTime")
            }
            
            val ongoingIdleDuration = currentTime - idleStartTime
            totalIdleTimeBelowThreshold += ongoingIdleDuration
            
            if (DEBUG_MODE) {
                Log.d(TAG, "⏱️ Idle Time Tracking Update:")
                Log.d(TAG, "   🕐 Ongoing Idle Duration: ${ongoingIdleDuration}ms (${ongoingIdleDuration/1000}s)")
                Log.d(TAG, "   📊 Total Idle Time Below Threshold: ${totalIdleTimeBelowThreshold}ms (${totalIdleTimeBelowThreshold/1000}s)")
                Log.d(TAG, "   🎯 Is Currently Idle: $isCurrentlyIdle")
            }
            
            if (isOutsideVisitTracking) {
                outsideVist_Total_IdleTime += ongoingIdleDuration
                if (DEBUG_MODE) {
                    Log.d(TAG, "🏠 Outside Visit Idle Time Updated:")
                    Log.d(TAG, "   🕐 Outside Visit Total Idle: ${outsideVist_Total_IdleTime}ms (${outsideVist_Total_IdleTime/1000}s)")
                }
            }
            
            idleStartTime = currentTime
        } else {
            if (isCurrentlyIdle) {
                val idleEndDuration = currentTime - idleStartTime
                totalIdleTimeBelowThreshold += idleEndDuration
                
                if (DEBUG_MODE) {
                    Log.d(TAG, "✅ Ending idle period:")
                    Log.d(TAG, "   🕐 Final Idle Duration: ${idleEndDuration}ms (${idleEndDuration/1000}s)")
                    Log.d(TAG, "   📊 Total Idle Time Below Threshold: ${totalIdleTimeBelowThreshold}ms (${totalIdleTimeBelowThreshold/1000}s)")
                }
                
                if (isOutsideVisitTracking) {
                    outsideVist_Total_IdleTime += idleEndDuration
                    if (DEBUG_MODE) {
                        Log.d(TAG, "🏠 Outside Visit Idle Time Final Update:")
                        Log.d(TAG, "   🕐 Outside Visit Total Idle: ${outsideVist_Total_IdleTime}ms (${outsideVist_Total_IdleTime/1000}s)")
                    }
                }
                
                isCurrentlyIdle = false
                idleStartTime = 0L
            }
        }
    }
    
    private fun sendIdleTimeOnlyUpdate(location: Location, currentTime: Long) {
        if (DEBUG_MODE) {
            Log.d(TAG, "🚀 sendIdleTimeOnlyUpdate called with location: $location at $currentTime")
            Log.d(TAG, "📋 REASON: Location update skipped due to stationary/insufficient movement")
        }
        
        val idleTimeData = Arguments.createMap().apply {
            putDouble("latitude", -999.0)
            putDouble("longitude", -999.0)
            putDouble("accuracy", -1.0)
            putDouble("speed", -1.0)
            putDouble("timestamp", currentTime.toDouble())
            putString("provider", "idle_tracking")
            putString("type", "idle_time_only")
            putString("skipReason", "stationary_insufficient_movement")
            putDouble("idleTime", totalIdleTimeBelowThreshold.toDouble())
            putDouble("outsideVisitIdleTime", outsideVist_Total_IdleTime.toDouble())
            putBoolean("isOutsideVisitTracking", isOutsideVisitTracking)
            putDouble("lastLocationUpdateTime", lastLocationUpdateTime.toDouble())
            putDouble("idleThreshold", IDLE_TIME_THRESHOLD_MS.toDouble())
            putDouble("totalDisplacementSinceLastUpdate", totalDisplacementSinceLastUpdate.toDouble())
            putDouble("minDistanceForUpdate", MIN_DISTANCE_FOR_UPDATE.toDouble())
        }
        
        if (DEBUG_MODE) {
            Log.d(TAG, "📤 Sending Idle Time Data to React Native (Location Skipped):")
            Log.d(TAG, "   📍 Location: (${idleTimeData.getDouble("latitude")}, ${idleTimeData.getDouble("longitude")})")
            Log.d(TAG, "   🎯 Accuracy: ${idleTimeData.getDouble("accuracy")}")
            Log.d(TAG, "   🏃 Speed: ${idleTimeData.getDouble("speed")}")
            Log.d(TAG, "   ⏰ Timestamp: ${idleTimeData.getDouble("timestamp")}")
            Log.d(TAG, "   🔧 Provider: ${idleTimeData.getString("provider")}")
            Log.d(TAG, "   📋 Type: ${idleTimeData.getString("type")}")
            Log.d(TAG, "   🚫 Skip Reason: ${idleTimeData.getString("skipReason")}")
            Log.d(TAG, "   🕐 Total Idle Time: ${idleTimeData.getDouble("idleTime")}ms (${idleTimeData.getDouble("idleTime")/1000}s)")
            Log.d(TAG, "   🏠 Outside Visit Idle Time: ${idleTimeData.getDouble("outsideVisitIdleTime")}ms (${idleTimeData.getDouble("outsideVisitIdleTime")/1000}s)")
            Log.d(TAG, "   🎯 Is Outside Visit Tracking: ${idleTimeData.getBoolean("isOutsideVisitTracking")}")
            Log.d(TAG, "   📅 Last Location Update: ${idleTimeData.getDouble("lastLocationUpdateTime")}")
            Log.d(TAG, "   ⚡ Idle Threshold: ${idleTimeData.getDouble("idleThreshold")}ms")
            Log.d(TAG, "   📏 Total Displacement: ${idleTimeData.getDouble("totalDisplacementSinceLastUpdate")}m")
            Log.d(TAG, "   🎯 Min Distance Threshold: ${idleTimeData.getDouble("minDistanceForUpdate")}m")
        }
        
        sendLocationToReactNative(idleTimeData, false)
        if (DEBUG_MODE) Log.d(TAG, "✅ Idle time data sent to React Native successfully! (Location update skipped)")
    }
    
    private fun sendLocationToReactNative(location: Location, currentTime: Long, isFirstLocation: Boolean = false) {
        if (DEBUG_MODE) {
            Log.d(TAG, "📡 sendLocationToReactNative called with location: $location at $currentTime, isFirstLocation: $isFirstLocation")
            Log.d(TAG, "📍 Sending LOCATION DATA to React Native")
            Log.d(TAG, "   📍 Coordinates: (${location.latitude}, ${location.longitude})")
            Log.d(TAG, "   🎯 Accuracy: ${location.accuracy}m")
            Log.d(TAG, "   🏃 Speed: ${location.speed}m/s")
            Log.d(TAG, "   ⏰ Timestamp: ${location.time}")
            Log.d(TAG, "   🔧 Provider: ${location.provider}")
        }
        
        val locationData = Arguments.createMap().apply {
            putDouble("latitude", location.latitude)
            putDouble("longitude", location.longitude)
            putDouble("accuracy", location.accuracy.toDouble())
            putDouble("altitude", location.altitude)
            putDouble("speed", location.speed.toDouble())
            putDouble("speedAccuracy", 0.0)
            putDouble("heading", 0.0)
            putDouble("timestamp", location.time.toDouble())
            putDouble("displacement", totalDisplacementSinceLastUpdate.toDouble())
            putString("updateReason", "location_update")
            putBoolean("isSameLocation", false)
            putBoolean("isBelowDistanceThreshold", false)
            putDouble("totalIdleTimeBelowThreshold", totalIdleTimeBelowThreshold.toDouble())
            putBoolean("isCurrentlyIdle", isCurrentlyIdle)
            putDouble("outsideVist_Total_IdleTime", outsideVist_Total_IdleTime.toDouble())
            putBoolean("isOutsideVisitTracking", isOutsideVisitTracking)
        }
        
        LocationModule.sendLocationUpdate(locationData)
        if (DEBUG_MODE) Log.d(TAG, "✅ Location data sent to React Native via LocationModule.sendLocationUpdate()")
    }
    
    private fun sendLocationToReactNative(locationData: WritableMap, isFirstLocation: Boolean) {
        if (DEBUG_MODE) {
            Log.d(TAG, "📡 sendLocationToReactNative called with WritableMap, isFirstLocation: $isFirstLocation")
            
            val isIdleTimeData = locationData.hasKey("type") && locationData.getString("type") == "idle_time_only"
            
            if (isIdleTimeData) {
                Log.d(TAG, "🕐 Sending IDLE TIME DATA to React Native")
                Log.d(TAG, "   📊 Data Type: Idle Time Only Update")
                Log.d(TAG, "   🕐 Total Idle Time: ${locationData.getDouble("idleTime")}ms")
                Log.d(TAG, "   🏠 Outside Visit Idle: ${locationData.getDouble("outsideVisitIdleTime")}ms")
            } else {
                Log.d(TAG, "📍 Sending LOCATION DATA to React Native")
                Log.d(TAG, "   📍 Coordinates: (${locationData.getDouble("latitude")}, ${locationData.getDouble("longitude")})")
                Log.d(TAG, "   🎯 Accuracy: ${locationData.getDouble("accuracy")}m")
                Log.d(TAG, "   🏃 Speed: ${locationData.getDouble("speed")}m/s")
                Log.d(TAG, "   ⏰ Timestamp: ${locationData.getDouble("timestamp")}")
                Log.d(TAG, "   🔧 Provider: ${locationData.getString("provider")}")
            }
        }
        
        LocationModule.sendLocationUpdate(locationData)
        if (DEBUG_MODE) Log.d(TAG, "✅ Data sent to React Native via LocationModule.sendLocationUpdate()")
    }

    // Timer management methods
    private fun startAdaptiveUpdateTimer() {
        if (DEBUG_MODE) Log.d(TAG, "startAdaptiveUpdateTimer called")
        stopAdaptiveUpdateTimer()
        adaptiveUpdateTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    try {
                        updateAdaptiveConfiguration()
        } catch (e: Exception) {
                        Log.e(TAG, "Error in adaptive update timer", e)
                    }
                }
            }, 1000, 5000)
        }
    }
    
    private fun stopAdaptiveUpdateTimer() {
        adaptiveUpdateTimer?.cancel()
        adaptiveUpdateTimer = null
    }
    
    private fun startPerformanceMonitoring() {
        if (DEBUG_MODE) Log.d(TAG, "startPerformanceMonitoring called")
        stopPerformanceMonitoring()
        performanceMonitorTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    try {
                        updatePerformanceMetrics(null, 0)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in performance monitoring", e)
                    }
                }
            }, PERFORMANCE_MONITORING_INTERVAL_MS, PERFORMANCE_MONITORING_INTERVAL_MS)
        }
    }
    
    private fun stopPerformanceMonitoring() {
        performanceMonitorTimer?.cancel()
        performanceMonitorTimer = null
    }
    
    private fun startBatteryMonitoring() {
        if (DEBUG_MODE) Log.d(TAG, "startBatteryMonitoring called")
        stopBatteryMonitoring()
        batteryMonitorTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    try {
                        updateBatteryOptimization()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in battery monitoring", e)
                    }
                }
            }, 30000, 60000)
        }
    }
    
    private fun stopBatteryMonitoring() {
        batteryMonitorTimer?.cancel()
        batteryMonitorTimer = null
    }
    
    private fun startCacheCleanupTimer() {
        if (DEBUG_MODE) Log.d(TAG, "startCacheCleanupTimer called")
        stopCacheCleanupTimer()
        cacheCleanupTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    try {
                        cleanupLocationCache()
        } catch (e: Exception) {
                        Log.e(TAG, "Error in cache cleanup", e)
                    }
                }
            }, CACHE_CLEANUP_INTERVAL_MS, CACHE_CLEANUP_INTERVAL_MS)
        }
    }
    
    private fun stopCacheCleanupTimer() {
        cacheCleanupTimer?.cancel()
        cacheCleanupTimer = null
    }
    
    private fun startIdleUpdateTimer() {
        if (DEBUG_MODE) Log.d(TAG, "startIdleUpdateTimer called")
        stopIdleUpdateTimer()
        idleUpdateTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    try {
                        sendPeriodicIdleTimeUpdate()
        } catch (e: Exception) {
                        Log.e(TAG, "Error in idle update timer", e)
                    }
                }
            }, IDLE_UPDATE_INTERVAL_MS, IDLE_UPDATE_INTERVAL_MS)
        }
    }
    
    private fun stopIdleUpdateTimer() {
        idleUpdateTimer?.cancel()
        idleUpdateTimer = null
    }
    
    // Cleanup methods
    private fun cleanupAllTimers() {
        if (DEBUG_MODE) Log.d(TAG, "cleanupAllTimers called")
        stopAdaptiveUpdateTimer()
        stopPerformanceMonitoring()
        stopBatteryMonitoring()
        stopCacheCleanupTimer()
        stopErrorRecoveryTimer()
        stopIdleUpdateTimer()
    }
    
    private fun stopErrorRecoveryTimer() {
        errorRecoveryTimer?.cancel()
        errorRecoveryTimer = null
    }
    
    private fun cleanupLocationListeners() {
        if (DEBUG_MODE) Log.d(TAG, "cleanupLocationListeners called")
        try {
            locationCallback?.let { callback ->
                fusedLocationClient.removeLocationUpdates(callback)
            }
            locationCallback = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up location listeners", e)
        }
    }
    
    // Error recovery methods
    private fun startErrorRecovery() {
        if (DEBUG_MODE) Log.d(TAG, "startErrorRecovery called")
        if (isInErrorRecoveryMode) return
        
        isInErrorRecoveryMode = true
        errorRecoveryAttempts++
        
        errorRecoveryTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    try {
                        performErrorRecovery()
        } catch (e: Exception) {
                        Log.e(TAG, "Error in error recovery", e)
                    }
                }
            }, ERROR_RECOVERY_DELAY_MS)
        }
    }

    private fun performErrorRecovery() {
        if (DEBUG_MODE) Log.d(TAG, "performErrorRecovery called")
        try {
            cleanupLocationListeners()
            cleanupAllTimers()
            consecutiveErrors.set(0)
            isInErrorRecoveryMode = false
            initializeLocationServices()
        } catch (e: Exception) {
            Log.e(TAG, "Error recovery failed", e)
            isInErrorRecoveryMode = false
            
            if (errorRecoveryAttempts >= MAX_CONSECUTIVE_ERRORS) {
                stopSelf()
            }
        }
    }
    
    // Intelligent caching
    private fun updateLocationCache(location: Location) {
        if (DEBUG_MODE) Log.d(TAG, "updateLocationCache called with: $location")
        locationCache.add(location)
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCacheCleanup > CACHE_CLEANUP_INTERVAL_MS) {
            cleanupLocationCache()
            lastCacheCleanup = currentTime
        }
        
        if (locationCache.size > LOCATION_CACHE_SIZE) {
            locationCache.removeAt(0)
        }
    }
    
    private fun cleanupLocationCache() {
        if (DEBUG_MODE) Log.d(TAG, "cleanupLocationCache called")
        val currentTime = System.currentTimeMillis()
        locationCache.removeAll { location ->
            currentTime - location.time > MAX_CACHE_AGE_MS
        }
    }
    
    // Utility methods
    private fun updateLastKnownLocation(location: Location?) {
        if (DEBUG_MODE) Log.d(TAG, "updateLastKnownLocation called with: $location")
        lastKnownLocation = location
        if (location != null && !isServiceReady) {
            isServiceReady = true
            onFirstLocationCallback?.invoke(location)
            onFirstLocationCallback = null
        }
    }
    
    // Persistence methods
    private fun saveOutsideVisitData() {
        if (DEBUG_MODE) Log.d(TAG, "saveOutsideVisitData called")
        try {
            val sharedPrefs = getSharedPreferences("LocationService", android.content.Context.MODE_PRIVATE)
            sharedPrefs.edit().apply {
                putLong("outsideVist_Total_IdleTime", outsideVist_Total_IdleTime)
                putLong("lastOutsideVisitUpdate", System.currentTimeMillis())
                putLong("idleTimeMetrics_totalIdleTime", idleTimeMetrics.totalIdleTime)
                putLong("idleTimeMetrics_averageIdleDuration", idleTimeMetrics.averageIdleDuration)
                putLong("idleTimeMetrics_longestIdlePeriod", idleTimeMetrics.longestIdlePeriod)
                putLong("idleTimeMetrics_idlePeriodCount", idleTimeMetrics.idlePeriodCount)
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save outside visit data", e)
        }
    }
    
    private fun restoreOutsideVisitData() {
        if (DEBUG_MODE) Log.d(TAG, "restoreOutsideVisitData called")
        try {
            val prefs = getSharedPreferences("LocationService", android.content.Context.MODE_PRIVATE)
            val savedIdleTime = prefs.getLong("outsideVist_Total_IdleTime", 0L)
            val savedTracking = prefs.getBoolean("isOutsideVisitTracking", true)
            val savedStartTime = prefs.getLong("outsideVisitStartTime", 0L)
            
            idleTimeMetrics.totalIdleTime = prefs.getLong("idleTimeMetrics_totalIdleTime", 0L)
            idleTimeMetrics.averageIdleDuration = prefs.getLong("idleTimeMetrics_averageIdleDuration", 0L)
            idleTimeMetrics.longestIdlePeriod = prefs.getLong("idleTimeMetrics_longestIdlePeriod", 0L)
            idleTimeMetrics.idlePeriodCount = prefs.getLong("idleTimeMetrics_idlePeriodCount", 0L)
            
            outsideVist_Total_IdleTime = savedIdleTime
            isOutsideVisitTracking = savedTracking
            outsideVisitStartTime = savedStartTime
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore outside visit data", e)
            resetToDefaults()
        }
    }
    
    private fun resetToDefaults() {
        if (DEBUG_MODE) Log.d(TAG, "resetToDefaults called")
        outsideVist_Total_IdleTime = 0L
        isOutsideVisitTracking = true
        outsideVisitStartTime = 0L
        idleTimeMetrics = IdleTimeMetrics()
    }
    
    // Periodic idle time update
    private fun sendPeriodicIdleTimeUpdate() {
        if (isCurrentlyIdle || totalIdleTimeBelowThreshold > 0 || outsideVist_Total_IdleTime > 0) {
            val idleParams = Arguments.createMap().apply {
                putDouble("latitude", Double.NaN)
                putDouble("longitude", Double.NaN)
                putDouble("accuracy", -1.0)
                putDouble("speed", -1.0)
                putDouble("timestamp", System.currentTimeMillis().toDouble())
                putDouble("displacement", -1.0)
                putString("updateReason", "Periodic idle update")
                putBoolean("isSameLocation", false)
                putBoolean("isBelowDistanceThreshold", true)
                putDouble("totalIdleTimeBelowThreshold", totalIdleTimeBelowThreshold.toDouble())
                putBoolean("isCurrentlyIdle", isCurrentlyIdle)
                putDouble("outsideVist_Total_IdleTime", outsideVist_Total_IdleTime.toDouble())
                putBoolean("isOutsideVisitTracking", isOutsideVisitTracking)
            }
            
            sendLocationToReactNative(idleParams, false)
        }
    }
    
    // Visit status methods
    fun updateVisitStatus(visitId: String?) {
        if (visitId != null && visitId != lastKnownVisitId) {
            lastKnownVisitId = visitId
            isOutsideVisitTracking = false
            outsideVisitStartTime = System.currentTimeMillis()
        } else if (visitId == null && !isOutsideVisitTracking) {
            isOutsideVisitTracking = true
            outsideVisitStartTime = System.currentTimeMillis()
        }
    }
    
    fun stopOutsideVisitTracking() {
        isOutsideVisitTracking = false
    }
    
    // Notification methods
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous location tracking"
            }
            
            val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Location Tracking Active")
            .setContentText("Tracking your location in background")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setAutoCancel(false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
} 