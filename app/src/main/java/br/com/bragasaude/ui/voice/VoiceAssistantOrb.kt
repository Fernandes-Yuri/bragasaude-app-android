package br.com.bragasaude.ui.voice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Estados visuais do Orb da assistente de voz "Voz da Saúde Braga".
 * Mapeados conforme a Seção 7 do documento de arquitetura
 * (Contexto/15_ASSISTENTE_DE_VOZ_CONVERSACIONAL_E_ARQUITETURA.md).
 */
enum class OrbVisualState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    SUCCESS
}

// Paleta oficial do Orb (Seção 7)
private val ElectricTeal = Color(0xFF00E5FF)
private val VividEmerald = Color(0xFF00A884)
private val CosmicViolet = Color(0xFF7C4DFF)

// Cor dominante por estado: Escutando (azul) -> Pensando (violeta) -> Sucesso (esmeralda)
private fun OrbVisualState.dominantColor(): Color = when (this) {
    OrbVisualState.LISTENING -> ElectricTeal
    OrbVisualState.THINKING -> CosmicViolet
    OrbVisualState.SPEAKING -> ElectricTeal
    OrbVisualState.SUCCESS -> VividEmerald
    OrbVisualState.IDLE -> VividEmerald
}

/**
 * Orb 3D bioluminescente áudio-reativo ("Pra Frentex").
 *
 * Camadas desenhadas em Canvas:
 *  1. Halo de bloom radial desfocado (Ciano -> Esmeralda).
 *  2. Anéis de gradiente concêntricos com cor dominante do estado.
 *  3. Manto de plasma intermediário com gradiente de varredura rotativo contínuo.
 *  4. Partículas luminosas orbitando o manto.
 *  5. Núcleo central brilhante branco-azulado (hot core).
 *
 * Em LISTENING o Orb expande com física de mola atrelada ao [audioRmsDb]
 * (ondas azuis). Em THINKING acelera o vórtice violeta. Em SUCCESS emite
 * pulso esmeralda. Em IDLE mantém respiração lenta suave (escala 0.96 a 1.04).
 *
 * @param state estado visual atual da assistente
 * @param audioRmsDb nível de áudio em dB vindo do SpeechRecognizer.onRmsChanged
 */
@Composable
fun VoiceAssistantOrb(
    state: OrbVisualState,
    audioRmsDb: Float,
    modifier: Modifier = Modifier
) {
    // Velocidade do vórtice: acelerado em THINKING/SPEAKING, suave nos demais
    val rotationDuration = when (state) {
        OrbVisualState.THINKING -> 1800
        OrbVisualState.SPEAKING -> 3000
        else -> 8000
    }

    val infiniteTransition = rememberInfiniteTransition(label = "OrbMotion")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = rotationDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbRotation"
    )

    // Rotação reversa suave para os anéis externos (efeito giroscópio)
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = rotationDuration * 2, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RingRotation"
    )

    // Respiração lenta em IDLE; levemente mais viva nos demais estados
    val breathingPeriod = if (state == OrbVisualState.IDLE) 3500 else 2400
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = breathingPeriod, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Breathing"
    )

    // Pulsação rítmica do núcleo em THINKING/SUCCESS
    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CorePulse"
    )

    // Fase contínua para as partículas luminosas orbitarem
    val particlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ParticlePhase"
    )

    // Suavização do RMS de decibéis do áudio -> escala alvo
    val targetAudioScale = remember(audioRmsDb) {
        val normalized = ((audioRmsDb + 2f) / 12f).coerceIn(0f, 1f)
        1f + (normalized * 0.45f)
    }
    val animatedAudioScale by animateFloatAsState(
        targetValue = if (state == OrbVisualState.LISTENING) targetAudioScale else 1f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "AudioReactiveSpring"
    )

    // Transição suave da cor dominante quando o estado muda
    val dominantColor by animateColorAsState(
        targetValue = state.dominantColor(),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "DominantColor"
    )

    // Em SUCCESS o Orb se acalma com um tom esmeralda mais presente
    val successBoost = if (state == OrbVisualState.SUCCESS) 1f + (corePulse - 1f) * 0.15f else 1f

    Box(
        modifier = modifier
            .size(240.dp)
            .graphicsLayer {
                scaleX = breathingScale * animatedAudioScale * successBoost
                scaleY = breathingScale * animatedAudioScale * successBoost
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2.4f

            // 1. Halo Luminescente Externo (Bloom) — tingido pela cor do estado
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        dominantColor.copy(alpha = 0.35f),
                        VividEmerald.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 1.6f
                ),
                radius = radius * 1.6f,
                center = center
            )

            // 2. Anéis de gradiente concêntricos (giro reverso ao plasma)
            rotate(degrees = ringRotation, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            dominantColor.copy(alpha = 0.75f),
                            Color.Transparent,
                            dominantColor.copy(alpha = 0.35f),
                            Color.Transparent,
                            dominantColor.copy(alpha = 0.75f)
                        ),
                        center = center
                    ),
                    radius = radius * 1.22f,
                    center = center,
                    style = Stroke(width = radius * 0.035f)
                )
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            dominantColor.copy(alpha = 0.55f),
                            Color.Transparent,
                            dominantColor.copy(alpha = 0.2f),
                            Color.Transparent
                        ),
                        center = center
                    ),
                    radius = radius * 1.38f,
                    center = center,
                    style = Stroke(width = radius * 0.02f)
                )
            }

            // 3. Manto de Plasma Intermediário com gradiente de varredura rotativo
            val plasmaBrush = Brush.sweepGradient(
                colors = listOf(
                    ElectricTeal,   // Electric Teal
                    VividEmerald,   // Vivid Emerald
                    CosmicViolet,   // Cosmic Violet
                    ElectricTeal    // Fechamento do ciclo
                ),
                center = center
            )
            rotate(degrees = rotation, pivot = center) {
                drawCircle(
                    brush = plasmaBrush,
                    radius = radius,
                    center = center
                )
            }

            // 4. Partículas luminosas orbitando o manto
            val particleCount = 10
            for (i in 0 until particleCount) {
                val baseAngle = particlePhase + (i * 2f * PI.toFloat() / particleCount)
                val wobble = sin(particlePhase * 2f + i * 1.3f) * radius * 0.06f
                val orbitRadius = radius * 1.1f + wobble
                val particleCenter = Offset(
                    x = center.x + cos(baseAngle) * orbitRadius,
                    y = center.y + sin(baseAngle) * orbitRadius
                )
                val particleRadius = radius * (0.03f + 0.02f * ((sin(particlePhase * 3f + i) + 1f) / 2f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.9f),
                            dominantColor.copy(alpha = 0.6f),
                            Color.Transparent
                        ),
                        center = particleCenter,
                        radius = particleRadius * 2.2f
                    ),
                    radius = particleRadius * 2.2f,
                    center = particleCenter
                )
            }

            // 5. Núcleo Luminescente Central (Hot Core)
            val coreRadius = radius * 0.55f *
                if (state == OrbVisualState.THINKING || state == OrbVisualState.SUCCESS) corePulse else 1f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.9f),
                        dominantColor.copy(alpha = 0.8f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )
        }
    }
}
