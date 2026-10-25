package br.com.bragasaude.data.local

import br.com.bragasaude.data.local.smoking.SmokingSupportSession
import br.com.bragasaude.util.BragaConstants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test

class SmokingSupportSessionTest {
    @Test fun guestAnonymousAndClosingSessionCannotWriteSensitiveDiary() {
        val auth = mockk<FirebaseAuth>()
        val user = mockk<FirebaseUser>()
        every { auth.currentUser } returns user
        every { user.uid } returns "A"
        every { user.isAnonymous } returns false
        val session = SmokingSupportSession(auth)
        assertEquals("A", session.currentUserId())
        session.closeForCleanup()
        assertNull(session.currentUserId())
        every { user.uid } returns "B"
        assertEquals("B", session.currentUserId())
        every { user.isAnonymous } returns true
        assertNull(session.currentUserId())
        every { user.isAnonymous } returns false
        every { user.uid } returns BragaConstants.GUEST_UID
        assertNull(session.currentUserId())
        every { auth.currentUser } returns null
        assertNull(session.currentUserId())
    }
}
