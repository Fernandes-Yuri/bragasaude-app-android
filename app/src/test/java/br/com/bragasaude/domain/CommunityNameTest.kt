package br.com.bragasaude.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CommunityNameTest {
    @Test fun prefereNomeComunitario() {
        assertEquals("Yuri Saúde", communityDisplayName("Yuri Fernandes", "Yuri Saúde"))
    }
    @Test fun abreviaNomeCivil() {
        assertEquals("Yuri F.", communityDisplayName("  Yuri   Fernandes do Nascimento  "))
        assertEquals("Ana", communityDisplayName("Ana", ""))
        assertEquals("Colega de Saúde", communityDisplayName(null))
    }
}
