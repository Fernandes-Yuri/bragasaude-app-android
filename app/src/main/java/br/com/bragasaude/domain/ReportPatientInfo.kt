package br.com.bragasaude.domain

import br.com.bragasaude.data.remote.model.RemoteProfile
import br.com.bragasaude.data.util.HealthFormatter

/** O contrato do perfil armazena altura em centímetros. */
object ReportPatientInfo {
    fun line(profile: RemoteProfile): String {
        val birth = HealthFormatter.formatDisplayDate(profile.birthDate).ifBlank { "--" }
        val weight = profile.weight?.let { "${HealthFormatter.formatNumber(it)} kg" } ?: "--"
        val height = profile.height?.let { "${HealthFormatter.formatNumber(it)} cm" } ?: "--"
        return "Nascimento: $birth  |  Gênero: ${profile.gender ?: "--"}  |  Peso: $weight  |  Altura: $height"
    }
}
