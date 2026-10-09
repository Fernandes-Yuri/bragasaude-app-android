package br.com.bragasaude.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import br.com.bragasaude.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.bragasaude.ui.components.BragaFormSheet
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaMintBorder
import br.com.bragasaude.ui.theme.BragaTextPrimary

enum class ExamEducationScene { STORE, PRIVACY, SHARE }

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
        ExamEducationScene.STORE
    ),
    ExamEducationStep(
        "Uma organização temporária",
        "Os documentos são processados apenas neste aparelho. Esta área não é um local de armazenamento.",
        ExamEducationScene.PRIVACY,
        "Salve o PDF fora do aplicativo e guarde os originais. As cópias temporárias expiram em 24 horas."
    ),
    ExamEducationStep(
        "Compartilhe quando precisar",
        "Salve o PDF no destino escolhido ou envie ao profissional. Compartilhar não confirma o salvamento. Encerre para apagar as cópias temporárias.",
        ExamEducationScene.SHARE
    )
)

/** Arte aprovada, empacotada para uso offline; cada etapa exibe sua própria cena. */
@Composable
fun ExamEducationIllustration(
    scene: ExamEducationScene,
    modifier: Modifier = Modifier,
    height: Dp = 250.dp
) {
    val artwork = ImageBitmap.imageResource(id = R.drawable.exams_onboarding_story)
    val painter = remember(artwork, scene) {
        val index = when (scene) {
            ExamEducationScene.STORE -> 0
            ExamEducationScene.PRIVACY -> 1
            ExamEducationScene.SHARE -> 2
        }
        val start = artwork.width * index / 3
        val end = artwork.width * (index + 1) / 3
        BitmapPainter(
            image = artwork,
            srcOffset = IntOffset(start, 0),
            srcSize = IntSize(end - start, artwork.height)
        )
    }
    val description = when (scene) {
        ExamEducationScene.STORE -> "Personagem fotografa um laudo para organizar seus exames."
        ExamEducationScene.PRIVACY -> "Personagem mostra o laudo protegido no celular; a nuvem aparece separada como opção."
        ExamEducationScene.SHARE -> "Personagem compartilha um documento com uma pessoa de confiança."
    }
    Surface(modifier = modifier.fillMaxWidth().height(height), shape = RoundedCornerShape(24.dp), color = Color.White) {
        Image(painter = painter, contentDescription = description, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun ExamExampleCard() {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, contentColor = BragaTextPrimary, border = BorderStroke(1.dp, BragaMintBorder)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("EXEMPLO", style = MaterialTheme.typography.labelSmall, color = BragaEmerald, fontWeight = FontWeight.Bold)
            Text("Meu laudo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Organizado por nome e data", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhoneAndroid, null, tint = BragaEmerald, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Salvo neste aparelho", style = MaterialTheme.typography.labelMedium, color = BragaEmerald)
            }
        }
    }
}

@Composable
fun ExamGettingStarted(onAddExam: () -> Unit, onLearnMore: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Prepare seus exames", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Guarde seus laudos, encontre os resultados e compartilhe quando precisar.", style = MaterialTheme.typography.bodyMedium)
        ExamEducationIllustration(ExamEducationScene.STORE, height = 190.dp)
        ExamExampleCard()
        Text("Organização local temporária. Gere o PDF e salve fora do aplicativo.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onAddExam, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Adicionar meu primeiro exame", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        }
        TextButton(onClick = onLearnMore, modifier = Modifier.fillMaxWidth()) { Text("Veja como funciona") }
    }
}

/** Reapresentação por iniciativa do usuário, sem refazer o onboarding da conta. */
@Composable
fun ExamIntroductionSheet(onDismiss: () -> Unit, onAddExam: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val step = examEducationSteps[page]
    BragaFormSheet(
        onDismissRequest = onDismiss,
        title = { Text(step.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${page + 1} de ${examEducationSteps.size}", style = MaterialTheme.typography.labelMedium)
                ExamEducationIllustration(step.scene)
                Text(step.description)
                step.notice?.let { ExamStorageNotice(it) }
            }
        },
        confirmButton = {
            Button(onClick = { if (page < examEducationSteps.lastIndex) page++ else onAddExam() }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (page == examEducationSteps.lastIndex) "Adicionar meu primeiro exame" else "Próximo", textAlign = TextAlign.Center)
            }
        },
        dismissButton = { TextButton(onClick = { if (page > 0) page-- else onDismiss() }) { Text(if (page > 0) "Voltar" else "Agora não") } }
    )
}

@Composable
fun ExamStorageNotice(text: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Info, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        }
    }
}
