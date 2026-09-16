package br.com.bragasaude.domain

import org.junit.Assert.*
import org.junit.Test

class VoiceSessionControlTest {
    @Test fun `closed session rejects late and duplicate recognition callbacks`() {
        val session = VoiceSessionControl()
        val first = session.begin()
        assertTrue(session.consume(first))
        assertFalse(session.consume(first))
        val second = session.begin()
        assertFalse(session.accepts(first))
        session.reset()
        assertFalse(session.accepts(second))
    }

    @Test fun `interruption invalidates earlier listener`() {
        val session = VoiceSessionControl()
        val before = session.begin()
        session.invalidate()
        val after = session.begin()
        assertFalse(session.consume(before))
        assertTrue(session.consume(after))
    }

    @Test fun `two silent turns pause but actual speech resets counter`() {
        val session = VoiceSessionControl()
        assertFalse(session.shouldPauseAfterSilence())
        session.hasSpeech()
        assertFalse(session.shouldPauseAfterSilence())
        assertTrue(session.shouldPauseAfterSilence())
        session.reset()
        assertFalse(session.shouldPauseAfterSilence())
    }

    @Test fun `commands tolerate accents courtesy and assistant name`() {
        assertEquals(VoiceSessionCommand.CLOSE, VoiceSessionCommand.parse("Braga, por hoje é só!"))
        assertEquals(VoiceSessionCommand.CLOSE, VoiceSessionCommand.parse("Obrigado, por hoje é só"))
        assertEquals(VoiceSessionCommand.PAUSE, VoiceSessionCommand.parse("Só um minuto, por favor"))
        assertEquals(VoiceSessionCommand.REPEAT, VoiceSessionCommand.parse("Braga, repete a última parte"))
    }

    @Test fun `ordinary sentences mentioning departure or corrections stay in conversation`() {
        listOf("vou sair para caminhar", "não quero encerrar", "vou fechar a janela", "espera foram três", "repete comigo esta frase").forEach {
            assertNull(it, VoiceSessionCommand.parse(it))
        }
    }
}
