package com.example.util

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

object FirebaseInitHelper {
    private val lock = Any()

    private const val DEFAULT_APP_ID = "1:722513442785:android:66925a221fceeae03104a0"
    private const val DEFAULT_API_KEY = "AIzaSyBrC9rfGtGTxe0a3zpl5u0SVQEgHe0fFJk"
    private const val DEFAULT_PROJECT_ID = "backpack-9e1e0"
    private const val DEFAULT_GCM_SENDER_ID = "722513442785"
    private const val DEFAULT_STORAGE_BUCKET = "backpack-9e1e0.firebasestorage.app"

    fun ensureInitialized(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        if (FirebaseApp.getApps(appContext).isNotEmpty()) {
            return true
        }
        synchronized(lock) {
            if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                return true
            }

            // 1. Try standard default initialization from google-services metadata
            try {
                FirebaseApp.initializeApp(appContext)
                if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                    return true
                }
            } catch (e: IllegalStateException) {
                // Already initialized
                if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                    return true
                }
            } catch (e: Exception) {
                Log.w("FirebaseInitHelper", "Standard FirebaseApp.initializeApp failed", e)
            }

            // 2. Try explicit FirebaseOptions builder with resource or fallback constants
            try {
                val appId = readStringRes(appContext, "google_app_id").ifBlank { DEFAULT_APP_ID }
                val apiKey = readStringRes(appContext, "google_api_key").ifBlank { DEFAULT_API_KEY }
                val projectId = readStringRes(appContext, "project_id").ifBlank { DEFAULT_PROJECT_ID }
                val gcmSenderId = readStringRes(appContext, "gcm_defaultSenderId").ifBlank { DEFAULT_GCM_SENDER_ID }
                val storageBucket = readStringRes(appContext, "google_storage_bucket").ifBlank { DEFAULT_STORAGE_BUCKET }

                if (appId.isNotBlank() && apiKey.isNotBlank()) {
                    val options = FirebaseOptions.Builder()
                        .setApplicationId(appId)
                        .setApiKey(apiKey)
                        .setProjectId(projectId)
                        .setGcmSenderId(gcmSenderId)
                        .setStorageBucket(storageBucket)
                        .build()
                    FirebaseApp.initializeApp(appContext, options)
                    return true
                }
            } catch (e: IllegalStateException) {
                if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                    return true
                }
            } catch (e: Exception) {
                Log.e("FirebaseInitHelper", "Explicit FirebaseOptions initialization failed", e)
            }

            return FirebaseApp.getApps(appContext).isNotEmpty()
        }
    }

    private fun readStringRes(context: Context, name: String): String {
        return runCatching {
            val resId = context.resources.getIdentifier(name, "string", context.packageName)
            if (resId != 0) context.getString(resId).trim() else ""
        }.getOrDefault("")
    }
}
