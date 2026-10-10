package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.FoodDao
import br.com.bragasaude.data.local.FoodEntity
import br.com.bragasaude.data.remote.model.RemoteProfile
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NutritionRepositoryTest {

    private val sampleCatalog = listOf(
        FoodEntity(
            remoteId = "food_1",
            name = "Aveia em Flocos",
            category = "Cereais",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),
        FoodEntity(
            remoteId = "food_2",
            name = "Pão Francês",
            category = "Panificados",
            isDiabetesSafe = false,
            isHypertensionSafe = true
        ),
        FoodEntity(
            remoteId = "food_3",
            name = "Bacon Defumado",
            category = "Carnes",
            isDiabetesSafe = true,
            isHypertensionSafe = false
        ),
        FoodEntity(
            remoteId = "food_4",
            name = "Dipirona 500mg",
            category = "medicamento",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),
        FoodEntity(
            remoteId = "food_5",
            name = "Paracetamol Gotas",
            category = "farmaco",
            isDiabetesSafe = true,
            isHypertensionSafe = true
        )
    )

    @Test
    fun getFullFoodCatalogExcludesOnlyPharmaceuticalsAndKeepsRestrictedFoodsForFactualLogging() = runTest {
        val foodDao = mockk<FoodDao>()
        val profileRepo = mockk<ProfileRepository>(relaxed = true)
        every { foodDao.getCatalog() } returns flowOf(sampleCatalog)

        val repository = NutritionRepository(foodDao, profileRepo)
        val fullCatalog = repository.getFullFoodCatalog().first()

        assertEquals(3, fullCatalog.size)
        assertTrue(fullCatalog.any { it.name == "Aveia em Flocos" })
        assertTrue(fullCatalog.any { it.name == "Pão Francês" })
        assertTrue(fullCatalog.any { it.name == "Bacon Defumado" })
        assertFalse(fullCatalog.any { it.name == "Dipirona 500mg" })
        assertFalse(fullCatalog.any { it.name == "Paracetamol Gotas" })
    }

    @Test
    fun getSafeFoodCatalogFiltersByClinicalProfileRestrictions() = runTest {
        val foodDao = mockk<FoodDao>()
        val profileRepo = mockk<ProfileRepository>(relaxed = true)
        every { foodDao.getCatalog() } returns flowOf(sampleCatalog)

        val repository = NutritionRepository(foodDao, profileRepo)
        val diabeticProfile = RemoteProfile(id = "user1", hasDiabetes = true)
        val safeFoods = repository.getSafeFoodCatalog(diabeticProfile).first()

        assertTrue(safeFoods.any { it.name == "Aveia em Flocos" })
        assertFalse(safeFoods.any { it.name == "Pão Francês" })
    }

    @Test
    fun evaluateFoodClinicalWarningIdentifiesDiabetesAndHypertensionRisksWithoutBlocking() {
        val foodDao = mockk<FoodDao>()
        val profileRepo = mockk<ProfileRepository>(relaxed = true)
        val repository = NutritionRepository(foodDao, profileRepo)

        val diabeticProfile = RemoteProfile(id = "user1", hasDiabetes = true)
        val bread = FoodEntity(
            remoteId = "pao",
            name = "Pão Francês",
            category = "Panificados",
            isDiabetesSafe = false,
            isHypertensionSafe = true
        )
        val warningDiabetes = repository.evaluateFoodClinicalWarning(bread, diabeticProfile)
        assertNotNull(warningDiabetes)
        assertTrue(warningDiabetes!!.contains("diabetes"))

        val hypertensiveProfile = RemoteProfile(id = "user2", hasHypertension = true)
        val bacon = FoodEntity(
            remoteId = "bacon",
            name = "Bacon",
            category = "Carnes",
            isDiabetesSafe = true,
            isHypertensionSafe = false
        )
        val warningHypertension = repository.evaluateFoodClinicalWarning(bacon, hypertensiveProfile)
        assertNotNull(warningHypertension)
        assertTrue(warningHypertension!!.contains("hipertensão"))

        val safeProfile = RemoteProfile(id = "user3", hasDiabetes = false, hasHypertension = false)
        assertNull(repository.evaluateFoodClinicalWarning(bread, safeProfile))
        assertNull(repository.evaluateFoodClinicalWarning(bread, null))
    }
}
