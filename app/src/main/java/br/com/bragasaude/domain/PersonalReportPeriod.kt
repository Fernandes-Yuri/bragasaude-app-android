package br.com.bragasaude.domain

import java.util.Calendar

data class PersonalReportPeriod(val startMillis: Long, val endMillis: Long) {
    fun contains(timestamp: Long) = timestamp in startMillis..endMillis

    companion object {
        fun current(now: Calendar = Calendar.getInstance()): PersonalReportPeriod {
            val start = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -29)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return PersonalReportPeriod(start.timeInMillis, now.timeInMillis)
        }
    }
}
