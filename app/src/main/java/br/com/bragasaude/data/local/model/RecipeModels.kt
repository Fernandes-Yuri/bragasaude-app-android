package br.com.bragasaude.data.local.model

/**
 * Catálogo de 20 Receitas Tradicionais Brasileiras Acessíveis para Idosos.
 *
 * Cada receita é uma preparação caseira simples, prática (3 a 4 passos)
 * e saudável, priorizando ingredientes que o idoso já tem na despensa.
 *
 * Os ingredientNames usam nomes que correspondem ao catálogo de 220 alimentos
 * do Braga Saúde para permitir o cruzamento com a despensa local.
 */

/**
 * Receita saudável simples para o módulo "O Que Cozinhar Hoje?".
 *
 * @param id Identificador único da receita.
 * @param title Título da receita.
 * @param mealType Tipo de refeição: "BREAKFAST", "LUNCH", "SNACK", "DINNER".
 * @param prepTimeMinutes Tempo de preparo em minutos.
 * @param difficulty Dificuldade: "Muito Fácil" ou "Fácil".
 * @param ingredientNames Lista de nomes de ingredientes (buscados no catálogo de alimentos).
 * @param ingredientFoodIds Lista de IDs de alimentos no catálogo (opcional, para vinculação direta).
 * @param instructions Passos de preparo (3 a 4 passos simples e claros).
 * @param clinicalBenefit Benefício clínico descritivo (sem alegação diagnóstica).
 * @param isDiabetesSafe Se a receita é adequada para pessoas com diabetes.
 * @param isHypertensionSafe Se a receita é adequada para pessoas com hipertensão.
 */
data class HealthyRecipe(
    val id: String,
    val title: String,
    val mealType: String,
    val prepTimeMinutes: Int,
    val difficulty: String,
    val ingredientNames: List<String>,
    val ingredientFoodIds: List<String>,
    val instructions: List<String>,
    val clinicalBenefit: String,
    val isDiabetesSafe: Boolean,
    val isHypertensionSafe: Boolean
)

/**
 * Catálogo completo de receitas do Braga Saúde.
 *
 * Cada receita é categorizada por tipo de refeição e avaliada para segurança
 * em condições clínicas comuns (diabetes, hipertensão).
 */
object RecipeCatalog {

    private val ALL = listOf(
        // ===== CAFÉ DA MANHÃ & LANCHES =====

        HealthyRecipe(
            id = "recipe_001",
            title = "Mingau de Aveia Quentinho com Banana e Canela",
            mealType = "BREAKFAST",
            prepTimeMinutes = 15,
            difficulty = "Muito Fácil",
            ingredientNames = listOf("Aveia em flocos", "Banana", "Leite", "Canela em pó"),
            ingredientFoodIds = listOf("food_aveia", "food_banana", "food_leite", "food_canela"),
            instructions = listOf(
                "1. Em uma panela, misture 3 colheres de aveia com 1 xícara de leite.",
                "2. Leve ao fogo baixo, mexendo até engrossar (cerca de 5 minutos).",
                "3. Descasque e amasse a banana. Misture ao mingau.",
                "4. Finalize com canela por cima e sirva morno."
            ),
            clinicalBenefit = "Rico em fibras solúveis e potássio, sem adição de açúcar",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_002",
            title = "Ovos Mexidos Cremosos com Tomate e Orégano",
            mealType = "BREAKFAST",
            prepTimeMinutes = 10,
            difficulty = "Muito Fácil",
            ingredientNames = listOf("Ovos", "Tomate", "Azeite de oliva", "Orégano"),
            ingredientFoodIds = listOf("food_ovo", "food_tomate", "food_azeite", "food_oregano"),
            instructions = listOf(
                "1. Bata 2 ovos com uma pitada de sal e pimenta.",
                "2. Aqueça 1 colher de azeite em uma frigideira antiaderente.",
                "3. Despeje os ovos e mexa devagar até começarem a firmar.",
                "4. Adicione o tomate picado e orégano. Sirva imediatamente."
            ),
            clinicalBenefit = "Boa fonte de proteína de alto valor biológico e licopeno",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_003",
            title = "Creme de Abacate com Limão e Chia",
            mealType = "SNACK",
            prepTimeMinutes = 5,
            difficulty = "Muito Fácil",
            ingredientNames = listOf("Abacate", "Limão", "Semente de chia"),
            ingredientFoodIds = listOf("food_abacate", "food_limao", "food_chia"),
            instructions = listOf(
                "1. Abra o abacate ao meio e retire a polpa com uma colher.",
                "2. Esprema meio limão sobre a polpa e amasse levemente.",
                "3. Adicione 1 colher de semente de chia e misture.",
                "4. Sirva como sobremesa ou lanche da tarde."
            ),
            clinicalBenefit = "Rico em gorduras boas e fibras, sem açúcar adicionado",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_004",
            title = "Vitamina de Mamão com Farelo de Aveia",
            mealType = "BREAKFAST",
            prepTimeMinutes = 5,
            difficulty = "Muito Fácil",
            ingredientNames = listOf("Mamão", "Leite", "Aveia em flocos"),
            ingredientFoodIds = listOf("food_mamao", "food_leite", "food_aveia"),
            instructions = listOf(
                "1. Corte o mamão ao meio, retire as sementes e coloque no liquidificador.",
                "2. Adicione 1 xícara de leite e 2 colheres de aveia.",
                "3. Bata até ficar cremoso.",
                "4. Sirva imediatamente para aproveitar as vitaminas."
            ),
            clinicalBenefit = "Rico em fibras, vitamina C e cálcio, auxilia o trânsito intestinal",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_005",
            title = "Crepioca Funcional de Ricota e Ervas",
            mealType = "BREAKFAST",
            prepTimeMinutes = 12,
            difficulty = "Fácil",
            ingredientNames = listOf("Ovos", "Tapioca", "Ricota", "Orégano"),
            ingredientFoodIds = listOf("food_ovo", "food_tapioca", "food_ricota", "food_oregano"),
            instructions = listOf(
                "1. Misture 1 ovo com 2 colheres de goma de tapioca.",
                "2. Despeje em uma frigideira quente e antiaderente.",
                "3. Quando firmar, coloque a ricota esfarelada por cima e orégano.",
                "4. Dobre ao meio e sirva quente."
            ),
            clinicalBenefit = "Boa proteína e cálcio, preparo simples sem fritura",
            isDiabetesSafe = false,
            isHypertensionSafe = true
        ),

        // ===== ALMOÇO =====

        HealthyRecipe(
            id = "recipe_006",
            title = "Omelete de Forno com Couve e Queijo Minas",
            mealType = "LUNCH",
            prepTimeMinutes = 20,
            difficulty = "Fácil",
            ingredientNames = listOf("Ovos", "Couve", "Queijo minas frescal"),
            ingredientFoodIds = listOf("food_ovo", "food_couve", "food_queijo_minas"),
            instructions = listOf(
                "1. Bata 3 ovos e misture com couve picadinha.",
                "2. Coloque em uma forma untada com um fio de azeite.",
                "3. Fatie o queijo minas e disponha por cima.",
                "4. Asse a 180°C por 15 minutos ou até dourar."
            ),
            clinicalBenefit = "Rico em proteínas e cálcio, com fibras da couve",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_007",
            title = "Tilápia Grelhada com Crosta de Aveia e Ervas",
            mealType = "LUNCH",
            prepTimeMinutes = 18,
            difficulty = "Fácil",
            ingredientNames = listOf("Tilápia", "Aveia em flocos", "Azeite de oliva", "Limão"),
            ingredientFoodIds = listOf("food_tilapia", "food_aveia", "food_azeite", "food_limao"),
            instructions = listOf(
                "1. Tempere o filé de tilápia com limão e sal.",
                "2. Passe os flocos de aveia em ambos os lados do peixe.",
                "3. Aqueça o azeite em uma frigideira e grelhe 4 minutos de cada lado.",
                "4. Sirva com salada verde."
            ),
            clinicalBenefit = "Proteína magra rica em ômega-3, com fibras da aveia",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_008",
            title = "Frango Desfiado com Abóbora Refogada",
            mealType = "LUNCH",
            prepTimeMinutes = 25,
            difficulty = "Fácil",
            ingredientNames = listOf("Peito de frango", "Abóbora cabotiá", "Azeite de oliva", "Cúrcuma"),
            ingredientFoodIds = listOf("food_frango", "food_abobora", "food_azeite", "food_cucurma"),
            instructions = listOf(
                "1. Cozinhe o peito de frango e desfie com um garfo.",
                "2. Descasque a abóbora, corte em cubos e refogue no azeite.",
                "3. Adicione a cúrcuma e o frango desfiado à abóbora.",
                "4. Misture bem e sirva com arroz integral."
            ),
            clinicalBenefit = "Proteína magra com vitamina A da abóbora e ação antioxidante da cúrcuma",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_009",
            title = "Caldinho Rústico de Feijão Carioca com Cenoura",
            mealType = "LUNCH",
            prepTimeMinutes = 30,
            difficulty = "Fácil",
            ingredientNames = listOf("Feijão carioca", "Cenoura", "Louro", "Azeite de oliva"),
            ingredientFoodIds = listOf("food_feijao_carioca", "food_cenoura", "food_louro", "food_azeite"),
            instructions = listOf(
                "1. Cozinhe o feijão com uma folha de louro até ficar macio.",
                "2. Rale a cenoura e refogue no azeite por 3 minutos.",
                "3. Adicione o feijão cozido (com o caldo) à cenoura.",
                "4. Amasse alguns grãos para engrossar e sirva quente."
            ),
            clinicalBenefit = "Rico em fibras, ferro e vitamina A, combinação completa de aminoácidos",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_010",
            title = "Sardinha ao Forno com Tomate e Cebola",
            mealType = "LUNCH",
            prepTimeMinutes = 22,
            difficulty = "Fácil",
            ingredientNames = listOf("Sardinha", "Tomate", "Cebola", "Azeite de oliva"),
            ingredientFoodIds = listOf("food_sardinha", "food_tomate", "food_cebola", "food_azeite"),
            instructions = listOf(
                "1. Tempere a sardinha com limão e sal.",
                "2. Forre uma assadeira com rodelas de tomate e cebola.",
                "3. Coloque a sardinha por cima, regue com azeite.",
                "4. Asse a 200°C por 15 minutos."
            ),
            clinicalBenefit = "Excelente fonte de ômega-3 e cálcio, preparo sem fritura",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        // ===== LANCHE =====

        HealthyRecipe(
            id = "recipe_011",
            title = "Salada Morna de Grão-de-Bico com Salsa e Azeite",
            mealType = "SNACK",
            prepTimeMinutes = 10,
            difficulty = "Muito Fácil",
            ingredientNames = listOf("Grão-de-bico", "Tomate", "Salsinha", "Azeite de oliva"),
            ingredientFoodIds = listOf("food_garaodebico", "food_tomate", "food_salsinha", "food_azeite"),
            instructions = listOf(
                "1. Escorra o grão-de-bico cozido.",
                "2. Pique o tomate e a salsinha bem fininhos.",
                "3. Misture tudo em uma tigela e regue com azeite.",
                "4. Sirva em temperatura ambiente ou levemente morno."
            ),
            clinicalBenefit = "Rico em fibras e proteína vegetal, sem sódio adicionado",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        // ===== JANTAR =====

        HealthyRecipe(
            id = "recipe_012",
            title = "Sopa Nutritiva de Legumes com Frango",
            mealType = "DINNER",
            prepTimeMinutes = 30,
            difficulty = "Fácil",
            ingredientNames = listOf("Chuchu", "Abobrinha", "Cenoura", "Peito de frango", "Louro"),
            ingredientFoodIds = listOf("food_chuchu", "food_abobrinha", "food_cenoura", "food_frango", "food_louro"),
            instructions = listOf(
                "1. Cozinhe o frango em água com louro por 15 minutos.",
                "2. Adicione os legumes picados (chuchu, abobrinha, cenoura).",
                "3. Cozinhe até os legumes ficarem macios.",
                "4. Desfie o frango, misture à sopa e sirva quente."
            ),
            clinicalBenefit = "Leve e nutritiva, ideal para o jantar, rica em vitaminas",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_013",
            title = "Arroz Integral com Brócolis ao Alho",
            mealType = "DINNER",
            prepTimeMinutes = 25,
            difficulty = "Fácil",
            ingredientNames = listOf("Arroz integral", "Brócolis", "Alho", "Azeite de oliva"),
            ingredientFoodIds = listOf("food_arroz_integral", "food_brocolis", "food_alho", "food_azeite"),
            instructions = listOf(
                "1. Cozinhe o arroz integral conforme a embalagem.",
                "2. Cozinhe o brócolis no vapor por 5 minutos.",
                "3. Refogue o alho fatiado no azeite até dourar.",
                "4. Misture o brócolis ao arroz e regue com o alho dourado."
            ),
            clinicalBenefit = "Rico em fibras e antioxidantes, combinação completa de nutrientes",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_014",
            title = "Purê Rústico de Mandioquinha com Azeite",
            mealType = "DINNER",
            prepTimeMinutes = 20,
            difficulty = "Muito Fácil",
            ingredientNames = listOf("Mandioquinha", "Azeite de oliva", "Salsinha"),
            ingredientFoodIds = listOf("food_mandioquinha", "food_azeite", "food_salsinha"),
            instructions = listOf(
                "1. Descasque a mandioquinha e cozinhe em água salgada até amolecer.",
                "2. Escorra e amasse com um garfo (não precisa ser liso).",
                "3. Regue com azeite e salsa picada.",
                "4. Sirva como acompanhamento ou refeição leve."
            ),
            clinicalBenefit = "Fonte de carboidrato complexo e potássio, preparo sem leite",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_015",
            title = "Carne Moída Refogada com Chuchu e Cúrcuma",
            mealType = "DINNER",
            prepTimeMinutes = 22,
            difficulty = "Fácil",
            ingredientNames = listOf("Patinho moído", "Chuchu", "Tomate", "Cúrcuma"),
            ingredientFoodIds = listOf("food_patinho", "food_chuchu", "food_tomate", "food_cucurma"),
            instructions = listOf(
                "1. Refogue a carne moída em uma panela com um fio de azeite.",
                "2. Adicione a cúrcuma e mexa bem.",
                "3. Descasque o chuchu, corte em cubos e adicione à carne.",
                "4. Cozinhe tampado por 10 minutos até o chuchu amolecer."
            ),
            clinicalBenefit = "Proteína magra com ferro, cúrcuma como antioxidante natural",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        // ===== RECEITAS ADICIONAIS PARA COMPLETAR 20 =====

        HealthyRecipe(
            id = "recipe_016",
            title = "Panqueca de Banana com Aveia sem Açúcar",
            mealType = "BREAKFAST",
            prepTimeMinutes = 12,
            difficulty = "Muito Fácil",
            ingredientNames = listOf("Banana", "Ovos", "Aveia em flocos", "Canela em pó"),
            ingredientFoodIds = listOf("food_banana", "food_ovo", "food_aveia", "food_canela"),
            instructions = listOf(
                "1. Amasse 1 banana e misture com 1 ovo e 2 colheres de aveia.",
                "2. Aqueça uma frigideira antiaderente.",
                "3. Despeje a massa e doure dos dois lados.",
                "4. Finalize com canela e sirva."
            ),
            clinicalBenefit = "Sem açúcar, rica em fibras e potássio",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_017",
            title = "Salada de Quinoa com Legumes",
            mealType = "LUNCH",
            prepTimeMinutes = 20,
            difficulty = "Fácil",
            ingredientNames = listOf("Quinoa", "Tomate", "Pepino", "Azeite de oliva", "Limão"),
            ingredientFoodIds = listOf("food_quinoa", "food_tomate", "food_pepino", "food_azeite", "food_limao"),
            instructions = listOf(
                "1. Cozinhe a quinoa conforme a embalagem e deixe esfriar.",
                "2. Pique o tomate e o pepino em cubos pequenos.",
                "3. Misture com a quinoa e tempere com limão e azeite.",
                "4. Sirva fria como salada ou acompanhamento."
            ),
            clinicalBenefit = "Proteína vegetal completa e rica em fibras",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_018",
            title = "Sopa de Abóbora com Gengibre",
            mealType = "DINNER",
            prepTimeMinutes = 25,
            difficulty = "Fácil",
            ingredientNames = listOf("Abóbora cabotiá", "Gengibre", "Cebola", "Azeite de oliva"),
            ingredientFoodIds = listOf("food_abobora", "food_gengibre", "food_cebola", "food_azeite"),
            instructions = listOf(
                "1. Descasque e corte a abóbora em cubos.",
                "2. Refogue a cebola no azeite, adicione a abóbora e gengibre ralado.",
                "3. Cubra com água e cozinhe até a abóbora amolecer.",
                "4. Bata no liquidificador até ficar cremoso e sirva."
            ),
            clinicalBenefit = "Leve, rica em vitamina A e com ação anti-inflamatória do gengibre",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_019",
            title = "Torrada Integral com Pasta de Grão-de-Bico",
            mealType = "SNACK",
            prepTimeMinutes = 8,
            difficulty = "Muito Fácil",
            ingredientNames = listOf("Pão integral", "Grão-de-bico", "Azeite de oliva", "Limão", "Alho"),
            ingredientFoodIds = listOf("food_pao_integral", "food_garaodebico", "food_azeite", "food_limao", "food_alho"),
            instructions = listOf(
                "1. Bata o grão-de-bico cozido com azeite, limão e alho no liquidificador.",
                "2. Toque o pão integral.",
                "3. Passe a pasta sobre a torrada.",
                "4. Sirva como lanche rápido."
            ),
            clinicalBenefit = "Proteína vegetal e fibras, alternativa nutritiva ao lanche processado",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_020",
            title = "Macarrão Integral com Molho de Tomate Caseiro",
            mealType = "DINNER",
            prepTimeMinutes = 20,
            difficulty = "Fácil",
            ingredientNames = listOf("Macarrão integral", "Tomate", "Alho", "Azeite de oliva", "Manjericão"),
            ingredientFoodIds = listOf("food_macarrao_integral", "food_tomate", "food_alho", "food_azeite", "food_manjericao"),
            instructions = listOf(
                "1. Cozinhe o macarrão integral conforme a embalagem.",
                "2. Refogue o alho no azeite, adicione tomates picados.",
                "3. Cozinhe o molho por 10 minutos até encorpar.",
                "4. Misture com o macarrão e finalize com manjericão."
            ),
            clinicalBenefit = "Carboidrato complexo com licopeno do tomate cozido",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        )
    )

    /** Retorna todas as receitas do catálogo. */
    fun getAll(): List<HealthyRecipe> = ALL

    /** Retorna receitas por tipo de refeição. */
    fun getByMealType(mealType: String): List<HealthyRecipe> = ALL.filter { it.mealType == mealType }

    /** Tipos de refeição disponíveis no catálogo. */
    val MEAL_TYPES = listOf("BREAKFAST", "LUNCH", "SNACK", "DINNER")
}
