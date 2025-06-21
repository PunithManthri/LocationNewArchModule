import { useState, useEffect, useCallback } from 'react';
import { NativeEventEmitter, NativeModules } from 'react-native';
import LocationModule, { LocationData, LocationError, PermissionResult } from './LocationModule';

export interface UseLocationTrackingReturn {
  // State
  isTracking: boolean;
  lastLocation: LocationData | null;
  permissionStatus: string;
  accuracyStatus: number;
  error: string | null;
  isLoading: boolean;
  
  // Actions
  startTracking: () => Promise<void>;
  stopTracking: () => Promise<void>;
  requestPermissions: () => Promise<void>;
  checkAccuracy: () => Promise<void>;
  requestAccuracy: () => Promise<void>;
  getLastLocation: () => Promise<void>;
  
  // Events
  onLocationUpdate: ((location: LocationData) => void) | null;
  onLocationError: ((error: LocationError) => void) | null;
  onPermissionChange: ((permission: PermissionResult) => void) | null;
}

export const useLocationTracking = (): UseLocationTrackingReturn => {
  const [isTracking, setIsTracking] = useState(false);
  const [lastLocation, setLastLocation] = useState<LocationData | null>(null);
  const [permissionStatus, setPermissionStatus] = useState<string>('unknown');
  const [accuracyStatus, setAccuracyStatus] = useState<number>(0);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  
  // Event handlers
  const [onLocationUpdate, setOnLocationUpdate] = useState<((location: LocationData) => void) | null>(null);
  const [onLocationError, setOnLocationError] = useState<((error: LocationError) => void) | null>(null);
  const [onPermissionChange, setOnPermissionChange] = useState<((permission: PermissionResult) => void) | null>(null);

  // Event emitter setup
  useEffect(() => {
    const eventEmitter = new NativeEventEmitter(NativeModules.LocationModule);
    
    const locationSubscription = eventEmitter.addListener('onLocationUpdate', (location: LocationData) => {
      console.log('📍 [NewArch] Location Update:', location);
      setLastLocation(location);
      setError(null);
      onLocationUpdate?.(location);
    });
    
    const errorSubscription = eventEmitter.addListener('onLocationError', (error: LocationError) => {
      console.log('❌ [NewArch] Location Error:', error);
      setError(error.error);
      onLocationError?.(error);
    });
    
    const permissionSubscription = eventEmitter.addListener('onLocationPermissionChanged', (permission: PermissionResult) => {
      console.log('🔐 [NewArch] Permission Changed:', permission);
      setPermissionStatus(permission.status);
      setAccuracyStatus(permission.accuracyStatus || 0);
      onPermissionChange?.(permission);
    });

    return () => {
      locationSubscription.remove();
      errorSubscription.remove();
      permissionSubscription.remove();
    };
  }, [onLocationUpdate, onLocationError, onPermissionChange]);

  const startTracking = useCallback(async () => {
    try {
      setIsLoading(true);
      setError(null);
      
      console.log('🚀 [NewArch] Starting location tracking...');
      const result = await LocationModule.startLocationTracking();
      
      console.log('✅ [NewArch] Tracking started:', result);
      setIsTracking(true);
      setPermissionStatus(result.permission);
    } catch (err: any) {
      console.log('❌ [NewArch] Failed to start tracking:', err);
      setError(err.message || 'Failed to start location tracking');
    } finally {
      setIsLoading(false);
    }
  }, []);

  const stopTracking = useCallback(async () => {
    try {
      setIsLoading(true);
      setError(null);
      
      console.log('🛑 [NewArch] Stopping location tracking...');
      const result = await LocationModule.stopLocationTracking();
      
      console.log('✅ [NewArch] Tracking stopped:', result);
      setIsTracking(false);
    } catch (err: any) {
      console.log('❌ [NewArch] Failed to stop tracking:', err);
      setError(err.message || 'Failed to stop location tracking');
    } finally {
      setIsLoading(false);
    }
  }, []);

  const requestPermissions = useCallback(async () => {
    try {
      setIsLoading(true);
      setError(null);
      
      console.log('🔐 [NewArch] Requesting location permissions...');
      const result = await LocationModule.requestLocationPermissions();
      
      console.log('✅ [NewArch] Permission result:', result);
      setPermissionStatus(result.status);
      setAccuracyStatus(result.accuracyStatus || 0);
    } catch (err: any) {
      console.log('❌ [NewArch] Permission request failed:', err);
      setError(err.message || 'Failed to request permissions');
    } finally {
      setIsLoading(false);
    }
  }, []);

  const checkAccuracy = useCallback(async () => {
    try {
      setError(null);
      
      console.log('🎯 [NewArch] Checking accuracy authorization...');
      const result = await LocationModule.checkAccuracyAuthorization();
      
      console.log('✅ [NewArch] Accuracy status:', result);
      setAccuracyStatus(result);
    } catch (err: any) {
      console.log('❌ [NewArch] Accuracy check failed:', err);
      setError(err.message || 'Failed to check accuracy');
    }
  }, []);

  const requestAccuracy = useCallback(async () => {
    try {
      setError(null);
      
      console.log('🎯 [NewArch] Requesting accuracy authorization...');
      const result = await LocationModule.requestAccuracyAuthorization();
      
      console.log('✅ [NewArch] Accuracy authorization result:', result);
      setAccuracyStatus(result);
    } catch (err: any) {
      console.log('❌ [NewArch] Accuracy request failed:', err);
      setError(err.message || 'Failed to request accuracy');
    }
  }, []);

  const getLastLocation = useCallback(async () => {
    try {
      setError(null);
      
      console.log('📍 [NewArch] Getting last location...');
      const location = await LocationModule.getLastLocation();
      
      console.log('✅ [NewArch] Last location:', location);
      setLastLocation(location);
    } catch (err: any) {
      console.log('❌ [NewArch] Failed to get last location:', err);
      setError(err.message || 'Failed to get last location');
    }
  }, []);

  return {
    // State
    isTracking,
    lastLocation,
    permissionStatus,
    accuracyStatus,
    error,
    isLoading,
    
    // Actions
    startTracking,
    stopTracking,
    requestPermissions,
    checkAccuracy,
    requestAccuracy,
    getLastLocation,
    
    // Events
    onLocationUpdate,
    onLocationError,
    onPermissionChange,
  };
}; 