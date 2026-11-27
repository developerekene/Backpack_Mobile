package com.example

import android.app.Application
import android.content.Context
import com.example.util.FirebaseInitHelper
import com.example.util.NotificationHelper

class BackpackApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        FirebaseInitHelper.ensureInitialized(base)
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseInitHelper.ensureInitialized(this)
        NotificationHelper.createNotificationChannel(this)
    }
}
