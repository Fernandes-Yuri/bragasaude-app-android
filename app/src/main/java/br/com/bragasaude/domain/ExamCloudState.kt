package br.com.bragasaude.domain

/** Estado da cópia remota, independente da conferência dos resultados. */
object ExamCloudState {
    const val UNKNOWN = "UNKNOWN"
    const val LOCAL_ONLY = "LOCAL_ONLY"
    const val PENDING = "PENDING"
    const val SYNCED = "SYNCED"
    const val ERROR = "ERROR"

    fun label(state: String): String = when (state) {
        LOCAL_ONLY -> "Salvo somente neste aparelho"
        PENDING -> "Salvo neste aparelho. Envio à nuvem pendente"
        SYNCED -> "Cópia na nuvem confirmada"
        ERROR -> "Salvo neste aparelho. Envio incompleto; pode existir cópia na nuvem"
        else -> "Situação na nuvem a verificar"
    }
}
