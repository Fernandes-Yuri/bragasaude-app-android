package br.com.bragasaude.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Estado por composição: a Orbe não disputa atenção com janelas, inclusive aninhadas. */
@Stable
class BragaModalState {
    var openCount by mutableIntStateOf(0)
        private set
    val isOpen: Boolean get() = openCount > 0
    internal fun opened() { openCount++ }
    internal fun closed() { openCount = (openCount - 1).coerceAtLeast(0) }
}
val LocalBragaModalState = staticCompositionLocalOf { BragaModalState() }

@Composable
fun BragaModalHost(content: @Composable () -> Unit) {
    val state = remember { BragaModalState() }
    CompositionLocalProvider(LocalBragaModalState provides state, content = content)
}

@Composable
private fun ModalAppearance(content: @Composable () -> Unit) {
    val state = LocalBragaModalState.current
    DisposableEffect(state) {
        state.opened()
        onDispose { state.closed() }
    }
    val typography = MaterialTheme.typography
    MaterialTheme(
        typography = typography.copy(
            headlineSmall = typography.headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
            titleLarge = typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp)
        ),
        shapes = MaterialTheme.shapes.copy(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)),
        content = content
    )
}

/** Confirmações breves compartilham superfície neutra, tipografia e ações. */
@Composable
fun BragaAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null
) {
    ModalAppearance {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
            modifier = modifier,
            icon = icon,
            title = title,
            text = text,
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        )
    }
}

/** Painel base também usado pelos fluxos que já possuíam conteúdo personalizado. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BragaBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit
) {
    ModalAppearance {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) },
            content = content
        )
    }
}

/** Formulários: corpo limitado/rolável e rodapé acessível acima do teclado. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BragaFormSheet(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    scrollContent: Boolean = true,
    dismissEnabled: Boolean = true
) {
    val currentDismissEnabled by rememberUpdatedState(dismissEnabled)
    val state = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || currentDismissEnabled }
    )
    BragaBottomSheet(onDismissRequest = onDismissRequest, sheetState = state) {
        Column(modifier.fillMaxWidth().imePadding().padding(horizontal = 24.dp)) {
            Column(
                Modifier.weight(1f, fill = false).then(
                    if (scrollContent) Modifier.verticalScroll(rememberScrollState()) else Modifier
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                icon?.invoke()
                title?.let { ProvideTextStyle(MaterialTheme.typography.titleLarge, it) }
                text?.let {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                        ProvideTextStyle(MaterialTheme.typography.bodyLarge, it)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            // Fluxo vertical suporta fontes grandes e traduções sem colisão de botões.
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth(), propagateMinConstraints = true) { confirmButton() }
                dismissButton?.let { Box(Modifier.fillMaxWidth(), propagateMinConstraints = true) { it() } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BragaContentSheet(
    onDismissRequest: () -> Unit,
    scrollContent: Boolean = false,
    content: @Composable () -> Unit
) {
    BragaBottomSheet(onDismissRequest = onDismissRequest) {
        Box(Modifier.fillMaxWidth().imePadding().then(
            if (scrollContent) Modifier.verticalScroll(rememberScrollState()) else Modifier
        )) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BragaDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalAppearance {
        DatePickerDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 0.dp,
            content = content
        )
    }
}
