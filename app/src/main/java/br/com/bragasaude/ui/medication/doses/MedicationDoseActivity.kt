package br.com.bragasaude.ui.medication.doses

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.bragasaude.ui.theme.BragaBackground
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragasaudeTheme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MedicationDoseActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val actor = FirebaseAuth.getInstance().currentUser?.uid
        val patient = intent.getStringExtra("patient")
        val patientName = intent.getStringExtra("patientName") ?: ""
        val isCaregiver = actor != null && patient != null && actor != patient

        setContent {
            BragasaudeTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    if (isCaregiver && patientName.isNotBlank()) "Doses de $patientName"
                                    else if (isCaregiver) "Doses do Acompanhado"
                                    else "Medicamentos por Horário",
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }, modifier = Modifier.size(48.dp)) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Voltar",
                                        tint = Color.White
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = BragaEmerald,
                                titleContentColor = Color.White,
                                navigationIconContentColor = Color.White
                            )
                        )
                    },
                    containerColor = BragaBackground
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        if (actor == null || patient == null) {
                            Text(
                                "Entre na conta deste paciente para abrir a notificação.",
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                MedicationDosePanel(
                                    patientId = patient,
                                    canTake = actor == patient || intent.getBooleanExtra("canTake", false),
                                    canEdit = actor == patient || intent.getBooleanExtra("canEdit", false),
                                    isCaregiver = isCaregiver,
                                    patientName = patientName
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
