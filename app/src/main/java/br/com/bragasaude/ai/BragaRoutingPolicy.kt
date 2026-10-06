package br.com.bragasaude.ai


enum class BragaRoute { BLOCKED, EMERGENCY, HEALTH_MEMORY, LOCAL_ACTION, LOCAL_CONVERSATION, CLARIFICATION, CLOUD }

/** Decisões locais explicáveis, sem modelo remoto para classificar a entrada. */
internal object BragaRoutingPolicy {
    fun normalize(text: String) = BragaLanguageRecovery.recognize(text)

    private val health = Regex("""\b(saude|medic\w*|remedio\w*|comprimido\w*|dose|tratamento\w*|pressao|glic\w*|diabet\w*|acucar|sangue|renal|rins|rim|cardi\w*|coracao|apneia|hormon\w*|menopausa|anemia|enxaqueca|muscular|exames?|contraindic\w*|efeitos? colaterais|efeitos? adversos|sono|insonia|dor|dores|sintomas?|respirar|respiracao|infarto|avc|hidrat\w*|agua|aliment\w*|nutri\w*|colesterol|cansaco|fadiga|lombar|caminha\w*|exercicios?|vacinas?|alerg\w*|hipertensao|cancer|artrite|artrose|ansiedade|depressao|febre|infecc\w*|doenc\w*|pulmao|pulmon\w*|estomago|figado|tireoide|osteoporose)\b""")
    private val complex = Regex("""\b(por que|porque|diferenca|interac\w*|intera\w*|contraindic\w*|efeitos? colaterais|efeitos? adversos|interpret\w*|exames?|diagnostic\w*|investig\w*|relacao|afeta\w*|interfere\w*|influencia\w*|posso (misturar|combinar)|qual dose|o que (e|significa|causa)|como (funciona|age)|precis\w* ser discutid\w*)\b""")
    private val unrelated = Regex("""\b(futebol|jogo|placar|campeonato|novela|loteria|cotacao|acoes da bolsa|bitcoins?|receita de bolo|previsao do tempo|capital de|presidente|eleicoes|resolva matematica|traduz\w*)\b""")
    private val chest = Regex("""\b(socorro|dor (forte |insuportavel )?no peito|aperto (insuportavel )?no peito|suor frio|falta de ar( repentina)?|nao (consigo|consegue) respira[r]?|desmai\w*|boca (ta |esta )?torta|vomitando sangue|infarto|avc|perdi a forca|dormencia|formigamento|ajuda rapido|passando muito mal|tontura (muito )?forte|visao escureceu|puxa pro braco|queimacao forte no meio do peito)\b""")
    private val fall = Regex("""\b(cai(u)?( aqui| no chao)?|nao (consigo|consegue) (me |se )?levantar|bati a cabeca|ta sangrando|esta sangrando|perna travou|levei um tombo|levou um tombo|escorreguei)\b""")
    private val deniedPrefix = Regex("""\b(nao|sem|nego|negou|nunca|jamais)\b(?:\W+\w+){0,6}\W*$""")
    private val historical = Regex("""\b(ontem|anteontem|semana passada|mes passado|ano passado|ja tive|ja teve|tive|teve|sentia|senti|sentiu|ha \d+ (dias|meses|anos))\b""")
    private val present = Regex("""\b(agora|hoje|estou|esta|tenho|tem|sinto|sente|continua|continuo|ainda|desde|socorro|nao (consigo|consegue))\b""")
    private val hypothetical = Regex("""\b(se (eu |ele |ela |alguem )?(tiver|sentir)|hipotet\w*|exemplo|no filme|no livro|li a frase)\b""")
    private val educational = Regex("""\b(o que (e|significa|causa)|por que|porque|como evitar|como prevenir)\b""")
    private val currentReport = Regex("""\b(estou|tenho|sinto|sentindo|agora|socorro|nao (consigo|consegue)|cai|caiu|bati a cabeca|levei um tombo|escorreguei)\b|\b(meu|minha|ele|ela)\b.{0,40}\b(esta|tem|sente)\b""")

    // Recorrência atual em qualquer trecho invalida a leitura de relato passado.
    private val recurrence = Regex("""\b(voltou|voltaram|de novo|outra vez|novamente|piorou|piorando|continua|ainda)\b""")

    fun inHealthScope(text: String) = health.containsMatchIn(text) && !unrelated.containsMatchIn(text)
    fun complexHealthQuestion(text: String): Boolean {
        val question = Regex("""\b(qual|quais|como|por que|porque|o que|existe|posso|devo|preciso|explique|explica|saber|intera\w*|contraindic\w*|efeitos? colaterais|efeitos? adversos)\b""")
        return inHealthScope(text) && complex.containsMatchIn(text) && question.containsMatchIn(text)
    }
    fun outsideScope(text: String) = unrelated.containsMatchIn(text)

    data class EmergencyAssessment(val intent: String? = null, val contextualMention: Boolean = false)

    fun emergency(text: String): EmergencyAssessment {
        var contextual = false
        val recurring = recurrence.containsMatchIn(text)
        // O "é" de "o que é" não é a conjunção que separa um novo relato.
        val clauses = text.split(Regex("""\b(mas|porem|contudo)\b|(?<!que )\be\b|[.;!?]"""))
        for (clause in clauses) {
            for ((pattern, intent) in listOf(chest to "emergencia_dor_peito_avc", fall to "emergencia_queda_trauma")) {
                for (match in pattern.findAll(clause)) {
                    val prefix = clause.take(match.range.first)
                    val denial = !match.value.startsWith("nao ") && deniedPrefix.containsMatchIn(prefix)
                    val past = historical.containsMatchIn(clause) && !present.containsMatchIn(clause) && !recurring
                    // Uma pergunta sobre um sintoma presente é relato, não exemplo educativo.
                    val example = hypothetical.containsMatchIn(clause) ||
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
