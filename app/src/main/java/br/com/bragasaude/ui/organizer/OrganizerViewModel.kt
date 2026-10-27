package br.com.bragasaude.ui.organizer

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.organizer.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class OrganizerUiState(val session: OrganizerSession? = null, val busy: Boolean = false,
    val error: String? = null, val notice: String? = null, val ready: Boolean = false, val saved: Boolean = false,
    val preview: Bitmap? = null, val previewPage: Int = -1, val resultPages: Int = 0, val newest: Boolean = true,
    val ocrAvailability: OcrAvailability? = null, val personalization: OrganizerPersonalization = OrganizerPersonalization())

@HiltViewModel
class OrganizerViewModel @Inject constructor(private val store: OrganizerStore, private val ocrCapability: OcrCapability,
                                             private val prefs: OrganizerPreferences? = null) : ViewModel() {
    private val mutable = MutableStateFlow(OrganizerUiState())
    val state = mutable.asStateFlow()
    private val operations = Mutex()
    /** Dispatcher da sonda de OCR fora da thread principal; testável para determinismo. */
    @VisibleForTesting
    internal var probeDispatcher: CoroutineDispatcher = Dispatchers.Default
    init { mutable.value = mutable.value.copy(personalization = prefs?.load() ?: OrganizerPersonalization()); refresh(); probeOcr() }
    private fun operation(block: suspend () -> Unit) {
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch { operations.withLock {
            mutable.value = mutable.value.copy(busy = true, error = null)
            try { block() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (problem: OrganizerProblem) { mutable.value = mutable.value.copy(error = problem.message) }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = "Não foi possível concluir. Confira o arquivo, o espaço disponível e tente novamente. PDFs com senha precisam de uma cópia sem senha.") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        } }
    }
    fun refresh() = operation {
        val session = store.restore()
        mutable.value = mutable.value.copy(session = session,
            ready = mutable.value.ready && session?.id == mutable.value.session?.id)
        probeOcr()
    }
    /** Sonda a infraestrutura de leitura assistida sem nunca bloquear ou derrubar a área. */
    fun probeOcr() {
        viewModelScope.launch(probeDispatcher) {
            val availability = ocrCapability.probe()
            if (mutable.value.ocrAvailability != availability) {
                mutable.value = mutable.value.copy(ocrAvailability = availability)
            }
        }
    }
    fun start() = operation {
        mutable.value = OrganizerUiState(session = store.start(), busy = true, personalization = mutable.value.personalization)
    }
    private fun changed(session: OrganizerSession) {
        mutable.value = mutable.value.copy(session = session, ready = false, saved = false, preview = null, notice = null)
    }
    fun import(uris: List<Uri>, appendTo: String? = null) = operation {
        val (session, failures) = store.import(requireNotNull(mutable.value.session).id, uris, appendTo)
        changed(session)
        if (failures.isNotEmpty()) {
            val imported = uris.size - failures.size
            mutable.value = mutable.value.copy(
                notice = "Importei $imported de ${uris.size} arquivo(s). Não consegui importar:\n" +
                    failures.joinToString("\n") { "• $it" }.take(900)
            )
        }
    }
    fun update(item: OrganizerDocument) = operation { changed(store.update(requireNotNull(mutable.value.session).id, item)) }
    fun delete(id: String) = operation { changed(store.delete(requireNotNull(mutable.value.session).id, id)) }
    fun editPhotoPage(id: String, page: Int, moveTo: Int? = null) = operation {
        changed(store.editPhotoPage(requireNotNull(mutable.value.session).id, id, page, moveTo))
    }
    fun saveOriginal(id: String, uri: Uri) = operation {
        store.saveOriginal(requireNotNull(mutable.value.session).id, id, uri)
        mutable.value = mutable.value.copy(notice = "Cópia do documento salva no destino escolhido.")
    }
    fun preview(id: String, page: Int) = operation {
        mutable.value = mutable.value.copy(preview = null)
        val bitmap = store.preview(requireNotNull(mutable.value.session).id, id, page)
        mutable.value = mutable.value.copy(preview = bitmap, previewPage = page)
    }
    fun previewResult(page: Int) = operation {
        mutable.value = mutable.value.copy(preview = null)
        val (bitmap, pages) = store.previewResult(requireNotNull(mutable.value.session).id, page)
        mutable.value = mutable.value.copy(preview = bitmap, previewPage = page, resultPages = pages)
    }
    fun clearPreview() { mutable.value = mutable.value.copy(preview = null) }
    fun order(newest: Boolean) { if (!mutable.value.busy) mutable.value = mutable.value.copy(newest = newest, ready = false, saved = false) }
    /** Personalizacao de capa: persistida localmente (sem dado de exame) e passada ao builder na geracao. */
    fun updatePersonalization(next: OrganizerPersonalization) {
        prefs?.save(next)
        mutable.value = mutable.value.copy(personalization = next, ready = false, saved = false)
    }
    fun resetPersonalization() = updatePersonalization(OrganizerPersonalization())
    fun generate() = operation {
        store.generate(requireNotNull(mutable.value.session).id, mutable.value.newest, mutable.value.personalization)
        mutable.value = mutable.value.copy(ready = true, saved = false)
    }
    fun save(uri: Uri) = operation {
        store.saveResult(requireNotNull(mutable.value.session).id, uri)
        mutable.value = mutable.value.copy(saved = true)
    }
    fun share(onReady: (Uri) -> Unit) = operation { onReady(store.shareResult(requireNotNull(mutable.value.session).id)) }
    fun end() = operation {
        mutable.value.session?.let { store.end(it.id) }
        mutable.value = OrganizerUiState(busy = true, personalization = mutable.value.personalization)
    }
}
