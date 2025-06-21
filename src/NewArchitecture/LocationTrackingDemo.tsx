import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  Alert,
} from 'react-native';
import { useLocationTracking } from './useLocationTracking';
import { LocationData, LocationError, PermissionResult } from './LocationModule';

const LocationTrackingDemo: React.FC = () => {
  const {
    isTracking,
    lastLocation,
    permissionStatus,
    accuracyStatus,
    error,
    isLoading,
    startTracking,
    stopTracking,
    requestPermissions,
    checkAccuracy,
    requestAccuracy,
    getLastLocation,
  } = useLocationTracking();

  const [locationHistory, setLocationHistory] = useState<LocationData[]>([]);
  const [errorHistory, setErrorHistory] = useState<LocationError[]>([]);

  // Set up event handlers
  useEffect(() => {
    // Handle location updates
    const handleLocationUpdate = (location: LocationData) => {
      console.log('📍 [NewArch Demo] Location Update:', location);
      setLocationHistory(prev => [location, ...prev.slice(0, 9)]); // Keep last 10
    };

    // Handle location errors
    const handleLocationError = (error: LocationError) => {
      console.log('❌ [NewArch Demo] Location Error:', error);
      setErrorHistory(prev => [error, ...prev.slice(0, 4)]); // Keep last 5
    };

    // Handle permission changes
    const handlePermissionChange = (permission: PermissionResult) => {
      console.log('🔐 [NewArch Demo] Permission Changed:', permission);
      // Alert.alert('Permission Changed', `Status: ${permission.status}`);
    };

    // Set the event handlers
    // Note: In a real implementation, these would be set through the hook
  }, []);

  const handleStartTracking = async () => {
    try {
      await startTracking();
      // Alert.alert('Success', 'Location tracking started!');
    } catch (err: any) {
      // Alert.alert('Error', err.message || 'Failed to start tracking');
    }
  };

  const handleStopTracking = async () => {
    try {
      await stopTracking();
      // Alert.alert('Success', 'Location tracking stopped!');
    } catch (err: any) {
      // Alert.alert('Error', err.message || 'Failed to stop tracking');
    }
  };

  const handleRequestPermissions = async () => {
    try {
      await requestPermissions();
      // Alert.alert('Success', 'Permissions requested!');
    } catch (err: any) {
      // Alert.alert('Error', err.message || 'Failed to request permissions');
    }
  };

  const formatLocation = (location: LocationData) => {
    return `📍 ${location.latitude.toFixed(6)}, ${location.longitude.toFixed(6)}
Accuracy: ${location.accuracy}m
Speed: ${location.speed}m/s
Idle Time: ${location.totalIdleTimeBelowThreshold}ms
Reason: ${location.updateReason}`;
  };

  const formatError = (error: LocationError) => {
    return `❌ ${error.error}
Code: ${error.code}
Count: ${error.errorCount}`;
  };

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.title}>New Architecture Location Tracking</Text>
      
      {/* Status Section */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Status</Text>
        <Text style={styles.statusText}>
          Tracking: {isTracking ? '🟢 Active' : '🔴 Inactive'}
        </Text>
        <Text style={styles.statusText}>
          Permission: {permissionStatus}
        </Text>
        <Text style={styles.statusText}>
          Accuracy: {accuracyStatus === 1 ? 'Full' : 'Reduced'}
        </Text>
        {error && (
          <Text style={styles.errorText}>Error: {error}</Text>
        )}
      </View>

      {/* Controls Section */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Controls</Text>
        
        <TouchableOpacity
          style={[styles.button, styles.startButton]}
          onPress={handleStartTracking}
          disabled={isLoading}
        >
          <Text style={styles.buttonText}>
            {isLoading ? 'Starting...' : 'Start Tracking'}
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.button, styles.stopButton]}
          onPress={handleStopTracking}
          disabled={isLoading}
        >
          <Text style={styles.buttonText}>
            {isLoading ? 'Stopping...' : 'Stop Tracking'}
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.button, styles.permissionButton]}
          onPress={handleRequestPermissions}
          disabled={isLoading}
        >
          <Text style={styles.buttonText}>Request Permissions</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.button, styles.accuracyButton]}
          onPress={checkAccuracy}
          disabled={isLoading}
        >
          <Text style={styles.buttonText}>Check Accuracy</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.button, styles.locationButton]}
          onPress={getLastLocation}
          disabled={isLoading}
        >
          <Text style={styles.buttonText}>Get Last Location</Text>
        </TouchableOpacity>
      </View>

      {/* Last Location Section */}
      {lastLocation && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Last Location</Text>
          <Text style={styles.locationText}>{formatLocation(lastLocation)}</Text>
        </View>
      )}

      {/* Location History Section */}
      {locationHistory.length > 0 && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Location History</Text>
          {locationHistory.map((location, index) => (
            <View key={index} style={styles.historyItem}>
              <Text style={styles.historyText}>{formatLocation(location)}</Text>
            </View>
          ))}
        </View>
      )}

      {/* Error History Section */}
      {errorHistory.length > 0 && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Error History</Text>
          {errorHistory.map((error, index) => (
            <View key={index} style={styles.errorItem}>
              <Text style={styles.errorHistoryText}>{formatError(error)}</Text>
            </View>
          ))}
        </View>
      )}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
    padding: 16,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 20,
    color: '#333',
  },
  section: {
    backgroundColor: 'white',
    borderRadius: 8,
    padding: 16,
    marginBottom: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 12,
    color: '#333',
  },
  statusText: {
    fontSize: 16,
    marginBottom: 4,
    color: '#666',
  },
  errorText: {
    fontSize: 16,
    marginTop: 8,
    color: '#d32f2f',
    fontWeight: 'bold',
  },
  button: {
    padding: 12,
    borderRadius: 6,
    marginBottom: 8,
    alignItems: 'center',
  },
  startButton: {
    backgroundColor: '#4caf50',
  },
  stopButton: {
    backgroundColor: '#f44336',
  },
  permissionButton: {
    backgroundColor: '#2196f3',
  },
  accuracyButton: {
    backgroundColor: '#ff9800',
  },
  locationButton: {
    backgroundColor: '#9c27b0',
  },
  buttonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: 'bold',
  },
  locationText: {
    fontSize: 14,
    fontFamily: 'monospace',
    color: '#333',
    lineHeight: 20,
  },
  historyItem: {
    backgroundColor: '#f8f9fa',
    padding: 12,
    borderRadius: 6,
    marginBottom: 8,
  },
  historyText: {
    fontSize: 12,
    fontFamily: 'monospace',
    color: '#666',
    lineHeight: 16,
  },
  errorItem: {
    backgroundColor: '#ffebee',
    padding: 12,
    borderRadius: 6,
    marginBottom: 8,
  },
  errorHistoryText: {
    fontSize: 12,
    fontFamily: 'monospace',
    color: '#d32f2f',
    lineHeight: 16,
  },
});

export default LocationTrackingDemo; 