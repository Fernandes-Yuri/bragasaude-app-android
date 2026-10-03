package br.com.bragasaude.data.local.slm

import javax.inject.Inject
import javax.inject.Singleton

enum class UserInputCategory { TRIAGEM_MEDICA, INSERCAO_DADOS, CONSULTA_DADOS, CONVERSA_LIVRE }
data class RoutedUserInput(val category: UserInputCategory, val triage: ClinicalTriage,
                           val request: BragaRequest? = null)
data class ImmediateTriageResponse(val severity: TriageSeverity, val text: String, val reason: String)

/** Uma só porta de entrada para texto/voz: triagem tem precedência e não usa LLM/Room. */
@Singleton
class UserInputRouter @Inject constructor(
    private val activities: BragaIntentRouter,
    private val clinical: ClinicalTriageEngine,
    private val responses: ResponseRotator
) {
    fun route(input: String): RoutedUserInput {
        val triage = clinical.classify(input)
        if (triage.severity != TriageSeverity.NENHUMA) return RoutedUserInput(UserInputCategory.TRIAGEM_MEDICA, triage)
        val request = activities.route(input)
        val category = when (request.type) {
            BragaIntent.CONSULTA_HISTORICO -> UserInputCategory.CONSULTA_DADOS
            BragaIntent.REGISTRO_AGUA, BragaIntent.REGISTRO_PRESSAO, BragaIntent.REGISTRO_GLICOSE,
            BragaIntent.REGISTRO_MEDICAMENTO -> UserInputCategory.INSERCAO_DADOS
            else -> UserInputCategory.CONVERSA_LIVRE
        }
        return RoutedUserInput(category, triage, request)
    }
    fun immediateResponse(input: String): ImmediateTriageResponse? {
        val routed = route(input)
        if (routed.category != UserInputCategory.TRIAGEM_MEDICA) return null
        return ImmediateTriageResponse(routed.triage.severity, responses.next(routed.triage.severity), routed.triage.reason)
    }
}
