export async function sendLocationToServer(
  lat: number,
  lon: number,
  time: Date | string | number,
  isIdle: boolean = false,
  idleDuration?: number
) {
  try {
    console.log("[API] Sending location to server:", { lat, lon, time, isIdle, idleDuration });
    
    // Convert time to proper format
    let timestamp: string;
    if (time instanceof Date) {
      timestamp = time.toISOString();
    } else if (typeof time === "string") {
      timestamp = time;
    } else if (typeof time === "number") {
      timestamp = new Date(time).toISOString();
    } else {
      timestamp = new Date().toISOString();
    }

    if (lat && lon && timestamp) {
      const requestBody = {
        userId: "June_19_PUNITH",
        latitude: lat,
        longitude: lon,
        timestamp: timestamp,
        isIdle: isIdle,
        idleDuration: idleDuration
      };

      console.log('[API] Request body:', requestBody);

      const response = await fetch(
        "https://location-service-backend-fc6cdca3b57b.herokuapp.com/api/location/update",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Accept: "application/json",
          },
          body: JSON.stringify(requestBody),
        }
      );

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const responseData = await response.json();
      console.log("[API] Location sync successful:", responseData);


      return responseData;
    } else {
      throw new Error("Invalid location data provided");
    }
  } catch (error: any) {
    console.log("[API] Failed to send location:", error);
    
    // Only show alert in development
    if (__DEV__) {
      // Alert.alert(
      //   "Location Sync Error",
      //   `Failed to sync location: ${error?.message || "Unknown error"}`
      // );
    }
    
    // Re-throw to let caller handle the error
    throw error;
  }
}
