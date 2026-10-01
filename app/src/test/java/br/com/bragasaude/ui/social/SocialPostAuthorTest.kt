package br.com.bragasaude.ui.social

import br.com.bragasaude.data.local.ProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SocialPostAuthorTest {
    @Test fun `previa herda nome comunitario foto personalizada avatar e nivel`() {
        val profile = ProfileEntity(userId = "usuario", fullName = "Yuri Fernandes dos Santos",
            customPhotoUri = "content://foto", avatarIdentifier = "Favorite", currentLevel = 8)
        val author = resolveSocialPostAuthor(profile, "Conta", "https://foto-google", "Yuri FJS")
        assertEquals("Yuri FJS", author.name)
        assertEquals("content://foto", author.photoUrl)
        assertEquals("Favorite", author.avatarIdentifier)
        assertEquals(8, author.level)
    }

    @Test fun `sem apelido respeita nome abreviado e usa foto da conta como alternativa`() {
        val profile = ProfileEntity(userId = "usuario", fullName = "Yuri Fernandes dos Santos")
        val author = resolveSocialPostAuthor(profile, "Conta", "https://foto-google", "")
        assertEquals("Yuri F.", author.name)
        assertEquals("https://foto-google", author.photoUrl)
    }

    @Test fun `perfil ainda indisponivel usa identidade da conta`() {
        val author = resolveSocialPostAuthor(null, "Ana Maria Silva", "https://foto-google", "")
        assertEquals("Ana M.", author.name)
        assertEquals("https://foto-google", author.photoUrl)
    }
}
