package br.com.bragasaude.data.local.organizer

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tres tons derivados do acento escolhido: faixa principal, faixa clara (brilho)
 * e cor da legenda sob o titulo. Os valores do indice 0 reproduzem EXATAMENTE
 * a identidade padrao Braga (verde esmeralda), entao quem nao personaliza
 * recebe um PDF identico ao de antes.
 */
data class AccentTone(val main: Triple<Int, Int, Int>, val light: Triple<Int, Int, Int>, val subtitle: Triple<Int, Int, Int>)

/**
 * Preferencias de desenho da capa do PDF consolidado. Nada clinico aqui:
 * apenas nome de exibicao, indice da paleta e indice da fonte. Persistido
 * localmente (SharedPreferences) para valer entre sessoes, sem tocar nos
 * dados efemeros dos exames.
 */
data class OrganizerPersonalization(
    val coverName: String = "",
    val accentIndex: Int = 0,
    val fontIndex: Int = 0
) {
    val isBragaDefault: Boolean get() = coverName.isBlank() && accentIndex == 0 && fontIndex == 0

    /** Titulo grande da banda da capa e dos cabecalhos das paginas geradas. */
    fun displayTitle(): String = coverName.trim().ifBlank { "BRAGA SAUDE" }.uppercase()

    fun accent(): AccentTone = ACCENTS[accentIndex.coerceIn(ACCENTS.indices)]

    fun regularFont(): PDType1Font = when (fontIndex) {
        1 -> PDType1Font.TIMES_ROMAN
        2 -> PDType1Font.COURIER
        else -> PDType1Font.HELVETICA
    }

    fun boldFont(): PDType1Font = when (fontIndex) {
        1 -> PDType1Font.TIMES_BOLD
        2 -> PDType1Font.COURIER_BOLD
        else -> PDType1Font.HELVETICA_BOLD
    }

    companion object {
        val ACCENTS: List<AccentTone> = listOf(
            AccentTone(Triple(0, 137, 123), Triple(0, 168, 132), Triple(230, 244, 241)),
            AccentTone(Triple(14, 116, 144), Triple(22, 157, 201), Triple(226, 243, 248)),
            AccentTone(Triple(109, 40, 217), Triple(139, 92, 246), Triple(239, 233, 252)),
            AccentTone(Triple(157, 23, 77), Triple(190, 24, 93), Triple(251, 230, 239)),
            AccentTone(Triple(71, 85, 105), Triple(100, 116, 139), Triple(233, 238, 245)),
            AccentTone(Triple(180, 83, 9), Triple(217, 119, 6), Triple(250, 241, 224))
        )
        val ACCENT_LABELS = listOf("Braga", "Petróleo", "Violeta", "Vinho", "Grafite", "Âmbar")
        val FONT_LABELS = listOf("Moderna", "Clássica", "Técnica")
        const val MAX_COVER_NAME = 60
    }
}

/** Cache local das preferencias de capa. De proposito: somente cor/fonte/nome, nenhum dado de exame. */
@Singleton
class OrganizerPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("organizer-prefs", Context.MODE_PRIVATE)

    fun load(): OrganizerPersonalization = OrganizerPersonalization(
        coverName = prefs.getString(KEY_NAME, "") ?: "",
        accentIndex = prefs.getInt(KEY_ACCENT, 0),
        fontIndex = prefs.getInt(KEY_FONT, 0)
    )

    fun save(personalization: OrganizerPersonalization) {
        prefs.edit()
            .putString(KEY_NAME, personalization.coverName.take(OrganizerPersonalization.MAX_COVER_NAME))
            .putInt(KEY_ACCENT, personalization.accentIndex)
            .putInt(KEY_FONT, personalization.fontIndex)
            .apply()
    }

    private companion object {
        const val KEY_NAME = "coverName"
        const val KEY_ACCENT = "accentIndex"
        const val KEY_FONT = "fontIndex"
    }
}
