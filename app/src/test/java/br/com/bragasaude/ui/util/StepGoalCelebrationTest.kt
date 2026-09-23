package br.com.bragasaude.ui.util

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StepGoalCelebrationTest {

    private val context = mockk<Context>(relaxed = true)
    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    private val storedPrefs = mutableMapOf<String, String>()

    @Before
    fun setup() {
        storedPrefs.clear()
        every { context.getSharedPreferences("braga_movement_prefs", Context.MODE_PRIVATE) } returns prefs
        every { prefs.getString(any(), any()) } answers {
            val key = firstArg<String>()
            val defaultVal = secondArg<String>()
            storedPrefs[key] ?: defaultVal
        }
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<String>()
            storedPrefs[key] = value
            editor
        }
        every { editor.apply() } just Runs

        mockkObject(NotificationHelper)
        every { NotificationHelper.checkAndNotifyStepGoal(any(), any(), any()) } answers { callOriginal() }
        every { NotificationHelper.sendStepGoal50Notification(any()) } just Runs
        every { NotificationHelper.sendStepGoal100Notification(any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `steps below 50 percent triggers no notification`() {
        NotificationHelper.checkAndNotifyStepGoal(context, currentSteps = 3000, targetSteps = 8000)

        verify(exactly = 0) { NotificationHelper.sendStepGoal50Notification(any()) }
        verify(exactly = 0) { NotificationHelper.sendStepGoal100Notification(any()) }
    }

    @Test
    fun `steps reaching 50 percent triggers 50 percent notification once today`() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        NotificationHelper.checkAndNotifyStepGoal(context, currentSteps = 4000, targetSteps = 8000)

        verify(exactly = 1) { NotificationHelper.sendStepGoal50Notification(context) }
        verify(exactly = 0) { NotificationHelper.sendStepGoal100Notification(any()) }
        assertEquals(today, storedPrefs["step_goal_50_notified_date"])

        // Second step increment on same day
        NotificationHelper.checkAndNotifyStepGoal(context, currentSteps = 4500, targetSteps = 8000)
        verify(exactly = 1) { NotificationHelper.sendStepGoal50Notification(context) }
    }

    @Test
    fun `steps reaching 100 percent triggers 100 percent notification once today`() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        NotificationHelper.checkAndNotifyStepGoal(context, currentSteps = 8000, targetSteps = 8000)

        verify(exactly = 1) { NotificationHelper.sendStepGoal100Notification(context) }
        assertEquals(today, storedPrefs["step_goal_100_notified_date"])
        assertEquals(today, storedPrefs["step_goal_50_notified_date"])

        // Second step increment on same day
        NotificationHelper.checkAndNotifyStepGoal(context, currentSteps = 8500, targetSteps = 8000)
        verify(exactly = 1) { NotificationHelper.sendStepGoal100Notification(context) }
        verify(exactly = 0) { NotificationHelper.sendStepGoal50Notification(any()) }
    }

    @Test
    fun `invalid target or current steps does nothing`() {
        NotificationHelper.checkAndNotifyStepGoal(context, currentSteps = 0, targetSteps = 8000)
        NotificationHelper.checkAndNotifyStepGoal(context, currentSteps = 5000, targetSteps = 0)
        NotificationHelper.checkAndNotifyStepGoal(context, currentSteps = -10, targetSteps = -5)

        verify(exactly = 0) { NotificationHelper.sendStepGoal50Notification(any()) }
        verify(exactly = 0) { NotificationHelper.sendStepGoal100Notification(any()) }
    }
}
