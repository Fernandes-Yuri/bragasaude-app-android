package br.com.bragasaude.ui.b2b

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formatação de texto compartilhada do B2B: plurais reais, datas pt-BR e rótulos
 * humanos para enums que não devem vazar para a UI.
 *
 * Regra de produto (auditoria UX 20/11/2026): nunca renderizar "(s)/(ões)/(ns)",
 * datas ISO cruas ou nomes de enum em CAIXA_ALTA na interface.
 */
object B2BTextFormatters {

    private val ptBr = Locale.forLanguageTag("pt-BR")
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", ptBr)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", ptBr)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", ptBr)

    /** "1 dia" / "2 dias". */
    fun days(count: Int): String = if (count == 1) "1 dia" else "$count dias"

    /** Concordância simples: escolhe singular ou plural pelo número. */
    fun plural(count: Int, singular: String, plural: String): String = when {
        count == 1 -> singular
        else -> plural
    }

    /** "1 leitura confirmada" / "3 leituras confirmadas". */
    fun countLabel(count: Int, singular: String, plural: String): String =
        "$count ${plural(count, singular, plural)}"

    /** "1 participante ativo" / "2 participantes ativos". */
    fun countLabelAgreement(
        count: Int,
        singular: String,
        plural: String,
        adjectiveSingular: String,
        adjectivePlural: String
    ): String = "$count ${plural(count, singular, plural)} ${plural(count, adjectiveSingular, adjectivePlural)}"

    /** Converte "2026-11-19" (ISO) em "19/11/2026"; devolve o original se não reconhecer. */
    fun formatIsoDate(raw: String?): String? {
        val clean = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { LocalDate.parse(clean).format(dateFormatter) }
            .getOrElse {
                runCatching { OffsetDateTime.parse(clean).format(dateFormatter) }
                    .getOrElse {
                        runCatching { LocalDateTime.parse(clean).format(dateFormatter) }
                            .getOrElse { clean }
                    }
            }
    }

    /** Converte ISO timestamp em "19/11/2026 às 19:51"; devolve o original se não reconhecer. */
    fun formatIsoDateTime(raw: String?): String? {
        val clean = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { OffsetDateTime.parse(clean).format(dateTimeFormatter) }
            .getOrElse {
                runCatching { Instant.parse(clean).atZone(ZoneId.systemDefault()).format(dateTimeFormatter) }
                    .getOrElse {
                        runCatching { LocalDateTime.parse(clean).format(dateTimeFormatter) }
                            .getOrElse { clean }
                    }
            }
    }

    /** Rótulo humano para o modo de ativação da Recepcionista IA (nunca expor o enum). */
    fun activationModeLabel(mode: String?): String = when (mode?.trim()?.uppercase(Locale.US)) {
        "ALWAYS_FIRST" -> "Sempre no primeiro contato"
        "OUT_OF_HOURS_ONLY" -> "Fora do expediente"
        "MANUAL_ONLY" -> "Sob demanda"
        else -> "Sob demanda"
    }

    /** Rótulo humano do papel na visão do gestor (não usar "Familiar" para gestor). */
    fun participantShareBadge(hasFamilyBinding: Boolean): String =
        if (hasFamilyBinding) "Participante autorizou compartilhamento" else "Compartilhamento autorizado pelo participante"
}
