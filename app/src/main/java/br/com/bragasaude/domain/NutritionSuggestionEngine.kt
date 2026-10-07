package br.com.bragasaude.domain

import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.local.ExamItemEntity
import br.com.bragasaude.data.local.FoodEntity
import br.com.bragasaude.data.local.VitalSignEntity
import br.com.bragasaude.data.remote.model.RemoteProfile

/**
 * Item individual de alimento sugerido com contexto de bem-estar.
 */
data class SuggestedFoodOption(
    val foodId: String,
    val name: String,
    val category: String,
    val portionTip: String,
    val functionalBenefit: String,
    val kcal: Double,
    val isDiabetesSafe: Boolean = true,
    val isHypertensionSafe: Boolean = true,
    val servingSizeGrams: Int = 100,
    val servingUnit: String = "100g",
    val minServingGrams: Int = 1,
    val maxServingGrams: Int = 500
)

/**
 * Grupo temático de sugestão nutricional (ex: Equilíbrio da Glicose, Proteção Vascular).
 */
data class NutritionalSuggestionGroup(
    val id: String,
    val title: String,
    val subtitle: String,
    val observedMarker: String,
    val targetNutrientGoal: String,
    val whyItMatters: String,
    val options: List<SuggestedFoodOption>,
    val disclaimer: String = "Sugestões de alimentos naturais para o seu dia a dia. Você tem total liberdade de escolha. Não substitui consulta médica ou plano nutricional individualizado."
)

/**
 * 🧠 Cérebro de Sugestões Nutricionais — Braga Saúde
 *
 * Cruza marcadores laboratoriais e sinais vitais autorreportados do usuário
 * com um catálogo rico de alimentos funcionais, gerando múltiplos caminhos
 * de escolha (frutas, sementes, vegetais, temperos) no campo de autocuidado.
 */
object NutritionSuggestionEngine {

    fun generateSuggestions(
        exams: List<ExamItemEntity>,
        vitals: List<VitalSignEntity>,
        profile: RemoteProfile?,
        catalog: List<FoodEntity>,
        selectedMealType: String = "Café da Manhã",
        dislikedFoodNames: Set<String> = emptySet(),
        loggedFoodNamesToday: Set<String> = emptySet(),
        pantryFoodNames: Set<String> = emptySet()
    ): List<NutritionalSuggestionGroup> {
        val groups = mutableListOf<NutritionalSuggestionGroup>()
        val dayOfYear = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)

        val availableFoods = catalog.filter { food ->
            val nameClean = food.name.trim().lowercase()
            val notDisliked = !dislikedFoodNames.any { it.trim().lowercase() == nameClean }
            val notLoggedToday = !loggedFoodNamesToday.any { it.trim().lowercase() == nameClean }
            val matchesMeal = food.suitableMeals.isEmpty() || food.suitableMeals.contains(selectedMealType)
            val allergySafe = isSafeFromAllergies(food, profile?.foodAllergies ?: emptyList(), profile?.customFoodRestrictions)
            notDisliked && notLoggedToday && matchesMeal && allergySafe
        }

        // 1. ANÁLISE DE GLICOSE / HbA1c
        val labGlucose = exams.firstOrNull { it.itemKey == "glucose" }?.valueNumeric
        val labHba1c = exams.firstOrNull { it.itemKey == "hba1c" }?.valueNumeric
        val recentVitalGlucose = vitals.firstOrNull { it.glucoseLevel != null }?.glucoseLevel?.toDouble()
        val glucoseVal = labGlucose ?: recentVitalGlucose

        if ((glucoseVal != null && glucoseVal > 100.0) || (labHba1c != null && labHba1c > 5.7) || profile?.hasDiabetes == true) {
            val markerText = when {
                glucoseVal != null -> "Glicemia recente em ${glucoseVal.toInt()} mg/dL"
                labHba1c != null -> "HbA1c em $labHba1c%"
                else -> "Acompanhamento de Glicemia"
            }

            val matchingFoods = availableFoods
                .filter { it.isDiabetesSafe && (it.functionalTags.contains("fibra_soluvel") || it.functionalTags.contains("baixo_ig") || it.functionalTags.contains("antioxidante_glicemia")) }
                .selectStableSuggestions(dayOfYear, groupSalt = 101, pantryFoodNames = pantryFoodNames)
                .map { food ->
                    food.toSuggestedOption(
                        defaultBenefit = "Rico em fibras solúveis que ajudam a modular a absorção de glicose após as refeições.",
                        isPantry = pantryFoodNames.any { it.trim().equals(food.name.trim(), ignoreCase = true) }
                    )
                }

            if (matchingFoods.isNotEmpty()) {
                groups.add(
                    NutritionalSuggestionGroup(
                        id = "glucose_balance",
                        title = "Equilíbrio da Glicemia",
                        subtitle = "Opções com fibras e baixo índice glicêmico",
                        observedMarker = markerText,
                        targetNutrientGoal = "Fibras Solúveis & Polifenóis",
                        whyItMatters = "Alimentos ricos em fibras solúveis formam um gel no estômago que torna a absorção dos carboidratos mais gradual e estável.",
                        options = matchingFoods
                    )
                )
            }
        }

        // 2. ANÁLISE DE COLESTEROL TOTAL / LDL
        val labCholesterol = exams.firstOrNull { it.itemKey == "total_cholesterol" }?.valueNumeric
        val labLdl = exams.firstOrNull { it.itemKey == "ldl" }?.valueNumeric

        if ((labCholesterol != null && labCholesterol > 190.0) || (labLdl != null && labLdl > 130.0)) {
            val markerText = when {
                labCholesterol != null -> "Colesterol Total em ${labCholesterol.toInt()} mg/dL"
                labLdl != null -> "LDL em ${labLdl.toInt()} mg/dL"
                else -> "Acompanhamento Lipídico"
            }

            val matchingFoods = availableFoods
                .filter { it.functionalTags.contains("fitoesterol") || it.functionalTags.contains("beta_glucana") || it.functionalTags.contains("gordura_boa") || it.functionalTags.contains("omega3") }
                .selectStableSuggestions(dayOfYear, groupSalt = 202, pantryFoodNames = pantryFoodNames)
                .map { food ->
                    food.toSuggestedOption(
                        defaultBenefit = "Possui fitoesteróis e gorduras insaturadas que auxiliam no equilíbrio natural das frações de colesterol.",
                        isPantry = pantryFoodNames.any { it.trim().equals(food.name.trim(), ignoreCase = true) }
                    )
                }

            if (matchingFoods.isNotEmpty()) {
                groups.add(
                    NutritionalSuggestionGroup(
                        id = "cholesterol_balance",
                        title = "Controle do Colesterol",
                        subtitle = "Fitoesteróis, Beta-glucanas e Gorduras Boas",
                        observedMarker = markerText,
                        targetNutrientGoal = "Fitoesteróis & Fibras de Aveia",
                        whyItMatters = "Fibras como a beta-glucana se ligam aos ácidos biliares no intestino, incentivando o organismo a equilibrar o colesterol circulante.",
                        options = matchingFoods
                    )
                )
            }
        }

        // 3. ANÁLISE DE TRIGLICERÍDEOS
        val labTriglycerides = exams.firstOrNull { it.itemKey == "triglycerides" }?.valueNumeric

        if (labTriglycerides != null && labTriglycerides > 150.0) {
            val matchingFoods = availableFoods
                .filter { it.functionalTags.contains("omega3") || it.functionalTags.contains("fibra_prebiotica") || it.functionalTags.contains("cha_antioxidante") }
                .selectStableSuggestions(dayOfYear, groupSalt = 303, pantryFoodNames = pantryFoodNames)
                .map { food ->
                    food.toSuggestedOption(
                        defaultBenefit = "Fonte de ácidos graxos essenciais e antioxidantes que favorecem o metabolismo das gorduras.",
                        isPantry = pantryFoodNames.any { it.trim().equals(food.name.trim(), ignoreCase = true) }
                    )
                }

            if (matchingFoods.isNotEmpty()) {
                groups.add(
                    NutritionalSuggestionGroup(
                        id = "triglycerides_balance",
                        title = "Equilíbrio dos Triglicerídeos",
                        subtitle = "Fontes de Ômega-3 vegetal e prebióticos",
                        observedMarker = "Triglicerídeos em ${labTriglycerides.toInt()} mg/dL",
                        targetNutrientGoal = "Ômega-3 & Compostos Bioativos",
                        whyItMatters = "O aporte de gorduras boas e folhas escuras apoia o fígado no processamento e transporte lipídico saudável.",
                        options = matchingFoods
                    )
                )
            }
        }

        // 4. ANÁLISE DE PRESSÃO ARTERIAL
        val recentBp = vitals.firstOrNull { it.systolicPressure != null }
        val sys = br.com.bragasaude.domain.util.BloodPressureParser.normalizePressure(recentBp?.systolicPressure ?: 0)
        val dia = br.com.bragasaude.domain.util.BloodPressureParser.normalizePressure(recentBp?.diastolicPressure ?: 0)

        if ((sys > 130 || dia > 85) || profile?.hasHypertension == true) {
            val markerText = if (sys > 0) "Pressão recente em $sys/$dia mmHg" else "Acompanhamento Pressórico"

            val matchingFoods = availableFoods
                .filter { it.isHypertensionSafe && (it.functionalTags.contains("nitrato_natural") || it.functionalTags.contains("potassio") || it.functionalTags.contains("magnesio") || it.functionalTags.contains("baixo_sodio")) }
                .selectStableSuggestions(dayOfYear, groupSalt = 404, pantryFoodNames = pantryFoodNames)
                .map { food ->
                    food.toSuggestedOption(
                        defaultBenefit = "Rico em minerais como potássio e nitratos naturais que auxiliam no relaxamento e flexibilidade dos vasos.",
                        isPantry = pantryFoodNames.any { it.trim().equals(food.name.trim(), ignoreCase = true) }
                    )
                }

            if (matchingFoods.isNotEmpty()) {
                groups.add(
                    NutritionalSuggestionGroup(
                        id = "blood_pressure_vascular",
                        title = "Saúde Vascular & Pressão",
                        subtitle = "Potássio, Magnésio e Nitratos Naturais",
                        observedMarker = markerText,
                        targetNutrientGoal = "Potássio & Vasodilatadores Naturais",
                        whyItMatters = "O potássio atua contrabalançando o sódio e os nitratos presentes em vegetais promovem a dilatação endotelial saudável.",
                        options = matchingFoods
                    )
                )
            }
        }

        // 5. ANÁLISE DE ÁCIDO ÚRICO
        val labUricAcid = exams.firstOrNull { it.itemKey == "uric_acid" }?.valueNumeric

        if (labUricAcid != null && labUricAcid > 6.8) {
            val matchingFoods = availableFoods
                .filter { it.functionalTags.contains("vitamina_c") || it.functionalTags.contains("alcalinizante") || it.functionalTags.contains("hidratante") }
                .selectStableSuggestions(dayOfYear, groupSalt = 505, pantryFoodNames = pantryFoodNames)
                .map { food ->
                    food.toSuggestedOption(
                        defaultBenefit = "Possui compostos alcalinizantes e vitamina C que estimulam a eliminação renal do ácido úrico.",
                        isPantry = pantryFoodNames.any { it.trim().equals(food.name.trim(), ignoreCase = true) }
                    )
                }

            if (matchingFoods.isNotEmpty()) {
                groups.add(
                    NutritionalSuggestionGroup(
                        id = "uric_acid_balance",
                        title = "Equilíbrio do Ácido Úrico",
                        subtitle = "Alimentos hidratantes e fontes de Vitamina C",
                        observedMarker = "Ácido Úrico em $labUricAcid mg/dL",
                        targetNutrientGoal = "Vitamina C & Hidratação",
                        whyItMatters = "Alimentos de alto teor hídrico e antioxidantes auxiliam os rins na filtragem e excreção de cristais de urato.",
                        options = matchingFoods
                    )
                )
            }
        }

        // 6. SUGESTÕES BASE DE EQUILÍBRIO & VARIEDADE (se nenhum exame estiver alterado)
        if (groups.isEmpty()) {
            val generalFoods = availableFoods
                .selectStableSuggestions(dayOfYear, groupSalt = 606 + selectedMealType.hashCode(), pantryFoodNames = pantryFoodNames)
                .map { food ->
                    food.toSuggestedOption(
                        defaultBenefit = "Alimento natural brasileiro rico em nutrientes essenciais para o seu bem-estar diário.",
                        isPantry = pantryFoodNames.any { it.trim().equals(food.name.trim(), ignoreCase = true) }
                    )
                }

            if (generalFoods.isNotEmpty()) {
                val (title, subtitle, nutrientGoal, whyMatters) = when (selectedMealType) {
                    "Café da Manhã" -> Quadruple(
                        "Vitalidade & Fibras",
                        "Opções matinais para iniciar o seu dia com equilíbrio",
                        "Fibras & Energia Limpa",
                        "Alimentos naturais ricos em fibras promovem saciedade duradoura e disposição constante ao longo da manhã."
                    )
                    "Almoço" -> Quadruple(
                        "Equilíbrio & Nutrição",
                        "Vegetais frescos, legumes e minerais essenciais",
                        "Minerais & Fibras Estruturais",
                        "A combinação de folhas verdes, leguminosas e legumes coloridos fornece variedade de antioxidantes e ferro."
                    )
                    "Lanche da Tarde" -> Quadruple(
                        "Pausa Leve & Saciedade",
                        "Frutas frescas, sementes e hidratação",
                        "Antioxidantes & Hidratação",
                        "Pequenas porções de oleaginosas e frutas ricas em água mantêm a saciedade e a hidratação até a noite."
                    )
                    else -> Quadruple(
                        "Jantar Leve & Digestão Suave",
                        "Preparo suave para um descanso tranquilo",
                        "Digestibilidade & Relaxamento",
                        "Alimentos de fácil mastigação e infusões aromáticas preparam o organismo para um sono reparador."
                    )
                }

                groups.add(
                    NutritionalSuggestionGroup(
                        id = "meal_balance_${selectedMealType.lowercase().replace(" ", "_")}",
                        title = title,
                        subtitle = subtitle,
                        observedMarker = "Longevidade & Saúde",
                        targetNutrientGoal = nutrientGoal,
                        whyItMatters = whyMatters,
                        options = generalFoods
                    )
                )
            }
        }

        return groups
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun FoodEntity.toSuggestedOption(defaultBenefit: String, isPantry: Boolean = false): SuggestedFoodOption {
        val pantryBadge = if (isPantry) "Na sua despensa • " else ""
        return SuggestedFoodOption(
            foodId = remoteId,
            name = name,
            category = category ?: "Alimento",
            portionTip = "$pantryBadge${consumptionTip ?: "Adicione uma porção moderada junto à sua refeição principal."}",
            functionalBenefit = healthBenefits ?: defaultBenefit,
            kcal = kcal ?: 0.0,
            isDiabetesSafe = isDiabetesSafe,
            isHypertensionSafe = isHypertensionSafe,
            servingSizeGrams = servingSizeGrams,
            servingUnit = servingUnit,
            minServingGrams = minServingGrams,
            maxServingGrams = maxServingGrams
        )
    }

    fun isSafeFromAllergies(
        food: FoodEntity,
        allergies: List<String>,
        customRestrictions: String?
    ): Boolean {
        val nameLower = food.name.lowercase()
        val catLower = (food.category ?: "").lowercase()

        // 1. Alergia a Lactose / Leite
        if (allergies.any { it.contains("Lactose", ignoreCase = true) || it.contains("Leite", ignoreCase = true) }) {
            if (catLower.contains("laticínios") || catLower.contains("queijo") ||
                nameLower.contains("leite") || nameLower.contains("iogurte") ||
                nameLower.contains("coalhada") || nameLower.contains("ricota") ||
                nameLower.contains("minas") || nameLower.contains("cottage")
            ) {
                return false
            }
        }

        // 2. Alergia a Glúten / Celíaco
        if (allergies.any { it.contains("Glúten", ignoreCase = true) || it.contains("Celíaco", ignoreCase = true) }) {
            if (nameLower.contains("trigo") || nameLower.contains("cevadinha") ||
                (nameLower.contains("macarrão") && !nameLower.contains("arroz") && !nameLower.contains("grão-de-bico"))
            ) {
                return false
            }
        }

        // 3. Alergia a Frutos do Mar / Crustáceos
        if (allergies.any { it.contains("Frutos do Mar", ignoreCase = true) }) {
            if (nameLower.contains("camarão") || nameLower.contains("lula") || nameLower.contains("marisco")) {
                return false
            }
        }

        // 4. Alergia a Oleaginosas / Amendoim / Castanhas
        if (allergies.any { it.contains("Castanha", ignoreCase = true) || it.contains("Amendoim", ignoreCase = true) }) {
            if (catLower.contains("oleaginosas") || nameLower.contains("amendoim") ||
                nameLower.contains("castanha") || nameLower.contains("nozes") ||
                nameLower.contains("amêndoa") || nameLower.contains("macadâmia")
            ) {
                return false
            }
        }

        // 5. Alergia a Ovos
        if (allergies.any { it.contains("Ovo", ignoreCase = true) }) {
            if (nameLower.contains("ovo") || nameLower.contains("omelete")) {
                return false
            }
        }

        // 6. Alergia a Peixes
        if (allergies.any { it.contains("Peixe", ignoreCase = true) }) {
            if (catLower.contains("peixes") || nameLower.contains("tilápia") ||
                nameLower.contains("sardinha") || nameLower.contains("atum") ||
                nameLower.contains("pescada") || nameLower.contains("merluza") ||
                nameLower.contains("bacalhau") || nameLower.contains("cação") ||
                nameLower.contains("salmão") || nameLower.contains("badejo")
            ) {
                return false
            }
        }

        // 7. Restrições personalizadas em texto livre
        if (!customRestrictions.isNullOrBlank()) {
            val terms = customRestrictions.split(",", ";", " ")
                .map { it.trim().lowercase() }
                .filter { it.length >= 3 }

            if (terms.any { term -> nameLower.contains(term) }) {
                return false
            }
        }

        return true
    }

    /**
     * Seleção determinística e estável de sugestões de alimentos.
     * Prioriza alimentos presentes na despensa do usuário e ordena os demais
     * utilizando um hash determinístico baseado no dia do ano e no sal do grupo clínico.
     * Isso impede que os cards fiquem trocando em loop a cada recomposição ou emissão do Room.
     */
    private fun List<FoodEntity>.selectStableSuggestions(
        dayOfYear: Int,
        groupSalt: Int,
        pantryFoodNames: Set<String>,
        limit: Int = 4
    ): List<FoodEntity> {
        return this
            .sortedWith(
                compareByDescending<FoodEntity> { food ->
                    if (pantryFoodNames.any { it.trim().equals(food.name.trim(), ignoreCase = true) }) 1 else 0
                }.thenBy { food ->
                    computeStableRank(food.name, dayOfYear, groupSalt)
                }
            )
            .distinctBy { it.category }
            .take(limit)
    }

    private fun computeStableRank(name: String, dayOfYear: Int, groupSalt: Int): Long {
        var h = (name.trim().lowercase().hashCode().toLong() and 0xFFFFFFFFL) xor (dayOfYear.toLong() * 1000003L) xor (groupSalt.toLong() * 9973L)
        h = h xor (h ushr 16)
        h = h * 0x85ebca6bL
        h = h xor (h ushr 13)
        h = h * 0xc2b2ae35L
        h = h xor (h ushr 16)
        return h
    }

    /**
     * 🥗 Sugere um alimento específico dentre os 220 itens do banco de dados,
     * respeitando o perfil clínico do paciente, sem jamais verbalizar que ele está "acima do peso",
     * e calculando proporções respaldadas nas diretrizes da SBD (diabetes) e SBC (cardiologia).
     */
    fun suggestFoodForUser(
        profile: ProfileEntity? = null,
        remoteProfile: RemoteProfile? = null,
        vitals: List<VitalSignEntity> = emptyList(),
        exams: List<ExamItemEntity> = emptyList(),
        catalog: List<FoodEntity>,
        userWeightKg: Double? = null,
        specificConditionOrQuery: String? = null
    ): ClinicalFoodSuggestion {
        val effectiveWeight = userWeightKg 
            ?: profile?.weight?.toDouble() 
            ?: remoteProfile?.weight?.toDouble() 
            ?: 70.0
        val effectiveHeight = profile?.height?.toFloat() 
            ?: remoteProfile?.height?.toFloat() 
            ?: 1.65f
        val imc = HealthCalculators.calculateIMC(effectiveWeight.toFloat(), effectiveHeight)
        val birthDate = profile?.birthDate ?: remoteProfile?.birthDate
        val isElderly = birthDate?.let { HealthCalculators.calculateAge(it) >= 60 } ?: false
        val hasDiabetes = profile?.hasDiabetes == true || remoteProfile?.hasDiabetes == true
        val hasHypertension = profile?.hasHypertension == true || remoteProfile?.hasHypertension == true

        // Verificação de glicemia recente
        val labGlucose = exams.firstOrNull { it.itemKey == "glucose" }?.valueNumeric
        val recentVitalGlucose = vitals.firstOrNull { it.glucoseLevel != null }?.glucoseLevel?.toDouble()
        val glucoseVal = labGlucose ?: recentVitalGlucose

        // Verificação de pressão arterial recente
        val recentBp = vitals.firstOrNull { it.systolicPressure != null }
        val sys = br.com.bragasaude.domain.util.BloodPressureParser.normalizePressure(recentBp?.systolicPressure ?: 0)
        val dia = br.com.bragasaude.domain.util.BloodPressureParser.normalizePressure(recentBp?.diastolicPressure ?: 0)

        val queryLower = (specificConditionOrQuery ?: "").lowercase()
        val requestedGlucose = queryLower.contains("glic") || queryLower.contains("acucar") || queryLower.contains("açúcar") || queryLower.contains("diabetes")
        val requestedHypertension = queryLower.contains("press") || queryLower.contains("hipertens") || queryLower.contains("coracao") || queryLower.contains("coração")
        val requestedWeight = queryLower.contains("peso") || queryLower.contains("emagrec") || queryLower.contains("saciedade") || queryLower.contains("leve")

        // 1. Caso: Hipoglicemia (Glicose < 70 mg/dL) ou solicitação explícita de glicose baixa
        val isHypoglycemia = (glucoseVal != null && glucoseVal < 70.0) || queryLower.contains("baixa") && requestedGlucose
        if (isHypoglycemia) {
            val fastCarbFoods = catalog.filter { food ->
                food.name.contains("Laranja", ignoreCase = true) ||
                food.name.contains("Banana", ignoreCase = true) ||
                food.name.contains("Mel", ignoreCase = true) ||
                food.category?.contains("Frutas", ignoreCase = true) == true
            }
            val selected = fastCarbFoods.firstOrNull { it.name.contains("Suco de Laranja", ignoreCase = true) }
                ?: fastCarbFoods.firstOrNull { it.name.contains("Banana", ignoreCase = true) }
                ?: fastCarbFoods.firstOrNull()
                ?: catalog.first()

            // Proporção baseada no peso e regra dos 15g da SBD (~0.2g a 0.3g carb/kg)
            val portionGrams = if (effectiveWeight > 80.0) 180 else 150
            val portionDesc = if (selected.name.contains("Suco", ignoreCase = true)) {
                "$portionGrams ml (1 copo pequeno)"
            } else {
                "1 unidade pequena (${portionGrams}g)"
            }

            return ClinicalFoodSuggestion(
                food = selected,
                reason = "De acordo com a Sociedade Brasileira de Diabetes (SBD), na presença de glicemia abaixo de 70 mg/dL é indicada a 'Regra dos 15g' com carboidrato simples para rápida recuperação, com nova checagem em 15 minutos.",
                portionGrams = portionGrams,
                portionDescription = portionDesc,
                clinicalSociety = "Sociedade Brasileira de Diabetes (SBD)",
                targetCondition = "GLUCOSE_LOW",
                spokenPrompt = "Segundo a Sociedade Brasileira de Diabetes, para restabelecer a sua glicose com segurança recomendo $portionDesc de ${selected.name}, que fornece cerca de 15 gramas de carboidrato de rápida absorção. Não se esqueça de medir novamente em 15 minutos!"
            )
        }

        // 2. Caso: Hiperglicemia / Diabetes (Glicose > 100 mg/dL ou perfil diabético)
        val isHyperglycemia = (glucoseVal != null && glucoseVal > 100.0) || hasDiabetes || requestedGlucose
        if (isHyperglycemia && !requestedHypertension) {
            val safeGlucoseFoods = catalog.filter { food ->
                food.isDiabetesSafe && (
                    food.functionalTags.contains("fibra_soluvel") ||
                    food.functionalTags.contains("baixo_ig") ||
                    food.functionalTags.contains("beta_glucana")
                )
            }
            val selected = safeGlucoseFoods.firstOrNull { it.name.contains("Farelo de Aveia", ignoreCase = true) }
                ?: safeGlucoseFoods.firstOrNull { it.name.contains("Chia", ignoreCase = true) }
                ?: safeGlucoseFoods.firstOrNull { it.name.contains("Brócolis", ignoreCase = true) }
                ?: safeGlucoseFoods.firstOrNull()
                ?: catalog.first { it.isDiabetesSafe }

            // Proporção baseada no peso: ~0.4g de fibra solúvel por kg / porção balanceada
            val portionGrams = when {
                selected.category?.contains("Cereais", ignoreCase = true) == true || selected.category?.contains("Sementes", ignoreCase = true) == true -> {
                    if (effectiveWeight >= 80.0) 25 else 20
                }
                else -> if (effectiveWeight >= 80.0) 150 else 120
            }
            val portionDesc = selected.servingUnit.ifBlank { "${portionGrams}g" }

            return ClinicalFoodSuggestion(
                food = selected,
                reason = "Conforme diretrizes da Sociedade Brasileira de Diabetes (SBD), alimentos com fibras solúveis retardam a absorção dos carboidratos, promovendo glicemia mais estável após as refeições.",
                portionGrams = portionGrams,
                portionDescription = portionDesc,
                clinicalSociety = "Sociedade Brasileira de Diabetes (SBD)",
                targetCondition = "GLUCOSE_HIGH",
                spokenPrompt = "Apoiado nas diretrizes da Sociedade Brasileira de Diabetes, sugiro $portionDesc de ${selected.name}. Suas fibras solúveis ajudam a suavizar a curva de glicose no sangue de forma muito benéfica para o seu organismo."
            )
        }

        // 3. Caso: Hipertensão / Pressão Elevada (> 120/80 mmHg ou perfil hipertenso)
        val isHypertension = (sys > 120 || dia > 80) || hasHypertension || requestedHypertension
        if (isHypertension) {
            val bpSafeFoods = catalog.filter { food ->
                food.isHypertensionSafe && (
                    food.functionalTags.contains("nitrato_natural") ||
                    food.functionalTags.contains("potassio") ||
                    food.functionalTags.contains("magnesio") ||
                    food.functionalTags.contains("baixo_sodio")
                )
            }
            val selected = bpSafeFoods.firstOrNull { it.name.contains("Beterraba", ignoreCase = true) }
                ?: bpSafeFoods.firstOrNull { it.name.contains("Couve", ignoreCase = true) }
                ?: bpSafeFoods.firstOrNull { it.name.contains("Aveia", ignoreCase = true) }
                ?: bpSafeFoods.firstOrNull()
                ?: catalog.first { it.isHypertensionSafe }

            val portionGrams = if (effectiveWeight >= 80.0) 150 else 100
            val portionDesc = selected.servingUnit.ifBlank { "${portionGrams}g" }

            return ClinicalFoodSuggestion(
                food = selected,
                reason = "Pelas diretrizes da Sociedade Brasileira de Cardiologia (SBC) e a abordagem dietética DASH, alimentos ricos em potássio, magnésio e nitratos naturais favorecem a elasticidade vascular e o equilíbrio pressórico.",
                portionGrams = portionGrams,
                portionDescription = portionDesc,
                clinicalSociety = "Sociedade Brasileira de Cardiologia (SBC)",
                targetCondition = "HYPERTENSION",
                spokenPrompt = "De acordo com a Sociedade Brasileira de Cardiologia e a dieta DASH, recomendo $portionDesc de ${selected.name}. Seus minerais e compostos vasodilatadores naturais apoiam com muito carinho a sua saúde vascular."
            )
        }

        // 4. Caso: IMC Elevado (Identificação de peso sem jamais verbalizar "acima do peso")
        // IMC >= 25 em adultos ou >= 27 em idosos
        val isImcElevated = (if (isElderly) imc >= 27.0f else imc >= 25.0f) || requestedWeight
        if (isImcElevated) {
            val satietyFoods = catalog.filter { food ->
                food.functionalTags.contains("fibra_soluvel") ||
                food.functionalTags.contains("beta_glucana") ||
                food.functionalTags.contains("baixo_ig") ||
                food.category?.contains("Verduras", ignoreCase = true) == true ||
                food.category?.contains("Cereais", ignoreCase = true) == true
            }
            val selected = satietyFoods.firstOrNull { it.name.contains("Farelo de Aveia", ignoreCase = true) }
                ?: satietyFoods.firstOrNull { it.name.contains("Chia", ignoreCase = true) }
                ?: satietyFoods.firstOrNull { it.name.contains("Abobrinha", ignoreCase = true) }
                ?: satietyFoods.firstOrNull()
                ?: catalog.first()

            val portionGrams = if (effectiveWeight >= 80.0) 25 else 20
            val portionDesc = selected.servingUnit.ifBlank { "${portionGrams}g" }

            return ClinicalFoodSuggestion(
                food = selected,
                reason = "Alimento de excelente densidade nutricional que promove saciedade prolongada e apoia a digestão suave e a leveza do corpo.",
                portionGrams = portionGrams,
                portionDescription = portionDesc,
                clinicalSociety = "Ministério da Saúde & OMS",
                targetCondition = "WEIGHT_MANAGEMENT",
                spokenPrompt = "Para trazer bastante disposição, leveza e saciedade duradoura ao seu dia, sugiro $portionDesc de ${selected.name}. É uma opção rica em fibras naturais que faz muito bem para a harmonia do seu corpo!"
            )
        }

        // 5. Caso Base: Bem-estar e Equilíbrio Geral
        val selected = catalog.firstOrNull { it.name.contains("Farelo de Aveia", ignoreCase = true) }
            ?: catalog.firstOrNull { it.name.contains("Maçã", ignoreCase = true) }
            ?: catalog.first()
        val portionDesc = selected.servingUnit.ifBlank { "1 porção moderada" }

        return ClinicalFoodSuggestion(
            food = selected,
            reason = "Alimento natural brasileiro com nutrientes essenciais para sua energia e vitalidade diária.",
            portionGrams = selected.servingSizeGrams,
            portionDescription = portionDesc,
            clinicalSociety = "Ministério da Saúde & OMS",
            targetCondition = "WELLNESS",
            spokenPrompt = "Para o seu bem-estar hoje, recomendo $portionDesc de ${selected.name}. É um alimento natural e nutritivo para enriquecer sua rotina saudável!"
        )
    }
}

data class ClinicalFoodSuggestion(
    val food: FoodEntity,
    val reason: String,
    val portionGrams: Int,
    val portionDescription: String,
    val clinicalSociety: String,
    val targetCondition: String,
    val spokenPrompt: String
)

