package br.com.bragasaude.data.remote.ai

/**
 * Resultado estruturado pelo Kotlin; somente a fala é humanizada pelo SLM.
 *
 * @param tipo Categoria da intenção detectada ("CONVERSA", "PRESSAO", "GLICEMIA", "DESCONHECIDO").
 * @param fala Mensagem humanizada e acolhedora gerada pelo Braga para ser falada via TextToSpeech.
 * @param sistolica Valor da pressão sistólica em mmHg (ex: 120), já normalizado se o usuário falou "12".
 * @param diastolica Valor da pressão diastólica em mmHg (ex: 80), já normalizado se o usuário falou "8".
 * @param glicemia Valor da glicemia em mg/dL (ex: 105).
 * @param rawResponse Resposta bruta recebida do modelo para fins de auditoria e fine-tuning.
 */
data class BragaAiResult(
    val tipo: String,
    val fala: String,
    val sistolica: Int? = null,
    val diastolica: Int? = null,
    val glicemia: Int? = null,
    val quantidadeMl: Int? = null,
    val alimento: String? = null,
    val refeicao: String? = null,
    val porcaoGramas: Int? = null,
    val motivoClinico: String? = null,
    val tipoMetrica: String? = null,
    val rawResponse: String? = null,
    val action: String? = null,
    val parameters: Map<String, String> = emptyMap(),
    val localIntent: br.com.bragasaude.domain.VoiceHealthIntent? = null
)
