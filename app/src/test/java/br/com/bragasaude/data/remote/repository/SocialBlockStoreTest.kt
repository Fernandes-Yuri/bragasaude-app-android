package br.com.bragasaude.data.remote.repository

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SocialBlockStoreTest {
    private val context = mockk<Context>()
    private val prefs = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>()
    private val values = mutableMapOf<String, Set<String>>()
    init {
        every { context.getSharedPreferences("social_blocks", Context.MODE_PRIVATE) } returns prefs
        every { prefs.getStringSet(any(), any()) } answers { values[firstArg()] ?: emptySet() }
        every { prefs.edit() } returns editor
        every { editor.putStringSet(any(), any()) } answers {
            values[firstArg()] = secondArg<Set<String>>().toSet(); editor
        }
        every { editor.remove(any()) } answers { values.remove(firstArg()); editor }
        every { editor.commit() } returns true
    }
    @Test fun `block survives reopen and is isolated by account`() = runBlocking {
        SocialBlockStore(context).block("account-a", "author")
        val reopened = SocialBlockStore(context)
        assertEquals(setOf("author"), reopened.observe("account-a").first())
        assertTrue(reopened.observe("account-b").first().isEmpty())
        reopened.block("account-b", "another-author")
        reopened.clear("account-a")
        assertTrue(reopened.observe("account-a").first().isEmpty())
        assertEquals(setOf("another-author"), reopened.observe("account-b").first())
    }
    @Test fun `cannot block self`() = runBlocking {
        assertTrue(runCatching { SocialBlockStore(context).block("account", "account") }.exceptionOrNull() is IllegalArgumentException)
        verify(exactly = 0) { editor.commit() }
    }
    @Test fun `failed write is not reported as success`() = runBlocking {
        every { editor.commit() } returns false
        assertTrue(runCatching { SocialBlockStore(context).block("account", "author") }.exceptionOrNull() is IllegalStateException)
    }
}
