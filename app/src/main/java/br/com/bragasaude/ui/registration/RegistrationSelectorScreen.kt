package br.com.bragasaude.ui.registration

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.R
import br.com.bragasaude.ui.util.Screen

import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.ui.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationSelectorScreen(
    onNavigate: (Screen) -> Unit,
    onClose: () -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val profile by homeViewModel.profile.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.register_data),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            val items = buildList {
                add(RegistrationItem("Hidratação", Icons.Default.WaterDrop, Screen.Hydration()))
                add(RegistrationItem("Pressão Arterial", Icons.Default.Favorite, Screen.Vitals("PRESSURE")))
                if (profile?.hasDiabetes == true) {
                    add(RegistrationItem("Glicose", Icons.Default.Bloodtype, Screen.Vitals("GLUCOSE")))
                }
                add(RegistrationItem("Peso", Icons.Default.MonitorWeight, Screen.Biometry))
                add(RegistrationItem("Medicamentos", Icons.Default.Medication, Screen.Reminders))
                add(RegistrationItem("Passos", Icons.AutoMirrored.Filled.DirectionsWalk, Screen.Steps))
                add(RegistrationItem("Exames", Icons.Default.Assignment, Screen.Exams))
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(items) { item ->
                    RegistrationCard(item) {
                        onNavigate(item.destination)
                    }
                }
            }
            
            Text(
                stringResource(R.string.recent_history),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = br.com.bragasaude.ui.theme.Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFF3F4F6))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Nenhum registro recente hoje.", style = MaterialTheme.typography.bodySmall)
                }
            }
            
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun RegistrationCard(item: RegistrationItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = br.com.bragasaude.ui.theme.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFF3F4F6))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                item.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            )
        }
    }
}

data class RegistrationItem(
    val title: String,
    val icon: ImageVector,
    val destination: Screen
)
