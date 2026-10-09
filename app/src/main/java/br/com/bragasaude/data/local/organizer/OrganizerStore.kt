package br.com.bragasaude.data.local.organizer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.FileProvider
import androidx.work.*
import br.com.bragasaude.data.util.ExamPhotoDecoder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Temporary workspace. No API, Room, account ID or clinical logging. */
@Singleton
class OrganizerStore @Inject constructor(@ApplicationContext private val context: Context) {
    companion object { private val lock = Mutex() }
    private val root get() = File(context.noBackupFilesDir, "organizer").apply { mkdirs() }
    private val cache get() = File(context.cacheDir, "organizer-work").apply { mkdirs() }
    private val pdf = OrganizerPdfBuilder(context)
    private val keys get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun alias(id: String) = "organizer-$id"
    private fun folder(id: String) = File(root, id)
    private fun temp(id: String) = File(cache, id).apply { mkdirs() }
    private fun key(id: String): SecretKey = keys.getKey(alias(id), null) as SecretKey
    private fun encrypt(id: String, bytes: ByteArray, target: File) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(id))
        val atomic = android.util.AtomicFile(target)
        val stream = atomic.startWrite()
        try {
            stream.write(cipher.iv); stream.write(cipher.doFinal(bytes)); atomic.finishWrite(stream)
        } catch (error: Throwable) { atomic.failWrite(stream); throw error }
    }
    private fun decrypt(id: String, source: File): ByteArray {
        val bytes = android.util.AtomicFile(source).readFully()
        require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(id), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes, 12, bytes.size - 12)
    }
    private fun save(s: OrganizerSession) {
        val json = JSONObject().put("id", s.id).put("createdAt", s.createdAt)
        val docs = JSONArray()
        s.documents.forEach { d -> docs.put(JSONObject().put("id", d.id).put("title", d.title)
            .put("date", d.date).put("type", d.type).put("pages", d.pages).put("confirmed", d.confirmed).put("photoOnly", d.photoOnly).put("sourceDigest", d.sourceDigest).put("possibleDuplicate", d.possibleDuplicate)) }
        json.put("documents", docs)
        encrypt(s.id, json.toString().toByteArray(Charsets.UTF_8), File(folder(s.id), "session.enc"))
    }
    private fun read(id: String): OrganizerSession {
        val json = JSONObject(String(decrypt(id, File(folder(id), "session.enc")), Charsets.UTF_8))
        val docs = json.getJSONArray("documents")
        return OrganizerSession(id, json.getLong("createdAt"), (0 until docs.length()).map { n ->
            val d = docs.getJSONObject(n)
            OrganizerDocument(d.getString("id"), d.getString("title"), d.getString("date"),
                d.getString("type"), d.getInt("pages"), d.getBoolean("confirmed"), d.optBoolean("photoOnly"), d.optString("sourceDigest"), d.optBoolean("possibleDuplicate"))
        })
    }
    private fun remove(id: String) {
        File(context.cacheDir, "organizer-outbox/$id").listFiles()?.forEach { file ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.revokeUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val removed = listOf(folder(id), File(cache, id), File(context.cacheDir, "organizer-outbox/$id"),
            File(context.cacheDir, "organizer-capture/$id")).map { !it.exists() || it.deleteRecursively() }.all { it }
        if (keys.containsAlias(alias(id))) keys.deleteEntry(alias(id))
        if (!removed) throw OrganizerProblem("A limpeza não foi concluída. As cópias restantes estão bloqueadas; tente encerrar novamente.")
    }
    private fun sessionIds(): Set<String> = listOf(root, cache, File(context.cacheDir, "organizer-outbox"),
        File(context.cacheDir, "organizer-capture")).flatMap { parent -> parent.listFiles()?.map { it.name }.orEmpty() }.toSet()
    private fun clean() {
        val now = System.currentTimeMillis()
        sessionIds().forEach { id ->
            val created = id.substringBefore('-').toLongOrNull()
            if (!folder(id).exists() || created == null || now < created || now >= created + OrganizerSession.SESSION_DURATION) remove(id)
        }
        val aliases = keys.aliases().toList()
        aliases.filter { it.startsWith("organizer-") && !folder(it.removePrefix("organizer-")).exists() }
            .forEach { keys.deleteEntry(it) }
    }
    suspend fun restore(): OrganizerSession? = withContext(Dispatchers.IO) { lock.withLock {
        clean()
        root.listFiles()?.firstOrNull()?.let { f ->
            File(cache, f.name).deleteRecursively()
            runCatching { read(f.name).also { scheduleCleanup(it) } }.getOrElse { remove(f.name); null }
        }
    } }
    suspend fun start(): OrganizerSession = withContext(Dispatchers.IO) { lock.withLock {
        clean()
        root.listFiles()?.firstOrNull()?.let { return@withLock read(it.name) }
        val s = OrganizerSession("${System.currentTimeMillis()}-${UUID.randomUUID()}", System.currentTimeMillis())
        folder(s.id).mkdirs()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias(s.id), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        generator.generateKey(); save(s)
        WorkManager.getInstance(context).enqueueUniqueWork("organizer-expiry", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<OrganizerCleanupWorker>().setInitialDelay(24, TimeUnit.HOURS).build())
        s
    } }
    private fun scheduleCleanup(session: OrganizerSession) {
        WorkManager.getInstance(context).enqueueUniqueWork("organizer-expiry", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<OrganizerCleanupWorker>().setInitialDelay(
                (session.expiresAt - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS).build())
    }
    private fun active(id: String): OrganizerSession {
        val s = read(id)
        if (s.expired(System.currentTimeMillis())) { remove(id); throw OrganizerProblem("A sessão expirou. Importe os originais novamente.") }
        return s
    }
    private fun invalidate(id: String) {
        File(folder(id), "result.enc").delete()
        temp(id).listFiles()?.forEach { it.deleteRecursively() }
        File(context.cacheDir, "organizer-capture/$id").deleteRecursively()
    }
    private fun encryptFile(id: String, source: File, target: File) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(id))
        val atomic = android.util.AtomicFile(target)
        val stream = atomic.startWrite()
        try {
            stream.write(cipher.iv)
            source.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) { val n = input.read(buffer); if (n < 0) break
                    cipher.update(buffer, 0, n)?.let { stream.write(it) } }
            }
            stream.write(cipher.doFinal()); atomic.finishWrite(stream)
        } catch (error: Throwable) { atomic.failWrite(stream); throw error }
    }
    private fun decryptFile(id: String, source: File, target: File) {
        android.util.AtomicFile(source).openRead().use { input ->
            val iv = ByteArray(12); java.io.DataInputStream(input).readFully(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(id), GCMParameterSpec(128, iv))
            try {
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) { val n = input.read(buffer); if (n < 0) break
                        cipher.update(buffer, 0, n)?.let { output.write(it) } }
                    output.write(cipher.doFinal())
                }
            } catch (error: Throwable) { target.delete(); throw error }
        }
    }
    private fun original(id: String, doc: OrganizerDocument): File = File(temp(id), "${doc.id}.pdf").apply {
        decryptFile(id, File(folder(id), "${doc.id}.enc"), this)
    }
    private suspend fun recognize(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try { suspendCoroutine { continuation ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { continuation.resume(it.text) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        } } finally { recognizer.close() }
    }
    private fun render(file: File, pageNumber: Int): Bitmap {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer -> renderer.openPage(pageNumber).use { page ->
                val scale = minOf(2f, 1800f / maxOf(page.width, page.height))
                val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1),
                    (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap
            } }
        }
    }
    suspend fun import(id: String, uris: List<Uri>, appendTo: String? = null): OrganizerSession = withContext(Dispatchers.IO) { lock.withLock {
        var s = active(id)
        require(uris.isNotEmpty() && uris.size <= 20) { "Selecione até 20 arquivos por vez." }
        // Atomic batch: do not commit a partial import if one source fails.
        val pending = mutableListOf<Pair<OrganizerDocument, File>>()
        val work = File(temp(id), "import").apply { mkdirs() }
        try {
            uris.forEach { uri ->
                require(s.documents.size + pending.size < 20 || appendTo != null) { "Limite de 20 documentos por sessão." }
                val docId = UUID.randomUUID().toString()
                val source = File(work, "$docId.source")
                context.contentResolver.openInputStream(uri)?.use { input -> source.outputStream().use { out ->
                    val buffer = ByteArray(8192); var total = 0L
                    while (true) { val n = input.read(buffer); if (n < 0) break
                        total += n; require(total <= 35L * 1024 * 1024) { "Cada arquivo pode ter até 35 MB." }; out.write(buffer, 0, n) }
                } } ?: error("Não foi possível ler o arquivo.")
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                source.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
                }
                val sourceDigest = digest.digest().joinToString("") { "%02x".format(it) }
                val duplicate = (s.documents + pending.map { it.first }).any { it.sourceDigest == sourceDigest }
                val normalized = File(work, "$docId.pdf")
                val isPdf = source.inputStream().use { String(ByteArray(5).also { header -> it.read(header) }, Charsets.US_ASCII) == "%PDF-" }
                var text = ""; var pages = 1
                if (isPdf) {
                    source.renameTo(normalized)
                    PDDocument.load(normalized, MemoryUsageSetting.setupTempFileOnly().setTempDir(work)).use { document ->
                        require(!document.isEncrypted) { "Abra e salve uma cópia sem senha do PDF antes de importar." }
                        pages = document.numberOfPages
                        require(pages in 1..100) { "O PDF precisa ter entre 1 e 100 páginas." }
                        text = PDFTextStripper().apply { endPage = minOf(pages, 3) }.getText(document).take(20000)
                    }
                    if (text.isBlank()) {
                        val bitmap = render(normalized, 0)
                        try { text = runCatching { recognize(bitmap) }.getOrDefault("") } finally { bitmap.recycle() }
                    }
                } else {
                    val bitmap = ExamPhotoDecoder.decode(context.contentResolver, uri)
                    try { pdf.imagePdf(bitmap, normalized); text = runCatching { recognize(bitmap) }.getOrDefault("") }
                    finally { bitmap.recycle() }
                }
                require(s.documents.sumOf { it.pages } + pending.sumOf { it.first.pages } + pages <= 100) { "Limite de 100 páginas por sessão." }
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: "Documento de exame"
                val (title, type) = OrganizerMetadata.suggest(text, name)
                require(normalized.length() <= 35L * 1024 * 1024) { "Cada documento pode ter até 35 MB após a organização." }
                require((folder(id).listFiles()?.filter { it.extension == "enc" }?.sumOf { it.length() } ?: 0L) +
                    pending.sumOf { it.second.length() } + normalized.length() <= 150L * 1024 * 1024) { "A sessão pode ter até 150 MB. Organize em lotes menores." }
                pending.add(OrganizerDocument(docId, title, OrganizerMetadata.suggestDate(text), type, pages, photoOnly = !isPdf, sourceDigest = sourceDigest, possibleDuplicate = duplicate) to normalized)
            }
            if (appendTo != null) {
                val item = s.documents.first { it.id == appendTo }
                val old = original(id, item)
                val merged = File(work, "merged.pdf")
                PDDocument.load(old).use { document ->
                    pending.forEach { (_, file) -> PDDocument.load(file).use {
                        com.tom_roush.pdfbox.multipdf.PDFMergerUtility().appendDocument(document, it)
                    } }; document.save(merged)
                }
                val replacementId = UUID.randomUUID().toString()
                encryptFile(id, merged, File(folder(id), "$replacementId.enc"))
                s = s.copy(documents = s.documents.map { if (it.id == item.id) it.copy(id = replacementId, pages = item.pages + pending.sumOf { p -> p.first.pages }, confirmed = false, photoOnly = item.photoOnly && pending.all { it.first.photoOnly }, sourceDigest = "", possibleDuplicate = item.possibleDuplicate || pending.any { it.first.possibleDuplicate }) else it })
            } else {
                pending.forEach { (item, file) -> encryptFile(id, file, File(folder(id), "${item.id}.enc")) }
                s = s.copy(documents = s.documents + pending.map { it.first })
            }
            active(id)
            save(s)
            val keep = s.documents.map { "${it.id}.enc" }.toSet() + setOf("session.enc", "result.enc")
            folder(id).listFiles()?.filter { it.extension == "enc" && it.name !in keep }?.forEach { it.delete() }
            invalidate(id); s
        } finally { work.deleteRecursively() }
    } }
    suspend fun update(id: String, item: OrganizerDocument): OrganizerSession = withContext(Dispatchers.IO) { lock.withLock {
        val s = active(id)
        require(item.title.isNotBlank() && item.title.length <= 80 && OrganizerMetadata.validDate(item.date) && item.type in OrganizerMetadata.types)
        val original = s.documents.first { it.id == item.id }
        val changed = s.copy(documents = s.documents.map { if (it.id == item.id) item.copy(pages = original.pages) else it })
        save(changed); invalidate(id); changed
    } }
    suspend fun delete(id: String, docId: String): OrganizerSession = withContext(Dispatchers.IO) { lock.withLock {
        val s = active(id).let { it.copy(documents = it.documents.filterNot { d -> d.id == docId }) }
        save(s); File(folder(id), "$docId.enc").delete(); invalidate(id); s
    } }
    suspend fun editPhotoPage(id: String, docId: String, page: Int, moveTo: Int? = null): OrganizerSession = withContext(Dispatchers.IO) { lock.withLock {
        val session = active(id)
        val item = session.documents.first { it.id == docId }
        require(item.photoOnly && page in 0 until item.pages)
        if (moveTo != null) require(moveTo in 0 until item.pages)
        val work = temp(id)
        val original = original(id, item)
        val edited = File(work, "edited.pdf")
        try {
            PDDocument.load(original, MemoryUsageSetting.setupTempFileOnly().setTempDir(work)).use { document ->
                val current = document.getPage(page)
                if (moveTo == null) current.rotation = (current.rotation + 90) % 360
                else {
                    document.removePage(page)
                    if (moveTo >= document.numberOfPages) document.addPage(current)
                    else document.pages.insertBefore(current, document.getPage(moveTo))
                }
                document.save(edited)
            }
            val replacement = item.copy(id = UUID.randomUUID().toString(), confirmed = false, sourceDigest = "")
            encryptFile(id, edited, File(folder(id), "${replacement.id}.enc"))
            val changed = session.copy(documents = session.documents.map { if (it.id == docId) replacement else it })
            active(id); save(changed); File(folder(id), "$docId.enc").delete(); invalidate(id); changed
        } finally { original.delete(); edited.delete() }
    } }
    suspend fun saveOriginal(id: String, docId: String, uri: Uri): Unit = withContext(Dispatchers.IO) { lock.withLock {
        val item = active(id).documents.first { it.id == docId }
        val file = original(id, item)
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { output -> file.inputStream().use { it.copyTo(output) }; output.flush() }
                ?: error("Não foi possível salvar o documento.")
        } finally { file.delete() }
    } }
    suspend fun preview(id: String, docId: String, page: Int): Bitmap = withContext(Dispatchers.IO) { lock.withLock {
        val s = active(id); val doc = s.documents.first { it.id == docId }
        require(page in 0 until doc.pages)
        val file = original(id, doc)
        try { render(file, page) } finally { file.delete() }
    } }
    suspend fun generate(id: String, newest: Boolean): Unit = withContext(Dispatchers.IO) { lock.withLock {
        val s = active(id); val documents = OrganizerMetadata.ordered(s.documents, newest)
        require(documents.isNotEmpty() && documents.all { it.confirmed }) { "Confira todos os documentos antes de gerar." }
        val work = temp(id)
        try {
            val files = documents.map { original(id, it) }
            val result = File(work, "result.pdf")
            pdf.build(documents, files, result, work)
            encryptFile(id, result, File(folder(id), "result.enc"))
        } finally { work.listFiles()?.forEach { it.deleteRecursively() } }
    } }
    suspend fun previewResult(id: String, page: Int): Pair<Bitmap, Int> = withContext(Dispatchers.IO) { lock.withLock {
        active(id)
        val file = File(temp(id), "preview-result.pdf")
        try {
            decryptFile(id, File(folder(id), "result.enc"), file)
            val count = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { it.pageCount }
            }
            require(page in 0 until count)
            render(file, page) to count
        } finally { file.delete() }
    } }
    suspend fun saveResult(id: String, uri: Uri): Unit = withContext(Dispatchers.IO) { lock.withLock {
        active(id)
        val file = File(temp(id), "export.pdf")
        try {
            decryptFile(id, File(folder(id), "result.enc"), file)
            context.contentResolver.openOutputStream(uri, "wt")?.use { output -> file.inputStream().use { it.copyTo(output) }; output.flush() }
                ?: error("Não foi possível salvar o PDF.")
        } finally { file.delete() }
    } }
    suspend fun shareResult(id: String): Uri = withContext(Dispatchers.IO) { lock.withLock {
        active(id)
        val file = File(context.cacheDir, "organizer-outbox/$id/exames-${UUID.randomUUID()}.pdf").apply { parentFile?.mkdirs() }
        decryptFile(id, File(folder(id), "result.enc"), file)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } }
    suspend fun end(id: String): Unit = withContext(Dispatchers.IO) { lock.withLock { remove(id) } }
    suspend fun clearAll(): Unit = withContext(Dispatchers.IO) { lock.withLock { sessionIds().forEach { remove(it) } } }
    suspend fun cleanup(): Unit = withContext(Dispatchers.IO) { lock.withLock { clean() } }
}

class OrganizerCleanupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try { OrganizerStore(applicationContext).cleanup(); Result.success() }
        catch (_: Exception) { Result.retry() }
}
