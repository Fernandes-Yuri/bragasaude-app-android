package br.com.bragasaude.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.bragasaude.ui.components.BragaFormSheet
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaMint
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
        "Guarde seus exames",
        "Fotografe um laudo, anexe um arquivo ou digite os resultados. Confira os valores e encontre tudo na sua biblioteca.",
        ExamEducationScene.STORE
    ),
    ExamEducationStep(
        "Você decide onde ficam",
        "Os novos exames ficam neste aparelho. Uma cópia na nuvem só é enviada se você autorizar, no detalhe de cada exame.",
        ExamEducationScene.PRIVACY,
        "Sem uma cópia fora do celular, você pode perder todos os exames se apagar o app, apagar seus dados ou perder acesso ao aparelho. Você pode gerar e guardar um PDF."
    ),
    ExamEducationStep(
        "Compartilhe quando precisar",
        "Gere um PDF e escolha com quem compartilhar: médico, cuidador ou familiar. Compartilhar o PDF não ativa o envio à nuvem.",
        ExamEducationScene.SHARE
    )
)

/** Ilustrações locais: nenhum exemplo é inserido no banco ou enviado ao servidor. */
@Composable
fun ExamEducationIllustration(scene: ExamEducationScene, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = BragaMint.copy(alpha = 0.65f),
        contentColor = BragaTextPrimary
    ) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (scene) {
                ExamEducationScene.STORE -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ExamAvatar("Você", BragaEmerald)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = BragaEmerald)
                        Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                            Icon(Icons.Default.Description, null, tint = BragaEmerald, modifier = Modifier.padding(20.dp).size(48.dp))
                        }
                    }
                    Text("Foto · Arquivo · Digitação", style = MaterialTheme.typography.labelLarge, color = BragaEmerald, textAlign = TextAlign.Center)
                    ExamExampleCard()
                }
                ExamEducationScene.PRIVACY -> {
                    ExamAvatar("A escolha é sua", BragaEmerald)
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhoneAndroid, null, tint = BragaEmerald, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Neste aparelho", fontWeight = FontWeight.Bold)
                                Text("Salvamento padrão", style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Default.CheckCircle, null, tint = BragaEmerald)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudQueue, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Nuvem opcional", fontWeight = FontWeight.SemiBold)
                            Text("Somente com sua autorização", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                ExamEducationScene.SHARE -> {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                        ExamAvatar("Você", BragaEmerald)
                        Surface(shape = CircleShape, color = Color.White) {
                            Icon(Icons.Default.PictureAsPdf, null, tint = BragaEmerald, modifier = Modifier.padding(16.dp).size(32.dp))
                        }
                        ExamAvatar("Quem você escolher", Color(0xFF567889))
                    }
                    Text("Seu PDF, seu destinatário", style = MaterialTheme.typography.labelLarge, color = BragaEmerald, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun ExamAvatar(label: String, color: Color) {
    Column(Modifier.widthIn(max = 88.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(shape = CircleShape, color = color.copy(alpha = 0.12f)) {
            Icon(Icons.Default.Person, null, tint = color, modifier = Modifier.padding(12.dp).size(36.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
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
        Text("Seus exames, à mão", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Guarde seus laudos, encontre os resultados e compartilhe quando precisar.", style = MaterialTheme.typography.bodyMedium)
        ExamExampleCard()
        Text("Os novos exames ficam no celular. Você escolhe se quer uma cópia na nuvem.", style = MaterialTheme.typography.bodySmall)
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
