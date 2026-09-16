package br.com.bragasaude.domain

import java.text.Normalizer
import java.util.Locale

/** Rascunho de uma conversa, sem acesso ao banco e sem preferência persistente de copo. */
class HydrationConversation(private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 }) {
    sealed interface Reply {
        data class Say(val text: String) : Reply
        data class Review(val amountMl: Int) : Reply
    }

    private var owner: String? = null
    private var touchedAt = 0L
    private var active = false
    private var count: Double? = null
    private var sizeMl: Double? = null
    private var totalMl: Int? = null
    private var handedOff = false
    private var sizeFromPreference = false

    fun reset() {
        owner = null
        active = false
        count = null
        sizeMl = null
        totalMl = null
        handedOff = false
        sizeFromPreference = false
    }

    /** Null devolve a fala intacta ao fluxo normal e descarta qualquer pergunta pendente. */
    fun respond(raw: String, userId: String, allowed: Boolean = true, defaultCupMl: Int? = null): Reply? {
        if (owner != userId || nowMs() - touchedAt > 120_000 || !allowed) reset()
        if (!allowed) return null
        owner = userId
        touchedAt = nowMs()
        val text = normalize(raw)
        if (text.isBlank()) return if (active) question() else null
        if (text.matches(Regex("(?:braga )?(?:cancela|cancelar|cancele|esquece(?: isso)?|deixa (?:pra|para) la|nao quero(?: mais)?|depois eu registro)"))) {
            val hadDraft = active
            reset()
            return if (hadDraft) Reply.Say("Tudo bem, descartei essa preparação. Nada foi salvo por esta conversa.") else null
        }
        val confirmation = text.matches(Regex("(?:sim|isso|isso mesmo|certo|pode|pode preparar|pode abrir|quero conferir|confirmo)"))
        if (confirmation && handedOff) return Reply.Say("O valor já foi preparado. Confira na tela e toque em Adicionar para salvar.")
        if (confirmation && active) {
            val amount = totalMl ?: return question()
            active = false
            handedOff = true
            return Reply.Review(amount)
        }
        if (handedOff && Regex("^(?:nao|corrig|era|eram|foram|espera|na verdade)").containsMatchIn(text)) {
            return Reply.Say("O valor já foi enviado para revisão. Corrija a quantidade na tela antes de tocar em Adicionar. Se já salvou, edite o registro na tela de hidratação.")
        }

        val water = Regex("\\b(?:agua|hidratacao)\\b").containsMatchIn(text)
        val container = Regex("\\b(?:copos?|copinhos?|garrafas?|garrafinhas?|canecas?|xicaras?)\\b").containsMatchIn(text)
        val volume = Regex("\\b(?:ml|mililitros?|litros?)\\b").containsMatchIn(text)
        val action = Regex("\\b(?:bebi|bebido|tomei|tomado|registra|registre|registrar|anota|anote|anotar|adicione|adicionar)\\b").containsMatchIn(text)
        val otherDrink = Regex("\\b(?:cafe|leite|suco|cerveja|vinho|refrigerante|cha|remedio|medicamento|xarope)\\b").containsMatchIn(text)
        val question = Regex("^(?:quanto|quantos|qual|como|por que|o que|voce|me explique)\\b").containsMatchIn(text)
        val negated = Regex("\\bnao (?:bebi|tomei|registre|registra|anote|anota)\\b").containsMatchIn(text)
        val start = !question && !negated && !otherDrink && (
            ((water || container || volume) && action) ||
                text.matches(Regex("[\\d.,]+ (?:ml|mililitros?|litros?|copos?|garrafinhas?|garrafas?|canecas?)(?: de agua)?"))
            )
        val correction = Regex("(?:espera[, ]*|na verdade[, ]*|quer dizer[, ]*|nao[, ]*(?:foram|eram|era)?[ ]*|^(?:foram|eram|era) )")
        val correctionMatch = correction.findAll(text).lastOrNull()
        // Processa os campos antes e depois de uma autocorreção no mesmo turno.
        if (!active && !start) {
            reset()
            return null
        }
        if (!active) {
            count = null
            sizeMl = null
            sizeFromPreference = false
            totalMl = null
            handedOff = false
            active = true
        } else if (question || negated || otherDrink) {
            reset()
            return null
        }
        if (Regex("-\\s*\\d").containsMatchIn(text)) {
            totalMl = null
            return Reply.Say("A quantidade precisa ser positiva. Qual foi o valor em ml?")
        }
        if (Regex("\\d+(?:[.,]\\d+)?\\s*(?:ml|mililitros?|litros?|l)\\b").findAll(text).count() > 1 && correctionMatch == null) {
            totalMl = null
            return Reply.Say("Ouvi mais de uma quantidade. Qual é o total em ml que você quer preparar?")
        }
        if (correctionMatch != null && correctionMatch.range.first > 0) {
            readFields(text.substring(0, correctionMatch.range.first), false)
        }
        val input = if (correctionMatch != null) text.substring(correctionMatch.range.last + 1) else text
        val parsed = readFields(input, correctionMatch != null)
        // Preferência confirmada serve apenas para copos, nunca para outros recipientes.
        if (sizeMl == null && count != null && Regex("\\bcopos?\\b").containsMatchIn(input)
            && defaultCupMl != null && defaultCupMl in 50..2000) {
            sizeMl = defaultCupMl.toDouble()
            sizeFromPreference = true
        }
        if (!parsed && !start) {
            if (text.matches(Regex("\\d+(?:[.,]\\d+)?"))) {
                return Reply.Say("Diga também a unidade, por exemplo: 500 ml ou dois copos.")
            }
            if (text in listOf("nao", "nao sei", "nao lembro")) {
                totalMl = null
                return Reply.Say("Pode informar uma estimativa em ml ou dizer cancelar para deixar para depois.")
            }
            reset()
            return null
        }
        val computed = if (count != null && sizeMl != null) count!! * sizeMl!! else sizeMl?.takeIf { count == null }
        if (computed != null) {
            if (!computed.isFinite() || computed < 50 || computed > 8000 || computed % 1.0 != 0.0) {
                totalMl = null
                return Reply.Say("Essa quantidade não cabe no registro de água do app. Pode conferir o valor em ml?")
            }
            totalMl = computed.toInt()
        }
        return question()
    }

    private fun question(): Reply.Say = when {
        totalMl != null -> Reply.Say("São $totalMl ml de água. Quer conferir esse valor na tela?")
        count != null && sizeMl == null -> Reply.Say("De quantos ml era cada recipiente?")
        else -> Reply.Say("Quanto você bebeu? Pode dizer a quantidade em ml ou em copos.")
    }

    private fun readFields(text: String, correcting: Boolean): Boolean {
        val number = "(\\d+(?:[.,]\\d+)?)"
        val containers = Regex("$number\\s*(?:copos?|copinhos?|garrafas?|garrafinhas?|canecas?|xicaras?)\\b").find(text)
        val volume = Regex("$number\\s*(ml|mililitros?|litros?|l)\\b").find(text)
        val lone = Regex("^(?:(?:foram|eram|era|de|tem|cada um tem|cada copo tem|o copo tem) )?$number(?: cada)?$").matchEntire(text.trim())
        fun value(raw: String) = raw.replace(',', '.').toDouble()
        if (containers != null) {
            if (sizeFromPreference && !Regex("\\bcopos?\\b").containsMatchIn(containers.value)) {
                sizeMl = null
                sizeFromPreference = false
            }
            count = value(containers.groupValues[1])
            totalMl = null
        }
        if (volume != null) {
            sizeFromPreference = false
            sizeMl = value(volume.groupValues[1]) * if (volume.groupValues[2].startsWith("l")) 1000 else 1
            totalMl = null
        } else if (lone != null) {
            val n = value(lone.groupValues[1])
            if (correcting && count != null && sizeMl != null) count = n
            else if (count != null) sizeMl = n
            else return false // Não adivinhar se um número isolado é ml ou quantidade de copos.
            totalMl = null
        }
        return containers != null || volume != null || lone != null
    }

    private fun normalize(raw: String): String {
        var s = Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("[!?;:]"), " ")
            .replace(Regex("(?<!\\d)[.,]|[.,](?!\\d)"), " ")
        val words = linkedMapOf("meio" to "0.5", "meia" to "0.5", "zero" to "0", "um" to "1", "uma" to "1", "dois" to "2", "duas" to "2", "tres" to "3", "quatro" to "4", "cinco" to "5", "seis" to "6", "sete" to "7", "oito" to "8", "nove" to "9", "dez" to "10", "vinte" to "20", "trinta" to "30", "quarenta" to "40", "cinquenta" to "50", "cem" to "100", "cento" to "100", "duzentos" to "200", "trezentos" to "300", "quatrocentos" to "400", "quinhentos" to "500", "seiscentos" to "600", "setecentos" to "700", "oitocentos" to "800", "novecentos" to "900", "mil" to "1000")
        words.forEach { (word, value) -> s = s.replace(Regex("\\b$word\\b"), value) }
        // Centenas e dezenas ditas juntas: duzentos e cinquenta -> 250.
        s = s.replace(Regex("\\b(100|200|300|400|500|600|700|800|900) e (10|20|30|40|50)\\b")) {
            (it.groupValues[1].toInt() + it.groupValues[2].toInt()).toString()
        }
        return s.replace(Regex("\\s+"), " ").trim()
    }
}
