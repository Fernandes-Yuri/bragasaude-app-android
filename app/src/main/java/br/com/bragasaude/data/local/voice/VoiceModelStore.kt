package br.com.bragasaude.data.local.voice

import java.io.File

/** Diretórios irmãos permitem renomeação no mesmo filesystem. A preferência é o commit. */
internal class VoiceModelStore(private val root: File) {
    val current = File(root, "current")
    val staging = File(root, "staging")
    val previous = File(root, "previous")

    fun recover(selectedId: String) {
        root.mkdirs()
        if (selectedId == VoiceCatalog.SYSTEM_ID) {
            clear(current)
            clear(previous)
        } else if (previous.exists()) {
            if (id(current) == selectedId && valid(current)) clear(previous)
            else { clear(current); check(previous.renameTo(current)) }
        }
        clear(staging)
    }

    fun prepare(): File {
        root.mkdirs()
        clear(staging)
        check(staging.mkdirs())
        return staging
    }

    fun begin(id: String) {
        check(valid(staging)) { "Modelo incompleto" }
        File(staging, "voice_id").writeText(id)
        check(!previous.exists()) { "Recuperação pendente" }
        if (current.exists()) check(current.renameTo(previous)) { "Falha ao preservar voz anterior" }
        if (!staging.renameTo(current)) {
            if (previous.exists()) check(previous.renameTo(current))
            error("Falha ao ativar modelo")
        }
    }

    fun rollback() {
        clear(current)
        if (previous.exists()) check(previous.renameTo(current))
    }

    fun finish() = clear(previous)
    fun removeModels() { clear(current); clear(previous); clear(staging) }

    companion object {
        fun id(dir: File): String? = File(dir, "voice_id").takeIf { it.isFile }?.readText()?.trim()
        fun valid(dir: File): Boolean = File(dir, "model.onnx").length() > 100_000 &&
            File(dir, "tokens.txt").length() > 50 &&
            File(dir, "espeak-ng-data/phontab").isFile &&
            File(dir, "espeak-ng-data/phondata").isFile &&
            File(dir, "espeak-ng-data/phonindex").isFile
        fun clear(dir: File) { check(!dir.exists() || dir.deleteRecursively()) { "Falha ao limpar arquivos de voz" } }
    }
}
