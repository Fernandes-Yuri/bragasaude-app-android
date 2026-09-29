package br.com.bragasaude.data.util

import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.local.SocialPostEntity
import org.junit.Assert.*
import org.junit.Test

class SocialAvatarTest {
    private val post = SocialPostEntity(id = "post", userId = "autor", postType = "milestone", title = "Meta", userAvatarUrl = "foto-antiga")

    @Test fun acompanhaFotoLocalSemAtualizarFeed() {
        val profile = ProfileEntity(userId = "autor", customPhotoUri = "foto-nova")
        assertEquals("foto-nova", post.withProfileAvatar(profile, "google").userAvatarUrl)
    }
    @Test fun usaMesmaFotoGoogleDoPerfil() {
        assertEquals("google", post.withProfileAvatar(ProfileEntity(userId = "autor"), "google").userAvatarUrl)
    }
    @Test fun remocaoNaoRessuscitaFotoAntigaDoPost() {
        assertNull(post.withProfileAvatar(ProfileEntity(userId = "autor"), null).userAvatarUrl)
    }
    @Test fun respeitaAvatarEscolhido() {
        assertEquals("Star", post.withProfileAvatar(ProfileEntity(userId = "autor", avatarIdentifier = "Star"), "google").userAvatarIdentifier)
    }
    @Test fun naoAlteraFotoDeOutroAutor() {
        assertEquals(post, post.withProfileAvatar(ProfileEntity(userId = "outro", customPhotoUri = "outra"), "google"))
    }
}
