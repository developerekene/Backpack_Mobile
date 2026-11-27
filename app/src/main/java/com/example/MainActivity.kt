package com.example

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.AuthState
import com.example.ui.auth.AuthViewModel
import com.example.ui.capture.CaptureViewModel
import com.example.ui.courses.CoursesViewModel
import com.example.ui.home.HomeViewModel
import com.example.ui.main.MainScreen
import com.example.ui.profile.ProfileAlertsViewModel
import com.example.ui.progress.ProgressViewModel
import com.example.ui.splash.SplashScreen
import com.example.ui.submissions.SubmissionsViewModel
import com.example.ui.theme.BackpackTheme
import com.example.util.FirebaseInitHelper
import com.example.util.NotificationHelper

class MainActivity : ComponentActivity() {
    private val pendingDestination = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseInitHelper.ensureInitialized(applicationContext)
        NotificationHelper.createNotificationChannel(applicationContext)
        pendingDestination.value = intent?.getStringExtra(NotificationHelper.EXTRA_OPEN_DESTINATION)

        enableEdgeToEdge()

        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkMode by rememberSaveable { mutableStateOf(systemDark) }
            var isSplashVisible by rememberSaveable { mutableStateOf(true) }
            val destination by pendingDestination

            BackpackTheme(darkTheme = isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (isSplashVisible) {
                        SplashScreen(
                            onSplashFinished = { isSplashVisible = false }
                        )
                    } else {
                        BackpackApp(
                            application = application,
                            isDarkMode = isDarkMode,
                            onToggleTheme = { isDarkMode = !isDarkMode },
                            initialDestination = destination,
                            onDestinationHandled = { pendingDestination.value = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val dest = intent.getStringExtra(NotificationHelper.EXTRA_OPEN_DESTINATION)
        if (!dest.isNullOrBlank()) {
            pendingDestination.value = dest
        }
    }
}

@Composable
fun BackpackApp(
    application: Application,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    initialDestination: String? = null,
    onDestinationHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val factory = remember(application) {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }
    val authViewModel: AuthViewModel = viewModel(factory = factory)
    val authState by authViewModel.authState.collectAsState()
    val sessionToken by authViewModel.sessionToken.collectAsState()

    when (val state = authState) {
        is AuthState.Authenticated -> {
            key(state.user.userId, sessionToken) {
                val sessionStoreOwner = remember(state.user.userId, sessionToken) {
                    object : ViewModelStoreOwner {
                        override val viewModelStore = ViewModelStore()
                    }
                }

                DisposableEffect(sessionStoreOwner) {
                    onDispose {
                        sessionStoreOwner.viewModelStore.clear()
                    }
                }

                CompositionLocalProvider(LocalViewModelStoreOwner provides sessionStoreOwner) {
                    val homeViewModel: HomeViewModel = viewModel(factory = factory)
                    val coursesViewModel: CoursesViewModel = viewModel(factory = factory)
                    val captureViewModel: CaptureViewModel = viewModel(factory = factory)
                    val profileAlertsViewModel: ProfileAlertsViewModel = viewModel(factory = factory)
                    val progressViewModel: ProgressViewModel = viewModel(factory = factory)
                    val submissionsViewModel: SubmissionsViewModel = viewModel(factory = factory)

                    val liveProfile by homeViewModel.userProfile.collectAsState()
                    val effectiveProfile = if (liveProfile != null && liveProfile?.userId == state.user.userId) {
                        liveProfile!!
                    } else {
                        state.user
                    }

                    MainScreen(
                        homeViewModel = homeViewModel,
                        coursesViewModel = coursesViewModel,
                        captureViewModel = captureViewModel,
                        profileAlertsViewModel = profileAlertsViewModel,
                        progressViewModel = progressViewModel,
                        submissionsViewModel = submissionsViewModel,
                        userProfile = effectiveProfile,
                        isDarkMode = isDarkMode,
                        onToggleTheme = onToggleTheme,
                        initialDestination = initialDestination,
                        onDestinationHandled = onDestinationHandled,
                        onSignOut = {
                            NotificationHelper.clearAllNotifications(context)
                            sessionStoreOwner.viewModelStore.clear()
                            authViewModel.signOut()
                        }
                    )
                }
            }
        }
        else -> {
            AuthScreen(
                authViewModel = authViewModel,
                onAuthSuccess = {
                    if (authViewModel.authState.value !is AuthState.Authenticated) {
                        authViewModel.checkCurrentUser()
                    }
                }
            )
        }
    }
}
