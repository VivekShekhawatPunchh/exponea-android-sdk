package com.exponea.sdk.manager

import android.app.NotificationManager
import com.exponea.sdk.models.NotificationData

interface FcmManager {
    fun showNotification(title: String, message: String, data: NotificationData, id: Int, manager: NotificationManager)
    fun createNotificationChannel(manager: NotificationManager)
}