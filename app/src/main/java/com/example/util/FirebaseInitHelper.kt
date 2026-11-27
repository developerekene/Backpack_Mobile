package com.example.util

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

object FirebaseInitHelper {
    private val lock = Any()

    fun ensureInitialized(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        if (FirebaseApp.getApps(appContext).isNotEmpty()) {
            return true
        }
        synchronized(lock) {
            if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                return true
            }
            val defaultInit = runCatching {
                FirebaseApp.initializeApp(appContext)
            }.onFailure { err ->
                Log.w("FirebaseInitHelper", "Standard FirebaseApp.initializeApp failed, trying resource fallback", err)
            }.getOrNull()

            if (defaultInit != null || FirebaseApp.getApps(appContext).isNotEmpty()) {
                return true
            }

            return runCatching {
                val appId = readStringRes(appContext, "google_app_id")
                val apiKey = readStringRes(appContext, "google_api_key")
                val projectId = readStringRes(appContext, "project_id")
                val gcmSenderId = readStringRes(appContext, "gcm_defaultSenderId")
                val storageBucket = readStringRes(appContext, "google_storage_bucket")

                if (appId.isNotBlank() && apiKey.isNotBlank()) {
                    val builder = FirebaseOptions.Builder()
                        .setApplicationId(appId)
                        .setApiKey(apiKey)
                    if (projectId.isNotBlank()) builder.setProjectId(projectId)
                    if (gcmSenderId.isNotBlank()) builder.setGcmSenderId(gcmSenderId)
                    if (storageBucket.isNotBlank()) builder.setStorageBucket(storageBucket)
                    FirebaseApp.initializeApp(appContext, builder.build())
                    true
                } else {
                    false
                }
            }.onFailure { err ->
                Log.e("FirebaseInitHelper", "Fallback FirebaseApp initialization failed", err)
            }.getOrDefault(false)
        }
    }

    private fun readStringRes(context: Context, name: String): String {
        return runCatching {
            val resId = context.resources.getIdentifier(name, "string", context.packageName)
            if (resId != 0) context.getString(resId).trim() else ""
        }.getOrDefault("")
    }
}
