package br.com.bragasaude.data.local

import kotlinx.serialization.Serializable

@Serializable enum class NutritionalPillar { GRAINS_TUBERS, PROTEIN, PRODUCE, PANTRY_FATS, UNKNOWN }
@Serializable enum class MealRole { STAPLE_BASE, PRIMARY_PROTEIN, COMPLEMENTARY_PROTEIN, VEGETABLE_SOLID, VEGETABLE_LEAFY, FRUIT_DENSE, FRUIT_HYDRATING, BREAKFAST_BASE, PANTRY_SEASONING, UNKNOWN }
@Serializable enum class ShelfLife { PERISHABLE_FAST, PERISHABLE_DURABLE, SHELF_STABLE }
@Serializable enum class CostTier { ESSENTIAL, REGULAR, PREMIUM }
@Serializable enum class ProteinSubtype { POULTRY, BEEF, PORK, FISH, EGGS, PLANT_BASED, DAIRY, NONE }

/** Unknown legacy semantics never depend on the display name. */
@Serializable
data class FoodSemanticMetadata(
    val pillar: NutritionalPillar = NutritionalPillar.UNKNOWN,
    val mealRole: MealRole = MealRole.UNKNOWN,
    val proteinSubtype: ProteinSubtype = ProteinSubtype.NONE,
    val shelfLife: ShelfLife = ShelfLife.PERISHABLE_FAST,
    val costTier: CostTier = CostTier.REGULAR,
    val isPantryStaple: Boolean = false
)
