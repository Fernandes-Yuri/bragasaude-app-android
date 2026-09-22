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
}
