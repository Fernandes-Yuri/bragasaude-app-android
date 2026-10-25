package br.com.bragasaude.data.remote.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GroceryRetailCardDtoTest {
    private fun product() = JSONObject("""{"id":"rice","name":"Arroz","category":"Cereais",
        "image_url":"https://example.org/rice.jpg","package_display":"1000 g","package_net_value":1000,
        "package_unit":"g","kcal_per_package":3500,"carbs_g":700,"protein_g":70,"fat_g":10,"price_avg":8.9}""")

    @Test fun dynamicNutrientsKeepCustomUnitsFiberAndSourceAttribution() {
        val source = JSONObject().put("source_name", "Tabela de composição")
        val facts = JSONArray()
            .put(JSONObject().put("key", "fiber").put("label", "Fibras").put("unit", "g").put("value", 0))
            .put(JSONObject().put("key", "sodium").put("label", "Sódio").put("unit", "mg").put("value", 12.5).put("reference", source))
        val card = GroceryRetailCardDto.parse(product().put("nutrition_facts", facts))
        assertEquals(0.0, card.fiberG!!, 0.001)
        val parsed = GroceryRetailCardDto.nutritionFacts(card)
        assertEquals(listOf("fiber", "sodium"), parsed.map { it.key })
        assertEquals("mg", parsed.last().unit)
        assertEquals(12.5, parsed.last().value, 0.001)
        assertEquals("Tabela de composição", JSONArray(card.nutritionFactsJson).getJSONObject(1).getJSONObject("reference").getString("source_name"))
    }

    @Test fun missingFiberIsDistinctFromZeroAndLegacyMacrosRemainAvailable() {
        val legacy = GroceryRetailCardDto.parse(product())
        assertNull(legacy.fiberG)
        assertFalse(GroceryRetailCardDto.nutritionFacts(legacy).any { it.key == "fiber" })
        assertEquals(3500.0, GroceryRetailCardDto.nutritionFacts(legacy).first().value, 0.001)
        assertEquals(0.0, GroceryRetailCardDto.parse(product().put("fiber_g", 0)).fiberG!!, 0.001)
    }

    @Test fun explicitEmptyNutritionIsPreservedAndNullPhotoIsNotLiteralNull() {
        val card = GroceryRetailCardDto.parse(product().put("nutrition_facts", JSONArray()).put("image_url", JSONObject.NULL))
        assertTrue(GroceryRetailCardDto.nutritionFacts(card).isEmpty())
        assertNull(card.fiberG)
        assertEquals("", card.imageUrl)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidNutrientFailsBeforeReplacingCachedCatalog() {
        GroceryRetailCardDto.parse(product().put("nutrition_facts", JSONArray().put(
            JSONObject().put("key", "sodium").put("label", "Sódio").put("unit", "mg").put("value", -1))))
    }

    @Test fun imageCreditAndUrlAreParsedAndNormalizedWhenPresent() {
        val json = product()
            .put("image_credit", "Foto por Fulano / CC BY-SA 4.0")
            .put("image_credit_url", "https://commons.wikimedia.org/wiki/File:Arroz.jpg")
        val card = GroceryRetailCardDto.parse(json)
        assertEquals("Foto por Fulano / CC BY-SA 4.0", card.imageCredit)
        assertEquals("https://commons.wikimedia.org/wiki/File:Arroz.jpg", card.imageCreditUrl)
    }

    @Test fun imageCreditAndUrlHandleMissingNullOrBlankAsNull() {
        val legacy = GroceryRetailCardDto.parse(product())
        assertNull(legacy.imageCredit)
        assertNull(legacy.imageCreditUrl)

        val nulls = GroceryRetailCardDto.parse(product()
            .put("image_credit", JSONObject.NULL)
            .put("image_credit_url", JSONObject.NULL))
        assertNull(nulls.imageCredit)
        assertNull(nulls.imageCreditUrl)

        val blanks = GroceryRetailCardDto.parse(product()
            .put("image_credit", "   ")
            .put("image_credit_url", ""))
        assertNull(blanks.imageCredit)
        assertNull(blanks.imageCreditUrl)
    }
}
