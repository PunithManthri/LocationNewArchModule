/**
 * Location New Architecture Test App
 * Comprehensive UI for displaying location data from Native Modules with Idle Time Tracking
 */

import React, { useState, useEffect, useRef } from 'react';
import {
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  Alert,
  Dimensions,
  Animated,
  Platform,
} from 'react-native';
import LocationTracker, { LocationUpdate } from './src/NewArchitecture/LocationTracker';

const { width } = Dimensions.get('window');

function App(): React.JSX.Element {
  const [isTracking, setIsTracking] = useState(false);
  const [currentLocation, setCurrentLocation] = useState<LocationUpdate | null>(null);
  const [lastLocation, setLastLocation] = useState<LocationUpdate | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [permissionStatus, setPermissionStatus] = useState<string>('Not requested');
  const [serviceStatus, setServiceStatus] = useState<string>('Not started');
  
  // Idle time tracking state
  const [currentIdleTime, setCurrentIdleTime] = useState(0);
  const [totalIdleTime, setTotalIdleTime] = useState(0);
  const [idleSessions, setIdleSessions] = useState(0);
  const [isCurrentlyIdle, setIsCurrentlyIdle] = useState(false);
  
  // Animation values
  const pulseAnim = useRef(new Animated.Value(1)).current;
  const idlePulseAnim = useRef(new Animated.Value(1)).current;
  
  // LocationTracker instance
  const locationTrackerRef = useRef<LocationTracker | null>(null);

  useEffect(() => {
    // Initialize LocationTracker
    try {
      locationTrackerRef.current = new LocationTracker();
      console.log('[App] LocationTracker initialized successfully');
    } catch (error) {
      console.error('[App] Failed to initialize LocationTracker:', error);
      setError(`Failed to initialize tracker: ${error}`);
    }

    // Cleanup on unmount
    return () => {
      if (locationTrackerRef.current) {
        locationTrackerRef.current.cleanup();
      }
    };
  }, []);

  // Start pulse animation for tracking indicator
  useEffect(() => {
    if (isTracking) {
      const pulseAnimation = Animated.loop(
        Animated.sequence([
          Animated.timing(pulseAnim, {
            toValue: 1.2,
            duration: 1000,
            useNativeDriver: true,
          }),
          Animated.timing(pulseAnim, {
            toValue: 1,
            duration: 1000,
            useNativeDriver: true,
          }),
        ])
      );
      pulseAnimation.start();
      return () => pulseAnimation.stop();
    }
  }, [isTracking, pulseAnim]);

  // Start pulse animation for idle indicator
  useEffect(() => {
    if (isCurrentlyIdle) {
      const idlePulseAnimation = Animated.loop(
        Animated.sequence([
          Animated.timing(idlePulseAnim, {
            toValue: 1.3,
            duration: 1500,
            useNativeDriver: true,
          }),
          Animated.timing(idlePulseAnim, {
            toValue: 1,
            duration: 1500,
            useNativeDriver: true,
          }),
        ])
      );
      idlePulseAnimation.start();
      return () => idlePulseAnimation.stop();
    }
  }, [isCurrentlyIdle, idlePulseAnim]);

  const handleLocationUpdate = (location: LocationUpdate) => {
    console.log('[App] Location update received:', location);
    
    setLastLocation(currentLocation);
    setCurrentLocation(location);
    
    // Update idle time tracking
    if (location.isCurrentlyIdle !== undefined) {
      setIsCurrentlyIdle(location.isCurrentlyIdle);
    }
    
    if (location.totalIdleTimeBelowThreshold !== undefined) {
      setCurrentIdleTime(location.totalIdleTimeBelowThreshold);
    }
    
    if (location.outsideVist_Total_IdleTime !== undefined) {
      setTotalIdleTime(location.outsideVist_Total_IdleTime);
    }
    
    // Update service status
    setServiceStatus('Active - Receiving updates');
  };

  const startTracking = async () => {
    try {
      setError(null);
      setServiceStatus('Starting...');
      
      if (!locationTrackerRef.current) {
        throw new Error('LocationTracker not initialized');
      }

      const success = await locationTrackerRef.current.startTracking(handleLocationUpdate);
      
      if (success) {
        setIsTracking(true);
        setServiceStatus('Active');
        console.log('[App] Location tracking started successfully');
      } else {
        setError('Failed to start location tracking');
        setServiceStatus('Failed to start');
      }
    } catch (err) {
      console.error('[App] Error starting tracking:', err);
      setError(`Error starting tracking: ${err}`);
      setServiceStatus('Error');
    }
  };

  const stopTracking = async () => {
    try {
      if (!locationTrackerRef.current) {
        throw new Error('LocationTracker not initialized');
      }

      await locationTrackerRef.current.stopTracking();
      setIsTracking(false);
      setServiceStatus('Stopped');
      console.log('[App] Location tracking stopped successfully');
    } catch (err) {
      console.error('[App] Error stopping tracking:', err);
      setError(`Error stopping tracking: ${err}`);
    }
  };

  const requestPermissions = async () => {
    try {
      setError(null);
      setPermissionStatus('Requesting...');
      
      if (!locationTrackerRef.current) {
        throw new Error('LocationTracker not initialized');
      }

      const hasPermissions = await locationTrackerRef.current.requestLocationPermissions();
      
      if (hasPermissions) {
        setPermissionStatus('Granted');
        console.log('[App] Location permissions granted');
      } else {
        setPermissionStatus('Denied');
        setError('Location permissions denied');
      }
    } catch (err) {
      console.error('[App] Error requesting permissions:', err);
      setError(`Error requesting permissions: ${err}`);
      setPermissionStatus('Error');
    }
  };

  const formatTime = (milliseconds: number): string => {
    const seconds = Math.floor(milliseconds / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);
    
    if (hours > 0) {
      return `${hours}h ${minutes % 60}m ${seconds % 60}s`;
    } else if (minutes > 0) {
      return `${minutes}m ${seconds % 60}s`;
    } else {
      return `${seconds}s`;
    }
  };

  const formatLocation = (location: LocationUpdate | null): string => {
    if (!location) return 'No location data';
    return `${location.latitude.toFixed(6)}, ${location.longitude.toFixed(6)}`;
  };

  const formatAccuracy = (accuracy?: number): string => {
    if (!accuracy) return 'N/A';
    return `${accuracy.toFixed(1)}m`;
  };

  const formatSpeed = (speed?: number): string => {
    if (!speed) return 'N/A';
    return `${(speed * 3.6).toFixed(1)} km/h`; // Convert m/s to km/h
  };

  const backgroundStyle = {
    backgroundColor: "black",
  };

  return (
    <SafeAreaView style={backgroundStyle}>
      <StatusBar
        barStyle="light-content"
        backgroundColor={'black'}
      />
      <ScrollView
        contentInsetAdjustmentBehavior="automatic"
        style={backgroundStyle}
      >
        <View style={styles.container}>
          <Text style={styles.title}>Location Tracking Demo</Text>
          
          {/* Status Indicators */}
          <View style={styles.statusContainer}>
            <View style={styles.statusRow}>
              <Animated.View 
                style={[
                  styles.statusIndicator, 
                  { 
                    backgroundColor: isTracking ? '#4CAF50' : '#F44336',
                    transform: [{ scale: pulseAnim }]
                  }
                ]} 
              />
              <Text style={styles.statusText}>
                Tracking: {isTracking ? 'Active' : 'Inactive'}
              </Text>
            </View>
            
            <View style={styles.statusRow}>
              <Animated.View 
                style={[
                  styles.statusIndicator, 
                  { 
                    backgroundColor: isCurrentlyIdle ? '#FF9800' : '#2196F3',
                    transform: [{ scale: idlePulseAnim }]
                  }
                ]} 
              />
              <Text style={styles.statusText}>
                Idle Status: {isCurrentlyIdle ? 'Idle' : 'Active'}
              </Text>
            </View>

            <View style={styles.statusRow}>
              <View style={[styles.statusIndicator, { backgroundColor: '#9C27B0' }]} />
              <Text style={styles.statusText}>
                Platform: {Platform.OS.toUpperCase()}
              </Text>
            </View>
          </View>

          {/* Permission Status */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Permission Status</Text>
            <Text style={styles.statusText}>{permissionStatus}</Text>
            <TouchableOpacity style={styles.button} onPress={requestPermissions}>
              <Text style={styles.buttonText}>Request Permissions</Text>
            </TouchableOpacity>
          </View>

          {/* Service Status */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Service Status</Text>
            <Text style={styles.statusText}>{serviceStatus}</Text>
          </View>

          {/* Current Location */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Current Location</Text>
            <Text style={styles.locationText}>
              Coordinates: {formatLocation(currentLocation)}
            </Text>
            <Text style={styles.locationText}>
              Accuracy: {formatAccuracy(currentLocation?.accuracy)}
            </Text>
            <Text style={styles.locationText}>
              Speed: {formatSpeed(currentLocation?.speed)}
            </Text>
            <Text style={styles.locationText}>
              Timestamp: {currentLocation?.timestamp ? new Date(currentLocation.timestamp).toLocaleString() : 'N/A'}
            </Text>
          </View>

          {/* Last Location */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Last Location</Text>
            <Text style={styles.locationText}>
              Coordinates: {formatLocation(lastLocation)}
            </Text>
            <Text style={styles.locationText}>
              Accuracy: {formatAccuracy(lastLocation?.accuracy)}
            </Text>
            <Text style={styles.locationText}>
              Speed: {formatSpeed(lastLocation?.speed)}
            </Text>
          </View>

          {/* Idle Time Tracking */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Idle Time Tracking</Text>
            <Text style={styles.idleText}>
              Current Idle Time: {formatTime(currentIdleTime)}
            </Text>
            <Text style={styles.idleText}>
              Total Idle Time: {formatTime(totalIdleTime)}
            </Text>
            <Text style={styles.idleText}>
              Idle Sessions: {idleSessions}
            </Text>
            <Text style={styles.idleText}>
              Status: {isCurrentlyIdle ? 'Currently Idle' : 'Active Movement'}
            </Text>
          </View>

          {/* Error Display */}
          {error && (
            <View style={styles.errorSection}>
              <Text style={styles.errorTitle}>Error</Text>
              <Text style={styles.errorText}>{error}</Text>
            </View>
          )}

          {/* Control Buttons */}
          <View style={styles.buttonContainer}>
            <TouchableOpacity 
              style={[styles.button, styles.startButton]} 
              onPress={startTracking}
              disabled={isTracking}
            >
              <Text style={styles.buttonText}>
                {isTracking ? 'Tracking...' : 'Start Tracking'}
              </Text>
            </TouchableOpacity>
            
            <TouchableOpacity 
              style={[styles.button, styles.stopButton]} 
              onPress={stopTracking}
              disabled={!isTracking}
            >
              <Text style={styles.buttonText}>
                Stop Tracking
              </Text>
            </TouchableOpacity>
          </View>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
    backgroundColor: 'black',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: 'white',
    textAlign: 'center',
    marginBottom: 30,
  },
  statusContainer: {
    marginBottom: 20,
  },
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 10,
  },
  statusIndicator: {
    width: 12,
    height: 12,
    borderRadius: 6,
    marginRight: 10,
  },
  statusText: {
    fontSize: 16,
    color: 'white',
    flex: 1,
  },
  section: {
    backgroundColor: 'black',
    padding: 15,
    borderRadius: 10,
    marginBottom: 15,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: 'white',
    marginBottom: 10,
  },
  locationText: {
    fontSize: 14,
    color: 'white',
    marginBottom: 5,
  },
  idleText: {
    fontSize: 14,
    color: 'white',
    marginBottom: 5,
  },
  errorSection: {
    backgroundColor: '#D32F2F',
    padding: 15,
    borderRadius: 10,
    marginBottom: 15,
  },
  errorTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: 'white',
    marginBottom: 5,
  },
  errorText: {
    fontSize: 14,
    color: 'white',
  },
  buttonContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 20,
  },
  button: {
    flex: 1,
    padding: 15,
    borderRadius: 10,
    alignItems: 'center',
    marginHorizontal: 5,
  },
  startButton: {
    backgroundColor: '#4CAF50',
  },
  stopButton: {
    backgroundColor: '#F44336',
  },
  buttonText: {
    fontSize: 16,
    fontWeight: 'bold',
    color: 'white',
  },
});

export default App;
