package com.tiagoviana.llmbenchmark

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.tiagoviana.llmbenchmark.ui.MainScreen
import androidx.test.core.app.ApplicationProvider
import android.app.Application

@RunWith(AndroidJUnit4::class)
class ComposeCrashTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun clickStart_doesNotCrash() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = BenchmarkViewModel(app)
        
        composeTestRule.setContent {
            MainScreen(viewModel = viewModel)
        }
        
        // Ensure UI is idle
        composeTestRule.waitForIdle()
        
        // Find the start button and click it
        composeTestRule.onNodeWithText("Start Benchmark").performClick()
        
        // Ensure UI is idle again
        composeTestRule.waitForIdle()
        
        // If we reach here, no crash happened!
        println("Test finished without crashing!")
    }
}