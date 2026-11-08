package br.com.bragasaude.ui.profile

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import br.com.bragasaude.ui.util.OnDeviceVoiceInputLauncher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class VoiceFeedbackTest {
    @Test fun permissionFailureIsDeliveredToTheFormWithoutNeedingARecognizerOrDevice() = runTest {
        var feedback = ""
        val launcher = OnDeviceVoiceInputLauncher(ApplicationProvider.getApplicationContext(), this) { fail("Nenhum valor reconhecido") }
        launcher.onFeedback = { feedback = it }
        launcher.permissionResult(false)
        assertTrue(feedback.isNotBlank())
        assertTrue(feedback.contains("microfone", ignoreCase = true) || feedback.contains("permiss", ignoreCase = true))
        launcher.cancel()
    }
}
