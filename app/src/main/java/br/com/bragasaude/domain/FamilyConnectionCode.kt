package br.com.bragasaude.domain

import java.util.Locale

object FamilyConnectionCode {
    fun parse(raw: String?): String? = raw?.trim()?.uppercase(Locale.ROOT)?.takeIf { Regex("[A-Z0-9]{8}").matches(it) }
}
