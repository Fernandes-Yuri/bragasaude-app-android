package br.com.bragasaude.data.local

import android.content.SharedPreferences
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test

class AlertReadStateStoreTest {
    private val values = mutableMapOf<String, Set<String>>("dismissed" to setOf("aviso dispensado"))
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
    private val timestamps = mutableMapOf<String, Long>()
    private val preferences = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>()

    init {
        every { preferences.getStringSet(any(), any()) } answers { values[firstArg()] ?: emptySet() }
        every { preferences.getLong(any(), any()) } answers { timestamps[firstArg()] ?: secondArg<Long>() }
        every { preferences.edit() } returns editor
        every { editor.putStringSet(any(), any()) } answers {
            val key = firstArg<String>()
            values[key] = secondArg<Set<String>>().toSet()
            listeners.toList().forEach { it.onSharedPreferenceChanged(preferences, key) }
            editor
        }
        every { editor.apply() } just Runs
        every { editor.putLong(any(), any()) } answers {
            timestamps[firstArg()] = secondArg()
            editor
        }
        every { preferences.registerOnSharedPreferenceChangeListener(any()) } answers { listeners.add(firstArg()); Unit }
        every { preferences.unregisterOnSharedPreferenceChangeListener(any()) } answers { listeners.remove(firstArg()); Unit }
    }

    @Test fun `ler preserva os descartes e permanece apos reabrir`() {
        val store = AlertReadStateStore(preferences, "read_user_today")
        store.markRead(setOf("beber água"))
        store.close()
        val reopened = AlertReadStateStore(preferences, "read_user_today")
        assertEquals(setOf("beber água"), reopened.readAlerts.value)
        assertEquals(setOf("aviso dispensado"), values["dismissed"])
        verify(exactly = 0) { editor.putStringSet("dismissed", any()) }
        reopened.close()
        assertTrue(listeners.isEmpty())
    }

    @Test fun `leitura atualiza outra tela sem perder avisos ja lidos`() {
        val home = AlertReadStateStore(preferences, "read_user_today")
        val inbox = AlertReadStateStore(preferences, "read_user_today")
        home.markRead(setOf("primeiro"))
        inbox.markRead(setOf("segundo"))
        assertEquals(setOf("primeiro", "segundo"), home.readAlerts.value)
        assertEquals(home.readAlerts.value, inbox.readAlerts.value)
        home.close()
        inbox.close()
    }

    @Test fun `leitura fica isolada por usuario e dia`() {
        val first = AlertReadStateStore(preferences, "read_user_today")
        val otherUser = AlertReadStateStore(preferences, "read_other_today")
        val tomorrow = AlertReadStateStore(preferences, "read_user_tomorrow")
        first.markRead(setOf("aviso"))
        assertTrue(otherUser.readAlerts.value.isEmpty())
        assertTrue(tomorrow.readAlerts.value.isEmpty())
        first.close()
        otherUser.close()
        tomorrow.close()
    }

    @Test fun `recalculo preserva horario da primeira identificacao`() {
        val store = AlertReadStateStore(preferences, "read_user_today")
        store.identify(setOf("aviso"), now = 1000L)
        store.identify(setOf("aviso", "novo"), now = 2000L)
        assertEquals(1000L, store.identifiedAt.value["aviso"])
        assertEquals(2000L, store.identifiedAt.value["novo"])
        store.close()
        val reopened = AlertReadStateStore(preferences, "read_user_today")
        reopened.identify(setOf("aviso"), now = 3000L)
        assertEquals(1000L, reopened.identifiedAt.value["aviso"])
        reopened.close()
    }
}
