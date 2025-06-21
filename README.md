# 📍 Location New Architecture Module

A comprehensive React Native location tracking application built with the New Architecture, featuring real-time location updates, idle time tracking, and a modern UI.

## 🚀 Features

### 📍 **Location Tracking**
- Real-time GPS location updates
- High-accuracy location data with configurable intervals
- Background location tracking with foreground service
- Location history with detailed metrics
- Speed, altitude, and accuracy monitoring

### ⏰ **Idle Time Tracking**
- Smart idle detection based on movement speed
- Real-time idle time counters
- Idle session history with location data
- Configurable idle thresholds
- Visual indicators for idle status

### 🎨 **Modern UI**
- Beautiful, responsive design
- Dark/Light mode support
- Animated status indicators
- Real-time data visualization
- Pull-to-refresh functionality
- Horizontal scrollable history cards

### 🔧 **Technical Features**
- React Native New Architecture support
- Native Android module with Kotlin
- Optimized event system with queuing
- Comprehensive error handling
- Performance monitoring and metrics
- Debug logging system

## 📋 Prerequisites

- Node.js (v16 or higher)
- React Native CLI
- Android Studio
- Android SDK (API 24+)
- Google Play Services
- Physical Android device or emulator

## 🛠️ Installation

### 1. Clone the Repository
```bash
git clone <repository-url>
cd LocationNewArchModule
```

### 2. Install Dependencies
```bash
npm install
# or
yarn install
```

### 3. Android Setup

#### Install Android Dependencies
```bash
cd android
./gradlew clean
cd ..
```

#### Configure Google Play Services
Ensure your device/emulator has Google Play Services installed and updated.

### 4. Run the Application
```bash
# Start Metro bundler
npx react-native start

# Run on Android
npx react-native run-android
```

## 📱 Usage

### Starting Location Tracking
1. Launch the app
2. Grant location permissions when prompted
3. Tap "▶️ Start Tracking" to begin location monitoring
4. The app will start collecting real-time location data

### Monitoring Idle Time
- The app automatically detects when you're idle (not moving)
- View current idle time in the "⏰ Idle Time Tracking" section
- Check idle session history for past idle periods
- Monitor total idle time across all sessions

### Viewing Location Data
- **Current Location**: Real-time coordinates, accuracy, and speed
- **Location History**: Swipeable cards showing recent locations
- **Status Indicators**: Visual feedback for tracking status
- **Performance Metrics**: Event success rates and processing times

## 🏗️ Project Structure

```
LocationNewArchModule/
├── android/
│   └── app/src/main/java/com/locationnewarchmodule/
│       ├── LocationModule.kt          # Main React Native module
│       ├── LocationService.kt         # Background location service
│       └── MainApplication.kt         # App initialization
├── src/
│   └── NewArchitecture/
│       ├── useLocationTracking.ts     # React hook for location
│       ├── LocationModule.ts          # TypeScript interfaces
│       └── LocationTrackingDemo.tsx   # Demo component
├── App.tsx                            # Main application UI
├── package.json                       # Dependencies
└── README.md                          # This file
```

## 🔧 Configuration

### Location Update Settings
```kotlin
// In LocationService.kt
locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
    .setMinUpdateIntervalMillis(1000L)
    .setMinUpdateDistanceMeters(1f)
    .build()
```

### Idle Detection Threshold
```typescript
// In App.tsx
const isMoving = speed > 0.5; // Consider moving if speed > 0.5 m/s
```

### Debug Logging
```kotlin
// Enable/disable debug logs
private const val DEBUG_MODE = true
```

## 📊 API Reference

### LocationModule Methods

#### `startLocationTracking()`
Starts real-time location tracking.
```typescript
await LocationModule.startLocationTracking();
```

#### `stopLocationTracking()`
Stops location tracking.
```typescript
await LocationModule.stopLocationTracking();
```

#### `requestLocationPermissions()`
Requests necessary location permissions.
```typescript
const result = await LocationModule.requestLocationPermissions();
```

#### `getLastLocation()`
Retrieves the most recent location data.
```typescript
const location = await LocationModule.getLastLocation();
```

### Location Data Structure
```typescript
interface LocationData {
  latitude: number;
  longitude: number;
  accuracy: number;
  altitude: number;
  speed: number;
  timestamp: number;
  updateReason: string;
  totalIdleTimeBelowThreshold: number;
  isCurrentlyIdle: boolean;
  isOutsideVisitTracking: boolean;
}
```

### Idle Time Data Structure
```typescript
interface IdleTimeData {
  totalIdleTime: number;        // Total idle time in milliseconds
  currentIdleTime: number;      // Current idle time in milliseconds
  isCurrentlyIdle: boolean;     // Whether user is currently idle
  idleThreshold: number;        // Idle detection threshold
  lastMovementTime: number;     // Last movement timestamp
  idleSessions: IdleSession[];  // History of idle sessions
}
```

## 🔍 Debugging

### View Native Logs
```bash
# Filter by module tags
adb logcat -v time -s LocationService_NewArch:V LocationModule_NewArch:V

# View all logs
adb logcat

# React Native logs
npx react-native log-android
```

### Common Debug Scenarios

#### Location Not Updating
1. Check if location permissions are granted
2. Verify Google Play Services is installed
3. Ensure device has GPS enabled
4. Check debug logs for errors

#### Idle Time Not Tracking
1. Verify location tracking is active
2. Check movement threshold settings
3. Monitor speed values in logs
4. Ensure location updates are frequent enough

## 🚨 Permissions

### Required Permissions
- `ACCESS_FINE_LOCATION` - Precise location access
- `ACCESS_COARSE_LOCATION` - Approximate location access
- `FOREGROUND_SERVICE_LOCATION` - Background location service (API 34+)

### Permission Flow
1. App requests permissions on startup
2. User grants permissions through system dialog
3. App validates permission status
4. Location tracking begins automatically

## 📈 Performance

### Optimizations
- **Event Queuing**: High-priority events processed first
- **Memory Management**: Automatic cleanup of old data
- **Error Recovery**: Automatic retry for failed events
- **Performance Monitoring**: Real-time metrics tracking

### Metrics
- **Event Success Rate**: Typically 100%
- **Processing Time**: ~1ms average
- **Memory Usage**: Optimized with cleanup intervals
- **Battery Impact**: Minimal with efficient location requests

## 🐛 Troubleshooting

### Build Issues
```bash
# Clean and rebuild
cd android && ./gradlew clean
cd .. && npx react-native run-android
```

### Permission Issues
1. Check AndroidManifest.xml permissions
2. Verify runtime permission requests
3. Test on physical device (emulators may have issues)

### Location Service Issues
1. Ensure Google Play Services is updated
2. Check device GPS settings
3. Verify location simulation is working

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🙏 Acknowledgments

- React Native team for the New Architecture
- Google Play Services for location APIs
- React Native community for best practices

## 📞 Support

For issues and questions:
1. Check the troubleshooting section
2. Review debug logs
3. Create an issue with detailed information
4. Include device information and error logs

---

**Built with ❤️ using React Native New Architecture**
