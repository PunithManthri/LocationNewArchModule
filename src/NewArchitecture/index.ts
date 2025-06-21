// New Architecture Location Tracking Module
// React Native 0.76.4+ Bridgeless Architecture Implementation

import LocationModule from './LocationModule';
import { useLocationTracking } from './useLocationTracking';
import LocationTrackingDemo from './LocationTrackingDemo';

export { LocationModule, useLocationTracking, LocationTrackingDemo };

// Type exports
export type {
  LocationData,
  LocationError,
  PermissionResult,
  Spec
} from './LocationModule';

export type { UseLocationTrackingReturn } from './useLocationTracking';

// Constants
export const NEW_ARCHITECTURE_VERSION = '1.0.0';
export const REACT_NATIVE_VERSION = '0.76.4';
export const ARCHITECTURE_TYPE = 'Bridgeless (TurboModules)';

// Utility functions
export const isNewArchitectureSupported = (): boolean => {
  // Check if running on React Native 0.76.4+
  const reactNativeVersion = require('react-native/package.json').version;
  return reactNativeVersion >= '0.76.0';
};

export const getArchitectureInfo = () => ({
  version: NEW_ARCHITECTURE_VERSION,
  reactNativeVersion: REACT_NATIVE_VERSION,
  architectureType: ARCHITECTURE_TYPE,
  isSupported: isNewArchitectureSupported(),
  features: [
    'TurboModules',
    'Bridgeless Architecture',
    'Type Safety',
    'Performance Optimization',
    'Idle Time Tracking',
    'Predictive Location',
    'Battery Optimization'
  ]
});

// Default export for backward compatibility
export default {
  LocationModule: LocationModule,
  useLocationTracking: useLocationTracking,
  LocationTrackingDemo: LocationTrackingDemo,
  isNewArchitectureSupported: isNewArchitectureSupported,
  getArchitectureInfo: getArchitectureInfo,
  NEW_ARCHITECTURE_VERSION: NEW_ARCHITECTURE_VERSION,
  REACT_NATIVE_VERSION: REACT_NATIVE_VERSION,
  ARCHITECTURE_TYPE: ARCHITECTURE_TYPE
}; 