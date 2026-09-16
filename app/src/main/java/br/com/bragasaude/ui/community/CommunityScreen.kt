package br.com.bragasaude.ui.community

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import br.com.bragasaude.ui.feed.FeedScreen
import br.com.bragasaude.ui.league.LeagueScreen

@Composable
fun CommunityScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Feed", "Ranking")

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Comunidade", fontWeight = FontWeight.Bold) }
                )
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { 
                                Text(
                                    title, 
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                ) 
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (selectedTab) {
                0 -> FeedScreen()
                1 -> LeagueScreen(onBack = {})
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CenterAlignedTopAppBar(
    title: @Composable () -> Unit
) {
    androidx.compose.material3.CenterAlignedTopAppBar(
        title = title
    )
}
