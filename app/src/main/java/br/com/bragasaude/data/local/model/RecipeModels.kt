package br.com.bragasaude.data.local.model

/**
 * Catálogo de 21 Receitas Caseiras Brasileiras Acessíveis para Idosos.
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
        HealthyRecipe(
            id = "recipe_021",
            title = "Ovo Cozido",
            mealType = "BREAKFAST",
            prepTimeMinutes = 15,
            difficulty = "Muito Fácil",
            ingredientNames = listOf("Ovos"),
            ingredientFoodIds = listOf("food_146"),
            instructions = listOf(
                "1. Coloque os ovos em uma panela e cubra com água.",
                "2. Leve ao fogo. Quando a água ferver, conte 10 minutos.",
                "3. Desligue o fogo e retire os ovos com uma colher.",
                "4. Espere esfriar antes de descascar e servir."
            ),
            clinicalBenefit = "Fonte de proteína, com preparo simples",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        // ===== CAFÉ DA MANHÃ & LANCHES =====

        HealthyRecipe(
            id = "recipe_001",
            title = "Mingau de Aveia com Banana e Gengibre",
            mealType = "BREAKFAST",
            prepTimeMinutes = 15,
            difficulty = "Muito Fácil",
            ingredientNames = listOf("Aveia em flocos", "Banana", "Leite", "Gengibre"),
            ingredientFoodIds = listOf("food_004", "food_026", "food_174", "food_193"),
            instructions = listOf(
                "1. Em uma panela, misture 3 colheres de aveia com 1 xícara de leite.",
                "2. Leve ao fogo baixo, mexendo até engrossar (cerca de 5 minutos).",
                "3. Descasque e amasse a banana. Misture ao mingau.",
                "4. Finalize com uma pitada de gengibre ralado e sirva morno."
            ),
            clinicalBenefit = "Rico em fibras solúveis e potássio, sem adição de açúcar",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_002",
            title = "Ovos Mexidos com Tomate e Orégano",
            mealType = "BREAKFAST",
            prepTimeMinutes = 10,
            difficulty = "Muito Fácil",
            ingredientNames = listOf("Ovos", "Tomate", "Óleo de soja", "Orégano"),
            ingredientFoodIds = listOf("food_146", "food_090", "food_221", "food_085"),
            instructions = listOf(
                "1. Bata 2 ovos.",
                "2. Aqueça 1 colher de chá de óleo de soja em uma frigideira antiaderente.",
                "3. Despeje os ovos e mexa devagar até começarem a firmar.",
                "4. Adicione o tomate picado e orégano. Sirva imediatamente."
            ),
            clinicalBenefit = "Boa fonte de proteína de alto valor biológico e licopeno",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_003",
            title = "Abacate Amassado com Limão",
            mealType = "SNACK",
            prepTimeMinutes = 5,
            difficulty = "Muito Fácil",
            ingredientNames = listOf("Abacate", "Limão"),
            ingredientFoodIds = listOf("food_042", "food_209"),
            instructions = listOf(
                "1. Abra o abacate ao meio e retire a polpa com uma colher.",
                "2. Esprema meio limão sobre a polpa e amasse levemente.",
                "3. Misture bem o abacate com o limão.",
                "4. Sirva como sobremesa ou lanche da tarde."
            ),
            clinicalBenefit = "Rico em gorduras boas e fibras, sem açúcar adicionado",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_004",
            title = "Vitamina de Mamão com Aveia em Flocos",
            mealType = "BREAKFAST",
            prepTimeMinutes = 5,
            difficulty = "Muito Fácil",
            ingredientNames = listOf("Mamão", "Leite", "Aveia em flocos"),
            ingredientFoodIds = listOf("food_031", "food_174", "food_004"),
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
            title = "Crepioca de Ricota e Orégano",
            mealType = "BREAKFAST",
            prepTimeMinutes = 12,
            difficulty = "Fácil",
            ingredientNames = listOf("Ovos", "Tapioca", "Ricota", "Orégano"),
            ingredientFoodIds = listOf("food_146", "tapioca", "food_172", "food_085"),
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
            title = "Omelete de Forno com Couve e Ricota",
            mealType = "LUNCH",
            prepTimeMinutes = 20,
            difficulty = "Fácil",
            ingredientNames = listOf("Ovos", "Couve", "Ricota", "Óleo de soja"),
            ingredientFoodIds = listOf("food_146", "food_061", "food_172", "food_221"),
            instructions = listOf(
                "1. Bata 3 ovos e misture com couve picadinha.",
                "2. Coloque em uma forma untada com um fio de óleo de soja.",
                "3. Fatie o ricota e disponha por cima.",
                "4. Asse a 180°C por 15 minutos ou até dourar."
            ),
            clinicalBenefit = "Rico em proteínas e cálcio, com fibras da couve",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_007",
            title = "Peixe na Frigideira",
            mealType = "LUNCH",
            prepTimeMinutes = 18,
            difficulty = "Fácil",
            ingredientNames = listOf("Sardinha", "Óleo de soja", "Limão"),
            ingredientFoodIds = listOf("food_157", "food_221", "food_209"),
            instructions = listOf(
                "1. Use sardinha limpa e sem espinhas e tempere com limão.",
                "2. Aqueça o óleo de soja em uma frigideira.",
                "3. Coloque o peixe e cozinhe dos dois lados até ficar opaco por dentro e se separar facilmente com um garfo.",
                "4. Sirva quente."
            ),
            clinicalBenefit = "Fonte de proteína, com preparo simples na frigideira",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_008",
            title = "Frango Desfiado com Abóbora Refogada",
            mealType = "LUNCH",
            prepTimeMinutes = 25,
            difficulty = "Fácil",
            ingredientNames = listOf("Peito de frango", "Abóbora cabotiá", "Óleo de soja"),
            ingredientFoodIds = listOf("food_149", "food_104", "food_221"),
            instructions = listOf(
                "1. Cozinhe o peito de frango e desfie com um garfo.",
                "2. Descasque a abóbora, corte em cubos e refogue no óleo de soja.",
                "3. Adicione o frango desfiado à abóbora.",
                "4. Misture bem e sirva quente."
            ),
            clinicalBenefit = "Fonte de proteína e vitamina A",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_009",
            title = "Feijão com Cenoura",
            mealType = "LUNCH",
            prepTimeMinutes = 30,
            difficulty = "Fácil",
            ingredientNames = listOf("Feijão carioca", "Cenoura", "Louro", "Óleo de soja"),
            ingredientFoodIds = listOf("food_121", "food_088", "food_215", "food_221"),
            instructions = listOf(
                "1. Cozinhe o feijão com uma folha de louro até ficar macio.",
                "2. Rale a cenoura e refogue no óleo de soja por 3 minutos.",
                "3. Adicione o feijão cozido (com o caldo) à cenoura.",
                "4. Amasse alguns grãos para engrossar e sirva quente."
            ),
            clinicalBenefit = "Fonte de fibras, com feijão e cenoura",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_010",
            title = "Sardinha ao Forno com Tomate e Cebola",
            mealType = "LUNCH",
            prepTimeMinutes = 22,
            difficulty = "Fácil",
            ingredientNames = listOf("Sardinha", "Tomate", "Cebola", "Óleo de soja", "Limão"),
            ingredientFoodIds = listOf("food_157", "food_090", "food_101", "food_221", "food_209"),
            instructions = listOf(
                "1. Tempere a sardinha com limão.",
                "2. Forre uma assadeira com rodelas de tomate e cebola.",
                "3. Coloque a sardinha por cima, regue com óleo de soja.",
                "4. Asse a 200°C por 15 minutos."
            ),
            clinicalBenefit = "Excelente fonte de ômega-3 e cálcio, preparo sem fritura",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        // ===== LANCHE =====

        HealthyRecipe(
            id = "recipe_011",
            title = "Salada de Feijão com Tomate",
            mealType = "SNACK",
            prepTimeMinutes = 10,
            difficulty = "Muito Fácil",
            ingredientNames = listOf("Feijão carioca", "Tomate", "Salsinha", "Óleo de soja"),
            ingredientFoodIds = listOf("food_121", "food_090", "salsinha", "food_221"),
            instructions = listOf(
                "1. Escorra o feijão carioca cozido.",
                "2. Pique o tomate e a salsinha bem fininhos.",
                "3. Misture tudo em uma tigela e regue com óleo de soja.",
                "4. Sirva em temperatura ambiente ou levemente morno."
            ),
            clinicalBenefit = "Rico em fibras e proteína vegetal, sem sódio adicionado",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        // ===== JANTAR =====

        HealthyRecipe(
            id = "recipe_012",
            title = "Sopa de Legumes com Frango",
            mealType = "DINNER",
            prepTimeMinutes = 30,
            difficulty = "Fácil",
            ingredientNames = listOf("Chuchu", "Abobrinha", "Cenoura", "Peito de frango", "Louro"),
            ingredientFoodIds = listOf("food_094", "food_092", "food_088", "food_149", "food_215"),
            instructions = listOf(
                "1. Cozinhe o frango em água com louro até ficar completamente cozido, sem partes rosadas.",
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
            prepTimeMinutes = 40,
            difficulty = "Fácil",
            ingredientNames = listOf("Arroz integral", "Brócolis", "Alho", "Óleo de soja"),
            ingredientFoodIds = listOf("food_132", "food_073", "food_103", "food_221"),
            instructions = listOf(
                "1. Cozinhe o arroz integral conforme a embalagem.",
                "2. Cozinhe o brócolis no vapor por 5 minutos.",
                "3. Refogue o alho fatiado no óleo de soja até dourar.",
                "4. Misture o brócolis ao arroz e regue com o alho dourado."
            ),
            clinicalBenefit = "Rico em fibras e antioxidantes, combinação completa de nutrientes",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_014",
            title = "Mandioca Amassada com Salsinha",
            mealType = "DINNER",
            prepTimeMinutes = 40,
            difficulty = "Muito Fácil",
            ingredientNames = listOf("Mandioca", "Óleo de soja", "Salsinha"),
            ingredientFoodIds = listOf("food_111", "food_221", "salsinha"),
            instructions = listOf(
                "1. Use mandioca de mesa (aipim), descasque e cozinhe em água até ficar bem macia. Descarte a água.",
                "2. Escorra e amasse com um garfo (não precisa ser liso).",
                "3. Regue com óleo de soja e salsa picada.",
                "4. Sirva como acompanhamento ou refeição leve."
            ),
            clinicalBenefit = "Fonte de carboidrato complexo e potássio, preparo sem leite",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_015",
            title = "Carne Moída com Chuchu e Tomate",
            mealType = "DINNER",
            prepTimeMinutes = 22,
            difficulty = "Fácil",
            ingredientNames = listOf("Músculo bovino moído", "Chuchu", "Tomate", "Óleo de soja"),
            ingredientFoodIds = listOf("food_180", "food_094", "food_090", "food_221"),
            instructions = listOf(
                "1. Refogue a carne moída em uma panela com um fio de óleo de soja.",
                "2. Mexa a carne até perder a cor rosada.",
                "3. Descasque o chuchu, corte em cubos e adicione à carne com o tomate picado.",
                "4. Cozinhe tampado por 10 minutos até o chuchu amolecer."
            ),
            clinicalBenefit = "Fonte de proteína e ferro, com legumes",
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
            ingredientNames = listOf("Banana", "Ovos", "Aveia em flocos", "Gengibre"),
            ingredientFoodIds = listOf("food_026", "food_146", "food_004", "food_193"),
            instructions = listOf(
                "1. Amasse 1 banana e misture com 1 ovo e 2 colheres de aveia.",
                "2. Aqueça uma frigideira antiaderente.",
                "3. Despeje a massa e doure dos dois lados.",
                "4. Finalize com uma pitada de gengibre ralado e sirva."
            ),
            clinicalBenefit = "Sem açúcar, rica em fibras e potássio",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_017",
            title = "Salada de Arroz Integral com Legumes",
            mealType = "LUNCH",
            prepTimeMinutes = 40,
            difficulty = "Fácil",
            ingredientNames = listOf("Arroz integral", "Tomate", "Pepino", "Óleo de soja", "Limão"),
            ingredientFoodIds = listOf("food_132", "food_090", "food_098", "food_221", "food_209"),
            instructions = listOf(
                "1. Cozinhe o arroz integral conforme a embalagem e deixe esfriar.",
                "2. Pique o tomate e o pepino em cubos pequenos.",
                "3. Misture com o arroz integral e tempere com limão e óleo de soja.",
                "4. Sirva fria como salada ou acompanhamento."
            ),
            clinicalBenefit = "Arroz integral e legumes: fonte de fibras",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_018",
            title = "Sopa de Abóbora",
            mealType = "DINNER",
            prepTimeMinutes = 25,
            difficulty = "Fácil",
            ingredientNames = listOf("Abóbora cabotiá", "Cebola", "Óleo de soja"),
            ingredientFoodIds = listOf("food_104", "food_101", "food_221"),
            instructions = listOf(
                "1. Descasque e corte a abóbora em cubos.",
                "2. Refogue a cebola no óleo de soja e adicione a abóbora.",
                "3. Cubra com água e cozinhe até a abóbora amolecer.",
                "4. Bata no liquidificador até ficar cremoso e sirva."
            ),
            clinicalBenefit = "Sopa de legumes, fonte de vitamina A",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),

        HealthyRecipe(
            id = "recipe_019",
            title = "Torrada Integral com Pasta de Feijão",
            mealType = "SNACK",
            prepTimeMinutes = 8,
            difficulty = "Muito Fácil",
            ingredientNames = listOf("Pão integral", "Feijão carioca", "Óleo de soja", "Limão", "Alho"),
            ingredientFoodIds = listOf("pao-integral", "food_121", "food_221", "food_209", "food_103"),
            instructions = listOf(
                "1. Bata o feijão carioca cozido com óleo de soja, limão e alho no liquidificador.",
                "2. Torre o pão integral.",
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
            ingredientNames = listOf("Macarrão integral", "Tomate", "Alho", "Óleo de soja", "Manjericão"),
            ingredientFoodIds = listOf("food_143", "food_090", "food_103", "food_221", "food_081"),
            instructions = listOf(
                "1. Cozinhe o macarrão integral conforme a embalagem.",
                "2. Refogue o alho no óleo de soja, adicione tomates picados.",
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
