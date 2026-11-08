package br.com.bragasaude.ui.profile

import android.app.Activity
import android.app.Application
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import br.com.bragasaude.ui.theme.BragasaudeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "night")
class SystemBarsThemeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun barsUseTheAppsThemeIncludingTheBackgroundOutsideSafeDrawing() {
        var mode by mutableStateOf("LIGHT")
        var view: View? = null
        compose.setContent { BragasaudeTheme(forceThemeMode = mode) { view = LocalView.current } }
        fun window(): android.view.Window {
            var owner = requireNotNull(view).context
            while (owner is ContextWrapper && owner !is Activity) owner = owner.baseContext
            return (owner as Activity).window
        }
        compose.runOnIdle {
            val window = window()
            assertTrue(WindowCompat.getInsetsController(window, view!!).isAppearanceLightStatusBars)
            assertTrue(ColorUtils.calculateLuminance((window.decorView.background as ColorDrawable).color) > .8)
            mode = "DARK"
        }
        compose.runOnIdle {
            val window = window()
            assertFalse(WindowCompat.getInsetsController(window, view!!).isAppearanceLightStatusBars)
            assertTrue(ColorUtils.calculateLuminance((window.decorView.background as ColorDrawable).color) < .1)
        }
    }
}
