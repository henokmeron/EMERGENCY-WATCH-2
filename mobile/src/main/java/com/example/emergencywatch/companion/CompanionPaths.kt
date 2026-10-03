package com.example.emergencywatch.companion

/**
 * Shared Wear OS Data Layer path contracts between :wear and :mobile.
 * Keep these strings identical in both modules.
 */
object CompanionPaths {
    const val STATUS_PATH = "/emergency_watch/status"
    const val STATUS_REQUEST_PATH = "/emergency_watch/status_request"
    const val PAIR_REQUEST_PATH = "/emergency_watch/pair_request"
    const val PAIR_RESULT_PATH = "/emergency_watch/pair_result"
    const val FORCE_SYNC_PATH = "/emergency_watch/force_sync"

    const val KEY_PAIRED = "paired"
    const val KEY_BATTERY = "battery"
    const val KEY_HEART_RATE = "heart_rate"
    const val KEY_CONTACT = "contact_status"
    const val KEY_QUEUED = "queued_readings"
    const val KEY_LAST_UPLOAD = "last_upload"
    const val KEY_SENSOR_SOURCE = "sensor_source"
    const val KEY_CAPABILITIES = "sensor_capabilities_json"
    const val KEY_SCREEN = "current_screen"
    const val KEY_SOS_ACTIVE = "sos_active"
    const val KEY_STATUS_MESSAGE = "status_message"
    const val KEY_PATIENT_ID = "patient_id"
    const val KEY_DEVICE_ID = "device_id"
    const val KEY_PATIENT_NAME = "patient_name"
    const val KEY_CLOUD_OK = "cloud_ok"
    const val KEY_UPDATED_AT = "updated_at_epoch"
    const val KEY_APP_VERSION = "app_version"

    const val KEY_PAIR_CODE = "code"
    const val KEY_PAIR_OK = "ok"
    const val KEY_PAIR_MESSAGE = "message"
}
