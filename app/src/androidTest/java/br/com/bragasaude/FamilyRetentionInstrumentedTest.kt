package br.com.bragasaude

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.bragasaude.data.local.FamilyMessageEntity
import br.com.bragasaude.domain.FamilyConversationPdfExporter
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class FamilyRetentionInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun message(id: String, text: String, expiry: Long = System.currentTimeMillis()+3600000) =
        FamilyMessageEntity(id=id, patientUserId="d47-synthetic", senderName="Familiar de teste",
            messageText=text, iconType="LOVE", expiresAt=expiry)

    @Test fun exportRejectsExpiredAndDeletedMessages() {
        val invalid = listOf(message("expired", "EXPIRED_SHOULD_NOT_EXPORT", 1L),
            message("deleted", "DELETED_SHOULD_NOT_EXPORT").copy(deletedAt=1L))
        assertNull(FamilyConversationPdfExporter.generateConversationPdf(context,invalid))
    }

    @Test fun longConversationExportsAndRendersEveryPage() {
        val text = (1..150).joinToString("\n") { "Linha $it: uma mensagem longa com acentos, saúde e carinho para a família." }
        val messages = listOf(message("long",text),message("word", "W".repeat(220)),
            message("expired", "EXPIRED_SHOULD_NOT_EXPORT", 1L),
            message("deleted", "DELETED_SHOULD_NOT_EXPORT").copy(deletedAt=1L))
        val uri = requireNotNull(FamilyConversationPdfExporter.generateConversationPdf(context,messages))
        val qa = File(context.filesDir,"d47_qa").apply { mkdirs() }
        context.contentResolver.openInputStream(uri)!!.use { input ->
            File(qa,"conversation.pdf").outputStream().use { input.copyTo(it) }
        }
        context.contentResolver.openFileDescriptor(uri,"r")!!.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertTrue("Long message must span pages",renderer.pageCount >= 3)
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val bitmap=Bitmap.createBitmap(page.width*2,page.height*2,Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        File(qa,"page-$index.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    @Test fun temporaryExportsAreCleanedAtExpiry() {
        val dir=File(context.cacheDir,"family_exports").apply { mkdirs() }
        val old=File(dir,"1_test.pdf").apply { writeText("synthetic") }
        val future=File(dir,"9999999999999_test.pdf").apply { writeText("synthetic") }
        try {
            FamilyConversationPdfExporter.cleanupTemporaryExports(context)
            assertFalse(old.exists())
            assertTrue(future.exists())
        } finally { future.delete() }
    }
}
