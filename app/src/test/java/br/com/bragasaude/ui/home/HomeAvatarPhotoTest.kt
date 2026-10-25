package br.com.bragasaude.ui.home

import org.junit.Assert.*
import org.junit.Test

class HomeAvatarPhotoTest {
    @Test fun uploaded_photo_wins_even_without_google_opt_in() {
        assertEquals("https://app/avatar", resolveHomeAvatarPhoto("https://app/avatar", "https://google/avatar", false))
    }
    @Test fun google_fallback_requires_explicit_opt_in() {
        assertNull(resolveHomeAvatarPhoto(null, "https://google/avatar", false))
        assertEquals("https://google/avatar", resolveHomeAvatarPhoto(" ", "https://google/avatar", true))
        assertNull(resolveHomeAvatarPhoto("", " ", true))
    }
}
