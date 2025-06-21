# Cross-Platform Location Tracking Testing Guide

## Overview
This guide helps you test the background-only location sync behavior on both iOS and Android platforms. The same LocationTracker.ts logic works seamlessly across both platforms with platform-specific native modules.

## Platform-Specific Implementation

### Android Implementation
- **Native Module**: `LocationModule.kt` (Kotlin)
- **Service**: `LocationService.kt` (Background service)
- **Features**: 
  - Background location tracking
  - Idle time detection
  - Adaptive update intervals
  - Battery optimization

### iOS Implementation
- **Native Module**: `LocationModule.swift` (Swift)
- **Features**:
  - Background location tracking
  - Idle time detection
  - Adaptive update intervals
  - Battery optimization

## Key Features (Both Platforms)

### Background-Only Sync Strategy
- **Foreground**: All location data stored locally only, no server communication
- **Background**: Stored locations synced to server when app goes to background
- **Return to Foreground**: Any remaining locations synced when app becomes active

### Idle Time Tracking
- Detects when user is stationary (below 50m displacement threshold)
- Sends idle time data with coordinates (-999, -999) and type "idle_time_only"
- Tracks total idle time and outside visit idle time
- Only reports idle status when threshold is exceeded

### Smart Location Filtering
- 50-meter displacement threshold for location updates
- Force update every 15 seconds if no movement
- Adaptive update intervals based on movement patterns
- Battery optimization with power saving modes

## Testing Steps

### Step 1: Platform Setup
1. **Android**: Ensure location permissions and background location access
2. **iOS**: Ensure location permissions and background app refresh enabled
3. **Both**: Grant location permissions when prompted

### Step 2: Basic Functionality Test
1. Start the app on your target platform
2. Press "Start Tracking" to begin location monitoring
3. Verify that location updates are being received
4. Check console logs for platform-specific messages

### Step 3: Foreground Testing (No Server Sync)
1. Keep the app in foreground
2. Move around or wait for location updates
3. Check console logs - should show "stored locally" messages
4. Verify that NO locations are being sent to the server
5. Check that locations are being stored in AsyncStorage

### Step 4: Background Sync Testing
1. Start location tracking and accumulate some location data
2. Press home button to background the app
3. Wait for the sync to complete (check console logs)
4. Return to the app
5. Check if any remaining locations were synced

### Step 5: Idle Time Testing
1. Start tracking and stay stationary
2. Wait for idle time data to be sent (coordinates -999, -999)
3. Verify idle time tracking is working
4. Move around to reset idle tracking

## Expected Console Logs

### Android Logs
```
[DEBUG] 🔧 Initializing LocationTracker for android
[DEBUG] 📡 Setting up event emitter listeners for android
[DEBUG] 📍 LOCATION UPDATE RECEIVED from native (android): {...}
[Background] Stored location locally (will sync when app goes to background): {...}
[Background] Starting background location sync (background-only mode) on android
```

### iOS Logs
```
[DEBUG] 🔧 Initializing LocationTracker for ios
[DEBUG] 📡 Setting up event emitter listeners for ios
[DEBUG] 📍 LOCATION UPDATE RECEIVED from native (ios): {...}
[Background] Stored location locally (will sync when app goes to background): {...}
[Background] Starting background location sync (background-only mode) on ios
```

### Idle Time Data (Both Platforms)
```
[DEBUG] 📍 LOCATION UPDATE RECEIVED from native (ios/android): {
  "latitude": -999,
  "longitude": -999,
  "type": "idle_time_only",
  "idleTime": 60000,
  "outsideVisitIdleTime": 30000
}
```

## Platform-Specific Considerations

### Android
- **Background Service**: Uses foreground service for continuous tracking
- **Battery Optimization**: May need to disable battery optimization for the app
- **Location Permissions**: Requires ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION
- **Background Location**: May require ACCESS_BACKGROUND_LOCATION for Android 10+

### iOS
- **Background App Refresh**: Must be enabled in Settings
- **Location Permissions**: Requires "Always" permission for background tracking
- **Battery Optimization**: iOS handles this automatically
- **Location Services**: Must be enabled in Settings

## Troubleshooting

### Common Issues

#### No Location Updates
1. **Check Permissions**: Ensure location permissions are granted
2. **Check Location Services**: Enable location services in device settings
3. **Check Background App Refresh** (iOS): Enable in Settings > General > Background App Refresh
4. **Check Battery Optimization** (Android): Disable battery optimization for the app

#### Background Sync Not Working
1. **Check Internet**: Ensure internet connectivity when app goes to background
2. **Check Permissions**: Verify background location permissions
3. **Check Console Logs**: Look for sync-related error messages
4. **Check AsyncStorage**: Verify locations are being stored locally

#### Idle Time Not Working
1. **Check Movement**: Ensure you're staying stationary (below 50m threshold)
2. **Check Console Logs**: Look for idle time data messages
3. **Check Threshold**: Verify the 50-meter displacement threshold
4. **Check Force Update**: Should force update every 15 seconds if stationary

### Platform-Specific Issues

#### Android Issues
- **Service Killed**: Check if battery optimization is killing the service
- **Permission Denied**: Ensure all required permissions are granted
- **Background Location**: May need to request background location permission separately

#### iOS Issues
- **Background App Refresh**: Must be enabled for background tracking
- **Location Accuracy**: May need to request "Always" permission for best results
- **App State**: iOS may suspend the app in background, affecting tracking

## Testing Checklist

### General Testing
- [ ] App starts and initializes LocationTracker
- [ ] Location permissions are granted
- [ ] Location tracking starts successfully
- [ ] Location updates are received in foreground
- [ ] Locations are stored locally in foreground (no server sync)
- [ ] App can be backgrounded while tracking
- [ ] Stored locations are synced when app goes to background
- [ ] App can be brought back to foreground
- [ ] Any remaining locations are synced when returning to foreground
- [ ] Idle time tracking works correctly
- [ ] Idle time data is sent with correct format

### Platform-Specific Testing
- [ ] **Android**: Background service continues running
- [ ] **Android**: Battery optimization doesn't kill the service
- [ ] **iOS**: Background app refresh is enabled
- [ ] **iOS**: Location permissions include "Always" access
- [ ] **Both**: Console logs show correct platform information
- [ ] **Both**: Event system works correctly
- [ ] **Both**: Error handling works properly

## Performance Considerations

### Battery Usage
- **Android**: Uses adaptive update intervals to minimize battery usage
- **iOS**: iOS handles battery optimization automatically
- **Both**: Idle time detection reduces unnecessary updates

### Data Usage
- **Foreground**: No server communication, minimal data usage
- **Background**: Batched syncs reduce network usage
- **Idle Time**: Minimal data for idle status updates

### Storage Usage
- **AsyncStorage**: Locations stored locally until synced
- **Cleanup**: Failed locations moved to end for retry
- **Memory**: Automatic cleanup of old cached data

## Notes

- The same LocationTracker.ts logic works on both platforms
- Platform-specific differences are handled in native modules
- Background-only sync reduces server load and improves performance
- Idle time tracking provides valuable user behavior insights
- All location data is eventually synced, ensuring no data loss
- The system is designed to be battery-efficient and user-friendly 