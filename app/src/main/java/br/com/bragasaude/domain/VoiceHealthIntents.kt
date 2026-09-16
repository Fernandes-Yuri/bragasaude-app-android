package br.com.bragasaude.domain

import br.com.bragasaude.data.local.FoodEntity
import br.com.bragasaude.domain.model.BloodPressureCategory
import br.com.bragasaude.domain.util.BloodPressureParser

/**
 * Intenções de saúde reconhecidas a partir da fala transcrita.
 *
 * Cada subtipo representa uma intenção de registro identificada
 * pelo [VoiceHealthParser] a partir da fala natural em português (pt-BR).
 */
sealed interface VoiceHealthIntent {

    /**
     * Registro de hidratacao.
     * @param amountMl Quantidade em mililitros (ex.: 250, 500).
     * @param description Texto humanizado para confirmacao na UI ("1 copo de agua").
     */
    data class Hydration(
        val amountMl: Int,
        val description: String
    ) : VoiceHealthIntent

    /**
     * Registro de pressao arterial (mmHg).
     * @param systolic Sistolica (valor "maior").
     * @param diastolic Diastolica (valor "menor").
     * @param rawInput Texto original reconhecido ("12 por 8", "13 por 8 e meio").
     */
    data class BloodPressure(
        val systolic: Int,
        val diastolic: Int,
        val rawInput: String
    ) : VoiceHealthIntent

    /**
     * 🩸 Registro de glicemia (mg/dL).
     * @param glucoseMgDl Valor em mg/dL.
     */
    data class Glucose(
        val glucoseMgDl: Int
    ) : VoiceHealthIntent

    /**
     * 🥗 Registro de refeição com identificação de alimentos do catálogo.
     * @param mealType Tipo inferido ("Café da Manhã", "Almoço", "Lanche", "Jantar", "Café da Tarde").
     * @param foodNames Nomes brutos mencionados na fala.
     * @param matchedFoods Alimentos encontrados no catálogo (220 itens Braga Saúde).
     * @param unmatchedFoods Itens não identificados no catálogo (para transparência ao idoso).
     */
    data class Meal(
        val mealType: String,
        val foodNames: List<String>,
        val matchedFoods: List<FoodEntity>,
        val unmatchedFoods: List<String>
    ) : VoiceHealthIntent

    /**
      * Registro de adesao a medicacao.
     * @param query Texto usado para buscar a medicação no catálogo do usuário.
     * @param scheduleTimeHint Horário sugerido ("8 horas" → "08:00").
     * @param query Texto usado para buscar a medicação no catálogo do usuário.
     * @param scheduleTimeHint Horário sugerido ("8 horas" → "08:00").
     */
    data class Medication(
        val query: String,
        val scheduleTimeHint: String? = null
    ) : VoiceHealthIntent

    /**
     * ⚖️ Registro de peso corporal (kg).
     * @param weightKg Peso em quilogramas.
     */
    data class Weight(
        val weightKg: Double
    ) : VoiceHealthIntent

    /**
     * ❤️ Registro de frequência cardíaca (bpm).
     * @param heartRateBpm Batimentos por minuto.
     */
    data class HeartRate(
        val heartRateBpm: Int
    ) : VoiceHealthIntent

    /**
     * 🫁 Registro de saturação de oxigênio (SpO2 %).
     * @param oxygenPercent Percentual de oxigênio no sangue.
     */
    data class OxygenSaturation(
        val oxygenPercent: Int
    ) : VoiceHealthIntent

    /**
     * 🛒 Registro de itens na lista de compras do supermercado ou feira.
     * @param items Lista de nomes dos alimentos ou itens a comprar.
     * @param quantityHint Quantidade ou observação (ex.: "1 kg", "2 litros").
     */
    data class Grocery(
        val items: List<String>,
        val quantityHint: String? = null
    ) : VoiceHealthIntent
    
    /**
     * 👨‍👩‍👧 Lembrete de medicação para familiar/cuidado.
     * @param targetName Nome do paciente/familiar (ex.: "meu pai", "minha mãe").
     * @param text Texto da mensagem ou nome do remédio.
     * @param timeHint Horário sugerido para o lembrete.
     */
    data class FamilyMedicationReminder(
        val targetName: String?,
        val text: String,
        val timeHint: String? = null
    ) : VoiceHealthIntent
    
    /**
     * 📅 Agendamento de consulta/compromisso para familiar.
     * @param targetName Nome do paciente/familiar.
     * @param title Título do compromisso (ex.: "consulta com cardiologista").
     * @param dateTimeHint Descrição aproximada de data/hora (ex.: "terça às 14h").
     */
    data class FamilyAppointment(
        val targetName: String?,
        val title: String,
        val dateTimeHint: String? = null
    ) : VoiceHealthIntent
    
    /**
     * ❓ Consulta sobre status/métricas do paciente.
     * @param metric Tipo de métrica (ex.: "pressão", "glicose", "água", "passos").
     * @param targetName Nome do paciente/familiar (opcional).
     */
    data class QueryPatientStatus(
        val metric: String,
        val targetName: String? = null
    ) : VoiceHealthIntent

    /**
     * 💬 Esclarecimento Conversacional Proativo (Diálogo Multi-Turno).
     * Disparado quando a fala do usuário é ambígua (ex.: "filé de frango")
     * e o agente precisa saber se o usuário já comeu ou se quer colocar na lista de compras.
     * @param originalText Texto original falado pelo usuário.
     * @param questionPrompt Pergunta acolhedora formulada pelo agente para ser falada (TTS) e lida.
     * @param options Opções estruturadas para exibição em chips visuais e reconhecimento por voz.
     * @param detectedFoodItems Alimentos detectados para encaminhamento.
     * @param candidateMeal Objeto Meal pré-montado se o usuário confirmar consumo.
     */
    data class ClarificationRequired(
        val originalText: String,
        val questionPrompt: String,
        val options: List<ClarificationOption>,
        val detectedFoodItems: List<String> = emptyList(),
        val candidateMeal: Meal? = null
    ) : VoiceHealthIntent

    /**
      * 💬 Resposta Conversacional Informal (saudações, identidade, agradecimentos).
      * Não é um registro de saúde — apenas uma fala acolhedora da assistente.
      * @param message Texto simpático a ser falado (TTS) e exibido.
      */
    data class ConversationalReply(
        val message: String
    ) : VoiceHealthIntent

    /**
     * 🍎 Sugestão personalizada de alimento do catálogo (220 itens).
     * @param suggestedFood Alimento recomendado do catálogo.
     * @param reason Motivo clínico ou benefício funcional.
     * @param portionGrams Porção sugerida em gramas (calculada proporcionalmente).
     * @param portionDescription Descrição humanizada da porção (ex: "1 colher de sopa cheia (20g)").
     * @param clinicalSociety Sociedade científica que respalda a recomendação (SBD, SBC, etc.).
     * @param targetCondition Condição atendida ("GLUCOSE_HIGH", "GLUCOSE_LOW", "HYPERTENSION", "WEIGHT_MANAGEMENT", "WELLNESS").
     */
    data class SuggestFood(
        val suggestedFood: FoodEntity,
        val reason: String,
        val portionGrams: Int,
        val portionDescription: String,
        val clinicalSociety: String,
        val targetCondition: String
    ) : VoiceHealthIntent

    /**
     * Fala não reconhecida como intenção de saúde.
     * @param rawText Texto transcrito original.
     * @param hint Sugestão contextual para o idoso reformular.
     */
    data class Unknown(
        val rawText: String,
        val hint: String
    ) : VoiceHealthIntent
}

enum class ClarificationActionType {
    LOG_MEAL,
    ADD_TO_GROCERY,
    LOG_HYDRATION_CUPS,
    LOG_HYDRATION_FULL,
    CANCEL
}

data class ClarificationOption(
    val id: String,
    val label: String,
    val actionType: ClarificationActionType
)

/**
 * Resultado completo de um ciclo de reconhecimento de voz.
 */
sealed interface VoiceResult {
    /** Reconhecimento em andamento (parcial). */
    data class Recognizing(val partialText: String) : VoiceResult

    /** Intenção identificada com sucesso, aguardando confirmação de 1 toque do idoso. */
    data class Parsed(val intent: VoiceHealthIntent) : VoiceResult

    /** Agente precisa de esclarecimento do usuário (diálogo multi-turno). */
    data class Clarifying(val intent: VoiceHealthIntent.ClarificationRequired) : VoiceResult

    /** Intenção confirmada e persistida localmente. */
    data class Saved(val intent: VoiceHealthIntent, val summary: String) : VoiceResult

    /** Falha (permissão negada, serviço indisponível, etc.). */
    data class Error(val message: String, val retryable: Boolean) : VoiceResult

    /** Idle — pronto para gravar. */
    object Idle : VoiceResult
}

/**
 * Item individual de régua/faixa de referência clínica (SBC / SBD).
 */
data class ReferenceStageItem(
    val stageName: String,
    val rangeText: String,
    val isCurrent: Boolean
)

/**
 * Metadados para tela e voz de confirmação consciente (diretriz ANVISA / CFM).
 */
data class ConfirmationDisplayData(
    val title: String,
    val value: String,
    val unit: String? = null,
    val questionText: String,
    val spokenQuestion: String,
    val clinicalSocietyName: String? = null,
    val clinicalStageBadge: String? = null,
    val referenceStages: List<ReferenceStageItem> = emptyList(),
    val isConversationalOnly: Boolean = false
)

fun VoiceHealthIntent.toConfirmationDisplay(): ConfirmationDisplayData {
    return when (this) {
        is VoiceHealthIntent.BloodPressure -> {
            val result = BloodPressureParser.classify(systolic, diastolic)
            val (spoken, stageBadge, stageIdx) = when (result.category) {
                BloodPressureCategory.OPTIMAL, BloodPressureCategory.NORMAL -> Triple(
                    "De acordo com a Sociedade Brasileira de Cardiologia, a sua pressão se encontra dentro do ideal. Deseja confirmar e salvar?",
                    "Dentro do Ideal",
                    0
                )
                BloodPressureCategory.PREHYPERTENSION -> Triple(
                    "De acordo com a Sociedade Brasileira de Cardiologia, a sua pressão se encontra em pré-hipertensão. Confira os valores de referência. Deseja salvar?",
                    "Pré-Hipertensão",
                    1
                )
                BloodPressureCategory.STAGE_1_HYPERTENSION -> Triple(
                    "De acordo com a Sociedade Brasileira de Cardiologia, a sua pressão se encontra em estágio um. Confira os valores de referência. Deseja salvar?",
                    "Estágio 1",
                    2
                )
                BloodPressureCategory.STAGE_1_TO_2_TRANSITION -> Triple(
                    "De acordo com a Sociedade Brasileira de Cardiologia, você está entre o estágio um e o estágio dois. Confira os valores de referência. Deseja salvar?",
                    "Estágio 1 a 2",
                    2
                )
                BloodPressureCategory.STAGE_2_HYPERTENSION -> Triple(
                    "De acordo com a Sociedade Brasileira de Cardiologia, a sua pressão se encontra em estágio dois. Confira os valores de referência. Deseja salvar?",
                    "Estágio 2",
                    3
                )
                BloodPressureCategory.STAGE_2_TO_3_TRANSITION, BloodPressureCategory.STAGE_3_HYPERTENSION -> Triple(
                    "De acordo com a Sociedade Brasileira de Cardiologia, a sua pressão se encontra em estágio três de atenção imediata. Confira os valores de referência. Deseja salvar?",
                    "Estágio 3 (Atenção)",
                    4
                )
                BloodPressureCategory.HYPOTENSION -> Triple(
                    "De acordo com a Sociedade Brasileira de Cardiologia, a sua pressão se encontra abaixo da faixa habitual. Confira os valores de referência. Deseja salvar?",
                    "Abaixo do Padrão",
                    -1
                )
                else -> Triple(
                    "De acordo com a Sociedade Brasileira de Cardiologia, a sua pressão requer observação. Confira os valores de referência. Deseja salvar?",
                    "Em Observação",
                    -1
                )
            }

            val sbcStages = listOf(
                ReferenceStageItem("Ideal / Normal", "< 120 / 80 mmHg", stageIdx == 0),
                ReferenceStageItem("Pré-Hipertensão", "120-139 / 80-89 mmHg", stageIdx == 1),
                ReferenceStageItem("Estágio 1", "140-159 / 90-99 mmHg", stageIdx == 2),
                ReferenceStageItem("Estágio 2", "160-179 / 100-109 mmHg", stageIdx == 3),
                ReferenceStageItem("Estágio 3 (Atenção)", "≥ 180 / ≥ 110 mmHg", stageIdx == 4)
            )

            ConfirmationDisplayData(
                title = "Pressão Arterial",
                value = "$systolic/$diastolic",
                unit = "mmHg",
                questionText = "Confirmar pressão de $systolic por $diastolic mmHg?",
                spokenQuestion = spoken,
                clinicalSocietyName = "Sociedade Brasileira de Cardiologia (SBC)",
                clinicalStageBadge = stageBadge,
                referenceStages = sbcStages
            )
        }
        is VoiceHealthIntent.Glucose -> {
            val (spoken, stageBadge, stageIdx) = when {
                glucoseMgDl < 70 -> Triple(
                    "De acordo com a Sociedade Brasileira de Diabetes, a sua glicemia se encontra baixa. Confira os valores de referência. Deseja salvar?",
                    "Hipoglicemia (< 70)",
                    0
                )
                glucoseMgDl in 70..99 -> Triple(
                    "De acordo com a Sociedade Brasileira de Diabetes, a sua glicemia se encontra dentro do ideal. Deseja confirmar e salvar?",
                    "Normal (70 a 99)",
                    1
                )
                glucoseMgDl in 100..125 -> Triple(
                    "De acordo com a Sociedade Brasileira de Diabetes, a sua glicemia se encontra em pré-diabetes. Confira os valores de referência. Deseja salvar?",
                    "Pré-Diabetes (100 a 125)",
                    2
                )
                else -> Triple(
                    "De acordo com a Sociedade Brasileira de Diabetes, a sua glicemia se encontra em estágio elevado de atenção. Confira os valores de referência. Deseja salvar?",
                    "Elevada (≥ 126)",
                    3
                )
            }

            val sbdStages = listOf(
                ReferenceStageItem("Hipoglicemia", "< 70 mg/dL", stageIdx == 0),
                ReferenceStageItem("Normal / Ideal", "70 a 99 mg/dL", stageIdx == 1),
                ReferenceStageItem("Pré-Diabetes", "100 a 125 mg/dL", stageIdx == 2),
                ReferenceStageItem("Diabetes / Elevada", "≥ 126 mg/dL", stageIdx == 3)
            )

            ConfirmationDisplayData(
                title = "Glicemia",
                value = "$glucoseMgDl",
                unit = "mg/dL",
                questionText = "Confirmar glicemia de $glucoseMgDl mg/dL?",
                spokenQuestion = spoken,
                clinicalSocietyName = "Sociedade Brasileira de Diabetes (SBD)",
                clinicalStageBadge = stageBadge,
                referenceStages = sbdStages
            )
        }
        is VoiceHealthIntent.Hydration -> ConfirmationDisplayData(
            title = "Hidratação",
            value = "$amountMl",
            unit = "ml",
            questionText = "Confirmar $description ($amountMl ml)?",
            spokenQuestion = "Você confirma o consumo de $amountMl mililitros de água?"
        )
        is VoiceHealthIntent.Weight -> {
            val formatted = "%.1f".format(java.util.Locale.US, weightKg).replace(".", ",")
            ConfirmationDisplayData(
                title = "Peso Corporal",
                value = formatted,
                unit = "kg",
                questionText = "Confirmar peso de $formatted kg?",
                spokenQuestion = "Você confirma o registro de $formatted quilos?"
            )
        }
        is VoiceHealthIntent.HeartRate -> ConfirmationDisplayData(
            title = "Frequência Cardíaca",
            value = "$heartRateBpm",
            unit = "bpm",
            questionText = "Confirmar batimentos de $heartRateBpm bpm?",
            spokenQuestion = "Você confirma que seus batimentos estão $heartRateBpm por minuto?"
        )
        is VoiceHealthIntent.OxygenSaturation -> ConfirmationDisplayData(
            title = "Saturação de Oxigênio",
            value = "$oxygenPercent",
            unit = "%",
            questionText = "Confirmar saturação de $oxygenPercent%?",
            spokenQuestion = "Você confirma que sua saturação de oxigênio está $oxygenPercent por cento?"
        )
        is VoiceHealthIntent.Medication -> ConfirmationDisplayData(
            title = "Medicamento",
            value = query,
            unit = scheduleTimeHint,
            questionText = "Confirmar adesão ao medicamento \"$query\"?",
            spokenQuestion = "Você confirma que tomou o remédio $query?"
        )
        is VoiceHealthIntent.Meal -> {
            val names = (matchedFoods.map { it.name } + unmatchedFoods).distinct()
            val text = if (names.isNotEmpty()) names.joinToString(", ") else foodNames.joinToString(", ")
            ConfirmationDisplayData(
                title = "Refeição ($mealType)",
                value = text,
                unit = null,
                questionText = "Confirmar $text no seu $mealType?",
                spokenQuestion = "Você confirma essa refeição no seu $mealType?"
            )
        }
        is VoiceHealthIntent.Grocery -> ConfirmationDisplayData(
            title = "Lista de Compras",
            value = items.joinToString(", "),
            unit = quantityHint,
            questionText = "Adicionar ${items.joinToString(", ")} à lista de compras?",
            spokenQuestion = "Deseja adicionar ${items.joinToString(", ")} à lista de compras?"
        )
        is VoiceHealthIntent.ClarificationRequired -> ConfirmationDisplayData(
            title = "Atenção",
            value = detectedFoodItems.joinToString(", "),
            unit = null,
            questionText = questionPrompt,
            spokenQuestion = questionPrompt
        )
        is VoiceHealthIntent.ConversationalReply -> ConfirmationDisplayData(
            title = "Assistente de Saúde",
            value = message,
            unit = null,
            questionText = message,
            spokenQuestion = message,
            isConversationalOnly = true
        )
        is VoiceHealthIntent.FamilyMedicationReminder -> ConfirmationDisplayData(
            title = "Lembrete Familiar",
            value = text,
            unit = timeHint,
            questionText = "Enviar lembrete de remédio \"$text\" para ${targetName ?: "seu familiar"}?",
            spokenQuestion = "Deseja enviar o lembrete de medicamento para ${targetName ?: "seu familiar"}?"
        )
        is VoiceHealthIntent.FamilyAppointment -> ConfirmationDisplayData(
            title = "Consulta / Exame",
            value = title,
            unit = dateTimeHint,
            questionText = "Agendar consulta \"$title\" para ${targetName ?: "seu familiar"}?",
            spokenQuestion = "Deseja agendar a consulta $title para ${targetName ?: "seu familiar"}?"
        )
        is VoiceHealthIntent.QueryPatientStatus -> ConfirmationDisplayData(
            title = "Consulta de Saúde",
            value = metric,
            unit = null,
            questionText = "Consultando dados de ${targetName ?: "seu familiar"}...",
            spokenQuestion = "Verificando dados de saúde...",
            isConversationalOnly = true
        )
        is VoiceHealthIntent.SuggestFood -> ConfirmationDisplayData(
            title = "Sugestão de Alimento",
            value = suggestedFood.name,
            unit = portionDescription,
            questionText = "$reason Proporção sugerida: $portionDescription.",
            spokenQuestion = "$reason Recomendo $portionDescription de ${suggestedFood.name}. Deseja adicionar à sua lista de compras?",
            clinicalSocietyName = clinicalSociety,
            clinicalStageBadge = "Sugestão Saudável",
            isConversationalOnly = false
        )
        is VoiceHealthIntent.Unknown -> ConfirmationDisplayData(
            title = "Não compreendido",
            value = rawText,
            unit = null,
            questionText = hint,
            spokenQuestion = "Não consegui entender bem o que você disse."
        )
    }
}

