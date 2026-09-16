package br.com.bragasaude.data.util

/** Mantém cada coordenada separada ao persistir o histórico entre atualizações GPS. */
internal fun appendLocationHistory(raw: String, lat: Double, lng: Double, limit: Int): String {
    val entries = raw.split(';').filter { it.isNotEmpty() }
    val entry = "$lat|$lng"
    val updated = if (entry in entries) entries else entries + entry
    return updated.takeLast(limit).joinToString(";")
}
