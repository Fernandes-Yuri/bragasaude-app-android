package br.com.bragasaude.ui.medication.doses

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import br.com.bragasaude.ui.theme.BragasaudeTheme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MedicationDoseActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val actor=FirebaseAuth.getInstance().currentUser?.uid
        val patient=intent.getStringExtra("patient")
        setContent { BragasaudeTheme {
            // O painel também é embutido em Lembretes; os insets pertencem a esta janela.
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(top = 12.dp)
                    .verticalScroll(rememberScrollState())) {
                    TextButton(onClick={finish()}) { Text("Voltar") }
                    if(actor==null || patient==null) Text("Entre na conta deste paciente para abrir a notificação.")
                    else MedicationDosePanel(patientId=patient,canTake=actor==patient || intent.getBooleanExtra("canTake",false),
                        canEdit=actor==patient || intent.getBooleanExtra("canEdit",false))
                }
            }
        } }
    }
}
