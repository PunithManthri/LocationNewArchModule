import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface LocationData {
  latitude: number;
  longitude: number;
  accuracy: number;
  altitude: number;
  speed: number;
  speedAccuracy: number;
  heading: number;
  timestamp: number;
  displacement: number;
  updateReason: string;
  isSameLocation: boolean;
  isBelowDistanceThreshold: boolean;
  totalIdleTimeBelowThreshold: number;
  isCurrentlyIdle: boolean;
  outsideVist_Total_IdleTime: number;
  isOutsideVisitTracking: boolean;
  predictionConfidence?: number;
  averageVelocity?: number;
  averageAccuracy?: number;
}

export interface LocationError {
  error: string;
  code: number;
  domain: string;
  errorCount: number;
  recoveryAttempts: number;
}

export interface PermissionResult {
  status: string;
  accuracy?: string;
  accuracyStatus?: number;
  message?: string;
}

export interface Spec extends TurboModule {
  // Location tracking methods
  startLocationTracking(): Promise<boolean>;
  
  stopLocationTracking(): Promise<boolean>;
  
  getLastLocation(): Promise<LocationData | null>;
  
  // Permission methods
  requestLocationPermissions(): Promise<PermissionResult>;
  
  checkAccuracyAuthorization(): Promise<number>;
  
  requestAccuracyAuthorization(): Promise<number>;
  
  // Event emitter methods
  addListener(eventName: string): void;
  removeListeners(count: number): void;

  testMethod(): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('LocationModule'); 