package br.com.bragasaude.data.local.slm

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BragaModelStore @Inject constructor(@ApplicationContext context: Context) {
    companion object {
        const val FILE_NAME = "braga_slm_v2_1_q4_k_m.gguf"
        const val SIZE = 396091296L
        const val SHA256 = "ef00e91adab2328a2598b47905b047eea354ef434c6f9964019c8f0a57623b04"
        const val URL = "https://huggingface.co/fernandes-yuri/braga-slm-0.5b/resolve/main/$FILE_NAME"
        const val REQUIRED_MESSAGE = "Escolha uma voz nas configurações do assistente para começar a conversar."
    }
    private val directory = File(context.noBackupFilesDir, "braga_slm")
    val model = File(directory, FILE_NAME)
    val staging = File(directory, "$FILE_NAME.part")
    private val marker = File(directory, "verified.sha256")
    fun installed(): Boolean = model.length() == SIZE && marker.isFile &&
        runCatching { marker.readText() == SHA256 }.getOrDefault(false)
    fun prepare() {
        check(directory.mkdirs() || directory.isDirectory)
        check(directory.usableSpace > SIZE + 64_000_000L) { "Libere pelo menos 460 MB para instalar o Braga." }
    }
    fun commit() {
        check(staging.length() == SIZE)
        check(staging.renameTo(model)) { "Não foi possível instalar o modelo." }
        marker.writeText(SHA256)
        // Libera o modelo anterior somente depois de concluir a instalação verificada.
        File(directory, "braga_slm_v2_2_q4_k_m.gguf").delete()
    }
}
