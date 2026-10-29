package br.com.bragasaude.organizer

import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import br.com.bragasaude.data.local.organizer.OrganizerStore
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Executar apenas em instalação de teste isolada, com documentos fictícios. */
@RunWith(AndroidJUnit4::class)
class OrganizerSessionInstrumentedTest {
    @Test fun organizaDoisDocumentosRemoveUmEInvalidaResultado() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = OrganizerStore(context)
        check(store.restore() == null) { "Use uma instalação de teste sem sessão existente." }
        val sources = (1..2).map { number ->
            File(context.cacheDir, "shared_pdfs/teste-${UUID.randomUUID()}.pdf").apply {
                parentFile?.mkdirs()
                PDDocument().use { document ->
                    val page = PDPage()
                    document.addPage(page)
                    PDPageContentStream(document, page).use { stream ->
                        stream.beginText()
                        stream.setFont(PDType1Font.HELVETICA, 12f)
                        stream.newLineAtOffset(40f, 700f)
                        stream.showText("DOCUMENTO FICTICIO DE EXAME $number")
                        stream.endText()
                    }
                    document.save(this)
                }
            }
        }
        var sessionId: String? = null
        try {
            val session = store.start()
            sessionId = session.id
            val uris = sources.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
            val (imported, failures) = store.import(session.id, uris)
            assertTrue(failures.toString(), failures.isEmpty())
            assertEquals(2, imported.documents.size)
            imported.documents.forEach { doc -> store.update(session.id, doc.copy(confirmed = true)) }
            store.generate(session.id, true)
            val preview = store.previewResult(session.id, 0)
            assertTrue(preview.second > 0)
            preview.first.recycle()
            val retainedId = imported.documents.last().id
            val remaining = store.delete(session.id, imported.documents.first().id)
            assertEquals(listOf(retainedId), remaining.documents.map { it.id })
            assertTrue(sources.all { it.exists() })
            assertTrue(runCatching { store.previewResult(session.id, 0) }.isFailure)
            store.generate(session.id, true)
            val updatedPreview = store.previewResult(session.id, 0)
            assertTrue(updatedPreview.second > 0)
            updatedPreview.first.recycle()
        } finally {
            sessionId?.let { store.end(it) }
            sources.forEach { it.delete() }
        }
    }
}
