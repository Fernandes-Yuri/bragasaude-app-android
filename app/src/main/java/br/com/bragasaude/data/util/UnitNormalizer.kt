package br.com.bragasaude.data.util

import kotlin.math.round

/**
 * Contrato do Normalizador Canônico de Unidades Laboratoriais.
 * Conforme Seção 6.3 do Caderno de Contratos (08_CADERNO_DE_CONTRATOS_EXAMES_E_DOSSIE.md) e Decisão D49.
 */
interface IUnitNormalizer {
    /**
     * Converte o valor medido para a unidade canônica brasileira padrão antes da agregação.
     */
    fun normalize(parameterKey: String, rawValue: Double, rawUnit: String): CanonicalValue
}

data class CanonicalValue(
    val normalizedValue: Double,
    val canonicalUnit: String,
    val wasConverted: Boolean
)

object UnitNormalizer : IUnitNormalizer {

    private fun roundTwoDecimals(value: Double): Double {
        return round(value * 100.0) / 100.0
    }

    private fun cleanUnit(unit: String): String {
        return unit.trim().lowercase()
            .replace(" ", "")
            .replace("μ", "u")
            .replace("µ", "u")
    }

    override fun normalize(parameterKey: String, rawValue: Double, rawUnit: String): CanonicalValue {
        val cleanedKey = parameterKey.trim().lowercase()
        val cleanedUnit = cleanUnit(rawUnit)

        return when (cleanedKey) {
            "glucose", "glicose", "glicemia" -> {
                when {
                    cleanedUnit == "mmol/l" -> {
                        // mmol/L -> mg/dL: multiplica por 18.018
                        CanonicalValue(
                            normalizedValue = roundTwoDecimals(rawValue * 18.018),
                            canonicalUnit = "mg/dL",
                            wasConverted = true
                        )
                    }
                    cleanedUnit == "mg/dl" || cleanedUnit.isEmpty() -> {
                        CanonicalValue(rawValue, "mg/dL", wasConverted = false)
                    }
                    else -> CanonicalValue(rawValue, rawUnit, wasConverted = false)
                }
            }

            "total_cholesterol", "colesterol_total",
            "hdl", "colesterol_hdl",
            "ldl", "colesterol_ldl",
            "vldl", "colesterol_vldl",
            "triglycerides", "triglicerideos", "triglicerides" -> {
                when {
                    cleanedUnit == "mmol/l" -> {
                        // mmol/L -> mg/dL: multiplica por 38.67
                        CanonicalValue(
                            normalizedValue = roundTwoDecimals(rawValue * 38.67),
                            canonicalUnit = "mg/dL",
                            wasConverted = true
                        )
                    }
                    cleanedUnit == "mg/dl" || cleanedUnit.isEmpty() -> {
                        CanonicalValue(rawValue, "mg/dL", wasConverted = false)
                    }
                    else -> CanonicalValue(rawValue, rawUnit, wasConverted = false)
                }
            }

            "vitamin_d", "vitamina_d" -> {
                when {
                    cleanedUnit == "nmol/l" -> {
                        // nmol/L -> ng/mL: divide por 2.496
                        CanonicalValue(
                            normalizedValue = roundTwoDecimals(rawValue / 2.496),
                            canonicalUnit = "ng/mL",
                            wasConverted = true
                        )
                    }
                    cleanedUnit == "ng/ml" || cleanedUnit.isEmpty() -> {
                        CanonicalValue(rawValue, "ng/mL", wasConverted = false)
                    }
                    else -> CanonicalValue(rawValue, rawUnit, wasConverted = false)
                }
            }

            "creatinine", "creatinina" -> {
                when {
                    cleanedUnit in listOf("umol/l", "mcmol/l", "µmol/l") -> {
                        // µmol/L -> mg/dL: divide por 88.4
                        CanonicalValue(
                            normalizedValue = roundTwoDecimals(rawValue / 88.4),
                            canonicalUnit = "mg/dL",
                            wasConverted = true
                        )
                    }
                    cleanedUnit == "mg/dl" || cleanedUnit.isEmpty() -> {
                        CanonicalValue(rawValue, "mg/dL", wasConverted = false)
                    }
                    else -> CanonicalValue(rawValue, rawUnit, wasConverted = false)
                }
            }

            else -> {
                CanonicalValue(rawValue, rawUnit, wasConverted = false)
            }
        }
    }
}
