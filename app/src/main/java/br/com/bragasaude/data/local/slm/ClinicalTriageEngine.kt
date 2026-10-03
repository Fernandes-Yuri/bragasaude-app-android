package br.com.bragasaude.data.local.slm

import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

enum class TriageSeverity { EMERGENCIA, URGENCIA, GRAVE, POUCO_URGENTE, NENHUMA }

data class ClinicalTriage(val severity: TriageSeverity, val reason: String = "")

/** Regras de sinalização, não diagnóstico. Nunca declara ausência de risco. */
@Singleton
class ClinicalTriageEngine @Inject constructor() {
    private fun patterns(vararg rules: String) = rules.map { Regex("\\b(?:$it)\\b") }
    private val red = patterns(
        "(?:dor|aperto|pressao) (?:muito |forte |intensa |aguda |insuportavel )?(?:no peito|toracica|no torax)",
        "(?:dor.{0,45}suor frio|suor frio.{0,45}dor)",
        "(?:perdi|perdeu|perda|sem) (?:subitamente |subita |de repente )?(?:a |de )?forca.{0,45}(?:lado|braco|perna)",
        "(?:paralisia|paralisado|paralisada|nao (?:consigo|consegue) mexer).{0,45}(?:lado|braco|perna)",
        "(?:um lado|lado (?:direito|esquerdo)).{0,35}(?:fraco|fraqueza|paralisado|sem forca)",
        "(?:fala enrolada|fala arrastada|dificuldade (?:para|de) falar|boca torta)",
        "(?:desmaiei|desmaiou|desmaio|inconsciente|perdi a consciencia|perdeu a consciencia|nao acorda)",
        "(?:convulsao|convulsionando|crise convulsiva)",
        "(?:falta de ar (?:forte|intensa|severa)|muita falta de ar|nao (?:consigo|consegue) respirar|sem conseguir respirar|sufocando|asfixia)",
        "(?:suspeita de (?:infarto|avc|derrame)|acho que.{0,25}(?:infarto|avc)|socorro|vomitando sangue)"
    )
    private val orange = patterns(
        "(?:cai(?! (?:na gargalhada|no sono|o preco))|caiu|caimos|queda(?! de (?:preco|rendimento))|levei (?:um )?tombo|levou (?:um )?tombo)",
        "(?:corte|cortei|ferida|sangramento).{0,45}(?:nao para|continuo|continuamente|muito sangue|sem parar)",
        "(?:bati|bateu|batida|pancada).{0,25}(?:cabeca)",
        "(?:fratura|fraturei|quebrei (?:o |a )?(?:braco|perna|osso)|osso quebrado)",
        "(?:queimadura (?:extensa|grande|grave)|queimei.{0,25}(?:corpo|muito))",
        "(?:engasguei|engasgou|engasgo|engasgado)"
    )
    private val yellow = patterns(
        "(?:vomitando|vomito|vomitei).{0,30}(?:varias vezes|sem parar|repetid|muito|de novo)",
        "(?:nao paro de vomitar|tontura forte|muita tontura|muito tonto|muito tonta|confusao mental|estou confuso|estou confusa)",
        "(?:falta de ar)"
    )
    private val green = patterns(
        "(?:dor de cabeca (?:leve|fraca)|leve dor de cabeca|azia|espirrando|espirro|nariz escorrendo|coriza|cansaco leve|um pouco cansado|um pouco cansada)",
        "(?:dor.{0,20}(?:costas|joelho|articulacoes).{0,25}(?:sempre|cronica|habitual)|(?:minha )?dor (?:cronica|de sempre))"
    )
    private val historical = Regex("\\b(?:ano passado|semana passada|ha (?:[0-9]+|um|dois|tres) (?:anos|meses|semanas)|quando era|historico de|ja passou|ja melhorou)\\b")
    private val educational = Regex("\\b(?:o que e|o que significa|quais (?:os )?sintomas|como prevenir|li sobre|me explique|se eu tiver)\\b")
    private val current = Regex("\\b(?:agora|estou|esta com|to com|tenho|sinto|sentindo|nao consigo|nao consegue|socorro)\\b")
    private val negative = Regex("\\b(?:nao (?:tenho|sinto|tive|estou com|teve|esta com)|sem|nunca tive|nem|nao)\\s+(?:(?:nenhuma?|mais|qualquer|uma?)\\s+)?$")
    private val negativeAfter = Regex("^\\s+(?:nao (?:tenho|sinto|esta)|nao aconteceu|nao ocorreu)\\b")

    private fun active(text: String, rules: List<Regex>): Boolean = rules.any { rule ->
        rule.findAll(text).any { match ->
            !negative.containsMatchIn(text.take(match.range.first)) &&
                !negativeAfter.containsMatchIn(text.drop(match.range.last + 1))
        }
    }

    fun classify(input: String): ClinicalTriage {
        val text = Normalizer.normalize(input.lowercase(java.util.Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ").trim()
        // Divide negações por oração, preservando vírgulas e pontos decimais das medidas.
        val clauses = text.split(Regex("[;!?]|(?<![0-9])[.,]|[.,](?![0-9])|\\b(?:mas|porem)\\b"))
            .filter { clause -> !(historical.containsMatchIn(clause) || educational.containsMatchIn(clause)) || current.containsMatchIn(clause) }
        val activeClauses = clauses.filterNot { Regex("\\b(?:se tivesse|se tiver|poderia ter)\\b").containsMatchIn(it) }
        if (activeClauses.any { active(it, red) }) return ClinicalTriage(TriageSeverity.EMERGENCIA, "Sinal de risco imediato")
        if (activeClauses.any { active(it, orange) }) return ClinicalTriage(TriageSeverity.URGENCIA, "Relato de acidente ou trauma")
        if (activeClauses.any { numericAlert(it) || active(it, yellow) }) return ClinicalTriage(TriageSeverity.GRAVE, "Sinal ou medida que merece avaliação hoje")
        if (activeClauses.any { active(it, green) }) return ClinicalTriage(TriageSeverity.POUCO_URGENTE, "Relato de desconforto cotidiano")
        return ClinicalTriage(TriageSeverity.NENHUMA)
    }

    private fun numericAlert(text: String): Boolean {
        // Consulta de histórico não é relato atual de uma medição.
        if (Regex("\\b(?:quanto|qual|historico|ontem|anteontem|semana|mes|ano)\\b").containsMatchIn(text)) return false
        if (Regex("\\b(?:nao|nunca|se|exemplo|normal|ideal)\\b").containsMatchIn(text)) return false
        Regex("\\bpressao.{0,30}?([0-9]{1,3})\\s*(?:por|x|/|sobre)\\s*([0-9]{1,3})\\b").find(text)?.let {
            val s = it.groupValues[1].toInt().let { n -> if (n in 5..25) n * 10 else n }
            val d = it.groupValues[2].toInt().let { n -> if (n in 3..15) n * 10 else n }
            if (s in 50..300 && d in 30..200 && s > d && (s >= 180 || d >= 110 || s < 90 || d < 60)) return true
        }
        Regex("\\b(?:glicemia|glicose|acucar no sangue).{0,25}?([0-9]{1,3})(?:\\s*mg)?\\b").find(text)?.let {
            val n = it.groupValues[1].toInt()
            // Hipoglicemia começa abaixo de 70 mg/dL, inclusive fora do jejum.
            if (n in 1..600 && (n < 70 || n >= 250)) return true
        }
        Regex("\\b(?:febre|temperatura).{0,20}?([0-9]{2}(?:[.,][0-9])?)").find(text)?.let {
            if (it.groupValues[1].replace(',', '.').toDouble() > 38.5) return true
        }
        return false
    }
}
