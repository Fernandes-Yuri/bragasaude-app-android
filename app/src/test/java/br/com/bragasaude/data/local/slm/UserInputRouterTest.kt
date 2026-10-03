package br.com.bragasaude.data.local.slm

import br.com.bragasaude.domain.VoiceHealthParser
import org.junit.Assert.*
import org.junit.Test

class UserInputRouterTest {
    private val router = UserInputRouter(BragaIntentRouter(VoiceHealthParser()), ClinicalTriageEngine(), ResponseRotator())
    @Test fun routesFourCategoriesAndPrioritizesClinicalRisk() {
        assertEquals(UserInputCategory.TRIAGEM_MEDICA, router.route("bebi água e estou com dor no peito").category)
        assertEquals(UserInputCategory.TRIAGEM_MEDICA, router.route("minha glicose deu 55").category)
        assertEquals(UserInputCategory.INSERCAO_DADOS, router.route("bebi 300ml de água").category)
        assertEquals(UserInputCategory.INSERCAO_DADOS, router.route("tomei a Losartana agora").category)
        assertEquals(UserInputCategory.CONSULTA_DADOS, router.route("quanto de água já bebi hoje?").category)
        assertEquals(UserInputCategory.CONSULTA_DADOS, router.route("eu já tomei o remédio hoje?").category)
        assertEquals(UserInputCategory.CONVERSA_LIVRE, router.route("estou me sentindo sozinho").category)
        assertNull(router.immediateResponse("o dia está bonito"))
    }
}
