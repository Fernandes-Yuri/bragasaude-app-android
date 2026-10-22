package br.com.bragasaude.ui.social

import br.com.bragasaude.data.local.ProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SocialPostAuthorTest {
    @Test fun `previa herda nome comunitario foto personalizada e avatar`() {
        val profile = ProfileEntity(userId = "usuario", fullName = "Yuri Fernandes dos Santos",
            customPhotoUri = "https://api.bragasaude.online/api/files/avatars/usuario/foto.jpg", avatarIdentifier = "Favorite")
        val author = resolveSocialPostAuthor(profile, "Conta", "Yuri FJS")
        assertEquals("Yuri FJS", author.name)
        assertEquals("https://api.bragasaude.online/api/files/avatars/usuario/foto.jpg", author.photoUrl)
        assertEquals("Favorite", author.avatarIdentifier)
    }

    @Test fun `sem apelido respeita nome abreviado sem simular foto compartilhada`() {
        val profile = ProfileEntity(userId = "usuario", fullName = "Yuri Fernandes dos Santos")
        val author = resolveSocialPostAuthor(profile, "Conta", "")
        assertEquals("Yuri F.", author.name)
        assertNull(author.photoUrl)
    }

    @Test fun `perfil ainda indisponivel usa identidade da conta`() {
        val author = resolveSocialPostAuthor(null, "Ana Maria Silva", "")
        assertEquals("Ana M.", author.name)
        assertNull(author.photoUrl)
    }

    @Test fun `nivel legado do perfil nao interfere na autoria da postagem`() {
        val profile = ProfileEntity(userId = "usuario", fullName = "Yuri Fernandes",
            customPhotoUri = "https://api.bragasaude.online/api/files/avatars/usuario/foto.jpg", avatarIdentifier = "Favorite", currentLevel = 1)
        val author = resolveSocialPostAuthor(profile, "Conta", "Yuri FJS")
        val authorWithLegacyLevel = resolveSocialPostAuthor(profile.copy(currentLevel = 8),
            "Conta", "Yuri FJS")
        assertEquals(author, authorWithLegacyLevel)
    }
}
