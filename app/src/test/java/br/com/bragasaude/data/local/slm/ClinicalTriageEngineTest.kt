package br.com.bragasaude.data.local.slm

import org.junit.Assert.*
import org.junit.Test

class ClinicalTriageEngineTest {
    private val engine = ClinicalTriageEngine()
    private fun expect(severity: TriageSeverity, vararg phrases: String) = phrases.forEach {
        assertEquals(it, severity, engine.classify(it).severity)
    }
    @Test fun redFlagsHavePriorityAcrossClauses() = expect(TriageSeverity.EMERGENCIA,
        "dor forte no peito depois que bebi água", "suor frio e dor no peito", "não consigo respirar",
        "minha mãe caiu e está inconsciente", "perda súbita de força de um lado do corpo",
        "estou com fala enrolada de repente", "meu pai está convulsionando", "desmaiei",
        "não tenho azia, mas tenho dor no peito")
    @Test fun accidentsRemainOrangeWithoutRedFlags() = expect(TriageSeverity.URGENCIA,
        "caí no chão", "levei um tombo", "minha mãe caiu e bateu a cabeça",
        "cortei a mão e o sangramento não para", "acho que tenho uma fratura",
        "queimadura extensa", "engasguei agora")
    @Test fun concerningSymptomsAndNumericThresholds() = expect(TriageSeverity.GRAVE,
        "minha pressão deu 18 por 11", "pressão 190/80", "pressão 120x115", "pressão 80 por 55",
        "glicemia deu 59 em jejum", "glicose deu 69", "glicose 250", "febre de 38,6 persistente",
        "temperatura 39.2", "vomitando várias vezes", "tontura forte ao levantar", "confusão mental leve")
    @Test fun mildSymptomsRemainGreen() = expect(TriageSeverity.POUCO_URGENTE,
        "dor de cabeça leve", "estou com azia", "minha dor no joelho de sempre",
        "cansaço leve", "estou espirrando", "nariz escorrendo")
    @Test fun negatedEducationalHistoricalAndSocialInputsDoNotTriggerAlerts() = expect(TriageSeverity.NENHUMA,
        "não tenho dor no peito", "não sinto falta de ar", "não caí", "sem azia",
        "o que é infarto?", "o que é desmaio?", "li sobre convulsão",
        "tive um desmaio há dois anos", "qual foi minha glicose 300 ontem?",
        "minha pressão deu 12 por 8", "glicose 70", "glicose 249", "febre 38,5",
        "estou com saudade dos meus netos", "o dia está bonito", "caí na gargalhada")
}
