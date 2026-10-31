package br.com.bragasaude.ai


enum class BragaRoute { BLOCKED, EMERGENCY, HEALTH_MEMORY, LOCAL_ACTION, LOCAL_CONVERSATION, CLARIFICATION, CLOUD }

/** Decisões locais explicáveis, sem modelo remoto para classificar a entrada. */
internal object BragaRoutingPolicy {
    const val MENTAL_HEALTH_EMERGENCY = "emergencia_saude_mental"
    fun normalize(text: String) = BragaLanguageRecovery.recognize(text)

    private val health = Regex("""\b(saude|medic\w*|remedio\w*|comprimido\w*|dose|tratamento\w*|pressao|glic\w*|diabet\w*|acucar|sangue|renal|rins|rim|cardi\w*|coracao|apneia|hormon\w*|menopausa|anemia|enxaqueca|muscular|exames?|contraindic\w*|efeitos? colaterais|efeitos? adversos|sono|insonia|dor|dores|sintomas?|respirar|respiracao|infarto|avc|hidrat\w*|agua|aliment\w*|nutri\w*|colesterol|cansaco|fadiga|lombar|caminha\w*|exercicios?|vacinas?|alerg\w*|hipertensao|cancer|artrite|artrose|ansiedade|depressao|febre|infecc\w*|doenc\w*|pulmao|pulmon\w*|estomago|figado|tireoide|osteoporose|almoc\w*|jantar|refeic\w*)\b""")
    private val complex = Regex("""\b(por que|porque|diferenca|interac\w*|intera\w*|contraindic\w*|efeitos? colaterais|efeitos? adversos|interpret\w*|exames?|diagnostic\w*|investig\w*|relacao|afeta\w*|interfere\w*|influencia\w*|posso (misturar|combinar)|qual dose|o que (e|significa|causa)|como (funciona|age)|precis\w* ser discutid\w*)\b""")
    private val unrelated = Regex("""\b(futebol|jogo|placar|campeonato|novela|loteria|cotacao|acoes da bolsa|bitcoins?|receita de bolo|previsao do tempo|capital de|presidente|eleicoes|resolva matematica|traduz\w*)\b""")
    private val chest = Regex("""\b(socorro|dor (forte |insuportavel )?no peito|aperto (insuportavel )?no peito|suor frio|falta de ar( repentina)?|nao (consigo|consegue) respira[r]?|desmai\w*|boca (ta |esta )?torta|vomitando sangue|infarto|avc|perdi a forca|dormencia|formigamento|ajuda rapido|passando muito mal|tontura (muito )?forte|visao escureceu|puxa pro braco|queimacao forte no meio do peito)\b""")
    private val mentalCrisis = Regex("""\b(quero morrer|vontade de morrer|pensando em morrer|tirar (a )?minha vida|acabar com (a )?minha vida|acabar com tudo|nao aguento mais viver|nao quero mais viver|vontade de sumir|preferia estar mort[oa]|preferia morrer|desespero total|socorro nao aguento mais|me machucar|automutilac\w*|cortar meus pulsos|suicid\w*)\b""")
    private val fall = Regex("""\b(cai[ur]?( aqui| no chao)?|caindo|machuquei|tombo|nao (consigo|consegue) (me |se )?levantar|bati a cabeca|ta sangrando|esta sangrando|perna travou|levei um tombo|levou um tombo|escorreg\w*)\b""")
    private val preventionPrefix = Regex("""\b(medo de|receio de|evitar|prevenir|risco de)\s*$""")
    private val deniedPrefix = Regex("""\b(nao|sem|nego|negou|nunca|jamais)\b(?:\W+\w+){0,6}\W*$""")
    // "Não consigo dormir, quero morrer" contém sofrimento, não uma negação da ideação.
    private val mentalDeniedPrefix = Regex("""\b(nao|sem|nego|negou|nunca|jamais)\b(?:\s+(?:mais|estou|esta|tenho|tem|sinto|sente|quero|quer|vou|vai|penso|pensando|nenhuma|qualquer|de|a|em)){0,6}\s*$""")
    private val historical = Regex("""\b(ontem|anteontem|semana passada|mes passado|ano passado|ja tive|ja teve|tive|teve|sentia|senti|sentiu|ha \d+ (dias|meses|anos))\b""")
    private val present = Regex("""\b(agora|hoje|estou|esta|tenho|tem|sinto|sente|continua|continuo|ainda|desde|socorro|acabei de|acabou de|nao (consigo|consegue))\b""")
    private val hypothetical = Regex("""\b(se (eu |ele |ela |alguem )?(tiver|sentir)|hipotet\w*|exemplo|no filme|no livro|li a frase)\b""")
    private val educational = Regex("""\b(o que (e|significa|causa)|por que|porque|como evitar|como prevenir)\b""")
    private val currentReport = Regex("""\b(estou|tenho|sinto|sentindo|agora|socorro|nao (consigo|consegue)|cai|caiu|acabei de cair|acabou de cair|bati a cabeca|levei um tombo|escorreguei|quero morrer|nao aguento mais viver|nao quero mais viver)\b|\b(meu|minha|ele|ela)\b.{0,40}\b(esta|tem|sente)\b""")

    // Recorrência pertence ao relato da mesma oração ou a uma retomada explícita.
    private val recurrence = Regex("""\b(voltou|voltaram|de novo|outra vez|novamente|piorou|piorando|continua|ainda)\b""")

    fun inHealthScope(text: String) = health.containsMatchIn(text) && !unrelated.containsMatchIn(text)
    fun complexHealthQuestion(text: String): Boolean {
        val question = Regex("""\b(qual|quais|como|por que|porque|o que|existe|posso|devo|preciso|explique|explica|saber|intera\w*|contraindic\w*|efeitos? colaterais|efeitos? adversos)\b""")
        val advice = Regex("""\b(?:posso|devo|preciso) (?:tomar|usar|comer|almocar|jantar)\b|\bo que (?:eu )?posso (?:comer|almocar|jantar)\b""")
        return inHealthScope(text) && question.containsMatchIn(text) &&
            (complex.containsMatchIn(text) || advice.containsMatchIn(text))
    }
    fun outsideScope(text: String) = unrelated.containsMatchIn(text)

    data class EmergencyAssessment(val intent: String? = null, val contextualMention: Boolean = false)

    fun emergency(text: String): EmergencyAssessment {
        var contextual = false
        // O "é" de "o que é" não é a conjunção que separa um novo relato.
        val clauses = text.split(Regex("""\b(mas|porem|contudo)\b|(?<!que )\be\b|[.;!?]|,\s*(?=(?:eu|ele|ela|meu|minha|nao|estou|tenho|sinto|bebi|tomei|agora)\b)"""))
        for ((index, clause) in clauses.withIndex()) {
            val recurring = recurrence.containsMatchIn(clause)
            val resumed = clauses.drop(index + 1).any {
                Regex("""^\s*(?:(?:agora|hoje)\s+)?(?:voltou|voltaram|continua|piorou)\b""").containsMatchIn(it)
            }
            for ((pattern, intent) in listOf(mentalCrisis to MENTAL_HEALTH_EMERGENCY,
                chest to "emergencia_dor_peito_avc", fall to "emergencia_queda_trauma")) {
                for (match in pattern.findAll(clause)) {
                    val prefix = clause.take(match.range.first)
                    val denialPattern = if (intent == MENTAL_HEALTH_EMERGENCY) mentalDeniedPrefix else deniedPrefix
                    val denial = !match.value.startsWith("nao ") && denialPattern.containsMatchIn(prefix)
                    val past = historical.containsMatchIn(clause) && !present.containsMatchIn(clause) && !recurring && !resumed
                    // Uma pergunta sobre um sintoma presente é relato, não exemplo educativo.
                    val example = hypothetical.containsMatchIn(clause) || preventionPrefix.containsMatchIn(prefix) ||
                        (educational.containsMatchIn(clause) && !currentReport.containsMatchIn(clause) && !recurring)
                    if (!denial && !past && !example) return EmergencyAssessment(intent)
                    // Perguntas explicativas continuam na análise clínica, sem disparar alerta por palavra.
                    if (!example) contextual = true
                }
            }
        }
        return EmergencyAssessment(contextualMention = contextual)
    }
}
