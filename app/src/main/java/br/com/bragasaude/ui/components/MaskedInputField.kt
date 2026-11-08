package br.com.bragasaude.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import br.com.bragasaude.data.util.HealthFormatter

enum class InputMask {
    DATE, TIME, TIME_LIST;

    fun raw(text: String): String = when (this) {
        DATE -> text.filter(Char::isDigit).take(8)
        TIME -> text.filter(Char::isDigit).take(4)
        TIME_LIST -> text.split(',').take(24).joinToString(",") { it.filter(Char::isDigit).take(4) }
    }

    fun display(raw: String): String = when (this) {
        DATE -> HealthFormatter.formatDateInput(raw)
        TIME -> HealthFormatter.formatTimeInput(raw)
        TIME_LIST -> raw.split(',').joinToString(", ") { HealthFormatter.formatTimeInput(it) }
    }

    /** A máscara nunca entra no buffer editável recebido do teclado. */
    fun edit(value: TextFieldValue): TextFieldValue {
        val text = raw(value.text)
        fun offset(index: Int) = raw(value.text.take(index.coerceIn(0, value.text.length))).length.coerceAtMost(text.length)
        return value.copy(text = text, selection = TextRange(offset(value.selection.start), offset(value.selection.end)),
            composition = value.composition?.let { TextRange(offset(it.start), offset(it.end)) })
    }

    fun transformation() = VisualTransformation { value ->
        val source = value.text
        val masked = display(source)
        val positions = masked.indices.filter { masked[it].isDigit() || (this == TIME_LIST && masked[it] == ',') }
        TransformedText(AnnotatedString(masked), object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = positions.getOrNull(offset) ?: masked.length
            override fun transformedToOriginal(offset: Int): Int = masked.take(offset).count {
                it.isDigit() || (this@InputMask == TIME_LIST && it == ',')
            }.coerceAtMost(source.length)
        })
    }
}

/** O formulário recebe o valor formatado; seleção/composição ficam no estado local cru. */
@Composable
fun MaskedInputField(
    value: String,
    onValueChange: (String) -> Unit,
    mask: InputMask,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = if (mask == InputMask.TIME_LIST) KeyboardType.Text else KeyboardType.Number)
) {
    var editing by remember(mask) { mutableStateOf(TextFieldValue(mask.raw(value))) }
    LaunchedEffect(value, mask) {
        val raw = mask.raw(value)
        if (raw != editing.text) editing = TextFieldValue(raw, TextRange(raw.length))
    }
    OutlinedTextField(
        value = editing,
        onValueChange = { next ->
            editing = mask.edit(next)
            onValueChange(mask.display(editing.text))
        },
        visualTransformation = remember(mask) { mask.transformation() },
        modifier = modifier, enabled = enabled, label = label, placeholder = placeholder,
        supportingText = supportingText, trailingIcon = trailingIcon, isError = isError,
        singleLine = singleLine, shape = shape, keyboardOptions = keyboardOptions
    )
}
