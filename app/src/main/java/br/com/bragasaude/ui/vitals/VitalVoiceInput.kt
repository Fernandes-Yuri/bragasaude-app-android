package br.com.bragasaude.ui.vitals

import br.com.bragasaude.ai.BragaActionGate
import br.com.bragasaude.ai.BragaNluEngine
import br.com.bragasaude.domain.VoiceHealthIntent
import br.com.bragasaude.domain.VoiceHealthParser

/** Ditado do formulário: prepara somente a métrica aberta, sem executar ou salvar. */
internal object VitalVoiceInput {
    fun parse(text: String, glucose: Boolean): VoiceHealthIntent? {
        if (!BragaActionGate.canParse(text, BragaNluEngine.analisar(text))) return null
        val input = if (glucose && text.trim().matches(Regex("[0-9]{2,3}"))) "glicemia ${text.trim()}" else text
        return VoiceHealthParser().parse(input).takeIf {
            if (glucose) it is VoiceHealthIntent.Glucose else it is VoiceHealthIntent.BloodPressure
        }
    }
}
