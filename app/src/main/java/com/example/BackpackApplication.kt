package com.example

import android.app.Application
import com.example.util.FirebaseInitHelper
import com.example.util.NotificationHelper

class BackpackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseInitHelper.ensureInitialized(this)
        NotificationHelper.createNotificationChannel(this)
    }
}
