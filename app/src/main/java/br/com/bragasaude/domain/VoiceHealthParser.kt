package br.com.bragasaude.domain

import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser inteligente em português (pt-BR) para registro de saúde por voz.
 *
 * Aplica expressões regulares e heurísticas clínicas para interpretar a fala
 * transcrita do usuário e auxiliar no preenchimento de sinais vitais e hábitos.
 *
 * **Ordem de detecção**:
 * 1. Pressão Arterial — "12 por 8", "13 por 8 e meio"
 * 2. Glicemia — "glicemia 105", "glicose 110"
 * 3. Hidratação — "copo de água", "250 ml", "garrafinha"
 */
@Singleton
class VoiceHealthParser @Inject constructor() {

    // ==================== NORMALIZAÇÃO ====================

    /** Remove acentos, pontuação e normaliza para lowercase pt-BR. */
    fun normalize(raw: String): String {
        val noAccents = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD)
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
        return noAccents
            .lowercase(Locale.forLanguageTag("pt-BR"))
            .replace(Regex("[.,;:!?\"'()\\[\\]]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Substitui números por extenso em português pelo equivalente numérico.
     * Ex.: "dois copos" → "2 copos"; "treze e meio" → "13.5"
     */
    private fun replaceNumbersInWords(text: String): String {
        var result = text
        val numberMap = linkedMapOf(
            "um quarto" to "0.25",
            "zero" to "0", "um" to "1", "uma" to "1", "dois" to "2", "duas" to "2",
            "tres" to "3", "quatro" to "4", "cinco" to "5",
            "seis" to "6", "sete" to "7", "oito" to "8",
            "nove" to "9", "dez" to "10", "onze" to "11",
            "doze" to "12", "treze" to "13", "quatorze" to "14", "catorze" to "14",
            "quinze" to "15", "dezesseis" to "16", "dezessete" to "17",
            "dezoito" to "18", "dezenove" to "19", "vinte" to "20",
            "trinta" to "30", "quarenta" to "40", "cinquenta" to "50",
            "sessenta" to "60", "setenta" to "70", "oitenta" to "80",
            "noventa" to "90", "cem" to "100", "cento" to "100",
            "duzentos" to "200", "trezentos" to "300", "quatrocentos" to "400",
            "quinhentos" to "500", "seiscentos" to "600", "setecentos" to "700",
            "oitocentos" to "800", "novecentos" to "900", "mil" to "1000"
        )
        numberMap.forEach { (word, digit) ->
            // substitui como palavra inteira (evita "um" dentro de "volume")
            result = result.replace(Regex("\\b$word\\b"), digit)
        }
        return result
    }

    // ==================== API PÚBLICA ====================

    /**
     * Parseia a fala transcrita e retorna a intenção detectada.
     *
     * @param rawTranscript Texto transcrito pelo SpeechRecognizer.
     * @param userRole Papel do usuário ("PATIENT", "CAREGIVER").
     * @param caregiverMode Modo do cuidador ("HYBRID", "VIEWER_ONLY", null).
     */
    fun parse(
        rawTranscript: String, 
        userRole: String? = null,
        caregiverMode: String? = null
    ): VoiceHealthIntent {
        if (rawTranscript.isBlank()) {
            return VoiceHealthIntent.Unknown(rawTranscript, "Não consegui ouvir. Fale novamente perto do microfone.")
        }

        return runCatching {
            val normalized = normalize(rawTranscript)
            val withNumbers = replaceNumbersInWords(normalized)
            
            // Verificar se usuário está em modo cuidador (HÍBRIDO ou VIEWER_ONLY)
            val isCaregiver = userRole == "CAREGIVER" || caregiverMode != null
            val isOnlyCaregiver = userRole == "CAREGIVER" && caregiverMode != "HYBRID"
            
            if (isCaregiver) {
                // Intenções específicas para cuidadores (lembretes e agendamentos do familiar)
                detectFamilyMedicationReminder(withNumbers, rawTranscript)?.let { return@runCatching it }
                detectFamilyAppointment(withNumbers, rawTranscript)?.let { return@runCatching it }
            }

            // Consultas sobre status e métricas (para cuidador consultar o familiar, ou paciente consultar suas próprias métricas)
            detectQueryPatientStatus(withNumbers, rawTranscript)?.let { return@runCatching it }

            // Conversação informal e saudações (Boa noite, Bom dia, Olá, Obrigado, Quem é você) — acolhedor para todos
            detectConversational(normalized)?.let { return@runCatching it }

            // 🛡️ TRAVA DE ACESSO: Usuário "Só Cuidador" (Acompanhante Exclusivo)
            // Não pode registrar sinais vitais próprios (pressão, glicose, peso, remédio, hidratação)
            if (isOnlyCaregiver) {
                val hasPersonalVitalsAttempt = detectBloodPressure(withNumbers, rawTranscript) != null ||
                    detectGlucose(withNumbers) != null ||
                    detectWeight(withNumbers) != null ||
                    detectHeartRate(withNumbers) != null ||
                    detectOxygenSaturation(withNumbers) != null ||
                    detectMedication(withNumbers) != null ||
                    detectHydration(withNumbers, rawTranscript) != null

                if (hasPersonalVitalsAttempt) {
                    return@runCatching VoiceHealthIntent.ConversationalReply(
                        "Você está no modo Acompanhante. Neste modo, o registro de sinais vitais próprios fica desativado. Você pode me pedir para acompanhar a saúde do seu familiar, como: 'como está a pressão do meu pai?' ou 'lembrar meu pai do remédio'!"
                    )
                }
            }

            detectFoodAction(normalized)?.let { return@runCatching it }

            // Ordem de prioridade (do mais específico ao mais genérico) - SAÚDE PESSOAL
            detectBloodPressure(withNumbers, rawTranscript)?.let { return@runCatching it }
            detectWeight(withNumbers)?.let { return@runCatching it }
            detectGlucose(withNumbers)?.let { return@runCatching it }
            detectHeartRate(withNumbers)?.let { return@runCatching it }
            detectOxygenSaturation(withNumbers)?.let { return@runCatching it }
            detectMedication(withNumbers)?.let { return@runCatching it }
            detectHydration(withNumbers, rawTranscript)?.let { return@runCatching it }

            VoiceHealthIntent.Unknown(
                rawText = rawTranscript,
                hint = "Não reconheci. Tente: \"tomei um copo de água\", \"pressão 12 por 8\" ou \"glicemia 105\"."
            )
        }.getOrElse { e ->
            runCatching { android.util.Log.e("VoiceHealthParser", "Erro inesperado ao parsear voz: ${e.message}", e) }
            VoiceHealthIntent.Unknown(
                rawText = rawTranscript,
                hint = "Não foi possível compreender. Tente falar novamente de forma pausada."
            )
        }
    }

    // ==================== PRESSÃO ARTERIAL ====================

    // "12 por 8" / "12 por 8 e meio" / "120 por 80"
    private val bpRegex = Regex(
        "(pressao|press[aã]o)?\\s*(\\d+(?:[.,]\\d+)?)\\s*(por|\\/|\\-)\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:e\\s*(mei[oa]|meio|\\d+))?",
        RegexOption.IGNORE_CASE
    )

    private fun detectBloodPressure(text: String, raw: String): VoiceHealthIntent.BloodPressure? {
        val match = bpRegex.find(text) ?: return null
        val sysPart = match.groupValues.getOrNull(2)?.replace(",", ".") ?: return null
        val diaPart = match.groupValues.getOrNull(4)?.replace(",", ".") ?: return null
        val halfModifier = match.groupValues.getOrNull(5)?.takeIf { it.isNotBlank() }

        val sysValue = sysPart.toDoubleOrNull() ?: return null
        val diaValue = diaPart.toDoubleOrNull() ?: return null

        // Heurística: se o valor é < 40, o idoso falou na escala curta (12 por 8 → 120/80).
        // Se >= 40, já está na escala mmHg (120 por 80).
        val systolic = if (sysValue < 40) (sysValue * 10).toInt() else sysValue.toInt()
        var diastolic = if (diaValue < 40) (diaValue * 10).toInt() else diaValue.toInt()

        // "e meio" / "meio" → +5 mmHg na diastólica (13 por 8 e meio → 130/85)
        if (halfModifier != null && halfModifier.startsWith("mei")) {
            diastolic += 5
        } else {
            halfModifier?.toIntOrNull()?.let { diastolic += it }
        }

        // Validação de faixa plausível (rede de segurança, sem bloquear)
        if (systolic !in 60..280 || diastolic !in 30..180) return null

        return VoiceHealthIntent.BloodPressure(
            systolic = systolic,
            diastolic = diastolic,
            rawInput = raw.trim()
        )
    }

    // ==================== PESO ====================

    // "72 quilos e meio", "pesei 72.5", "meu peso deu 72,5", "pesando 72"
    private val weightRegex = Regex(
        "(peso|pesei|pesando|balanca|balan[cç]a)?\\s*(\\d+(?:[.,]\\d+)?)\\s*(quilos?|kg|kilo)?\\s*(?:e\\s*(mei[oa]|meio|\\d+))?",
        RegexOption.IGNORE_CASE
    )

    private fun detectWeight(text: String): VoiceHealthIntent.Weight? {
        val triggerWords = listOf("peso", "pesei", "pesando", "balanca", "balanç")
        val hasTrigger = triggerWords.any { text.contains(it) }

        val match = weightRegex.find(text) ?: return null
        val hasUnit = match.groupValues[3].isNotBlank()
        if (!hasTrigger && !hasUnit) return null

        val base = match.groupValues[2].replace(",", ".").toDoubleOrNull() ?: return null
        val half = match.groupValues[4].takeIf { it.isNotBlank() }

        val extra = when {
            half == null -> 0.0
            half.startsWith("mei") -> 0.5
            else -> half.toDoubleOrNull() ?: 0.0
        }

        val weight = base + extra
        if (weight !in 20.0..300.0) return null

        return VoiceHealthIntent.Weight(weightKg = weight)
    }

    // ==================== GLICEMIA ====================

    // "glicemia 105", "glicose 110", "açúcar no sangue 98", "105 de glicose", "minha glicemia deu 105"
    private val glucosePrefixRegex = Regex(
        "(glicemia|glicose|acucar no sangue)\\s+(?:\\w+\\s+)*(\\d{2,3})",
        RegexOption.IGNORE_CASE
    )
    private val glucoseSuffixRegex = Regex(
        "(\\d{2,3})\\s*(de\\s+)?(glicemia|glicose|acucar)",
        RegexOption.IGNORE_CASE
    )

    private fun detectGlucose(text: String): VoiceHealthIntent.Glucose? {
        val prefixMatch = glucosePrefixRegex.find(text)
        val suffixMatch = glucoseSuffixRegex.find(text)
        val value = prefixMatch?.groupValues?.get(2)?.toIntOrNull()
            ?: suffixMatch?.groupValues?.get(1)?.toIntOrNull()
            ?: return null

        if (value !in 20..600) return null
        return VoiceHealthIntent.Glucose(glucoseMgDl = value)
    }

    // ==================== FREQUÊNCIA CARDÍACA ====================

    // "batimentos 72", "frequência cardíaca deu 80", "meu pulso tá 65", "72 bpm"
    private val heartRateRegex = Regex(
        "(batimentos?|frequencia\\s+cardiaca|pulso|bpm)\\s*(?:deu|esta|ta|estao|estao|com|de|e)?\\s*(\\d{2,3})",
        RegexOption.IGNORE_CASE
    )
    private val heartRateSuffixRegex = Regex(
        "(\\d{2,3})\\s*(batimentos?|frequencia\\s+cardiaca|pulso|bpm)",
        RegexOption.IGNORE_CASE
    )

    private fun detectHeartRate(text: String): VoiceHealthIntent.HeartRate? {
        val match = heartRateRegex.find(text) ?: heartRateSuffixRegex.find(text) ?: return null
        val value = (match.groupValues.getOrNull(2) ?: match.groupValues.getOrNull(1))?.toIntOrNull() ?: return null
        if (value !in 30..250) return null
        return VoiceHealthIntent.HeartRate(heartRateBpm = value)
    }

    // ==================== SATURAÇÃO DE OXIGênIO ====================

    // "spo2 98", "oxigenação 95", "saturação deu 97", "98 de saturação"
    private val oxygenRegex = Regex(
        "(spo\\s*2|oxigenacao|saturacao|oxigenio|oximetro)\\s*(?:deu|esta|ta|estao|com|de|e)?\\s*(\\d{2,3})",
        RegexOption.IGNORE_CASE
    )
    private val oxygenSuffixRegex = Regex(
        "(\\d{2,3})\\s*(spo\\s*2|oxigenacao|saturacao|oxigenio|oximetro)",
        RegexOption.IGNORE_CASE
    )

    private fun detectOxygenSaturation(text: String): VoiceHealthIntent.OxygenSaturation? {
        val match = oxygenRegex.find(text) ?: oxygenSuffixRegex.find(text) ?: return null
        val value = (match.groupValues.getOrNull(2) ?: match.groupValues.getOrNull(1))?.toIntOrNull() ?: return null
        if (value !in 50..100) return null
        return VoiceHealthIntent.OxygenSaturation(oxygenPercent = value)
    }

    // ==================== CONVERSAÇÃO INFORMAL ====================

    private val identityRegex = Regex(
        "(qual\\s+(e\\s+)?seu\\s+nome|qual\\s+o\\s+seu\\s+nome|quem\\s+e\\s+voce|quem\\s+es\\s+voce|voce\\s+e\\s+quem|qual\\s+seu\\s+nome)",
        RegexOption.IGNORE_CASE
    )
    private val capabilitiesRegex = Regex(
        "(o\\s+que\\s+voce\\s+(faz|sabe\\s+fazer|pode\\s+fazer)|como\\s+voce\\s+funciona|para\\s+que\\s+voce\\s+serve|voce\\s+serve\\s+para\\s+que)",
        RegexOption.IGNORE_CASE
    )
    private val goodNightRegex = Regex(
        "\\b(boa\\s+noite|vou\\s+dormir|bom\\s+descanso|ate\\s+amanha)\\b",
        RegexOption.IGNORE_CASE
    )
    private val goodMorningRegex = Regex(
        "\\b(bom\\s+dia|acordei|otimo\\s+dia|excelente\\s+dia)\\b",
        RegexOption.IGNORE_CASE
    )
    private val goodAfternoonRegex = Regex(
        "\\b(boa\\s+tarde|otima\\s+tarde)\\b",
        RegexOption.IGNORE_CASE
    )
    private val greetingRegex = Regex(
        "\\b(ola|oi|ola\\s+braga|oi\\s+braga|tudo\\s+bem|como\\s+vai|e\\s+ai|como\\s+voce\\s+esta)\\b",
        RegexOption.IGNORE_CASE
    )
    private val thanksRegex = Regex(
        "\\b(obrigado|obrigada|valeu|agradecido|agradecida|muito\\s+obrigado|muito\\s+obrigada|grato|grata|valeu\\s+braga)\\b",
        RegexOption.IGNORE_CASE
    )
    private val timeQueryRegex = Regex(
        "^(?:(?:braga|por favor) )?(?:que horas sao|que horas sao agora|qual (?:e )?a hora(?: agora)?|me (?:diz|diga|fale) as horas|diga a hora|hora exata)(?: por favor)?$"
    )
    private val dateQueryRegex = Regex(
        "^(?:(?:braga|por favor) )?(?:que dia (?:e )?hoje|qual (?:e )?(?:o dia|a data)(?: de hoje| hoje)?|data de hoje|dia de hoje)(?: por favor)?$"
    )

    /** Só responde localmente a falas completas, sem esconder a continuação do usuário. */
    fun detectConversational(text: String): VoiceHealthIntent.ConversationalReply? {
        LocalCalendarAnswers.answer(text)?.let { return VoiceHealthIntent.ConversationalReply(it) }
        val normalized = normalize(text)
        val simple = normalized.replace(Regex("\\bbraga\\b"), "").trim().replace(Regex("\\s+"), " ")
        val message = when {
            identityRegex.matches(simple) -> "Eu sou o Braga, o assistente virtual de rotina e autocuidado do Braga Saúde."
            capabilitiesRegex.matches(simple) -> "Posso conversar e preparar água, pressão e glicemia para você conferir e confirmar na tela."
            timeQueryRegex.matches(simple) -> {
                val cal = Calendar.getInstance()
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val minute = cal.get(Calendar.MINUTE)
                val hours = if (hour == 1) "É 1 hora" else "São $hour horas"
                if (minute == 0) "$hours." else "$hours e $minute ${if (minute == 1) "minuto" else "minutos"}."
            }
            dateQueryRegex.matches(simple) -> {
                val dateFormat = java.text.SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))
                "Hoje é ${dateFormat.format(Calendar.getInstance().time)}."
            }
            goodMorningRegex.matches(simple) -> "Bom dia! Como está sendo seu dia?"
            goodAfternoonRegex.matches(simple) -> "Boa tarde! Como está sendo seu dia?"
            goodNightRegex.matches(simple) -> "Boa noite! Como foi seu dia?"
            thanksRegex.matches(simple) -> "De nada!"
            greetingRegex.matches(simple) || simple == "bom dia como voce esta" -> "Olá! Como você está?"
            else -> null
        }
        return message?.let { VoiceHealthIntent.ConversationalReply(it) }
    }

    // Conversas sobre comida seguem para a IA; somente pedidos de ação recebem orientação local.
    private fun detectFoodAction(text: String): VoiceHealthIntent.ConversationalReply? {
        val action = Regex("\\b(adicion\\w*|registr\\w*|anot\\w*|abrir|abra|abr\\w*|coloc\\w*)\\b").containsMatchIn(text)
        val food = Regex("\\b(refeicao|refeicoes|alimento|alimentos|alimentacao|lista de compras|almoco|jantar|cafe da manha|lanche|arroz|feijao|frango|banana|maca|leite|aveia|chia)\\b").containsMatchIn(text)
        return if (action && food) VoiceHealthIntent.ConversationalReply(
            "Para registrar refeições ou organizar sua lista de compras, use a tela de Alimentação. Ainda não faço esse registro por voz."
        ) else null
    }

    // ==================== MEDICAMENTO ====================

    // "tomei meu remédio da pressão", "tomei o remédio das 8 horas"
    private val medTriggerRegex = Regex(
        "(tomei|tomei\\s+o|tomei\\s+meu|tomei\\s+minha)?\\s*" +
            "(rem[eé]dio|comprimido|medicamento|remedios|rem[eé]dios|comprimidos)\\s+" +
            "(da|do|de|das|dos|para)?\\s*(.+)",
        RegexOption.IGNORE_CASE
    )
    private val timeHintRegex = Regex(
        "(?:das|às?|a)?\\s*(\\d{1,2})(?:\\s*(?:horas?|h|da\\s+manha|da\\s+tarde|da\\s+noite))?",
        RegexOption.IGNORE_CASE
    )

    private fun detectMedication(text: String): VoiceHealthIntent.Medication? {
        val match = medTriggerRegex.find(text) ?: return null
        val query = match.groupValues[4]
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { return null }

        val scheduleTimeHint = timeHintRegex.find(query)?.let { timeMatch ->
            val hour = timeMatch.groupValues[1].toIntOrNull()
            hour?.let { String.format("%02d:00", it) }
        }

        return VoiceHealthIntent.Medication(
            query = query,
            scheduleTimeHint = scheduleTimeHint
        )
    }
    
    // ==================== INTENÇÕES FAMILIARES (CUIDADOR) ====================
    
    // Detecta lembretes de medicação para familiar
    private val familyMedRegex = Regex(
        "(lembra[r]?|avisa[r]?|diga[r]?)\\s+" +
        "(?:meu|minha|o|a|ao|à|pro|pra o|pra a|para o|para a)\\s+" +
        "([a-z]+)" +  // nome do familiar (pai, mãe, filho, etc.)
        "\\s+(?:de\\s+)?(?:tomar|pegar|beber)\\s+" +
        "(?:o|a|um|uma|seu|sua)?\\s*" +
        "(rem[eé]dio|medicamento|comprimido)?",
        RegexOption.IGNORE_CASE
    )
    
    private fun detectFamilyMedicationReminder(text: String, raw: String): VoiceHealthIntent.FamilyMedicationReminder? {
        val match = familyMedRegex.find(text) ?: return null
        
        val targetName = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
        val textMsg = match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() } ?: "remédio"
        
        // Extrair horário se mencionado
        val timeMatch = timeHintRegex.find(text)
        val timeHint = timeMatch?.let { 
            val hour = it.groupValues[1].toIntOrNull()
            hour?.let { h -> String.format("%02d:00", h) }
        }
        
        return VoiceHealthIntent.FamilyMedicationReminder(
            targetName = targetName ?: "familiar",
            text = textMsg.replace(Regex("\\s+"), " ").trim(),
            timeHint = timeHint
        )
    }
    
    // Detecta agendamentos para familiar
    private val familyApptRegex = Regex(
        "(agenda[r]?|marca[r]?)\\s+" +
        "(?:uma|um|pro|para o|para a)\\s+" +
        "(consulta|apontamento|reunião|consulta com)" +
        "(?:\\s+(?:com|para|do|da))?\\s*" +
        "([a-zA-Z]+(?:\\s+[a-zA-Z]+)?)?" +
        "(?:\\s+([a-z]+))?",
        RegexOption.IGNORE_CASE
    )
    
    private fun detectFamilyAppointment(text: String, raw: String): VoiceHealthIntent.FamilyAppointment? {
        val match = familyApptRegex.find(text) ?: return null
        
        val title = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "consulta"
        val targetName = match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
        
        // Extrair data/hora aproximada
        val dateTimePattern = Regex("(segunda|terça|quarta|quinta|sexta|sábado|domingo|amanha|manhã|tarde|noite)|(\\d{1,2})\\s*(?:h|horas)?")
        val dateTimeHint = dateTimePattern.find(text)?.value?.takeIf { it.isNotBlank() }
        
        return VoiceHealthIntent.FamilyAppointment(
            targetName = targetName ?: "familiar",
            title = title.replace(Regex("\\s+"), " ").trim(),
            dateTimeHint = dateTimeHint
        )
    }
    
    // Detecta consultas sobre status do paciente ou usuário
    private val queryPatientRegex = Regex(
        "(como|qual|onde|quando)" +
        "\\s*(está|esta|ficou|foi|está indo|anda|estao|estão)" +
        "\\s+(?:a|o|as|os|minha|meu|minhas|meus|sua|seu)?\\s*" +
        "(pressão|pressao|glicose|glicemia|agua|água|peso|passos|sinais|metrica|metricas|métrica|métricas|saude|saúde)?",
        RegexOption.IGNORE_CASE
    )
    

    
    private fun detectQueryPatientStatus(text: String, raw: String): VoiceHealthIntent.QueryPatientStatus? {
        val match = queryPatientRegex.find(text) ?: return null
        
        val rawMetric = match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
        val metric = when (rawMetric?.lowercase()) {
            "pressao", "pressão" -> "pressão"
            "agua", "água" -> "água"
            "metrica", "metricas", "métrica", "métricas" -> "métricas"
            "glicemia", "glicose" -> "glicemia"
            "peso" -> "peso"
            "passos" -> "passos"
            "sinais" -> "sinais"
            "saude", "saúde" -> "saúde"
            else -> if (rawMetric.isNullOrBlank()) "status geral" else rawMetric
        }
        
        // Tentar extrair nome do familiar, descartando nomes de métricas (ex: "minha pressão")
        val metricNames = setOf("pressao", "pressão", "glicose", "glicemia", "agua", "água", "peso", "passos", "sinais", "metrica", "metricas", "métrica", "métricas", "saude", "saúde")
        val namePattern = Regex("(meu|minha|seu|sua)\\s+([a-z]+)")
        val nameMatch = namePattern.find(text)
        val extractedName = nameMatch?.groupValues?.getOrNull(2)?.takeIf { it.isNotBlank() }
        val targetName = if (extractedName != null && extractedName.lowercase() !in metricNames) extractedName else null
        
        return VoiceHealthIntent.QueryPatientStatus(
            metric = metric,
            targetName = targetName
        )
    }

    // ==================== HIDRATAÇÃO ====================

    // "um copo de água" → 250ml
    // "dois copos" → 500ml
    // "meio copo" → 125ml
    // "garrafinha" → 500ml
    // "garrafa" → 1000ml
    // "500 ml" → 500
    private val hydrationContainerRegex = Regex(
        "(\\d+(?:[.,]\\d+)?)\\s*(copos?|garrafinhas?|garrafas?|xicaras?|x[ií]caras?|copinhos?|canecas?|litros?|litro|l|ml|mililitros?)",
        RegexOption.IGNORE_CASE
    )
    private val hydrationWaterRegex = Regex(
        "agua|a[gɡ]ua|h2o|hidrata",
        RegexOption.IGNORE_CASE
    )

    private fun detectHydration(text: String, raw: String): VoiceHealthIntent? {
        // Descrever um recipiente ou pedir para lembrar seu tamanho não relata consumo.
        val intake = Regex("\\b(?:bebi|tomei|consumi|registre|registra|registrar|anote|anota|adicionar|adicione)\\b").containsMatchIn(text)
        val bareAmount = Regex("(?:agua\\s+)?\\d+(?:[.,]\\d+)?\\s*(?:copos?|garrafinhas?|garrafas?|xicaras?|copinhos?|canecas?|litros?|l|ml|mililitros?)(?:\\s+de\\s+agua)?").matches(text)
        if (!intake && !bareAmount) return null
        // Precisa mencionar água OU recipiente para ser hidratação
        val mentionsWater = hydrationWaterRegex.containsMatchIn(text)
        val containerMatch = hydrationContainerRegex.find(text)

        if (!mentionsWater && containerMatch == null) return null
        if (containerMatch == null && mentionsWater) {
            // "bebi água" sem quantidade → 1 copo padrão
            return VoiceHealthIntent.Hydration(
                amountMl = 250,
                description = "1 copo de água (250 ml)"
            )
        }

        val amountStr = containerMatch!!.groupValues[1].replace(",", ".")
        val unit = containerMatch.groupValues[2].lowercase(Locale.ROOT)

        val unitMl = when {
            unit.startsWith("ml") || unit.startsWith("mililitro") -> 1
            unit.startsWith("l") || unit == "litro" || unit == "litros" -> 1000
            unit.startsWith("cop") -> 250
            unit.startsWith("garrafinha") -> 500
            unit.startsWith("garrafa") -> 1000
            unit.startsWith("xicara") || unit.startsWith("xícara") -> 200
            unit.startsWith("caneca") -> 350
            else -> 250
        }

        val amount = amountStr.toDoubleOrNull() ?: return null
        val totalMl = (amount * unitMl).toInt()

        if (totalMl !in 50..8000) return null

        val readableAmount = if (amount == amount.toInt().toDouble()) amount.toInt().toString() else amount.toString()
        val readableUnit = when {
            unit.startsWith("cop") -> if (amount == 1.0) "copo" else "copos"
            unit.startsWith("garrafinha") -> if (amount == 1.0) "garrafinha" else "garrafinhas"
            unit.startsWith("garrafa") -> if (amount == 1.0) "garrafa" else "garrafas"
            unit.startsWith("ml") -> "ml"
            unit.startsWith("l") -> "litros"
            else -> unit
        }

        // 🛡️ Filtro de Bom Senso Fisiológico: Ingestão de água > 1500 ml de uma só vez
        if (totalMl > 1500) {
            val cupsEquiv = (amount * 250).toInt()
            return VoiceHealthIntent.ClarificationRequired(
                originalText = raw,
                questionPrompt = "Você disse $readableAmount $readableUnit de água ($totalMl ml). Beber tanta água de uma vez só pode fazer mal. Você quis dizer $readableAmount copos ou foi ao longo de todo o dia?",
                options = listOf(
                    ClarificationOption(
                        id = "cups_equiv",
                        label = "🥛 Eram $readableAmount copos (${cupsEquiv} ml)",
                        actionType = ClarificationActionType.LOG_HYDRATION_CUPS
                    ),
                    ClarificationOption(
                        id = "day_total",
                        label = "💧 Foi ao longo do dia todo ($totalMl ml)",
                        actionType = ClarificationActionType.LOG_HYDRATION_FULL
                    ),
                    ClarificationOption(
                        id = "cancel",
                        label = "✏️ Corrigir quantidade",
                        actionType = ClarificationActionType.CANCEL
                    )
                ),
                detectedFoodItems = listOf("$totalMl ml de água")
            )
        }

        return VoiceHealthIntent.Hydration(
            amountMl = totalMl,
            description = "$readableAmount $readableUnit de água ($totalMl ml)"
        )
    }

}
