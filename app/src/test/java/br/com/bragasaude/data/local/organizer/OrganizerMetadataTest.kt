package br.com.bragasaude.data.local.organizer
import org.junit.Assert.*
import org.junit.Test
class OrganizerMetadataTest {
    @Test fun rejectsImpossibleDatesAndDoesNotUseBirthDate() {
        assertFalse(OrganizerMetadata.validDate("31/02/2026"))
        assertTrue(OrganizerMetadata.validDate("29/02/2024"))
        // anti-nascimento: nunca sugere o nascimento; emissao contextual (com vocabulario de saude) preenche
        assertEquals("01/10/2026", OrganizerMetadata.suggestDate("Nascimento: 01/01/1980 | Laboratório X\nEmissão: 01/10/2026"))
        assertEquals("02/10/2026", OrganizerMetadata.suggestDate("Nascimento: 01/01/1980 Coleta: 02/10/2026"))
        assertEquals("", OrganizerMetadata.suggestDate("Nascimento: 23/07/1985 | laudo do ano sem outra data"))
    }
    @Test fun keepsUndatedDocumentsLastInBothDirectionsAndAllPages() {
        val a = OrganizerDocument("a", "A", "01/10/2026", pages = 3)
        val b = OrganizerDocument("b", "B", "02/10/2026", pages = 2)
        val c = OrganizerDocument("c", "C", pages = 4)
        assertEquals(listOf(a,b,c), OrganizerMetadata.ordered(listOf(c,b,a), false))
        assertEquals(listOf(b,a,c), OrganizerMetadata.ordered(listOf(c,b,a), true))
        assertEquals(9, OrganizerMetadata.ordered(listOf(c,b,a), true).sumOf { it.pages })
    }
    @Test fun deniesExpiredSessionsIncludingClockGoingBackward() {
        val session = OrganizerSession("id", 1000)
        assertFalse(session.expired(1001))
        assertTrue(session.expired(session.expiresAt))
        assertTrue(session.expired(999))
    }
    @Test fun groupsBySectorFirstThenDatesNewestWithUndatedLast() {
        val lab1 = OrganizerDocument("1", "Hemograma", "10/10/2026", "Laboratorial", pages = 1)
        val lab2 = OrganizerDocument("2", "Glicemia", "15/10/2026", "Laboratorial", pages = 1)
        val lab0 = OrganizerDocument("3", "Ureia", "", "Laboratorial", pages = 1)
        val img = OrganizerDocument("4", "US abdome", "01/09/2026", "Imagem", pages = 1)
        val sectors = OrganizerMetadata.sections(listOf(img, lab0, lab1, lab2), newest = true)
        assertEquals(listOf("Laboratorial", "Imagem"), sectors.map { it.type })
        val laboratorial = sectors.first()
        assertEquals(listOf("15/10/2026", "10/10/2026", ""), laboratorial.groups.map { it.date })
        assertEquals(listOf("2", "1", "3"), laboratorial.groups.flatMap { it.documents }.map { it.id })
        assertEquals(listOf("4"), sectors[1].groups.single().documents.map { it.id })
        val oldestFirst = OrganizerMetadata.sections(listOf(lab0, lab1, lab2), newest = false)
        assertEquals(listOf("10/10/2026", "15/10/2026", ""), oldestFirst.single().groups.map { it.date })
    }
    @Test fun flatteningSectionsKeepsUndatedLastInBothDirections() {
        val a = OrganizerDocument("a", "A", "01/10/2026", pages = 3)
        val b = OrganizerDocument("b", "B", "02/10/2026", pages = 2)
        val c = OrganizerDocument("c", "C", pages = 4)
        val flat = OrganizerMetadata.sections(listOf(c, b, a), true).flatMap { it.groups }.flatMap { it.documents }
        assertEquals(listOf(b, a, c), flat)
        val flatAsc = OrganizerMetadata.sections(listOf(c, b, a), false).flatMap { it.groups }.flatMap { it.documents }
        assertEquals(listOf(a, b, c), flatAsc)
    }
    @Test fun doesNotConfuseUltrasoundEchogenicityWithEchocardiogram() {
        assertFalse(OrganizerMetadata.extractTopics("Nódulo hipoecoico no lobo direito, parenquima hiperecoico").contains("Ecocardiograma"))
        assertTrue(OrganizerMetadata.extractTopics("ECO com Doppler normal").contains("Ecocardiograma"))
        assertTrue(OrganizerMetadata.extractTopics("Ecocardiografia com Doppler").contains("Ecocardiograma"))
        val (title, type, _) = OrganizerMetadata.suggestWithTopics("Ecocardiografia com doppler", "x")
        assertEquals("Ecocardiograma", title)
        assertEquals("Cardiológico", type)
    }
    @Test fun examDateCascadeFromStrictLabelsToRelaxedContext() {
        // rotulos estritos seguem prioritarios (padrao dos arquivos digitais)
        assertEquals("12/05/2026", OrganizerMetadata.suggestDate("Data do exame: 12/05/2026"))
        assertEquals("03/02/2025", OrganizerMetadata.suggestDate("data de coleta - 03/02/2025"))
        // formas reais de hospitais (com vocabulario de saude no texto, como o portao exige)
        assertEquals("12/05/2026", OrganizerMetadata.suggestDate("CLÍNICA MODELO\nData: 12/05/2026"))
        assertEquals("12/05/2026", OrganizerMetadata.suggestDate("Hospital Y — Data da realização: 12/05/2026"))
        assertEquals("20/10/2026", OrganizerMetadata.suggestDate("TERMO DE ALTA HOSPITALAR\nData da internação: 20/10/2026\nData da alta: 24/10/2026"))
        assertEquals("05/03/2025", OrganizerMetadata.suggestDate("RECEITUARIO\nAtendimento em 05/03/2025 com retorno"))
        assertEquals("15/08/2025", OrganizerMetadata.suggestDate("Laboratório Modelo de Análises\nEmissão: 15/08/2025"))
        // sem rótulo e sem contexto: continua em branco (usuario confere manualmente)
        assertEquals("", OrganizerMetadata.suggestDate("Paciente 01/01/1980 exame sem rotulo"))
        assertEquals("", OrganizerMetadata.suggestDate("Conclusao radiologica estavel."))
    }
    @Test fun relaxedDateNeedsHealthVocabularyOrKnownExamKeyword() {
        // o portao: sem vocabulario de saude nem exame conhecido, data relaxada fica em branco —
        // feedback do teste fisico: "Data:" solto numa foto de objeto nao pode preencher o campo
        assertEquals("", OrganizerMetadata.suggestDate("udDdLd\nUn novD raminbo, Ue rava vica.\nData: 29/06/2026"))
        assertEquals("", OrganizerMetadata.suggestDate("Empresa:\nEndereço:\nData: 19/03/2026"))
        assertEquals("", OrganizerMetadata.suggestDate("Data: 12/05/2026"))
        // com 1 palavra de vocabulario a mesma forma preenche (regra aprovada: basta uma)
        assertEquals("12/05/2026", OrganizerMetadata.suggestDate("Exame de rotina.\nData: 12/05/2026"))
        assertEquals("12/05/2026", OrganizerMetadata.suggestDate("Resultado normal.\nData: 12/05/2026"))
    }
    @Test fun birthDatesAreNeverSuggestedAsExamDate() {
        assertEquals("", OrganizerMetadata.suggestDate("Paciente: Fulano | Nascimento: 23/07/1985 | laudo"))
        assertEquals("15/08/2025", OrganizerMetadata.suggestDate("Laudo\nNascimento: 23/07/1985\nEmissão: 15/08/2025"))
    }
    @Test fun ocrTitleUsesFirstUsefulLinesWhenNoTopicMatches() {
        // termo de alta fotografado: OCR leu, mas nenhum exame conhecido bateu (reproduz o teste fisico)
        val ocr = "Hospital Geral de Caxias\nTERMO DE ALTA HOSPITALAR\nPaciente internado 5 dias, quadro estavel."
        val (title, type, topics) = OrganizerMetadata.suggestWithTopics(ocr, "IMG_20261027_1154.jpg")
        assertEquals("Hospital Geral de Caxias — TERMO DE ALTA HOSPITALAR", title)
        assertEquals("Outros", type)
        assertTrue(topics.isEmpty())
    }
    @Test fun ocrNoiseFallsBackToNeutralPhotoTitle() {
        // feedback real do 4o teste fisico: fotos de objetos geravam titulos como
        // "udDdLd — Un novD raminbo" — agora viram convite neutro para o usuario digitar
        assertEquals("Foto de exame",
            OrganizerMetadata.suggestWithTopics("udDdLd\nUn novD raminbo, Ue rava vica.", "IMG_0001.jpg").first)
        assertEquals("Foto de exame",
            OrganizerMetadata.suggestWithTopics("Endereço:\nEndereço:", "IMG_0002.jpg").first)
        assertEquals("Foto de exame",
            OrganizerMetadata.suggestWithTopics("MNTRIS\nEmpresa:", "IMG_0003.jpg").first)
    }
    @Test fun labelLinesAndJunkTokensInsideGoodLinesNeverReachTheTitle() {
        // 6o teste fisico — "Responsável famillar pela Internação:" e "OcUPACIQNAL" sujavam o titulo
        assertEquals("TERMO DE ALTA",
            OrganizerMetadata.suggestWithTopics(
                "TERMO DE ALTA\nResponsável famillar pela Internação:\nQuadro estável no momento",
                "IMG_termo.jpg"
            ).first)
        assertEquals("ATESTADO DE SAÚDE — Médico Coordenador do",
            OrganizerMetadata.suggestWithTopics(
                "ATESTADO DE SAÚDE OcUPACIQNAL\nMédico Coordenador do PCMSO",
                "IMG_atestado.jpg"
            ).first)
    }
    @Test fun healthGateOpensWithOneVocabularyWord() {
        assertFalse(OrganizerMetadata.looksLikeHealthDocument("udDdLd Un novD raminbo"))
        assertTrue(OrganizerMetadata.looksLikeHealthDocument("Hospital Geral de Caxias"))
        assertTrue(OrganizerMetadata.looksLikeHealthDocument("nota de resultado qualquer")) // 1 palavra basta (regra aprovada)
    }
    @Test fun doesNotConfuseBoneTermsWithUrineExam() {
        // regressao real do teste fisico: "osseas" casava com "eas" por substring
        val raiox = "RADIOGRAFIA DE TORAX. Raio-x em incidencias PA e perfil. " +
            "Estruturas osseas e partes moles sem alteracoes. Seios costofrenicos livres."
        val (title, type, topics) = OrganizerMetadata.suggestWithTopics(raiox, "raiox_torax.pdf")
        assertEquals("Radiografia", title)
        assertEquals("Imagem", type)
        assertFalse(topics.contains("Urina (EAS)"))
        assertTrue(topics.contains("Radiografia"))
    }
    @Test fun matchesShortTokensOnlyAsStandaloneWords() {
        assertTrue(OrganizerMetadata.extractTopics("resultado do exame EAS dentro da normalidade").contains("Urina (EAS)"))
        assertFalse(OrganizerMetadata.extractTopics("tem ideias brilhantes e varias formas").contains("Urina (EAS)"))
        assertTrue(OrganizerMetadata.extractTopics("traco registado: ECG de 12 derivacoes").contains("Eletrocardiograma"))
        assertEquals("Outros", OrganizerMetadata.suggestWithTopics("relatorio de mapeamento da area de risco", "x").second)
        assertEquals("Cardiológico", OrganizerMetadata.suggestWithTopics("MAPA de 24 horas em repouso", "x").second)
        assertFalse(OrganizerMetadata.extractTopics("amostras t34 e ut3g lacradas").contains("Tireoide"))
    }
}
