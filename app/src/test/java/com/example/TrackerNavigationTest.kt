package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.ui.AppScreen
import com.example.ui.DiagnosticViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrackerNavigationTest {

    @Test
    fun trackerTabNavigatesToPlaceholderScreen() {
        val viewModel = DiagnosticViewModel(ApplicationProvider.getApplicationContext())

        viewModel.navigateTo(AppScreen.TRACKER)

        assertEquals(AppScreen.TRACKER, viewModel.currentScreen.value)
    }
}
