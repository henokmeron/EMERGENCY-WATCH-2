package com.example.emergencywatch.domain.sensor

enum class SensorType(val keyName: String) {
    HEART_RATE("heart_rate"),
    ACCELEROMETER("accelerometer"),
    PPG("ppg"),
    SKIN_TEMPERATURE("skin_temperature"),
    SPO2("spo2"),
    ECG("ecg"),
    EDA("eda"),
    BIA("bia")
}
