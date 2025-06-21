import Foundation
import CoreLocation
import React

@objcMembers
@objc(LocationModule)
class LocationModule: RCTEventEmitter {
    
    // MARK: - Advanced Constants (Adaptive and Intelligent)
    private struct Constants {
        // Core thresholds
        static let MIN_DISTANCE_FOR_UPDATE: Double = 50.0
        static let FORCE_UPDATE_INTERVAL_MS: Double = 30000.0
        static let PERIODIC_UPDATE_INTERVAL: Double = 10.0
        static let LOCATION_ACCURACY_THRESHOLD: Double = 100.0
        static let MAX_LOCATION_AGE: TimeInterval = 30.0
        static let BACKGROUND_UPDATE_INTERVAL: Double = 60.0
        static let IDLE_TIMER_INTERVAL: Double = 10.0
        // Advanced optimization constants
        static let ADAPTIVE_ACCURACY_THRESHOLD: Double = 50.0
        static let PREDICTIVE_UPDATE_THRESHOLD: Double = 25.0
        static let CACHE_SIZE: Int = 15
        static let MAX_CONSECUTIVE_ERRORS: Int = 3
        static let ERROR_RESET_INTERVAL: TimeInterval = 300
        static let PERFORMANCE_MONITORING_INTERVAL: TimeInterval = 60
        static let BATTERY_SAVING_MODE_THRESHOLD: TimeInterval = 300 // 5 minutes idle
        static let HIGH_ACCURACY_MODE_THRESHOLD: TimeInterval = 30 // 30 seconds active
        // Adaptive timing
        static let MIN_UPDATE_INTERVAL: TimeInterval = 5.0
        static let MAX_UPDATE_INTERVAL: TimeInterval = 120.0
        static let ADAPTIVE_INTERVAL_MULTIPLIER: Double = 1.5
    }
    
    // MARK: - Constants
    private static let DEBUG_MODE = true
    private static let TAG = "LocationModule_iOS"
    
    // MARK: - Core Properties
    private var locationManager: CLLocationManager?
    private var lastLocation: CLLocation?
    private var lastKnownLocation: CLLocation? = nil
    private var preferredProvider: String? = nil
    private var lastLocationTime: TimeInterval = 0
    private var lastLocationUpdateTime: TimeInterval = 0
    private var isTracking = false
    private var isServiceReady: Bool = false
    private var permissionCallback: RCTPromiseResolveBlock?
    private var listenerCount: [String: Int] = [:]
    private var hasListeners: Bool = false
    
    // MARK: - Location Tracking Properties
    private var lastUpdateTime: TimeInterval = 0
    private var lastUpdateLocation: CLLocation?
    private var totalDisplacementSinceLastUpdate: Double = 0.0
    
    // MARK: - Idle Time Tracking Properties
    private var totalIdleTimeBelowThreshold: TimeInterval = 0.0
    private var idleStartTime: TimeInterval = 0.0
    private var isCurrentlyIdle: Bool = false
    
    // MARK: - Outside Visit Tracking Properties
    private var outsideVist_Total_IdleTime: TimeInterval = 0.0
    private var isOutsideVisitTracking: Bool = false
    private var lastOutsideVisitUpdate: TimeInterval = 0.0
    private var lastKnownVisitId: String? = nil
    private var outsideVisitStartTime: TimeInterval = 0
    
    // MARK: - Advanced Performance Optimization Properties
    private var periodicTimer: Timer?
    private var performanceTimer: Timer?
    private var backgroundTaskID: UIBackgroundTaskIdentifier = .invalid
    private var locationUpdateQueue = DispatchQueue(label: "com.fsl.locationUpdate", qos: .utility)
    private var processingQueue = DispatchQueue(label: "com.fsl.locationProcessing", qos: .userInitiated)
    private var isInBackground = false
    private var lastAccuracyCheck: TimeInterval = 0
    private var consecutiveLowAccuracyCount = 0
    private var maxConsecutiveLowAccuracy = 5
    
    // MARK: - Advanced Memory Management
    private var locationCache: [CLLocation] = []
    private var locationHistory: [CLLocation] = []
    private var velocityHistory: [Double] = []
    private var accuracyHistory: [Double] = []
    
    // MARK: - Advanced Error Handling
    private var lastErrorTime: TimeInterval = 0
    private var errorCount = 0
    private var consecutiveErrors: Int = 0
    private var errorHistory: [Error] = []
    private var recoveryAttempts = 0
    private var maxRecoveryAttempts = 5
    
    // MARK: - Adaptive Performance Properties
    private var currentUpdateInterval: TimeInterval = Constants.FORCE_UPDATE_INTERVAL_MS / 1000
    private var adaptiveAccuracyMode: Bool = true
    private var batterySavingMode: Bool = false
    private var highAccuracyMode: Bool = false
    private var lastActivityTime: TimeInterval = 0
    private var averageVelocity: Double = 0
    private var averageAccuracy: Double = 0
    
    // MARK: - Performance Monitoring
    private var performanceMetrics = PerformanceMetrics()
    private var locationUpdateCount = 0
    private var successfulUpdates = 0
    private var failedUpdates = 0
    private var lastPerformanceReport: TimeInterval = 0
    private var totalLocationsProcessed: Int = 0
    private var averageLocationAccuracy: Double = 0.0
    private var averageUpdateInterval: Double = 0.0
    
    // MARK: - Predictive Location Properties
    private var predictedLocation: CLLocation?
    private var predictionConfidence: Double = 0
    private var lastPredictionTime: TimeInterval = 0
    
    // MARK: - Performance Metrics Structure
    private struct PerformanceMetrics {
        var averageProcessingTime: TimeInterval = 0
        var averageAccuracy: Double = 0
        var averageVelocity: Double = 0
        var errorRate: Double = 0
        var batteryImpact: Double = 0
        var memoryUsage: Double = 0
        var updateFrequency: Double = 0
    }
    
    // MARK: - Optimized Permission Handling
    private var pendingPermissionResolve: RCTPromiseResolveBlock? = nil
    private var pendingPermissionReject: RCTPromiseRejectBlock? = nil
    
    // MARK: - Initialization
    @objc
    override static func requiresMainQueueSetup() -> Bool {
        return true
    }

    @objc override init() {
        super.init()
        setupLocationManager()
        loadOutsideVisitData()
        setupBackgroundNotifications()
        setupPerformanceMonitoring()
        initializeAdaptiveSettings()
        print("LocationModule: 🚀 Initialized with advanced optimization configuration")
    }

    deinit {
        cleanup()
    }

    // MARK: - Advanced Initialization
    private func initializeAdaptiveSettings() {
        // Initialize adaptive settings based on device capabilities
        let deviceMemory = ProcessInfo.processInfo.physicalMemory
        let batteryLevel = UIDevice.current.batteryLevel
        // Adjust cache size based on available memory
        if deviceMemory > 4 * 1024 * 1024 * 1024 { // 4GB+
            locationCache.reserveCapacity(Constants.CACHE_SIZE)
        }
        // Set initial accuracy mode based on battery level
        if batteryLevel > 0.5 {
            highAccuracyMode = true
            adaptiveAccuracyMode = true
        } else {
            batterySavingMode = true
            adaptiveAccuracyMode = false
        }
        print("LocationModule: ⚙️ Adaptive settings initialized - Battery: \(batteryLevel), Memory: \(deviceMemory / 1024 / 1024)MB")
    }

    private func setupPerformanceMonitoring() {
        performanceTimer = Timer.scheduledTimer(withTimeInterval: Constants.PERFORMANCE_MONITORING_INTERVAL, repeats: true) { [weak self] _ in
            self?.updatePerformanceMetrics()
        }
        print("LocationModule: 📊 Performance monitoring initialized")
    }

    // MARK: - Advanced Performance Monitoring
    private func updatePerformanceMetrics() {
        let currentTime = Date().timeIntervalSince1970
        // Calculate performance metrics
        performanceMetrics.averageProcessingTime = calculateAverageProcessingTime()
        performanceMetrics.averageAccuracy = calculateAverageAccuracy()
        performanceMetrics.averageVelocity = calculateAverageVelocity()
        performanceMetrics.errorRate = calculateErrorRate()
        performanceMetrics.batteryImpact = calculateBatteryImpact()
        performanceMetrics.memoryUsage = calculateMemoryUsage()
        performanceMetrics.updateFrequency = calculateUpdateFrequency()
        // Adaptive adjustments based on performance
        adjustSettingsBasedOnPerformance()
        // Log performance report
        if currentTime - lastPerformanceReport > 300 { // Every 5 minutes
            logPerformanceReport()
            lastPerformanceReport = currentTime
        }
        // Clean up old data
        cleanupOldData()
    }

    private func calculateAverageProcessingTime() -> TimeInterval {
        // Implementation for processing time calculation
        return 0.1 // Placeholder
    }
    private func calculateAverageAccuracy() -> Double {
        guard !accuracyHistory.isEmpty else { return 0 }
        return accuracyHistory.reduce(0, +) / Double(accuracyHistory.count)
    }
    private func calculateAverageVelocity() -> Double {
        guard !velocityHistory.isEmpty else { return 0 }
        return velocityHistory.reduce(0, +) / Double(velocityHistory.count)
    }
    private func calculateErrorRate() -> Double {
        guard locationUpdateCount > 0 else { return 0 }
        return Double(failedUpdates) / Double(locationUpdateCount)
    }
    private func calculateBatteryImpact() -> Double {
        // Implementation for battery impact calculation
        return 0.05 // Placeholder
    }
    private func calculateMemoryUsage() -> Double {
        let cacheSize = locationCache.count + locationHistory.count
        return Double(cacheSize) / Double(Constants.CACHE_SIZE)
    }
    private func calculateUpdateFrequency() -> Double {
        let currentTime = Date().timeIntervalSince1970
        let timeSpan = currentTime - lastUpdateTime
        return timeSpan > 0 ? 1.0 / timeSpan : 0
    }
    private func adjustSettingsBasedOnPerformance() {
        // Adjust update interval based on performance
        if performanceMetrics.errorRate > 0.1 { // High error rate
            currentUpdateInterval = min(currentUpdateInterval * Constants.ADAPTIVE_INTERVAL_MULTIPLIER, Constants.MAX_UPDATE_INTERVAL)
            print("LocationModule: 🔧 Increased update interval due to high error rate: \(currentUpdateInterval)s")
        } else if performanceMetrics.errorRate < 0.05 && performanceMetrics.averageAccuracy < 20 { // Good performance
            currentUpdateInterval = max(currentUpdateInterval / Constants.ADAPTIVE_INTERVAL_MULTIPLIER, Constants.MIN_UPDATE_INTERVAL)
            print("LocationModule: 🔧 Decreased update interval due to good performance: \(currentUpdateInterval)s")
        }
        // Adjust accuracy mode based on battery and performance
        if performanceMetrics.batteryImpact > 0.1 && !highAccuracyMode {
            batterySavingMode = true
            adaptiveAccuracyMode = false
            print("LocationModule: 🔋 Switched to battery saving mode")
        } else if performanceMetrics.averageAccuracy > 50 && !batterySavingMode {
            highAccuracyMode = true
            adaptiveAccuracyMode = true
            print("LocationModule: 🎯 Switched to high accuracy mode")
        }
    }
    private func logPerformanceReport() {
        print("LocationModule: 📊 Performance Report:")
        print("  - Average Processing Time: \(performanceMetrics.averageProcessingTime)s")
        print("  - Average Accuracy: \(performanceMetrics.averageAccuracy)m")
        print("  - Average Velocity: \(performanceMetrics.averageVelocity)m/s")
        print("  - Error Rate: \(performanceMetrics.errorRate * 100)%")
        print("  - Battery Impact: \(performanceMetrics.batteryImpact * 100)%")
        print("  - Memory Usage: \(performanceMetrics.memoryUsage * 100)%")
        print("  - Update Frequency: \(performanceMetrics.updateFrequency) updates/s")
        print("  - Current Update Interval: \(currentUpdateInterval)s")
        print("  - Adaptive Mode: \(adaptiveAccuracyMode)")
        print("  - Battery Saving: \(batterySavingMode)")
        print("  - High Accuracy: \(highAccuracyMode)")
    }
    private func cleanupOldData() {
        let currentTime = Date().timeIntervalSince1970
        let maxAge: TimeInterval = 3600 // 1 hour
        // Clean up location history
        locationHistory = locationHistory.filter { currentTime - $0.timestamp.timeIntervalSince1970 < maxAge }
        // Clean up velocity and accuracy history (keep last 100 entries)
        if velocityHistory.count > 100 {
            velocityHistory = Array(velocityHistory.suffix(100))
        }
        if accuracyHistory.count > 100 {
            accuracyHistory = Array(accuracyHistory.suffix(100))
        }
        // Clean up error history (keep last 50 errors)
        if errorHistory.count > 50 {
            errorHistory = Array(errorHistory.suffix(50))
        }
    }

    // MARK: - Background Notification Setup
    private func setupBackgroundNotifications() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appDidEnterBackground),
            name: UIApplication.didEnterBackgroundNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appWillEnterForeground),
            name: UIApplication.willEnterForegroundNotification,
            object: nil
        )
    }
    @objc private func appDidEnterBackground() {
        isInBackground = true
        startBackgroundTask()
        print("LocationModule: 🌙 App entered background")
    }
    @objc private func appWillEnterForeground() {
        isInBackground = false
        endBackgroundTask()
        configureAdaptiveAccuracy() // Reconfigure for foreground
        print("LocationModule: ☀️ App entered foreground")
    }
    // MARK: - Background Task Management
    private func startBackgroundTask() {
        endBackgroundTask() // End any existing task
        backgroundTaskID = UIApplication.shared.beginBackgroundTask(withName: "LocationTracking") { [weak self] in
            self?.endBackgroundTask()
        }
        if backgroundTaskID != .invalid {
            print("LocationModule: 🔄 Started background task: \(backgroundTaskID.rawValue)")
        }
    }
    private func endBackgroundTask() {
        if backgroundTaskID != .invalid {
            UIApplication.shared.endBackgroundTask(backgroundTaskID)
            backgroundTaskID = .invalid
            print("LocationModule: 🔄 Ended background task")
        }
    }
    // MARK: - Cleanup
    private func cleanup() {
        print("LocationModule: 🧹 Cleaning up LocationModule")
        stopLocationTracking({ _ in }, rejecter: { _, _, _ in })
        stopPeriodicTimer()
        performanceTimer?.invalidate()
        performanceTimer = nil
        NotificationCenter.default.removeObserver(self)
        endBackgroundTask()
        clearLocationData()
    }

    override func supportedEvents() -> [String] {
        return ["onLocationUpdate", "onLocationError", "onLocationPermissionChanged"]
    }
    
    // MARK: - New Architecture Module Registration
    @objc
    static func registerModule() {
        // This method ensures the module is properly registered in the new architecture
        if LocationModule.DEBUG_MODE {
            print("🔧 [\(LocationModule.TAG)] Module registration called")
        }
    }
    
    // MARK: - RCTEventEmitter
    override func startObserving() {
        super.startObserving()
        hasListeners = true
        print("LocationModule: 👂 startObserving called, hasListeners: \(hasListeners)")
    }

    override func stopObserving() {
        super.stopObserving()
        hasListeners = false
        print("LocationModule: 👂 stopObserving called, hasListeners: \(hasListeners)")
        listenerCount.removeAll()
    }

    @objc
    override func addListener(_ eventName: String) {
        super.addListener(eventName)
        listenerCount[eventName] = (listenerCount[eventName] ?? 0) + 1
        hasListeners = true
        print("LocationModule: 👂 addListener for \(eventName), count: \(listenerCount[eventName] ?? 0)")
    }

    @objc(removeLocationListeners:)
    func removeLocationListeners(_ count: Int = 1) {
        print("LocationModule: 👂 removeLocationListeners called with count: \(count)")
        for (eventName, currentCount) in listenerCount {
            let removeCount = min(count, currentCount)
            if removeCount > 0 {
                listenerCount[eventName] = currentCount - removeCount
                if listenerCount[eventName] == 0 {
                    listenerCount.removeValue(forKey: eventName)
                }
            }
        }
        hasListeners = !listenerCount.isEmpty
        if let method = class_getInstanceMethod(RCTEventEmitter.self, Selector(("removeListeners:"))) {
            typealias RemoveListenersFunc = @convention(c) (AnyObject, Selector, Int) -> Void
            let implementation = method_getImplementation(method)
            let removeListeners = unsafeBitCast(implementation, to: RemoveListenersFunc.self)
            removeListeners(self, Selector(("removeListeners:")), count)
        }
        print("LocationModule: 👂 After removing listeners, hasListeners: \(hasListeners)")
    }

    // MARK: - Optimized Location Manager Setup
    private func setupLocationManager() {
        print("LocationModule: ⚙️ Setting up advanced location manager")
        // Clean up existing manager
        cleanupLocationManager()
        // Create new location manager with advanced settings
        locationManager = CLLocationManager()
        guard let locationManager = locationManager else {
            print("LocationModule: ❌ Failed to create location manager")
            return
        }
        // Configure delegate
        locationManager.delegate = self
        // Advanced optimization settings
        locationManager.allowsBackgroundLocationUpdates = true
        locationManager.pausesLocationUpdatesAutomatically = false
        locationManager.showsBackgroundLocationIndicator = true
        // Set activity type for better accuracy
        if #available(iOS 12.0, *) {
            locationManager.activityType = .fitness
        }
        // Configure adaptive accuracy
        configureAdaptiveAccuracy()
        // Set heading filter
        locationManager.headingFilter = kCLHeadingFilterNone
        print("LocationModule: ✅ Advanced location manager configured")
    }

    private func configureAdaptiveAccuracy() {
        guard let locationManager = locationManager else { return }
        let currentTime = Date().timeIntervalSince1970
        let timeSinceLastActivity = currentTime - lastActivityTime
        // Determine accuracy mode based on various factors
        var targetAccuracy: CLLocationAccuracy
        if batterySavingMode {
            targetAccuracy = kCLLocationAccuracyHundredMeters
            print("LocationModule: 🔋 Battery saving mode - using 100m accuracy")
        } else if highAccuracyMode {
            targetAccuracy = kCLLocationAccuracyBestForNavigation
            print("LocationModule: 🎯 High accuracy mode - using best navigation accuracy")
        } else if timeSinceLastActivity > Constants.BATTERY_SAVING_MODE_THRESHOLD {
            // Switch to battery saving if inactive
            batterySavingMode = true
            targetAccuracy = kCLLocationAccuracyHundredMeters
            print("LocationModule: 🔋 Switched to battery saving due to inactivity")
        } else if timeSinceLastActivity < Constants.HIGH_ACCURACY_MODE_THRESHOLD {
            // Switch to high accuracy if recently active
            highAccuracyMode = true
            targetAccuracy = kCLLocationAccuracyBestForNavigation
            print("LocationModule: 🎯 Switched to high accuracy due to recent activity")
        } else {
            // Default accuracy
            targetAccuracy = kCLLocationAccuracyBest
            print("LocationModule: ⚖️ Default accuracy mode - using best accuracy")
        }
        // Apply accuracy setting
        locationManager.desiredAccuracy = targetAccuracy
        // Adjust distance filter based on accuracy mode
        if batterySavingMode {
            locationManager.distanceFilter = 100.0 // 100 meters
        } else if highAccuracyMode {
            locationManager.distanceFilter = 10.0 // 10 meters
        } else {
            locationManager.distanceFilter = Constants.MIN_DISTANCE_FOR_UPDATE
        }
        print("LocationModule: ⚙️ Configured accuracy: \(targetAccuracy)m, distance filter: \(locationManager.distanceFilter)m")
    }

    private func cleanupLocationManager() {
        if let existingManager = locationManager {
            existingManager.stopUpdatingLocation()
            existingManager.stopMonitoringSignificantLocationChanges()
            if #available(iOS 14.0, *) {
                existingManager.stopMonitoringVisits()
            }
        }
    }

    // MARK: - Optimized Location Tracking
    @objc
    func startLocationTracking(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        print("LocationModule: 🚀 Starting optimized location tracking")
        // Reset error count on successful start
        errorCount = 0
        if isTracking {
            print("LocationModule: 🔄 Already tracking, stopping first")
            locationManager?.stopUpdatingLocation()
            locationManager?.stopMonitoringSignificantLocationChanges()
            if #available(iOS 14.0, *) {
                locationManager?.stopMonitoringVisits()
            }
            isTracking = false
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                self.startTrackingWithChecks(resolve: resolve, rejecter: reject)
            }
            return
        }
        startTrackingWithChecks(resolve: resolve, rejecter: reject)
    }

    private func startTrackingWithChecks(resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        guard let locationManager = locationManager else {
            print("LocationModule: ❌ Location manager not initialized")
            reject("ERROR", "Location manager not initialized", nil)
            return
        }
        let status = locationManager.authorizationStatus
        print("LocationModule: 🔐 Starting tracking with permission status: \(status.rawValue)")
        if status == .denied || status == .restricted {
            print("LocationModule: ❌ Cannot start tracking - permission denied or restricted")
            reject("PERMISSION_DENIED", "Location permission denied", nil)
            return
        }
        if CLLocationManager.locationServicesEnabled() {
            print("LocationModule: ✅ Starting location updates")
            isTracking = true
            print("LocationModule: 📍 Tracking state set to: \(isTracking)")
            // Ensure optimal accuracy configuration
            if #available(iOS 14.0, *) {
                let accuracyStatus = locationManager.accuracyAuthorization
                print("LocationModule: 🎯 Current accuracy authorization: \(accuracyStatus.rawValue)")
                if accuracyStatus != .fullAccuracy {
                    print("LocationModule: 🔄 Requesting full accuracy before starting")
                    locationManager.requestTemporaryFullAccuracyAuthorization(withPurposeKey: "LocationAccuracyPurpose")
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                        let newAccuracy = locationManager.accuracyAuthorization
                        print("LocationModule: 🎯 New accuracy authorization: \(newAccuracy.rawValue)")
                        self.configureAdaptiveAccuracy()
                        self.startAllLocationUpdates()
                        self.startPeriodicTimer()
                        let permissionType = status == .authorizedAlways ? "always" : "whenInUse"
                        print("LocationModule: ✅ Tracking started with permission: \(permissionType)")
                        resolve([
                            "status": "started",
                            "permission": permissionType,
                            "accuracy": newAccuracy == .fullAccuracy ? "full" : "reduced",
                            "message": "Location tracking started with \(permissionType) permission"
                        ])
                    }
                    return
                }
            }
            configureAdaptiveAccuracy()
            startAllLocationUpdates()
            startPeriodicTimer()
            let permissionType = status == .authorizedAlways ? "always" : "whenInUse"
            print("LocationModule: ✅ Tracking started with permission: \(permissionType)")
            resolve([
                "status": "started",
                "permission": permissionType,
                "message": "Location tracking started with \(permissionType) permission"
            ])
        } else {
            print("LocationModule: ❌ Location services disabled")
            isTracking = false
            reject("LOCATION_SERVICES_DISABLED", "Location services are disabled", nil)
        }
    }

    private func startAllLocationUpdates() {
        print("LocationModule: 📍 Starting all location updates")
        locationManager?.startUpdatingLocation()
        if #available(iOS 14.0, *) {
            locationManager?.startMonitoringVisits()
        }
        locationManager?.startMonitoringSignificantLocationChanges()
    }

    @objc
    func stopLocationTracking(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        print("LocationModule: 🛑 Stopping optimized location tracking")
        guard isTracking else {
            print("LocationModule: ⚠️ Not currently tracking, nothing to stop")
            resolve(true)
            return
        }
        // Stop all location updates first
        print("LocationModule: 🛑 Stopping location updates")
        locationManager?.stopUpdatingLocation()
        locationManager?.stopMonitoringSignificantLocationChanges()
        if #available(iOS 14.0, *) {
            locationManager?.stopMonitoringVisits()
        }
        // Stop periodic timer
        stopPeriodicTimer()
        // Wait a moment to ensure all updates are stopped
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            // Calculate final idle time if currently idle
            if self.isCurrentlyIdle {
                let finalIdleDuration = Date().timeIntervalSince1970 * 1000 - self.idleStartTime
                self.totalIdleTimeBelowThreshold += finalIdleDuration
                print("LocationModule: ⏱️ Tracking stopped while idle. Final idle duration: \(finalIdleDuration)ms, Total idle time: \(self.totalIdleTimeBelowThreshold)ms")
            }
            // Update and save final outside visit idle time
            self.updateOutsideVisitIdleTime()
            // Reset state
            self.isTracking = false
            print("LocationModule: 📍 Tracking state set to: \(self.isTracking)")
            // Clear location data and cache
            self.clearLocationData()
            // Clean up listeners
            print("LocationModule: 🧹 Cleaning up listeners")
            self.hasListeners = false
            self.listenerCount.removeAll()
            self.removeLocationListeners(1)
            // Clean up location manager
            print("LocationModule: 🔧 Resetting location manager")
            self.setupLocationManager()
            // End background task
            self.endBackgroundTask()
            print("LocationModule: ✅ Location tracking stopped and cleaned up")
            resolve(true)
        }
    }

    private func clearLocationData() {
        lastLocation = nil
        lastUpdateLocation = nil
        lastUpdateTime = 0
        totalDisplacementSinceLastUpdate = 0.0
        totalIdleTimeBelowThreshold = 0.0
        idleStartTime = 0.0
        isCurrentlyIdle = false
        locationCache.removeAll()
        locationHistory.removeAll()
        velocityHistory.removeAll()
        accuracyHistory.removeAll()
        consecutiveLowAccuracyCount = 0
        predictedLocation = nil
        predictionConfidence = 0
        recoveryAttempts = 0
        errorHistory.removeAll()
        locationUpdateCount = 0
        successfulUpdates = 0
        failedUpdates = 0
    }

    // MARK: - Periodic timer for idle time updates
    private func startPeriodicTimer() {
        stopPeriodicTimer() // Stop any existing timer
        // Use adaptive interval based on current performance
        let adaptiveInterval = max(Constants.IDLE_TIMER_INTERVAL, currentUpdateInterval)
        periodicTimer = Timer.scheduledTimer(withTimeInterval: adaptiveInterval, repeats: true) { [weak self] _ in
            self?.sendPeriodicIdleTimeUpdate()
        }
        print("LocationModule: ⏰ [IdleTimer] Started adaptive periodic timer: \(adaptiveInterval)s")
    }

    private func stopPeriodicTimer() {
        periodicTimer?.invalidate()
        periodicTimer = nil
        print("LocationModule: ⏰ [IdleTimer] Stopped periodic timer")
    }

    private func sendPeriodicIdleTimeUpdate() {
        guard isTracking && hasListeners else {
            print("LocationModule: ⏰ [IdleTimer] Skipping periodic update - not tracking or no listeners")
            return
        }
        // Calculate current idle time if currently idle
        var currentIdleTime = totalIdleTimeBelowThreshold
        if isCurrentlyIdle {
            let ongoingIdleDuration = Date().timeIntervalSince1970 * 1000 - idleStartTime
            currentIdleTime += ongoingIdleDuration
        }
        // Send idle time only update with invalid location values (matching Android)
        let idleTimeDict: [String: Any] = [
            "latitude": Double.nan,
            "longitude": Double.nan,
            "accuracy": -1.0,
            "altitude": -1.0,
            "speed": -1.0,
            "speedAccuracy": -1.0,
            "heading": -1.0,
            "timestamp": Date().timeIntervalSince1970 * 1000,
            "displacement": -1.0,
            "updateReason": "Periodic idle update",
            "isSameLocation": false,
            "isBelowDistanceThreshold": true,
            "totalIdleTimeBelowThreshold": currentIdleTime,
            "isCurrentlyIdle": isCurrentlyIdle,
            "outsideVist_Total_IdleTime": outsideVist_Total_IdleTime,
            "isOutsideVisitTracking": isOutsideVisitTracking
        ]
        print("LocationModule: 📦 [IdleTimer] PERIODIC idle time update params: " +
              "latitude: NaN (invalid), " +
              "longitude: NaN (invalid), " +
              "accuracy: -1.0, " +
              "speed: -1.0, " +
              "timestamp: \(Date().timeIntervalSince1970 * 1000), " +
              "displacement: -1.0, " +
              "updateReason: Periodic idle update, " +
              "isSameLocation: false, " +
              "isBelowDistanceThreshold: true, " +
              "totalIdleTimeBelowThreshold: \(currentIdleTime), " +
              "isCurrentlyIdle: \(isCurrentlyIdle), " +
              "outsideVist_Total_IdleTime: \(outsideVist_Total_IdleTime), " +
              "isOutsideVisitTracking: \(isOutsideVisitTracking)")
        sendEvent(withName: "onLocationUpdate", body: idleTimeDict)
    }

    // MARK: - React Native Methods
    @objc(getLastLocation:rejecter:)
    func getLastLocation(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        if LocationModule.DEBUG_MODE {
            print("📍 [\(LocationModule.TAG)] getLastLocation called")
        }
        
        if let lastLocation = lastLocation {
            if LocationModule.DEBUG_MODE {
                print("📍 [\(LocationModule.TAG)] Found actual last location")
            }
            
            let locationData: [String: Any] = [
                "latitude": lastLocation.coordinate.latitude,
                "longitude": lastLocation.coordinate.longitude,
                "accuracy": lastLocation.horizontalAccuracy,
                "altitude": lastLocation.altitude,
                "speed": lastLocation.speed,
                "speedAccuracy": 0.0,
                "heading": 0.0,
                "timestamp": lastLocation.timestamp.timeIntervalSince1970 * 1000,
                "displacement": 0.0,
                "updateReason": "manual",
                "isSameLocation": false,
                "isBelowDistanceThreshold": false,
                "totalIdleTimeBelowThreshold": totalIdleTimeBelowThreshold * 1000,
                "isCurrentlyIdle": isCurrentlyIdle,
                "outsideVist_Total_IdleTime": outsideVist_Total_IdleTime * 1000,
                "isOutsideVisitTracking": isOutsideVisitTracking
            ]
            
            resolve(locationData)
        } else {
            if LocationModule.DEBUG_MODE {
                print("⚠️ [\(LocationModule.TAG)] No last location available, returning null")
            }
            resolve(nil)
        }
    }
    
    @objc
    func requestLocationPermissions(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        guard let locationManager = locationManager else {
            print("LocationModule: ❌ Location manager not initialized")
            reject("ERROR", "Location manager not initialized", nil)
            return
        }
        let currentStatus = locationManager.authorizationStatus
        print("LocationModule: 🔐 Current authorization status: \(currentStatus.rawValue)")
        switch currentStatus {
        case .authorizedAlways:
            handleAlwaysAuthorized(resolve: resolve)
        case .authorizedWhenInUse:
            handleWhenInUseAuthorized(resolve: resolve)
        case .denied:
            print("LocationModule: ❌ Permission denied")
            reject("PERMISSION_DENIED", "Location permission denied", nil)
        case .restricted:
            print("LocationModule: ❌ Permission restricted")
            reject("PERMISSION_RESTRICTED", "Location permission restricted", nil)
        case .notDetermined:
            print("LocationModule: ❓ Permission not determined, requesting when in use")
            // Store the promise blocks to resolve/reject later in the delegate
            pendingPermissionResolve = resolve
            pendingPermissionReject = reject
            locationManager.requestWhenInUseAuthorization()
        @unknown default:
            print("LocationModule: ❓ Unknown permission status")
            reject("UNKNOWN_STATUS", "Unknown permission status", nil)
        }
    }
    
    private func handleAlwaysAuthorized(resolve: @escaping RCTPromiseResolveBlock) {
        print("LocationModule: ✅ Already authorized always")
        if #available(iOS 14.0, *) {
            let accuracyStatus = locationManager?.accuracyAuthorization
            print("LocationModule: 🎯 Accuracy authorization status: \(accuracyStatus?.rawValue ?? -1)")
            if accuracyStatus != .fullAccuracy {
                print("LocationModule: 🔄 Requesting full accuracy")
                locationManager?.requestTemporaryFullAccuracyAuthorization(withPurposeKey: "LocationAccuracyPurpose")
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                    let newAccuracy = self.locationManager?.accuracyAuthorization
                    print("LocationModule: 🎯 New accuracy authorization: \(newAccuracy?.rawValue ?? -1)")
                    self.configureAdaptiveAccuracy()
                    resolve([
                        "status": "authorizedAlways",
                        "accuracy": newAccuracy == .fullAccuracy ? "full" : "reduced",
                        "accuracyStatus": newAccuracy?.rawValue ?? 0
                    ])
                }
                return
            }
            configureAdaptiveAccuracy()
            resolve([
                "status": "authorizedAlways",
                "accuracy": "full",
                "accuracyStatus": accuracyStatus?.rawValue ?? 0
            ])
        } else {
            resolve(["status": "authorizedAlways"])
        }
    }

    private func handleWhenInUseAuthorized(resolve: @escaping RCTPromiseResolveBlock) {
        print("LocationModule: ⚠️ Authorized when in use")
        permissionCallback = resolve
        locationManager?.requestAlwaysAuthorization()
        print("LocationModule: 🔄 Requesting always authorization")
        resolve(["status": "authorizedWhenInUse", "message": "Location permission granted for when in use"])
    }

    @objc(checkAccuracyAuthorization:rejecter:)
    func checkAccuracyAuthorization(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        if #available(iOS 14.0, *) {
            guard let locationManager = locationManager else {
                reject("ERROR", "Location manager not initialized", nil)
                return
            }
            let accuracyStatus = locationManager.accuracyAuthorization
            print("LocationModule: 🎯 Checking accuracy authorization: \(accuracyStatus.rawValue)")
            if accuracyStatus != .fullAccuracy {
                print("LocationModule: 🔄 Requesting full accuracy during check")
                locationManager.requestTemporaryFullAccuracyAuthorization(withPurposeKey: "LocationAccuracyPurpose")
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                    let newAccuracy = locationManager.accuracyAuthorization
                    print("LocationModule: 🎯 New accuracy authorization after request: \(newAccuracy.rawValue)")
                    self.configureAdaptiveAccuracy()
                    resolve(newAccuracy == .fullAccuracy ? 1 : 0)
                }
                return
            }
            resolve(accuracyStatus == .fullAccuracy ? 1 : 0)
        } else {
            resolve(1)
        }
    }
    
    @objc(requestAccuracyAuthorization:rejecter:)
    func requestAccuracyAuthorization(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        if #available(iOS 14.0, *) {
            guard let locationManager = locationManager else {
                reject("ERROR", "Location manager not initialized", nil)
                return
            }
            let currentAccuracy = locationManager.accuracyAuthorization
            print("LocationModule: 🎯 Current accuracy authorization: \(currentAccuracy.rawValue)")
            if currentAccuracy == .fullAccuracy {
                print("LocationModule: ✅ Full accuracy already granted")
                resolve(1)
                return
            }
            // Request temporary full accuracy authorization
            locationManager.requestTemporaryFullAccuracyAuthorization(withPurposeKey: "LocationAccuracyPurpose")
            // Wait for the authorization to change with timeout
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                let newAccuracy = locationManager.accuracyAuthorization
                print("LocationModule: 🎯 New accuracy authorization: \(newAccuracy.rawValue)")
                self.configureAdaptiveAccuracy()
                resolve(newAccuracy == .fullAccuracy ? 1 : 0)
            }
        } else {
            resolve(1)
        }
    }
    
    // MARK: - Location Processing
    private func processLocationUpdate(_ location: CLLocation, currentTime: TimeInterval) {
        if LocationModule.DEBUG_MODE {
            print("🔄 [\(LocationModule.TAG)] processLocationUpdate called with: \(location) at \(currentTime)")
        }
        
        do {
            let lastLocation = lastUpdateLocation
            let timeSinceLastUpdate = currentTime - lastUpdateTime
            let shouldForceUpdate = timeSinceLastUpdate >= Constants.FORCE_UPDATE_INTERVAL_MS / 1000
            
            if LocationModule.DEBUG_MODE {
                print("⏰ Time Analysis:")
                print("   📅 Current Time: \(currentTime)")
                print("   📅 Last Update Time: \(lastUpdateTime)")
                print("   ⏱️ Time Since Last Update: \(timeSinceLastUpdate)s")
                print("   🔄 Force Update Interval: \(Constants.FORCE_UPDATE_INTERVAL_MS / 1000)s")
                print("   ⚡ Should Force Update: \(shouldForceUpdate)")
            }
            
            if let lastLocation = lastLocation {
                let currentDisplacement = lastLocation.distance(from: location)
                let totalDisplacement = totalDisplacementSinceLastUpdate + currentDisplacement
                
                if LocationModule.DEBUG_MODE {
                    print("📏 Distance Analysis:")
                    print("   📍 Current Displacement: \(currentDisplacement)m")
                    print("   📏 Total Displacement: \(totalDisplacement)m")
                    print("   🎯 Minimum Distance Threshold: \(Constants.MIN_DISTANCE_FOR_UPDATE)m")
                    print("   ✅ Above Distance Threshold: \(totalDisplacement >= Constants.MIN_DISTANCE_FOR_UPDATE)")
                }
                
                let isBelowThreshold = totalDisplacement < Constants.MIN_DISTANCE_FOR_UPDATE
                let isStationary = currentDisplacement < 1.0
                
                if LocationModule.DEBUG_MODE {
                    print("🚶 Movement Analysis:")
                    print("   🛑 Is Below Threshold: \(isBelowThreshold)")
                    print("   🛑 Is Stationary: \(isStationary)")
                }
                
                if isBelowThreshold && !shouldForceUpdate {
                    if LocationModule.DEBUG_MODE {
                        print("⏭️ SKIPPING LOCATION UPDATE - Stationary/Insufficient Movement")
                        print("   📊 Reason: Total displacement (\(totalDisplacement)m) < threshold (\(Constants.MIN_DISTANCE_FOR_UPDATE)m)")
                        print("   ⏰ Time since last update: \(timeSinceLastUpdate)s")
                        print("   🔄 Force update not due yet")
                    }
                    
                    updateIdleTimeTracking(currentTime: currentTime, isBelowThreshold: isBelowThreshold)
                    sendIdleTimeOnlyUpdate(location: location, currentTime: currentTime)
                    totalDisplacementSinceLastUpdate = totalDisplacement
                    lastUpdateLocation = location
                    return
                }
                
                if isStationary {
                    if LocationModule.DEBUG_MODE {
                        print("🛑 STATIONARY DETECTED - Minimal Movement")
                        print("   📏 Current Displacement: \(currentDisplacement)m")
                        print("   🎯 Stationary Threshold: 1.0m")
                        print("   🕐 Idle Time Accumulation: \(totalIdleTimeBelowThreshold)s")
                    }
                }
                
                totalDisplacementSinceLastUpdate = totalDisplacement
            } else {
                if LocationModule.DEBUG_MODE {
                    print("🆕 First Location Update - No Previous Location")
                }
            }
            
            if LocationModule.DEBUG_MODE {
                print("✅ PROCEEDING WITH LOCATION UPDATE")
            }
            
            sendLocationToReactNative(location: location, currentTime: currentTime, isFirstLocation: false)
            updatePerformanceMetrics(location: location, processingTime: Date().timeIntervalSince1970 - currentTime)
            
            lastUpdateLocation = location
            lastUpdateTime = currentTime
            lastKnownLocation = location
            
        } catch {
            if LocationModule.DEBUG_MODE {
                print("❌ Error processing location update: \(error)")
            }
            handleLocationError(error)
        }
    }
    
    private func handleFirstLocation(_ location: CLLocation, currentTime: TimeInterval) {
        if LocationModule.DEBUG_MODE {
            print("🆕 [\(LocationModule.TAG)] handleFirstLocation called with: \(location) at \(currentTime)")
        }
        
        preferredProvider = location.timestamp.description
        lastLocationTime = currentTime
        lastKnownLocation = location
        
        lastUpdateLocation = location
        lastUpdateTime = currentTime
        
        sendLocationToReactNative(location: location, currentTime: currentTime, isFirstLocation: true)
        
        isServiceReady = true
        
        if LocationModule.DEBUG_MODE {
            print("✅ [\(LocationModule.TAG)] First location handled successfully")
        }
    }
    
    // MARK: - Idle Time Tracking
    private func updateIdleTimeTracking(currentTime: TimeInterval, isBelowThreshold: Bool) {
        if LocationModule.DEBUG_MODE {
            print("⏱️ [\(LocationModule.TAG)] updateIdleTimeTracking called with currentTime: \(currentTime), isBelowThreshold: \(isBelowThreshold)")
        }
        
        if isBelowThreshold {
            if !isCurrentlyIdle {
                idleStartTime = currentTime
                isCurrentlyIdle = true
                if LocationModule.DEBUG_MODE {
                    print("🕐 Starting idle period at: \(currentTime)")
                }
            }
            
            let ongoingIdleDuration = currentTime - idleStartTime
            totalIdleTimeBelowThreshold += ongoingIdleDuration
            
            if LocationModule.DEBUG_MODE {
                print("⏱️ Idle Time Tracking Update:")
                print("   🕐 Ongoing Idle Duration: \(ongoingIdleDuration)s")
                print("   📊 Total Idle Time Below Threshold: \(totalIdleTimeBelowThreshold)s")
                print("   🎯 Is Currently Idle: \(isCurrentlyIdle)")
            }
            
            if isOutsideVisitTracking {
                outsideVist_Total_IdleTime += ongoingIdleDuration
                if LocationModule.DEBUG_MODE {
                    print("🏠 Outside Visit Idle Time Updated:")
                    print("   🕐 Outside Visit Total Idle: \(outsideVist_Total_IdleTime)s")
                }
            }
            
            idleStartTime = currentTime
        } else {
            if isCurrentlyIdle {
                let idleEndDuration = currentTime - idleStartTime
                totalIdleTimeBelowThreshold += idleEndDuration
                
                if LocationModule.DEBUG_MODE {
                    print("✅ Ending idle period:")
                    print("   🕐 Final Idle Duration: \(idleEndDuration)s")
                    print("   📊 Total Idle Time Below Threshold: \(totalIdleTimeBelowThreshold)s")
                }
                
                if isOutsideVisitTracking {
                    outsideVist_Total_IdleTime += idleEndDuration
                    if LocationModule.DEBUG_MODE {
                        print("🏠 Outside Visit Idle Time Final Update:")
                        print("   🕐 Outside Visit Total Idle: \(outsideVist_Total_IdleTime)s")
                    }
                }
                
                isCurrentlyIdle = false
                idleStartTime = 0
            }
        }
    }
    
    private func sendIdleTimeOnlyUpdate(location: CLLocation, currentTime: TimeInterval) {
        if LocationModule.DEBUG_MODE {
            print("🚀 [\(LocationModule.TAG)] sendIdleTimeOnlyUpdate called with location: \(location) at \(currentTime)")
            print("📋 REASON: Location update skipped due to stationary/insufficient movement")
        }
        
        let idleTimeData: [String: Any] = [
            "latitude": -999.0,
            "longitude": -999.0,
            "accuracy": -1.0,
            "speed": -1.0,
            "timestamp": currentTime * 1000,
            "provider": "idle_tracking",
            "type": "idle_time_only",
            "skipReason": "stationary_insufficient_movement",
            "idleTime": totalIdleTimeBelowThreshold * 1000,
            "outsideVisitIdleTime": outsideVist_Total_IdleTime * 1000,
            "isOutsideVisitTracking": isOutsideVisitTracking,
            "lastLocationUpdateTime": lastLocationUpdateTime * 1000,
            "idleThreshold": Constants.IDLE_TIMER_INTERVAL * 1000,
            "totalDisplacementSinceLastUpdate": totalDisplacementSinceLastUpdate,
            "minDistanceForUpdate": Constants.MIN_DISTANCE_FOR_UPDATE
        ]
        
        if LocationModule.DEBUG_MODE {
            print("📤 Sending Idle Time Data to React Native (Location Skipped):")
            print("   📍 Location: (\(idleTimeData["latitude"] as! Double), \(idleTimeData["longitude"] as! Double))")
            print("   🎯 Accuracy: \(idleTimeData["accuracy"] as! Double)")
            print("   🏃 Speed: \(idleTimeData["speed"] as! Double)")
            print("   ⏰ Timestamp: \(idleTimeData["timestamp"] as! Double)")
            print("   🔧 Provider: \(idleTimeData["provider"] as! String)")
            print("   📋 Type: \(idleTimeData["type"] as! String)")
            print("   🚫 Skip Reason: \(idleTimeData["skipReason"] as! String)")
            print("   🕐 Total Idle Time: \(idleTimeData["idleTime"] as! Double)ms")
            print("   🏠 Outside Visit Idle Time: \(idleTimeData["outsideVisitIdleTime"] as! Double)ms")
            print("   🎯 Is Outside Visit Tracking: \(idleTimeData["isOutsideVisitTracking"] as! Bool)")
            print("   📅 Last Location Update: \(idleTimeData["lastLocationUpdateTime"] as! Double)")
            print("   ⚡ Idle Threshold: \(idleTimeData["idleThreshold"] as! Double)ms")
            print("   📏 Total Displacement: \(idleTimeData["totalDisplacementSinceLastUpdate"] as! Double)m")
            print("   🎯 Min Distance Threshold: \(idleTimeData["minDistanceForUpdate"] as! Double)m")
        }
        
        sendLocationToReactNative(locationData: idleTimeData, isFirstLocation: false)
        
        if LocationModule.DEBUG_MODE {
            print("✅ Idle time data sent to React Native successfully! (Location update skipped)")
        }
    }
    
    // MARK: - React Native Communication
    private func sendLocationToReactNative(location: CLLocation, currentTime: TimeInterval, isFirstLocation: Bool) {
        if LocationModule.DEBUG_MODE {
            print("📡 [\(LocationModule.TAG)] sendLocationToReactNative called with location: \(location) at \(currentTime), isFirstLocation: \(isFirstLocation)")
            print("📍 Sending LOCATION DATA to React Native")
            print("   📍 Coordinates: (\(location.coordinate.latitude), \(location.coordinate.longitude))")
            print("   🎯 Accuracy: \(location.horizontalAccuracy)m")
            print("   🏃 Speed: \(location.speed)m/s")
            print("   ⏰ Timestamp: \(location.timestamp.timeIntervalSince1970 * 1000)")
            print("   🔧 Provider: \(location.timestamp.description)")
        }
        
        let locationData: [String: Any] = [
            "latitude": location.coordinate.latitude,
            "longitude": location.coordinate.longitude,
            "accuracy": location.horizontalAccuracy,
            "altitude": location.altitude,
            "speed": location.speed,
            "speedAccuracy": 0.0,
            "heading": 0.0,
            "timestamp": location.timestamp.timeIntervalSince1970 * 1000,
            "displacement": totalDisplacementSinceLastUpdate,
            "updateReason": "location_update",
            "isSameLocation": false,
            "isBelowDistanceThreshold": false,
            "totalIdleTimeBelowThreshold": totalIdleTimeBelowThreshold * 1000,
            "isCurrentlyIdle": isCurrentlyIdle,
            "outsideVist_Total_IdleTime": outsideVist_Total_IdleTime * 1000,
            "isOutsideVisitTracking": isOutsideVisitTracking
        ]
        
        sendEvent(withName: "onLocationUpdate", body: locationData)
        
        if LocationModule.DEBUG_MODE {
            print("✅ Location data sent to React Native via event system")
        }
    }
    
    private func sendLocationToReactNative(locationData: [String: Any], isFirstLocation: Bool) {
        if LocationModule.DEBUG_MODE {
            print("📡 [\(LocationModule.TAG)] sendLocationToReactNative called with locationData, isFirstLocation: \(isFirstLocation)")
            
            let isIdleTimeData = locationData["type"] as? String == "idle_time_only"
            
            if isIdleTimeData {
                print("🕐 Sending IDLE TIME DATA to React Native")
                print("   📊 Data Type: Idle Time Only Update")
                print("   🕐 Total Idle Time: \(locationData["idleTime"] as! Double)ms")
                print("   🏠 Outside Visit Idle: \(locationData["outsideVisitIdleTime"] as! Double)ms")
            } else {
                print("📍 Sending LOCATION DATA to React Native")
                print("   📍 Coordinates: (\(locationData["latitude"] as! Double), \(locationData["longitude"] as! Double))")
                print("   🎯 Accuracy: \(locationData["accuracy"] as! Double)m")
                print("   🏃 Speed: \(locationData["speed"] as! Double)m/s")
                print("   ⏰ Timestamp: \(locationData["timestamp"] as! Double)")
                print("   🔧 Provider: \(locationData["provider"] as! String)")
            }
        }
        
        sendEvent(withName: "onLocationUpdate", body: locationData)
        
        if LocationModule.DEBUG_MODE {
            print("✅ Data sent to React Native via event system")
        }
    }
    
    // MARK: - Error Handling
    private func handleLocationError(_ error: Error) {
        if LocationModule.DEBUG_MODE {
            print("❌ [\(LocationModule.TAG)] handleLocationError called: \(error)")
        }
        
        lastErrorTime = Date().timeIntervalSince1970
        consecutiveErrors += 1
        errorCount += 1
        
        let errorData: [String: Any] = [
            "error": error.localizedDescription,
            "code": -1,
            "errorCount": errorCount
        ]
        
        sendEvent(withName: "onLocationError", body: errorData)
    }
    
    // MARK: - Performance Monitoring
    private func updatePerformanceMetrics(location: CLLocation, processingTime: TimeInterval) {
        if LocationModule.DEBUG_MODE {
            print("📊 [\(LocationModule.TAG)] updatePerformanceMetrics called with: \(location), processingTime: \(processingTime)")
        }
        
        totalLocationsProcessed += 1
        averageLocationAccuracy = (averageLocationAccuracy * Double(totalLocationsProcessed - 1) + location.horizontalAccuracy) / Double(totalLocationsProcessed)
        averageUpdateInterval = (averageUpdateInterval * Double(totalLocationsProcessed - 1) + processingTime) / Double(totalLocationsProcessed)
    }
    
    // MARK: - Visit Status Methods
    func updateVisitStatus(visitId: String?) {
        if visitId != nil && visitId != lastKnownVisitId {
            lastKnownVisitId = visitId
            isOutsideVisitTracking = false
            outsideVisitStartTime = Date().timeIntervalSince1970
        } else if visitId == nil && !isOutsideVisitTracking {
            isOutsideVisitTracking = true
            outsideVisitStartTime = Date().timeIntervalSince1970
        }
    }
    
    func stopOutsideVisitTracking() {
        isOutsideVisitTracking = false
    }
    
    // MARK: - Cleanup
    override func invalidate() {
        if LocationModule.DEBUG_MODE {
            print("🧹 [\(LocationModule.TAG)] invalidate called")
        }
        
        locationManager?.stopUpdatingLocation()
        locationManager = nil
        isTracking = false
        isServiceReady = false
        
        super.invalidate()
    }

    // MARK: - Persistence methods for outside visit data
    private func loadOutsideVisitData() {
        let userDefaults = UserDefaults.standard
        outsideVist_Total_IdleTime = userDefaults.double(forKey: "outsideVist_Total_IdleTime")
        lastOutsideVisitUpdate = userDefaults.double(forKey: "lastOutsideVisitUpdate")
        print("LocationModule: 📂 [IdleTime] Loaded outside visit idle time: \(outsideVist_Total_IdleTime)ms (\(outsideVist_Total_IdleTime/60000) minutes)")
    }

    private func saveOutsideVisitData() {
        let userDefaults = UserDefaults.standard
        userDefaults.set(outsideVist_Total_IdleTime, forKey: "outsideVist_Total_IdleTime")
        userDefaults.set(Date().timeIntervalSince1970 * 1000, forKey: "lastOutsideVisitUpdate")
        userDefaults.synchronize()
        print("LocationModule: 💾 [IdleTime] Saved outside visit idle time: \(outsideVist_Total_IdleTime)ms (\(outsideVist_Total_IdleTime/60000) minutes)")
    }

    // MARK: - Idle time outside visit tracking
    private func updateOutsideVisitIdleTime() {
        // Calculate current idle time
        var currentIdleTime = totalIdleTimeBelowThreshold
        if isCurrentlyIdle {
            let ongoingIdleDuration = Date().timeIntervalSince1970 * 1000 - idleStartTime
            currentIdleTime += ongoingIdleDuration
        }
        // Update outside visit idle time
        outsideVist_Total_IdleTime = currentIdleTime
        isOutsideVisitTracking = true
        lastOutsideVisitUpdate = Date().timeIntervalSince1970 * 1000
        // Save to persistent storage
        saveOutsideVisitData()
        print("LocationModule: 🏠 [IdleTime] Updated outside visit idle time: \(outsideVist_Total_IdleTime)ms (\(outsideVist_Total_IdleTime/60000) minutes)")
    }
}

// MARK: - CLLocationManagerDelegate
extension LocationModule: CLLocationManagerDelegate {
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else {
            if LocationModule.DEBUG_MODE {
                print("⚠️ [\(LocationModule.TAG)] No valid location in update")
            }
            return
        }
        
        if LocationModule.DEBUG_MODE {
            print("📍 [\(LocationModule.TAG)] Location update received: \(location)")
        }
        
        let currentTime = Date().timeIntervalSince1970
        
        if lastKnownLocation == nil {
            handleFirstLocation(location, currentTime: currentTime)
        } else {
            processLocationUpdate(location, currentTime: currentTime)
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        if LocationModule.DEBUG_MODE {
            print("❌ [\(LocationModule.TAG)] Location manager failed with error: \(error)")
        }
        handleLocationError(error)
    }
    
    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        print("LocationModule: 🔐 [Delegate] didChangeAuthorization: \(status.rawValue)")
        if let resolve = pendingPermissionResolve {
            switch status {
            case .authorizedAlways:
                configureAdaptiveAccuracy()
                resolve(["status": "authorizedAlways", "message": "Location permission granted for always"])
                pendingPermissionResolve = nil
                pendingPermissionReject = nil
            case .authorizedWhenInUse:
                configureAdaptiveAccuracy()
                resolve(["status": "authorizedWhenInUse", "message": "Location permission granted for when in use"])
                pendingPermissionResolve = nil
                pendingPermissionReject = nil
            case .denied:
                pendingPermissionReject?("PERMISSION_DENIED", "Location permission denied", nil)
                pendingPermissionResolve = nil
                pendingPermissionReject = nil
            case .restricted:
                pendingPermissionReject?("PERMISSION_RESTRICTED", "Location permission restricted", nil)
                pendingPermissionResolve = nil
                pendingPermissionReject = nil
            case .notDetermined:
                // Still waiting for user input, do nothing
                break
            @unknown default:
                pendingPermissionReject?("UNKNOWN_STATUS", "Unknown permission status", nil)
                pendingPermissionResolve = nil
                pendingPermissionReject = nil
            }
        }
        // Also send event to JS
        let permissionData: [String: Any] = [
            "status": status.rawValue,
            "message": "Authorization status changed"
        ]
        sendEvent(withName: "onLocationPermissionChanged", body: permissionData)
    }
    
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        if LocationModule.DEBUG_MODE {
            print("🔐 [\(LocationModule.TAG)] Authorization status changed (iOS 14+): \(status.rawValue)")
        }
        
        let permissionData: [String: Any] = [
            "status": status.rawValue,
            "accuracy": status == .authorizedAlways ? "full" : "reduced",
            "accuracyStatus": status == .authorizedAlways ? 1 : 2,
            "message": "Authorization status changed"
        ]
        
        sendEvent(withName: "onLocationPermissionChanged", body: permissionData)
    }
} 
