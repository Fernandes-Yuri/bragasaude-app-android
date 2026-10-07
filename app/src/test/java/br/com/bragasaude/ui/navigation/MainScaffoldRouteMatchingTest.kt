package br.com.bragasaude.ui.navigation

import br.com.bragasaude.ui.util.Screen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScaffoldRouteMatchingTest {

    @Test fun objectScreen_matchesExactSerialName() {
        // O Navigation Compose 2.8 deriva a rota do serialName do serializador.
        assertTrue(isCurrentRoute("Screen\$Home", Screen.Home))
        assertTrue(isCurrentRoute("Screen\$OrbChat", Screen.OrbChat))
        assertTrue(isCurrentRoute("Screen\$SocialFeed", Screen.SocialFeed))
    }

    @Test fun argScreen_matchesSerialNameFollowedByQueryString() {
        // Rotas com argumentos chegam como "Screen$Nutrition?searchFood=...".
        assertTrue(isCurrentRoute("Screen\$Nutrition?searchFood=arroz&openGroceryList=false&groceryItems=[]", Screen.Nutrition()))
    }

    @Test fun profileScreen_doesNotMatchProfileEditRoute() {
        // APP-6: contains("Profile") casava "Screen$ProfileEdit" — bug de
        // substring que marcava a aba Perfil como selecionada na edição.
        assertTrue(isCurrentRoute("Screen\$Profile", Screen.Profile))
        assertFalse(isCurrentRoute("Screen\$ProfileEdit", Screen.Profile))
        assertFalse(isCurrentRoute("Screen\$ProfileEdit?foo=1", Screen.Profile))
    }

    @Test fun nullOrDifferentRoute_neverMatches() {
        assertFalse(isCurrentRoute(null, Screen.Home))
        assertFalse(isCurrentRoute("Screen\$Report", Screen.Home))
        assertFalse(isCurrentRoute("", Screen.Home))
        // Nome parecido mas diferente (âncora de "?" evita prefixo parcial).
        assertFalse(isCurrentRoute("Screen\$HomeX", Screen.Home))
    }

    @Test fun packageQualifiedRoute_matchesCorrectly() {
        assertTrue(isCurrentRoute("br.com.bragasaude.ui.util.Screen.MedicationStock", Screen.MedicationStock))
        assertTrue(isCurrentRoute("br.com.bragasaude.ui.util.Screen\$MedicationStock", Screen.MedicationStock))
        assertTrue(isCurrentRoute("br.com.bragasaude.ui.util.Screen.BarcodeScanner", Screen.BarcodeScanner))
        assertTrue(isCurrentRoute("br.com.bragasaude.ui.util.Screen.Nutrition?searchFood=feijao", Screen.Nutrition()))
        assertFalse(isCurrentRoute("br.com.bragasaude.ui.util.Screen.ProfileEdit", Screen.Profile))
    }
    @Test fun `orbe aparece nas visoes gerais com argumentos de navegacao`() {
        assertTrue(shouldShowAssistantOrb("Screen\$Home"))
        assertTrue(shouldShowAssistantOrb("Screen\$Report"))
        assertTrue(shouldShowAssistantOrb("Screen\$CaregiverDashboard"))
        assertTrue(shouldShowAssistantOrb("Screen\$Nutrition?searchFood=arroz"))
        assertTrue(shouldShowAssistantOrb("Screen\$Hydration?initialMl=200"))
        assertTrue(shouldShowAssistantOrb("Screen\$Steps"))
        assertTrue(shouldShowAssistantOrb("Screen\$Reminders"))
    }

    @Test fun `orbe fica oculta em exames formularios conversas e telas administrativas`() {
        listOf("Exams", "AddData", "ProfileEdit",
            "CaregiverRegistration", "FamilyConnect", "FamilyChat", "DoctorMode", "Settings",
            "TermsOfUse", "PrivacyPolicy", "Feedback", "MedicationStock", "BarcodeScanner",
            "SocialFeed", "OrbChat").forEach { name ->
            assertFalse(name, shouldShowAssistantOrb("Screen\$$name"))
        }
    }

    @Test fun `orbe permanece nos formularios de medidas abertos pelo assistente`() {
        assertTrue(shouldShowAssistantOrb("Screen\$Vitals?type=PRESSURE&initialValue=120/80"))
        assertTrue(shouldShowAssistantOrb("Screen\$HealthReadings?metric=HEART_RATE"))
        assertTrue(shouldShowAssistantOrb("Screen\$HealthReadings/{metric}?initialValue={initialValue}"))
        assertTrue(shouldShowAssistantOrb("Screen\$HealthReadings/OXYGEN_SATURATION?initialValue=98"))
        assertTrue(shouldShowAssistantOrb("Screen\$Biometry"))
    }

    @Test fun `rota desconhecida nao cria sobreposicao por padrao`() {
        assertFalse(shouldShowAssistantOrb(null))
        assertFalse(shouldShowAssistantOrb(""))
        assertFalse(shouldShowAssistantOrb("Screen\$HomeEdit"))
        assertFalse(shouldShowAssistantOrb("Screen\$NewForm"))
    }
}
