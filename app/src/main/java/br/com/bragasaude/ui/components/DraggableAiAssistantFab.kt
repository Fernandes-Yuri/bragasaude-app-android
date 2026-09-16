package br.com.bragasaude.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaMint
import br.com.bragasaude.ui.util.Screen
import br.com.bragasaude.ui.voice.OrbVisualState
import br.com.bragasaude.ui.voice.VoiceAssistantOrb
import br.com.bragasaude.ui.voice.VoiceHealthViewModel
import br.com.bragasaude.ui.voice.VoiceNavigationEvent
import br.com.bragasaude.ui.voice.VoiceUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Paleta oficial do Copiloto Metamórfico Braga Saúde
private val FabCyan = Color(0xFF00E5FF)
private val FabViolet = Color(0xFF7C4DFF)
private val FabEmerald = Color(0xFF00A884)
private val FabListeningRed = Color(0xFFFF5252)

/**
 * Ícone estilizado com 3 corações verdes (BragaEmerald) com cruz médica branca dentro,
 * dispostos em ordem crescente (pequeno → médio → grande) em arco ascendente diagonal,
 * inspirado no padrão visual das estrelas do Gemini.
 */
@Composable
fun TripleHeartAssistantIcon(
    modifier: Modifier = Modifier,
    heartColor: Color = BragaEmerald,
    crossColor: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Heart 1 — Pequeno (canto inferior esquerdo, proporcional e visível)
        drawHeartWithCross(
            drawScope = this,
            centerX = w * 0.20f,
            centerY = h * 0.76f,
            width = w * 0.26f,
            height = h * 0.25f,
            heartColor = heartColor,
            crossColor = crossColor
        )

        // Heart 2 — Médio (central, harmonioso e equidistante)
        drawHeartWithCross(
            drawScope = this,
            centerX = w * 0.44f,
            centerY = h * 0.52f,
            width = w * 0.34f,
            height = h * 0.33f,
            heartColor = heartColor,
            crossColor = crossColor
        )

        // Heart 3 — Grande (canto superior direito, sem sobrepor o médio)
        drawHeartWithCross(
            drawScope = this,
            centerX = w * 0.77f,
            centerY = h * 0.23f,
            width = w * 0.43f,
            height = h * 0.41f,
            heartColor = heartColor,
            crossColor = crossColor
        )
    }
}

private fun drawHeartWithCross(
    drawScope: DrawScope,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    heartColor: Color,
    crossColor: Color
) {
    val path = Path().apply {
        moveTo(centerX, centerY + height * 0.46f)
        cubicTo(
            centerX - width * 0.28f, centerY + height * 0.22f,
            centerX - width * 0.54f, centerY - height * 0.05f,
            centerX - width * 0.50f, centerY - height * 0.26f
        )
        cubicTo(
            centerX - width * 0.44f, centerY - height * 0.50f,
            centerX - width * 0.10f, centerY - height * 0.48f,
            centerX, centerY - height * 0.20f
        )
        cubicTo(
            centerX + width * 0.10f, centerY - height * 0.48f,
            centerX + width * 0.44f, centerY - height * 0.50f,
            centerX + width * 0.50f, centerY - height * 0.26f
        )
        cubicTo(
            centerX + width * 0.54f, centerY - height * 0.05f,
            centerX + width * 0.28f, centerY + height * 0.22f,
            centerX, centerY + height * 0.46f
        )
        close()
    }
    drawScope.drawPath(path, heartColor)

    // Cruz médica branca centralizada no coração
    val crossArm = width * 0.28f
    val crossThick = width * 0.09f
    val crossCenterY = centerY - height * 0.02f
    val cornerRadius = CornerRadius(crossThick * 0.35f, crossThick * 0.35f)

    // Braço horizontal
    drawScope.drawRoundRect(
        color = crossColor,
        topLeft = Offset(centerX - crossArm / 2f, crossCenterY - crossThick / 2f),
        size = Size(crossArm, crossThick),
        cornerRadius = cornerRadius
    )
    // Braço vertical
    drawScope.drawRoundRect(
        color = crossColor,
        topLeft = Offset(centerX - crossThick / 2f, crossCenterY - crossArm / 2f),
        size = Size(crossThick, crossArm),
        cornerRadius = cornerRadius
    )
}

@Composable
private fun AnimatedAudioWaveBar(delay: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "WaveBar")
    val heightScale by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(delay)
        ),
        label = "WaveBarHeight"
    )
    Box(
        modifier = Modifier
            .width(3.dp)
            .height(18.dp * heightScale)
            .clip(CircleShape)
            .background(FabListeningRed)
    )
}

/**
 * UI-V01 / D14 — Copiloto de Voz Metamórfico e Arrastável (In-App Copilot).
 */
@Composable
fun DraggableAiAssistantFab(
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VoiceHealthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val audioRmsDb by viewModel.audioRmsDb.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val isLiveMode by viewModel.isLiveMode.collectAsState()
    var showEmergencyDialog by remember { mutableStateOf(false) }

    // Observar eventos de navegação autônoma disparados pelo Copiloto de Voz
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is VoiceNavigationEvent.NavigateToVitals -> {
                    if (event.type in setOf("HEART_RATE", "OXYGEN_SATURATION")) {
                        onNavigate(Screen.HealthReadings(event.type, event.value))
                    } else {
                        onNavigate(Screen.Vitals(type = event.type, initialValue = event.value))
                    }
                }
                is VoiceNavigationEvent.NavigateToHydration -> {
                    onNavigate(Screen.Hydration(initialMl = event.addMl, autoOpenDialog = event.openCustomDialog))
                }
                is VoiceNavigationEvent.NavigateToNutrition -> {
                    onNavigate(Screen.Nutrition(searchFood = event.searchFoodQuery, openGroceryList = event.openGroceryList))
                }
                is VoiceNavigationEvent.OpenEmergencyDialog -> {
                    showEmergencyDialog = true
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startListening(context)
        }
    }

    val handleFabTap = {
        when {
            !isLiveMode -> {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    viewModel.startListening(context)
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            else -> {
                // Também cancela a resposta em geração, antes de haver áudio.
                viewModel.interruptAndListen(context)
            }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val fabSizePx = with(density) { 62.dp.toPx() }
        val marginPx = with(density) { 16.dp.toPx() }
        val navBarClearancePx = with(density) { 92.dp.toPx() }
        val screenWidthPx = constraints.maxWidth.toFloat()
        val maxX = (screenWidthPx - fabSizePx).coerceAtLeast(0f)
        val maxY = (constraints.maxHeight.toFloat() - fabSizePx).coerceAtLeast(0f)

        // Posição inicial padrão: canto inferior direito, acima da NavigationBar
        val offsetX = remember { Animatable((maxX - marginPx).coerceAtLeast(0f)) }
        val offsetY = remember { Animatable((maxY - navBarClearancePx).coerceIn(0f, maxY)) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(maxX, maxY) {
            if (maxX > 0f && offsetX.value == 0f) {
                offsetX.snapTo((maxX - marginPx).coerceAtLeast(0f))
            }
            if (maxY > 0f && offsetY.value == 0f) {
                offsetY.snapTo((maxY - navBarClearancePx).coerceIn(0f, maxY))
            }
        }

        // Pulso suave de convite ao toque em IDLE
        val infiniteTransition = rememberInfiniteTransition(label = "FabPulse")
        val idlePulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "FabIdlePulse"
        )

        val isOrbMode = isLiveMode || state !is VoiceUiState.Idle
        val isListening = state is VoiceUiState.Listening

        // Configurações de animação visual
        val glowDuration = if (isListening) 800 else 2000
        val maxGlowRadiusMult = if (isListening) 1.0f else 0.85f

        val glowRadiusMult by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = maxGlowRadiusMult,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = glowDuration, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "GlowRadius"
        )

        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = glowDuration, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "GlowAlpha"
        )

        val ring1Scale by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(2500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "Ring1Scale"
        )

        val ring1Alpha by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(2500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "Ring1Alpha"
        )

        val ring2Scale by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(3500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(1500)
            ),
            label = "Ring2Scale"
        )

        val ring2Alpha by infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(3500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(1500)
            ),
            label = "Ring2Alpha"
        )

        val borderRotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "BorderRotation"
        )

        val borderColors = if (isListening) {
            listOf(FabListeningRed, FabCyan, FabListeningRed, FabCyan)
        } else {
            listOf(FabCyan, FabEmerald, FabViolet, FabCyan)
        }

        var shockwaveProgress by remember { mutableStateOf<Animatable<Float, AnimationVector1D>?>(null) }

        val targetShadow = when {
            isOrbMode -> 24.dp
            isListening -> 20.dp
            else -> {
                val fraction = ((idlePulseScale - 1f) / 0.06f).coerceIn(0f, 1f)
                lerp(8.dp, 18.dp, fraction)
            }
        }
        val shadowDp by animateDpAsState(targetShadow, label = "Shadow")

        // 2. Continuous Glow Pulse + Concentric Rings + Shockwave
        Canvas(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .size(62.dp)
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val canvasFabSize = size.width

            if (!isOrbMode) {
                // Glow Pulse
                val glowColor1 = if (isListening) FabListeningRed.copy(alpha = 0.25f * glowAlpha) else FabCyan.copy(alpha = 0.25f * glowAlpha)
                val glowColor2 = if (isListening) FabListeningRed.copy(alpha = 0.12f * glowAlpha) else FabEmerald.copy(alpha = 0.12f * glowAlpha)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor1, glowColor2, Color.Transparent),
                        center = center,
                        radius = canvasFabSize * glowRadiusMult
                    ),
                    radius = canvasFabSize * glowRadiusMult,
                    center = center
                )

                // Concentric Rings
                drawCircle(
                    color = FabEmerald,
                    radius = canvasFabSize * 0.55f * ring1Scale,
                    center = center,
                    style = Stroke(width = 1.5f),
                    alpha = ring1Alpha
                )
                drawCircle(
                    color = FabEmerald,
                    radius = canvasFabSize * 0.55f * ring2Scale,
                    center = center,
                    style = Stroke(width = 1.5f),
                    alpha = ring2Alpha
                )
            }

            // Click Shockwave
            val shockwave = shockwaveProgress
            if (shockwave != null && shockwave.value > 0f) {
                drawCircle(
                    color = FabCyan,
                    radius = canvasFabSize * 0.5f * (1f + shockwave.value),
                    center = center,
                    alpha = 0.3f * (1f - shockwave.value)
                )
            }
        }

        // Container para FAB + Badge 'X' de Fechar
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .size(72.dp)
        ) {
            // Main FAB Box (62.dp centralizado no container)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(62.dp)
                .graphicsLayer {
                    if (!isOrbMode && !isListening) {
                        scaleX = idlePulseScale
                        scaleY = idlePulseScale
                    }
                }
                .shadow(shadowDp, CircleShape)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            scope.launch {
                                val animatable = Animatable(0f)
                                shockwaveProgress = animatable
                                animatable.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
                                if (shockwaveProgress == animatable) {
                                    shockwaveProgress = null
                                }
                            }
                            handleFabTap()
                        }
                    )
                }
                .pointerInput(maxX, maxY) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newX = (offsetX.value + dragAmount.x).coerceIn(0f, maxX)
                            val newY = (offsetY.value + dragAmount.y).coerceIn(0f, maxY)
                            scope.launch {
                                offsetX.snapTo(newX)
                                offsetY.snapTo(newY)
                            }
                        },
                        onDragEnd = {
                            // Repouso lateral suave (snap-to-edge)
                            val targetX = if (offsetX.value + fabSizePx / 2f <= screenWidthPx / 2f) {
                                marginPx
                            } else {
                                maxX - marginPx
                            }
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue = targetX,
                                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                                )
                            }
                        }
                    )
                }
        ) {
            // Animated Gradient Border (Outer layer)
            if (!isOrbMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = borderRotation }
                        .background(Brush.sweepGradient(borderColors))
                )
            }

            // Inner Background & Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isOrbMode) 0.dp else 2.dp)
                    .clip(CircleShape)
                    .then(
                        when {
                            isOrbMode -> Modifier.background(Color.Transparent)
                            isListening -> Modifier.background(Color(0xFF1E293B))
                            else -> Modifier.background(
                                brush = Brush.linearGradient(
                                    colors = listOf(BragaMint, BragaEmerald.copy(alpha = 0.25f)),
                                    start = Offset.Zero,
                                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                                )
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = if (isOrbMode) "ORB" else "IDLE",
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    },
                    label = "CopilotMorphing"
                ) { mode ->
                    when (mode) {
                        "ORB" -> {
                            val orbVisualState = when {
                                isSpeaking -> OrbVisualState.SPEAKING
                                isListening -> OrbVisualState.LISTENING
                                else -> OrbVisualState.THINKING
                            }
                            VoiceAssistantOrb(
                                state = orbVisualState,
                                audioRmsDb = audioRmsDb,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            var showMic by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                while (true) {
                                    delay(2500)
                                    showMic = !showMic
                                }
                            }
                            AnimatedContent(
                                targetState = showMic,
                                transitionSpec = {
                                    fadeIn(
                                        tween(800, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f))
                                    ) togetherWith fadeOut(
                                        tween(800, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f))
                                    )
                                },
                                label = "IdleIconAlternation"
                            ) { isMic ->
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isMic) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = if (isSpeaking || state is VoiceUiState.Saving) "Interromper resposta e falar com o Braga" else "Falar com o Braga",
                                            tint = BragaEmerald,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    } else {
                                        TripleHeartAssistantIcon(
                                            modifier = Modifier.size(34.dp),
                                            heartColor = BragaEmerald,
                                            crossColor = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

            // Badge 'X' para encerrar o Live Streaming e voltar ao modo repouso
            if (isOrbMode) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.TopEnd)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A))
                        .border(1.5.dp, FabCyan, CircleShape)
                        .clickable { viewModel.stopLiveMode() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Encerrar conversa ao vivo",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }

    if (showEmergencyDialog) {
        // Assume this component exists in the current scope or is imported
        // Since we are preserving it, it should just compile if it compiled before.
        RiskNotificationDialog(
            type = "EMERGENCIA",
            message = "Atenção: Sintomas agudos exigem avaliação médica urgente. Escolha uma das opções abaixo para obter socorro imediato:",
            onDismiss = { showEmergencyDialog = false }
        )
    }
}
