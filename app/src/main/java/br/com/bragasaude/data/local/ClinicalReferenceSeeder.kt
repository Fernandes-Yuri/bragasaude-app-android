package br.com.bragasaude.data.local

import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeder para popular referências metodológicas clínicas de conformidade com SBC, SBD, ADA e OMS.
 */
@Singleton
class ClinicalReferenceSeeder @Inject constructor(
    private val clinicalReferenceDao: ClinicalReferenceDao
) {

    suspend fun seedIfNeeded() {
        if (clinicalReferenceDao.count() > 0) return

        val initialReferences = listOf(
            // === SBC (Sociedade Brasileira de Cardiologia) / DBHA & OMS ===
            ClinicalReferenceEntity(
                itemKey = "systolic_pressure",
                itemName = "Pressão Sistólica",
                category = "Cardiovascular",
                minTarget = 90.0,
                maxTarget = 129.0,
                minCritical = 60.0,
                maxCritical = 180.0,
                unit = "mmHg",
                interpretationHint = "Faixa ótima < 120 mmHg. Normal: 120–129 mmHg. Pré-hipertensão: 130–139 mmHg.",
                institution = "SBC (Sociedade Brasileira de Cardiologia) / OMS",
                documentVersion = "Diretrizes Brasileiras de Hipertensão Arterial",
                parameter = "Pressão Máxima Arterial",
                updatedAt = Date()
            ),
            ClinicalReferenceEntity(
                itemKey = "diastolic_pressure",
                itemName = "Pressão Diastólica",
                category = "Cardiovascular",
                minTarget = 60.0,
                maxTarget = 84.0,
                minCritical = 40.0,
                maxCritical = 110.0,
                unit = "mmHg",
                interpretationHint = "Faixa ótima < 80 mmHg. Normal: 80–84 mmHg. Pré-hipertensão: 85–89 mmHg.",
                institution = "SBC (Sociedade Brasileira de Cardiologia) / OMS",
                documentVersion = "Diretrizes Brasileiras de Hipertensão Arterial",
                parameter = "Pressão Mínima Arterial",
                updatedAt = Date()
            ),
            ClinicalReferenceEntity(
                itemKey = "heart_rate",
                itemName = "Frequência Cardíaca em Repouso",
                category = "Cardiovascular",
                minTarget = 50.0,
                maxTarget = 100.0,
                minCritical = 40.0,
                maxCritical = 130.0,
                unit = "bpm",
                interpretationHint = "Batimentos por minuto em estado de repouso.",
                institution = "SBC / AHA Diretrizes",
                documentVersion = "2026",
                parameter = "Ritmo Cardíaco",
                updatedAt = Date()
            ),

            // === SBD (Sociedade Brasileira de Diabetes) & ADA 2026 ===
            ClinicalReferenceEntity(
                itemKey = "glucose_fasting",
                itemName = "Glicose em Jejum",
                category = "Metabólico",
                minTarget = 70.0,
                maxTarget = 99.0,
                minCritical = 54.0,
                maxCritical = 300.0,
                unit = "mg/dL",
                interpretationHint = "Jejum de 8 a 12 horas. Faixa de referência esperada: 70-99 mg/dL.",
                institution = "SBD (Sociedade Brasileira de Diabetes) / ADA",
                documentVersion = "2026",
                parameter = "Glicemia Basal",
                updatedAt = Date()
            ),
            ClinicalReferenceEntity(
                itemKey = "glucose_postprandial",
                itemName = "Glicose Pós-Prandial",
                category = "Metabólico",
                minTarget = 70.0,
                maxTarget = 140.0,
                minCritical = 54.0,
                maxCritical = 300.0,
                unit = "mg/dL",
                interpretationHint = "Medição realizada 1 a 2 horas após início da refeição (esperado < 140 mg/dL).",
                institution = "SBD (Sociedade Brasileira de Diabetes) / ADA",
                documentVersion = "2026",
                parameter = "Glicemia Pós-Refeição",
                updatedAt = Date()
            ),
            ClinicalReferenceEntity(
                itemKey = "glucose_cgm",
                itemName = "Glicose Sensor Contínuo (CGM)",
                category = "Metabólico",
                minTarget = 70.0,
                maxTarget = 180.0,
                minCritical = 54.0,
                maxCritical = 300.0,
                unit = "mg/dL",
                interpretationHint = "Tempo no Alvo (Time in Range - TIR): 70 a 180 mg/dL.",
                institution = "SBD / Consenso Internacional CGM",
                documentVersion = "2026",
                parameter = "Monitoramento Contínuo",
                updatedAt = Date()
            ),
            ClinicalReferenceEntity(
                itemKey = "hba1c",
                itemName = "Hemoglobina Glicada (HbA1c)",
                category = "Metabólico",
                minTarget = 4.0,
                maxTarget = 5.6,
                minCritical = 3.5,
                maxCritical = 10.0,
                unit = "%",
                interpretationHint = "Média ponderada da glicemia dos últimos 90 a 120 dias.",
                institution = "SBD (Sociedade Brasileira de Diabetes)",
                documentVersion = "2026",
                parameter = "Fração de Hemoglobina Glicosilada",
                updatedAt = Date()
            ),

            // === OMS (Organização Mundial da Saúde) ===
            ClinicalReferenceEntity(
                itemKey = "imc",
                itemName = "Índice de Massa Corporal (IMC)",
                category = "Antropometria",
                minTarget = 18.5,
                maxTarget = 24.9,
                minCritical = 16.0,
                maxCritical = 35.0,
                unit = "kg/m²",
                interpretationHint = "Razão peso / altura² para avaliação antropométrica populacional.",
                institution = "OMS (Organização Mundial da Saúde)",
                documentVersion = "2026",
                parameter = "Composição Corporal",
                updatedAt = Date()
            ),
            ClinicalReferenceEntity(
                itemKey = "total_cholesterol",
                itemName = "Colesterol Total",
                category = "Perfil Lipídico",
                minTarget = 120.0,
                maxTarget = 199.0,
                minCritical = 80.0,
                maxCritical = 300.0,
                unit = "mg/dL",
                interpretationHint = "Faixa desejável para a população geral adulta.",
                institution = "SBC / OMS",
                documentVersion = "2026",
                parameter = "Lipídios Séricos",
                updatedAt = Date()
            ),
            ClinicalReferenceEntity(
                itemKey = "triglycerides",
                itemName = "Triglicerídeos",
                category = "Perfil Lipídico",
                minTarget = 50.0,
                maxTarget = 149.0,
                minCritical = 30.0,
                maxCritical = 500.0,
                unit = "mg/dL",
                interpretationHint = "Medição com jejum de 12 horas.",
                institution = "SBC / OMS",
                documentVersion = "2026",
                parameter = "Triglicérides Séricos",
                updatedAt = Date()
            )
        )

        clinicalReferenceDao.insertAll(initialReferences)
    }
}
