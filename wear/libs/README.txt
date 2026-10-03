Place samsung-health-sensor-api.aar (Samsung Health Sensor SDK v1.4.1) in this directory.
The build script wear/build.gradle.kts is configured to automatically include all *.aar and *.jar files placed in this folder.

Expected public classes used by SamsungHealthSensorProvider:
- com.samsung.android.service.health.tracking.HealthTrackingService
- com.samsung.android.service.health.tracking.ConnectionListener
- com.samsung.android.service.health.tracking.HealthTracker
- com.samsung.android.service.health.tracking.HealthTracker$TrackerEventListener
- com.samsung.android.service.health.tracking.data.HealthTrackerType
  (HEART_RATE_CONTINUOUS, ACCELEROMETER_CONTINUOUS)
- com.samsung.android.service.health.tracking.data.ValueKey$HeartRateSet
- com.samsung.android.service.health.tracking.data.ValueKey$AccelerometerSet
- com.samsung.android.service.health.tracking.data.DataPoint
