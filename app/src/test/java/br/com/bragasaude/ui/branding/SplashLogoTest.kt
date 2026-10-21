package br.com.bragasaude.ui.branding

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import br.com.bragasaude.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 30, 31, 35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SplashLogoTest {
    @Test fun launch_icon_preserves_transparent_corners_inside_the_platform_safe_circle() {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Application>(), R.style.Theme_Bragasaude_Splash)
        val attributes = context.obtainStyledAttributes(intArrayOf(
            androidx.core.splashscreen.R.attr.windowSplashScreenAnimatedIcon,
            androidx.core.splashscreen.R.attr.windowSplashScreenIconBackgroundColor
        ))
        val iconId = attributes.getResourceId(0, 0)
        assertNotEquals(0, iconId)
        assertEquals(Color.TRANSPARENT, attributes.getColor(1, Color.MAGENTA))
        attributes.recycle()
        val drawable = requireNotNull(ContextCompat.getDrawable(context, iconId))
        val bitmap = Bitmap.createBitmap(288, 288, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 288, 288)
        drawable.draw(Canvas(bitmap))
        assertEquals("The rounded corner must remain transparent", 0, Color.alpha(bitmap.getPixel(74, 74)))
        assertTrue("The brand mark must be visible", Color.alpha(bitmap.getPixel(144, 144)) > 0)
        var opaque = 0
        for (y in 0 until 288) for (x in 0 until 288) {
            if (Color.alpha(bitmap.getPixel(x, y)) > 0) {
                opaque++
                val dx = x + 0.5 - 144
                val dy = y + 0.5 - 144
                assertTrue("Pixel $x,$y would be clipped by the splash mask", dx * dx + dy * dy <= 96.0 * 96.0)
            }
        }
        assertTrue("The icon must contain the full mark", opaque > 10000)
        val file = File("build/outputs/nutrition-review/splash-api-${RuntimeEnvironment.getApiLevel()}.png")
        requireNotNull(file.parentFile).mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
