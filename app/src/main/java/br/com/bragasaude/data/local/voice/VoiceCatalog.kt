package br.com.bragasaude.data.local.voice

import br.com.bragasaude.R

data class VoiceOption(
    val id: String,
    val displayName: String,
    val archiveName: String? = null,
    val downloadBytes: Long = 0,
    val sha256: String? = null,
    val previewResId: Int? = null
) {
    val isNeural: Boolean get() = archiveName != null
    val downloadUrl: String? get() = archiveName?.let {
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$it.tar.bz2"
    }
}

object VoiceCatalog {
    const val SYSTEM_ID = "system_native"
    val options = listOf(
        VoiceOption(SYSTEM_ID, "Voz do dispositivo"),
        VoiceOption("faber", "Faber", "vits-piper-pt_BR-faber-medium", 67_183_065,
            "7add3f923ad6bc25ca8a192805fd1a64d1b3893e4611c4a9719545a825039a83", R.raw.voice_preview_faber),
        VoiceOption("cadu", "Cadu", "vits-piper-pt_BR-cadu-medium", 67_207_562,
            "aba78157d4b89acc17ddef15a70f4b2474f4c189d4de6035002ac7fec9d5d303", R.raw.voice_preview_cadu)
    )
    fun find(id: String) = options.firstOrNull { it.id == id }
}
