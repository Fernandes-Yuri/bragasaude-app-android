package br.com.bragasaude.domain

import java.text.Normalizer
import java.util.Locale

/** Controle de callbacks do reconhecedor; acesso restrito à thread principal. */
class VoiceSessionControl {
    private var generation = 0L
    private var accepting = false
    private var silentTurns = 0
    fun begin(): Long { accepting = true; return ++generation }
    fun accepts(token: Long) = accepting && token == generation
    fun consume(token: Long): Boolean {
        if (!accepts(token)) return false
        accepting = false
        return true
    }
    fun invalidate() { accepting = false; generation++ }
    fun hasSpeech() { silentTurns = 0 }
    fun shouldPauseAfterSilence(): Boolean = ++silentTurns >= 2
    fun reset() { invalidate(); silentTurns = 0 }
}

enum class VoiceSessionCommand { CLOSE, PAUSE, REPEAT;
    companion object {
        fun parse(raw: String): VoiceSessionCommand? {
            val text = Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9 ]"), " ")
                .replace(Regex("\\bbraga\\b"), "").replace(Regex("\\s+"), " ").trim()
                .removePrefix("por favor ").removeSuffix(" por favor").trim()
            return when {
                text.matches(Regex("(?:tchau|ate logo|ate amanha|encerrar|encerra|pode encerrar|fechar|pode fechar|sair|por hoje e so|obrigad[oa] por hoje e so|nao quero mais conversar)")) -> CLOSE
                text.matches(Regex("(?:so um minuto|um minuto|espera|espere|pausa|pausar|pode pausar|ja volto|aguarde um pouco)")) -> PAUSE
                text.matches(Regex("(?:(?:(?:voce )?(?:pode|consegue) )?(?:repetir|repete|repita|falar de novo|dizer de novo)(?: (?:a )?(?:sua )?ultima (?:parte|resposta|mensagem|frase)(?: que (?:voce )?(?:(?:havia|tinha) )?(?:falou|disse|falado|dito))?| o que (?:voce )?(?:falou|disse))?|nao ouvi|nao ouvi o final)")) -> REPEAT
                else -> null
            }
        }
    }
}
