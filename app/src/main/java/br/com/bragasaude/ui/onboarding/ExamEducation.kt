package br.com.bragasaude.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ExamEducationScene { ORGANIZE, LOCAL, SHARE }

data class ExamEducationStep(
    val title: String,
    val description: String,
    val scene: ExamEducationScene,
    val notice: String? = null
)

val examEducationSteps = listOf(
    ExamEducationStep(
        "Organize para a consulta",
        "Selecione PDFs ou fotos, confira título, data e tipo e gere um PDF com índice e todas as páginas.",
        ExamEducationScene.ORGANIZE
    ),
    ExamEducationStep(
        "Uma organização temporária",
        "Os documentos são processados apenas neste aparelho. Esta área não é um local de armazenamento.",
        ExamEducationScene.LOCAL,
        "Salve o PDF fora do aplicativo e guarde os originais. As cópias temporárias expiram em 24 horas."
    ),
    ExamEducationStep(
        "Compartilhe quando precisar",
        "Salve o PDF no destino escolhido ou envie ao profissional. Compartilhar não confirma o salvamento. Encerre para apagar as cópias temporárias.",
        ExamEducationScene.SHARE
    )
)

@Composable
fun ExamEducationIllustration(scene: ExamEducationScene, modifier: Modifier = Modifier, height: Dp = 250.dp) {
    val (icon, description) = when (scene) {
        ExamEducationScene.ORGANIZE -> Icons.Default.Assignment to "Organização dos documentos para a consulta"
        ExamEducationScene.LOCAL -> Icons.Default.PhoneAndroid to "Sessão temporária no dispositivo, com expiração em 24 horas"
        ExamEducationScene.SHARE -> Icons.Default.Share to "PDF salvo ou compartilhado no destino escolhido"
    }
    Surface(modifier.fillMaxWidth().height(height), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
