package br.com.bragasaude.ui.profile

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import br.com.bragasaude.data.util.HealthFormatter

/**
 * Máscaras de digitação dos campos de data e telefone do perfil.
 *
 * D-INP1: antes a formatação era aplicada dentro de `onValueChange`, o que mudava o
 * tamanho da string a cada tecla e fazia o Compose perder a referência do cursor —
 * o usuário via o cursor saltar para o início e os números sendo digitados invertidos.
 *
 * Com `VisualTransformation` + `OffsetMapping` o estado guarda apenas os DÍGITOS e a
 * máscara é só de exibição; o cursor permanece alinhado à digitação física.
 */

private fun maskTransform(
    text: AnnotatedString,
    masker: (String) -> String
): TransformedText {
    val digits = text.text.filter { it.isDigit() }
    val masked = masker(digits)

    // Índice (em `masked`) onde cada dígito original caiu.
    val digitPositions = IntArray(digits.length)
    var di = 0
    for (i in masked.indices) {
        if (di < digits.length && masked[i].isDigit()) {
            digitPositions[di++] = i
        }
    }

    val offsetMapping = object : OffsetMapping {
        // Cursor original (0..n dígitos) -> posição na string mascarada.
        override fun originalToTransformed(offset: Int): Int = when {
            offset < digits.length -> digitPositions[offset]
            else -> masked.length
        }

        // Cursor na string mascarada -> índice nos dígitos originais. Separadores
        // mapeiam para o dígito vizinho, mantendo a edição previsível.
        override fun transformedToOriginal(offset: Int): Int =
            masked.take(offset).count { it.isDigit() }.coerceAtMost(digits.length)
    }

    return TransformedText(AnnotatedString(masked), offsetMapping)
}

/** Máscara DD/MM/AAAA — o estado deve conter apenas dígitos (máx. 8). */
class DateMaskTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        maskTransform(text) { HealthFormatter.formatDateInput(it) }
}

/** Máscara (XX) XXXXX-XXXX — o estado deve conter apenas dígitos (máx. 11). */
class PhoneMaskTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        maskTransform(text) { HealthFormatter.formatPhoneInput(it) }
}
