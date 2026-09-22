package br.com.bragasaude.util

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Test

class DeveloperModeDetectorDebugTest {
    @Test
    fun debugBuildNeverBlocksDeveloperMode() {
        assertFalse(DeveloperModeDetector.isDeveloperModeEnabled(mockk<Context>()))
    }
}
