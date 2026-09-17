package br.com.bragasaude.ui.profile

/**
 * Encapsula os dados de entrada do formulário de perfil clínico,
 * eliminando funções monolíticas com dezenas de parâmetros soltos.
 */
data class ProfileInput(
    val name: String,
    val birthDate: String? = null,
    val gender: String? = null,
    val height: Double? = null,
    val weight: Double? = null,
    val isSmoker: Boolean = false,
    val hasDiabetes: Boolean = false,
    val hasHypertension: Boolean = false,
    val hasThyroid: Boolean = false,
    val hasRenal: Boolean? = null,
    val hasBone: Boolean? = null,
    val hasMuscular: Boolean? = null,
    val hydrationTargetMl: Int? = null,
    val dailyCalorieTarget: Double? = null,
    val stepGoal: Int? = null,
    val weightGoal: Double? = null,
    val sleepStart: String? = "22:00",
    val sleepEnd: String? = "06:00",
    val emergencyName: String? = null,
    val emergencyRelation: String? = null,
    val emergencyPhone: String? = null,
    val notificationsEnabled: Boolean? = null,
    val locationEnabled: Boolean? = null,
    val activityLevel: String? = null,
    val diabetesType: String? = null,
    val foodAllergies: List<String> = emptyList(),
    val customFoodRestrictions: String? = null
) {
    /**
     * Validação básica de regras clínicas e de integridade cadastral.
     * Retorna mapa de erros por campo (chave -> mensagem).
     */
    fun validate(enableSelfCare: Boolean = true): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (name.isBlank()) {
            errors["name"] = "Informe seu nome completo."
        }
        if (enableSelfCare) {
            if (weight == null || weight !in 20.0..350.0) {
                errors["weight"] = "Informe um peso válido entre 20kg e 350kg."
            }
            if (height == null || height !in 50.0..250.0) {
                errors["height"] = "Informe uma altura válida entre 50cm e 250cm."
            }
        }
        return errors
    }
}
