package br.com.bragasaude.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Utilitário para geração de QR Code a partir de códigos de conexão.
 * Utilizado na Ponte Familiar para compartilhamento de códigos via QR Code.
 */
object QrCodeGenerator {
    
    /**
     * Gera um QR Code em formato Bitmap a partir de um código.
     * 
     * @param code O código a ser codificado no QR Code (ex: "A1B2C3D4")
     * @param size Tamanho do QR Code em pixels (padrão: 512x512)
     * @return Bitmap do QR Code gerado
     */
    fun generateQrCode(code: String, size: Int = 512): Bitmap {
        val writer = QRCodeWriter()
        
        // Configurações do QR Code
        val hints = hashMapOf<EncodeHintType, Any>(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H, // Alta correção de erros
            EncodeHintType.MARGIN to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        
        // Gerar matriz do QR Code
        val matrix = writer.encode(code, BarcodeFormat.QR_CODE, size, size, hints)
        
        // Criar Bitmap
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        
        // Preencher o Bitmap com as cores (preto para módulos ativos, branco para inativos)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        
        return bitmap
    }
    
    /**
     * Formata a data de expiração para exibição na UI.
     *
     * @param expiresAt Timestamp da expiração em millis
     * @param now Relogio injetavel (default: real). Testes fixam o instante para
     *           nao depender da meia-noite — antes o teste somava 3 dias a
     *           System.currentTimeMillis() e a implementacao lia de novo, numa
     *           race que fazia o CI falhar (flaky) perto da virada do dia.
     * @return String formatada (ex: "Expira em 3 dias")
     */
    fun formatExpiration(expiresAt: Long, now: Long = System.currentTimeMillis()): String {
        val diffMillis = expiresAt - now
        
        if (diffMillis <= 0) {
            return "Expirado"
        }
        
        val diffDays = diffMillis / (24 * 60 * 60 * 1000)
        val diffHours = (diffMillis % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)
        
        return when {
            diffDays > 0 -> "Expira em $diffDays dia${if (diffDays > 1) "s" else ""}"
            diffHours > 0 -> "Expira em $diffHours hora${if (diffHours > 1) "s" else ""}"
            else -> "Expira em menos de 1 hora"
        }
    }
}
