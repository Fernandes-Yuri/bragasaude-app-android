package br.com.bragasaude.domain

object HealthCalculators {
    fun calculateIMC(weight: Float, height: Float): Float {
        if (height <= 0) return 0f
        return weight / (height * height)
    }

    fun calculateAge(birthDateStr: String?): Int {
        if (birthDateStr.isNullOrBlank()) return 30
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val birthDate = sdf.parse(birthDateStr) ?: return 30
            val dob = java.util.Calendar.getInstance().apply { time = birthDate }
            val today = java.util.Calendar.getInstance()
            var age = today.get(java.util.Calendar.YEAR) - dob.get(java.util.Calendar.YEAR)
            if (today.get(java.util.Calendar.DAY_OF_YEAR) < dob.get(java.util.Calendar.DAY_OF_YEAR)) {
                age--
            }
            age.coerceIn(1, 120)
        } catch (_: Exception) {
            30
        }
    }

}
