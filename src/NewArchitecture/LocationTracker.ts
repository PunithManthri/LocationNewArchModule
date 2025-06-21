import {
  Alert,
  NativeEventEmitter,
  NativeModules,
  Platform,
  AppState,
  Linking,
} from "react-native";
import NetInfo from "@react-native-community/netinfo";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { sendLocationToServer } from "./LocationSync";
import LocationModule from "./LocationModule";

const { LocationModule: NativeLocationModule } = NativeModules;

// Platform-specific constants
const IS_IOS = Platform.OS === 'ios';
const IS_ANDROID = Platform.OS === 'android';

// Constants
const BACKGROUND_LOCATIONS_KEY = "background_locations";
const LAST_LOCATION_KEY = "last_location";
const DISTANCE_TO_TRACK = 50; // 50 meters minimum distance to track

// Helper function to calculate distance between two points using Haversine formula
function haversine(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371e3; // Earth's radius in meters
  const φ1 = (lat1 * Math.PI) / 180;
  const φ2 = (lat2 * Math.PI) / 180;
  const Δφ = ((lat2 - lat1) * Math.PI) / 180;
  const Δλ = ((lon2 - lon1) * Math.PI) / 180;

  const a =
    Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
    Math.cos(φ1) * Math.cos(φ2) * Math.sin(Δλ / 2) * Math.sin(Δλ / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

  return R * c;
}

// Interfaces
export interface LocationUpdate {
  latitude: number;
  longitude: number;
  timestamp: string;
  accuracy?: number;
  speed?: number;
  distance?: number;
  time?: number;
  totalIdleTimeBelowThreshold?: number;
  isCurrentlyIdle?: boolean;
  isBelowDistanceThreshold?: boolean;
  updateReason?: string;
  displacement?: number;
  isSameLocation?: boolean;
  outsideVist_Total_IdleTime?: number;
  isOutsideVisitTracking?: boolean;
}

interface NativeLocation {
  latitude: number;
  longitude: number;
  timestamp?: string | number;
  accuracy?: number;
  speed?: number;
  distance?: number;
  time?: number;
  totalIdleTimeBelowThreshold?: number;
  isCurrentlyIdle?: boolean;
  isBelowDistanceThreshold?: boolean;
  updateReason?: string;
  displacement?: number;
  isSameLocation?: boolean;
  outsideVist_Total_IdleTime?: number;
  isOutsideVisitTracking?: boolean;
  type?: string;
}

// Helper function to convert location objects
function convertToLocationUpdate(location: NativeLocation): LocationUpdate {
  let timestamp: string;

  if (typeof location.timestamp === "string") {
    timestamp = location.timestamp;
  } else if (typeof location.timestamp === "number") {
    timestamp = new Date(location.timestamp).toISOString();
  } else {
    timestamp = new Date().toISOString();
  }

  // Check if this is idle time data
  const isIdleTimeData = location.latitude === -999 || 
                        location.longitude === -999 || 
                        location.type === 'idle_time_only';

  return {
    latitude: location.latitude,
    longitude: location.longitude,
    timestamp,
    accuracy: location.accuracy,
    speed: location.speed,
    distance: location.distance,
    time: location.time,
    totalIdleTimeBelowThreshold: location.totalIdleTimeBelowThreshold,
    isCurrentlyIdle: location.isCurrentlyIdle,
    isBelowDistanceThreshold: location.isBelowDistanceThreshold,
    updateReason: isIdleTimeData ? 'idle_time_only' : location.updateReason,
    displacement: location.displacement,
    isSameLocation: location.isSameLocation,
    outsideVist_Total_IdleTime: location.outsideVist_Total_IdleTime,
    isOutsideVisitTracking: location.isOutsideVisitTracking,
  };
}

// Helper functions for last location
async function getLastLocation(): Promise<{ latitude: number; longitude: number } | null> {
  try {
    const lastLocation = await AsyncStorage.getItem(LAST_LOCATION_KEY);
    return lastLocation ? JSON.parse(lastLocation) : null;
  } catch (error) {
    console.error("[Tracker] Error getting last location:", error);
    return null;
  }
}

async function saveLastLocation(location: { latitude: number; longitude: number }): Promise<void> {
  try {
    await AsyncStorage.setItem(LAST_LOCATION_KEY, JSON.stringify(location));
  } catch (error) {
    console.error("[Tracker] Error saving last location:", error);
  }
}

class LocationTracker {
  private eventEmitter: NativeEventEmitter;
  private locationSubscription: any | null = null;
  private onLocationUpdate: ((location: LocationUpdate) => void) | null = null;
  private netInfoUnsubscribe: (() => void) | null = null;
  private appStateSubscription: any | null = null;
  private isTracking: boolean = false;
  private lastNetworkState: boolean = true;
  private errorSubscription: any | null = null;
  private permissionSubscription: any | null = null;
  private activeListeners: Set<string> = new Set();
  private lastKnownLocation: LocationUpdate | null = null;
  private syncTimeout: NodeJS.Timeout | null = null;
  
  // Idle tracking properties
  private staticLocationStartTime: number | null = null;
  private readonly IDLE_THRESHOLD = 15 * 60 * 1000; // 15 minutes in milliseconds
  private lastIdleReportTime: number | null = null;
  private readonly IDLE_REPORT_INTERVAL = 15 * 60 * 1000; // Report idle status every 15 minutes

  // Event names
  private static readonly EVENT_NAMES = {
    LOCATION_UPDATE: "onLocationUpdate",
    LOCATION_ERROR: "onLocationError",
    PERMISSION_CHANGED: "onLocationPermissionChanged",
  } as const;

  constructor() {
    console.log("[DEBUG] 🔧 Initializing LocationTracker for", Platform.OS);
    
    if (!NativeLocationModule) {
      console.error("[DEBUG] ❌ LocationModule is not available!");
      throw new Error("LocationModule is not available");
    }

    this.eventEmitter = new NativeEventEmitter(NativeLocationModule);
    console.log("[DEBUG] ✅ Event emitter created for", Platform.OS);

    this.setupNetworkListener();
    this.setupAppStateListener();
    this.setupEventEmitterListeners();
    
    console.log("[DEBUG] ✅ LocationTracker initialization complete for", Platform.OS);
  }

  private setupEventEmitterListeners(): void {
    console.log("[DEBUG] 📡 Setting up event emitter listeners for", Platform.OS);

    // Location update listener
    this.locationSubscription = this.eventEmitter.addListener(
      LocationTracker.EVENT_NAMES.LOCATION_UPDATE,
      (locationData: NativeLocation) => {
        console.log("[DEBUG] 📍 LOCATION UPDATE RECEIVED from native (", Platform.OS, "):", locationData);
        
        try {
          const locationUpdate = convertToLocationUpdate(locationData);
          this.lastKnownLocation = locationUpdate;
          
          console.log("[DEBUG] 📍 Converted location update:", {
            latitude: locationUpdate.latitude,
            longitude: locationUpdate.longitude,
            timestamp: locationUpdate.timestamp,
            updateReason: locationUpdate.updateReason,
            platform: Platform.OS,
          });
          
          // Process the location update
          this.processLocationUpdate(locationUpdate);
          
          // Only call the callback for actual location updates, not idle time data
          const isIdleTimeData = locationUpdate.latitude === -999 || 
                                locationUpdate.longitude === -999 || 
                                locationUpdate.updateReason === 'idle_time_only';
          
          if (!isIdleTimeData && this.onLocationUpdate) {
            console.log("[DEBUG] 📍 Calling user callback with location update");
            this.onLocationUpdate(locationUpdate);
          } else if (isIdleTimeData) {
            console.log("[DEBUG] 📍 Skipping user callback for idle time data");
          } else {
            console.log("[DEBUG] ⚠️ No user callback set for location update");
          }
        } catch (error) {
          console.error("[DEBUG] ❌ Error processing location update:", error);
        }
      }
    );

    // Error listener
    this.errorSubscription = this.eventEmitter.addListener(
      LocationTracker.EVENT_NAMES.LOCATION_ERROR,
      (errorData: any) => {
        console.error("[DEBUG] ❌ LOCATION ERROR RECEIVED from native (", Platform.OS, "):", errorData);
        Alert.alert("Location Error", `Location tracking error: ${errorData?.error || "Unknown error"}`);
      }
    );

    // Permission change listener
    this.permissionSubscription = this.eventEmitter.addListener(
      LocationTracker.EVENT_NAMES.PERMISSION_CHANGED,
      (permissionData: any) => {
        console.log("[DEBUG] 🔐 PERMISSION CHANGE RECEIVED from native (", Platform.OS, "):", permissionData);
      }
    );

    console.log("[DEBUG] ✅ Event emitter listeners set up successfully for", Platform.OS);
  }

  private async processLocationUpdate(location: LocationUpdate): Promise<void> {
    try {
      console.log("[Tracker] Processing location update (", Platform.OS, "):", {
        latitude: location.latitude,
        longitude: location.longitude,
        timestamp: location.timestamp,
        isBelowDistanceThreshold: location.isBelowDistanceThreshold,
        updateReason: location.updateReason,
      });

      // Check if this is idle time data (indicated by -999 coordinates or type field)
      const isIdleTimeData = location.latitude === -999 || 
                           location.longitude === -999 || 
                           location.updateReason === 'idle_time_only';

      if (isIdleTimeData) {
        console.log("[Tracker] Processing idle time data - handling separately");
        await this.handleIdleTimeData(location);
        return;
      }

      // Always store background location for actual location updates
      await this.storeBackgroundLocation(
        location.latitude,
        location.longitude,
        new Date(location.timestamp)
      );

      // Handle distance-based tracking only for actual location updates
      await this.saveDistanceIfMoved_handler(
        location.latitude,
        location.longitude,
        location.isBelowDistanceThreshold || false,
        location.timestamp
      );

    } catch (error) {
      console.error("[Tracker] Error processing location update:", error);
    }
  }

  private async handleIdleTimeData(location: LocationUpdate): Promise<void> {
    try {
      console.log("[Idle] Handling idle time data (", Platform.OS, "):", {
        totalIdleTime: location.totalIdleTimeBelowThreshold,
        isCurrentlyIdle: location.isCurrentlyIdle,
        outsideVisitIdleTime: location.outsideVist_Total_IdleTime,
      });

      // For idle time data, we store locally only, don't sync to server in foreground
      // The idle time information is for internal tracking only
      // We only send idle status when the user has been idle for the threshold period AND app is in background
      
      if (location.totalIdleTimeBelowThreshold && location.totalIdleTimeBelowThreshold >= this.IDLE_THRESHOLD) {
        console.log("[Idle] User has been idle for threshold period, storing locally only");
        
        // Store idle status locally only, don't sync to server in foreground
        const lastLocation = await getLastLocation();
        if (lastLocation) {
          console.log("[Idle] Idle status stored locally (no foreground sync)");
        }
      } else {
        console.log("[Idle] Idle time below threshold, storing locally only");
      }
    } catch (error) {
      console.error("[Idle] Error handling idle time data:", error);
    }
  }

  private setupNetworkListener() {
    this.netInfoUnsubscribe = NetInfo.addEventListener((state) => {
      const isConnected = (state.isConnected && state.isInternetReachable) ?? false;
      
      if (this.lastNetworkState !== isConnected) {
        console.log("[Tracker] Network state changed (", Platform.OS, "):", {
          from: this.lastNetworkState,
          to: isConnected,
        });
        
        this.lastNetworkState = isConnected;
        
        if (isConnected) {
          // Sync background locations when network becomes available
          this.syncBackgroundLocations();
        }
      }
    });
  }

  private setupAppStateListener() {
    this.appStateSubscription = AppState.addEventListener("change", (nextAppState) => {
      console.log("[Tracker] App state changed (", Platform.OS, "):", nextAppState);
      
      if (nextAppState === "active") {
        // App came to foreground, sync any pending locations
        console.log("[Tracker] App came to foreground - syncing pending locations");
        this.syncBackgroundLocations();
      } else if (nextAppState === "background") {
        console.log("[Tracker] App went to background - continuing location tracking");
      } else if (nextAppState === "inactive") {
        console.log("[Tracker] App became inactive");
      }
    });
  }

  private showNoInternetAlert = () => {
    Alert.alert(
      "No Internet Connection",
      "Please check your internet connection and try again.",
      [{ text: "OK" }]
    );
  };

  private async checkInternetConnection(): Promise<boolean> {
    try {
      const state = await NetInfo.fetch();
      return (state.isConnected && state.isInternetReachable) ?? false;
    } catch (error) {
      console.error("[Tracker] Error checking internet connection:", error);
      return false;
    }
  }

  async startTracking(callback: (location: LocationUpdate) => void): Promise<boolean> {
    try {
      console.log("[DEBUG] 🚀 Starting location tracking on", Platform.OS);
      console.log("[DEBUG] 📱 NativeLocationModule available:", !!NativeLocationModule);
      console.log("[DEBUG] 📱 NativeLocationModule methods:", Object.keys(NativeLocationModule));

      if (this.isTracking) {
        console.log("[DEBUG] ⚠️ Already tracking, skipping start");
        return true;
      }

      // Set the callback
      this.onLocationUpdate = callback;
      console.log("[DEBUG] ✅ Callback set");

      // Request permissions first
      console.log("[DEBUG] 🔐 Requesting permissions...");
      const hasPermissions = await this.requestLocationPermissions();
      console.log("[DEBUG] 🔐 Permission result:", hasPermissions);
      
      if (!hasPermissions) {
        console.error("[DEBUG] ❌ Location permissions not granted");
        return false;
      }

      // Start native tracking
      console.log("[DEBUG] 🚀 Calling native startLocationTracking...");
      const result = await NativeLocationModule.startLocationTracking();
      console.log("[DEBUG] 🚀 Native tracking start result:", result);

      if (result?.status === "success") {
        this.isTracking = true;
        console.log("[DEBUG] ✅ Location tracking started successfully on", Platform.OS);
        return true;
      } else {
        console.error("[DEBUG] ❌ Failed to start location tracking:", result);
        return false;
      }
    } catch (error) {
      console.error("[DEBUG] ❌ Error starting location tracking:", error);
      return false;
    }
  }

  async stopTracking(): Promise<void> {
    try {
      console.log("[DEBUG] 🛑 Stopping location tracking on", Platform.OS);

      if (!this.isTracking) {
        console.log("[DEBUG] ⚠️ Not tracking, skipping stop");
        return;
      }

      // Stop native tracking
      await NativeLocationModule.stopLocationTracking();
      
      this.isTracking = false;
      this.onLocationUpdate = null;
      
      console.log("[DEBUG] ✅ Location tracking stopped successfully on", Platform.OS);
    } catch (error) {
      console.error("[DEBUG] ❌ Error stopping location tracking:", error);
    }
  }

  async requestLocationPermissions(): Promise<boolean> {
    try {
      console.log("[DEBUG] 🔐 Requesting location permissions on", Platform.OS);

      const result = await NativeLocationModule.requestLocationPermissions();
      console.log("[DEBUG] 🔐 Permission request result:", result);

      if (result?.status === "granted") {
        console.log("[DEBUG] ✅ Location permissions granted on", Platform.OS);
        return true;
      } else {
        console.error("[DEBUG] ❌ Location permissions denied on", Platform.OS, ":", result);
        return false;
      }
    } catch (error) {
      console.error("[DEBUG] ❌ Error requesting location permissions:", error);
      return false;
    }
  }

  async storeBackgroundLocation(
    latitude: number,
    longitude: number,
    time: Date
  ): Promise<void> {
    try {
      // Check if this is idle time data (indicated by -999 coordinates)
      if (latitude === -999 || longitude === -999) {
        console.log("[Background] Skipping idle time data in storeBackgroundLocation");
        return;
      }

      const storedLocations = await AsyncStorage.getItem(BACKGROUND_LOCATIONS_KEY);
      let locations = storedLocations ? JSON.parse(storedLocations) : [];

      locations.push({
        latitude,
        longitude,
        timestamp: time.toISOString(),
      });

      await AsyncStorage.setItem(BACKGROUND_LOCATIONS_KEY, JSON.stringify(locations));
      
      console.log("[Background] Stored location locally (will sync when app goes to background):", {
        latitude,
        longitude,
        timestamp: time.toISOString(),
        totalStored: locations.length,
        platform: Platform.OS,
      });
    } catch (error) {
      console.error("[Background] Failed to store location:", error);
      throw error;
    }
  }

  async syncBackgroundLocations(): Promise<void> {
    try {
      console.log("[Background] Starting background location sync (background-only mode) on", Platform.OS);
      const isOnline = await this.checkInternetConnection();
      if (!isOnline) {
        console.log("[Background] No internet connection, skipping sync");
        return;
      }

      const storedLocations = await AsyncStorage.getItem(BACKGROUND_LOCATIONS_KEY);
      let locations = storedLocations ? JSON.parse(storedLocations) : [];

      if (locations.length === 0) {
        console.log("[Background] No stored locations to sync");
        return;
      }

      console.log("[Background] Starting sync of", locations.length, "locations (background-only mode) on", Platform.OS);

      let successCount = 0;
      let failureCount = 0;
      let processedCount = 0;

      // Process locations one by one and remove them immediately after successful sync
      while (locations.length > 0 && processedCount < locations.length) {
        const location = locations[0]; // Always take the first location
        
        try {
          await sendLocationToServer(
            location.latitude,
            location.longitude,
            location.timestamp
          );
          successCount++;
          console.log("[Background] Successfully synced location to server:", location);
          
          // Remove this location from the array after successful sync
          locations.shift(); // Remove the first element
          
          // Update AsyncStorage with remaining locations
          await AsyncStorage.setItem(BACKGROUND_LOCATIONS_KEY, JSON.stringify(locations));
          console.log("[Background] Removed synced location from storage. Remaining:", locations.length);
          
        } catch (error) {
          failureCount++;
          console.error("[Background] Error syncing location to server:", error);
          
          // Move failed location to the end of the array for retry later
          const failedLocation = locations.shift();
          if (failedLocation) {
            locations.push(failedLocation);
            await AsyncStorage.setItem(BACKGROUND_LOCATIONS_KEY, JSON.stringify(locations));
            console.log("[Background] Moved failed location to end for retry. Remaining:", locations.length);
          }
        }
        
        processedCount++;
      }

      console.log("[Background] Background sync completed - Success:", successCount, "Failed:", failureCount, "Remaining in storage:", locations.length, "on", Platform.OS);
    } catch (error) {
      console.error("[Background] Failed to sync locations:", error);
      throw error;
    }
  }

  async saveDistanceIfMoved_handler(
    lat: number,
    lon: number,
    isBelowDistanceThreshold: boolean,
    time: Date | string | number
  ): Promise<void> {
    try {
      // Check if this is idle time data (indicated by -999 coordinates)
      if (lat === -999 || lon === -999) {
        console.log("[Tracking] Skipping idle time data in saveDistanceIfMoved_handler");
        return;
      }

      let lastLocation = await getLastLocation();

      // Convert time to a valid Date object
      let timestamp: Date;
      try {
        if (time instanceof Date) {
          timestamp = time;
        } else if (typeof time === "string") {
          timestamp = new Date(time);
        } else if (typeof time === "number") {
          timestamp = new Date(time);
        } else {
          timestamp = new Date();
        }

        if (isNaN(timestamp.getTime())) {
          console.warn("[Tracking] Invalid timestamp, using current time:", time);
          timestamp = new Date();
        }
      } catch (error) {
        console.warn("[Tracking] Error parsing timestamp, using current time:", error);
        timestamp = new Date();
      }

      console.log("[Tracking] Processing location update (", Platform.OS, "):", {
        current: { lat, lon, time: timestamp.toISOString() },
        last: lastLocation,
        originalTime: time,
        isBelowDistanceThreshold,
      });

      if (!lastLocation) {
        // First location update - store locally only, don't sync to server
        console.log("[Tracking] First location update - storing locally only");
        await saveLastLocation({ latitude: lat, longitude: lon });
        return;
      }

      // Calculate distance from last location
      const distance = haversine(
        lastLocation.latitude,
        lastLocation.longitude,
        lat,
        lon
      );

      console.log("[Tracking] Distance calculation:", {
        distance: distance.toFixed(2) + "m",
        threshold: DISTANCE_TO_TRACK + "m",
        shouldUpdate: distance >= DISTANCE_TO_TRACK,
      });

      // Check if displacement is greater than threshold
      if (distance >= DISTANCE_TO_TRACK) {
        // User has moved beyond threshold - store locally only
        console.log("[Tracking] Displacement > threshold - storing locally only");
        
        // Reset idle tracking since user has moved
        this.staticLocationStartTime = null;
        this.lastIdleReportTime = null;
        console.log("[Tracking] Reset idle tracking - user has moved");
        
        await saveLastLocation({ latitude: lat, longitude: lon });
        console.log("[Tracking] Location stored locally (no foreground sync)");
      } else {
        // User is within threshold - handle idle time tracking
        console.log("[Tracking] Displacement < threshold - handling idle time tracking");
        
        // Handle idle time tracking
        const currentTime = Date.now();

        // Initialize static location start time if not set
        if (this.staticLocationStartTime === null) {
          this.staticLocationStartTime = currentTime;
          console.log(
            "[Idle] Started tracking static location at:",
            new Date(currentTime).toISOString()
          );
        }

        // Check if we've been idle for the threshold
        const idleDuration = currentTime - this.staticLocationStartTime;

        if (idleDuration >= this.IDLE_THRESHOLD) {
          // Check if we should report idle status
          if (
            this.lastIdleReportTime === null ||
            currentTime - this.lastIdleReportTime >= this.IDLE_REPORT_INTERVAL
          ) {
            console.log(
              "[Idle] User has been idle for",
              Math.floor(idleDuration / 60000),
              "minutes"
            );

            // Store idle status locally only, don't sync to server in foreground
            console.log("[Idle] Idle status stored locally (no foreground sync)");
            this.lastIdleReportTime = currentTime;
          }
        }
      }
    } catch (error) {
      console.error("[Tracking] Error in saveDistanceIfMoved_handler:", error);
      throw error;
    }
  }

  // Debug method for checking stored locations
  async debugStoredLocations(): Promise<any[]> {
    try {
      const storedLocations = await AsyncStorage.getItem(BACKGROUND_LOCATIONS_KEY);
      const locations = storedLocations ? JSON.parse(storedLocations) : [];
      
      console.log("[Debug] Stored locations in AsyncStorage (", Platform.OS, "):", {
        count: locations.length,
        locations: locations.slice(0, 5), // Show first 5 locations
        totalStored: locations.length,
      });
      
      return locations;
    } catch (error) {
      console.error("[Debug] Error checking stored locations:", error);
      return [];
    }
  }

  // Cleanup method
  async cleanup(): Promise<void> {
    try {
      console.log("[Tracker] Cleaning up LocationTracker on", Platform.OS);

      // Stop tracking
      await this.stopTracking();

      // Remove event listeners
      if (this.locationSubscription) {
        this.locationSubscription.remove();
        this.locationSubscription = null;
      }

      if (this.errorSubscription) {
        this.errorSubscription.remove();
        this.errorSubscription = null;
      }

      if (this.permissionSubscription) {
        this.permissionSubscription.remove();
        this.permissionSubscription = null;
      }

      if (this.netInfoUnsubscribe) {
        this.netInfoUnsubscribe();
        this.netInfoUnsubscribe = null;
      }

      if (this.appStateSubscription) {
        this.appStateSubscription.remove();
        this.appStateSubscription = null;
      }

      // Clear timeout
      if (this.syncTimeout) {
        clearTimeout(this.syncTimeout);
        this.syncTimeout = null;
      }

      console.log("[Tracker] LocationTracker cleanup completed on", Platform.OS);
    } catch (error) {
      console.error("[Tracker] Error during cleanup:", error);
    }
  }

  // Getter for tracking state
  get isCurrentlyTracking(): boolean {
    return this.isTracking;
  }

  // Getter for last known location
  get lastLocation(): LocationUpdate | null {
    return this.lastKnownLocation;
  }

  // Debug method for testing native module communication
  async testNativeCommunication(): Promise<void> {
    try {
      console.log("[DEBUG] 🧪 Testing native module communication on", Platform.OS);
      
      // Test 1: Check if module exists
      console.log("[DEBUG] 🧪 Module exists:", !!NativeLocationModule);
      
      // Test 2: Check available methods
      const methods = Object.getOwnPropertyNames(NativeLocationModule);
      console.log("[DEBUG] 🧪 Available methods:", methods);
      
      // Test 3: Test permission request
      const permissionResult = await NativeLocationModule.requestLocationPermissions();
      console.log("[DEBUG] 🧪 Permission test result:", permissionResult);
      
      // Test 4: Test get last location
      const lastLocation = await NativeLocationModule.getLastLocation();
      console.log("[DEBUG] 🧪 Last location test:", lastLocation);
      
    } catch (error) {
      console.error("[DEBUG] ❌ Native communication test failed:", error);
    }
  }

  // Debug method for checking event listeners
  debugEventListeners(): void {
    console.log("[DEBUG] 📡 Event listener status on", Platform.OS);
    console.log("[DEBUG]   - Location subscription:", !!this.locationSubscription);
    console.log("[DEBUG]   - Error subscription:", !!this.errorSubscription);
    console.log("[DEBUG]   - Permission subscription:", !!this.permissionSubscription);
    console.log("[DEBUG]   - Event emitter:", !!this.eventEmitter);
    
    // Test event emitter
    if (this.eventEmitter) {
      console.log("[DEBUG] 📡 Event emitter listeners:", this.eventEmitter.listenerCount);
    }
  }

  // Comprehensive debug method
  async debugLocationTracking(): Promise<void> {
    console.log("=== LOCATION TRACKING DEBUG (", Platform.OS, ") ===");
    
    // Test 1: Native module availability
    await this.testNativeCommunication();
    
    // Test 2: Event listeners
    this.debugEventListeners();
    
    // Test 3: Start tracking with debug
    console.log("[DEBUG] 🚀 Starting tracking with debug...");
    const success = await this.startTracking((location) => {
      console.log("[DEBUG] 📍 LOCATION RECEIVED:", location);
    });
    
    console.log("[DEBUG] 🚀 Tracking start result:", success);
    
    // Test 4: Wait for updates
    setTimeout(() => {
      console.log("[DEBUG] ⏰ 10 seconds passed, checking for updates...");
      console.log("[DEBUG] 📍 Last known location:", this.lastLocation);
      console.log("[DEBUG] 🔄 Is tracking:", this.isCurrentlyTracking);
    }, 10000);
    
    console.log("=== DEBUG COMPLETE (", Platform.OS, ") ===");
  }
}

export default LocationTracker; 