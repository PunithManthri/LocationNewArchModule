#import <Foundation/Foundation.h>
// If the import below fails, ensure your header search path includes build/generated/ios/LocationModule
#import "../build/generated/ios/LocationModule/LocationModule.h" // fallback for generated protocol
#import "LocationNewArchModule-Swift.h" // imports Swift implementation class

// Forward declaration if needed
@class LocationModuleImpl;

// If NativeLocationModuleSpec is not found, ensure codegen ran and header search path is correct
@interface LocationModule () <NativeLocationModuleSpec>
@end

@implementation LocationModule {
    LocationModuleImpl *locationModuleImpl;
}

RCT_EXPORT_MODULE()

- (instancetype)init {
    if (self = [super init]) {
        locationModuleImpl = [[LocationModuleImpl alloc] init];
    }
    return self;
}

#ifdef __cplusplus
- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeLocationModuleSpecJSI>(params);
}
#endif

// Delegate all protocol methods to Swift implementation
- (void)startLocationTracking:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    [locationModuleImpl startLocationTrackingWithResolve:resolve reject:reject];
}
- (void)stopLocationTracking:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    [locationModuleImpl stopLocationTrackingWithResolve:resolve reject:reject];
}
- (void)getLastLocation:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    [locationModuleImpl getLastLocationWithResolve:resolve reject:reject];
}
- (void)requestLocationPermissions:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    [locationModuleImpl requestLocationPermissionsWithResolve:resolve reject:reject];
}
- (void)checkAccuracyAuthorization:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    [locationModuleImpl checkAccuracyAuthorizationWithResolve:resolve reject:reject];
}
- (void)requestAccuracyAuthorization:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    [locationModuleImpl requestAccuracyAuthorizationWithResolve:resolve reject:reject];
}
- (void)addListener:(NSString *)eventName {
    [locationModuleImpl addListenerWithEventName:eventName];
}
- (void)removeListeners:(double)count {
    [locationModuleImpl removeListenersWithCount:count];
}
- (void)testMethod {
    [locationModuleImpl testMethod];
}

@end
// NOTE: If you get a 'LocationModule.h not found' or 'NativeLocationModuleSpec not found' error, ensure your Xcode header search paths include the build/generated/ios/LocationModule directory, or adjust the import accordingly. Also, make sure your Swift class is named LocationModuleImpl and is exposed to Objective-C. 