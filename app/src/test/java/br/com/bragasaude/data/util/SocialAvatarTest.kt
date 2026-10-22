package br.com.bragasaude.data.util

import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.local.SocialPostEntity
import org.junit.Assert.*
import org.junit.Test

class SocialAvatarTest {
    private val photo = "https://api.bragasaude.online/api/files/avatars/autor/foto.jpg"
    private val post = SocialPostEntity(id = "post", userId = "autor", postType = "milestone", title = "Meta", userAvatarUrl = "foto-antiga")

    @Test fun acompanhaFotoCompartilhadaSemAtualizarFeed() {
        assertEquals(photo, post.withProfileAvatar(ProfileEntity(userId = "autor", customPhotoUri = photo)).userAvatarUrl)
    }
    @Test fun fotoLocalNaoSePassaPorFotoCompartilhada() {
        for (uri in listOf("content://galeria/foto", "file:///foto", "https://lh3.googleusercontent.com/foto")) {
            assertNull(post.withProfileAvatar(ProfileEntity(userId = "autor", customPhotoUri = uri)).userAvatarUrl)
        }
    }
    @Test fun remocaoNaoRessuscitaFotoAntigaDoPost() {
        assertNull(post.withProfileAvatar(ProfileEntity(userId = "autor")).userAvatarUrl)
    }
    @Test fun respeitaAvatarEscolhido() {
        assertEquals("Star", post.withProfileAvatar(ProfileEntity(userId = "autor", avatarIdentifier = "Star")).userAvatarIdentifier)
    }
    @Test fun naoAlteraFotoDeOutroAutor() {
        assertEquals(post, post.withProfileAvatar(ProfileEntity(userId = "outro", customPhotoUri = photo)))
    }
    @Test fun rejeitaOrigemParecidaETransporteSemTls() {
        assertNull(sharedSocialPhotoUrl("https://api.bragasaude.online.evil.test/api/files/avatars/a/f.jpg"))
        assertNull(sharedSocialPhotoUrl(photo.replace("https:", "http:")))
        assertNull(sharedSocialPhotoUrl("https://api.bragasaude.online/api/profile/autor"))
    }
}
