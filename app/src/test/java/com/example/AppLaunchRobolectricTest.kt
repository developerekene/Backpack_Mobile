package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.util.FirebaseInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = BackpackApplication::class)
class AppLaunchRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `application initializes firebase and main activity launches cleanly past splash to auth`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(app is Application)
        assertTrue(FirebaseInitHelper.ensureInitialized(app))
        assertNotNull(composeRule.activity)
        assertEquals("Backpack", app.getString(R.string.app_name))

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("splash_screen").assertIsDisplayed()

        // Advance past the 600ms splash screen delay
        composeRule.mainClock.advanceTimeBy(1000L)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("auth_email_input").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_submit_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sign In to Backpack").assertIsDisplayed()
    }
}
