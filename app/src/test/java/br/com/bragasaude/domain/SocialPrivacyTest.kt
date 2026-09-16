package br.com.bragasaude.domain

import org.junit.Test
import org.junit.Assert.assertFalse

class SocialPrivacyTest {

    // Lista de frases reais que o sistema pode gerar (hardcoded para teste)
    private val generatedPosts = listOf(
        "Fulano bateu a meta de passos hoje! 👣",
        "conquistou um novo marco: Hidratação de Ouro! 🎊",
        "subiu para o Nível 3! Parabéns pela dedicação! 🚀",
        "completou uma sequência de 7 dias se cuidando! 🔥"
    )

    // Regex para detectar dados clínicos identificáveis
    private val clinicalDataRegex = listOf(
        """\d{2,3}/\d{2,3}""".toRegex(), // Pressão 120/80
        """\d+\s?mg/dL""".toRegex(),    // Glicose 100 mg/dL
        """\d+\s?mmHg""".toRegex(),     // Pressão isolada
        """\d+\s?bpm""".toRegex(),      // Batimentos
        """\d+\s?%""".toRegex(),        // SpO2
        """\d+\s?kg""".toRegex()         // Peso
    )

    @Test
    fun `ensure social posts do not contain sensitive clinical data`() {
        generatedPosts.forEach { post ->
            clinicalDataRegex.forEach { regex ->
                assertFalse(
                    "O post '$post' contém dados clínicos sensíveis capturados pela regex $regex",
                    regex.containsMatchIn(post)
                )
            }
        }
    }

    @Test
    fun `ensure templates fail if they mention specific medical values`() {
        val badPost = "Fulano registrou pressão de 140/90 hoje"
        val hasMatch = clinicalDataRegex.any { it.containsMatchIn(badPost) }
        assert(hasMatch) { "A regex de segurança deveria ter capturado o dado clínico no post '$badPost'" }
    }
}
