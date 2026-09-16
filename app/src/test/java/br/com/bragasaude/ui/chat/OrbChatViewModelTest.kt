package br.com.bragasaude.ui.chat

import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.ai.*
import br.com.bragasaude.data.remote.service.NotificationClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class OrbChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var gateway: OrbChatGateway
    private lateinit var vm: OrbChatViewModel
    private val reply = OrbReply("""{"fala":"Confira os valores","acao":"REGISTRAR_PRESSAO","parametros":{"sistolica":120,"diastolica":80}}""")

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        gateway = mockk(relaxed = true)
        every { gateway.connection } returns MutableStateFlow(OrbConnectionState.CONNECTED)
        val store = mockk<OrbChatStore>(relaxed = true)
        every { store.observe(any()) } returns flowOf(emptyList())
        val auth = mockk<FirebaseAuth>(relaxed = true)
        val user = mockk<FirebaseUser>()
        every { user.uid } returns "owner"
        every { auth.currentUser } returns user
        vm = OrbChatViewModel(gateway, store, auth, mockk<NeuralAudioPlayer>(relaxed = true),
            mockk<NotificationClient>(relaxed = true), mockk<ProfileDao>(relaxed = true))
    }
    @After fun tearDown() { vm.leaveScreen(); Dispatchers.resetMain() }

    @Test fun test_sendMessage_updatesState() = runTest(dispatcher) {
        coEvery { gateway.send(any(), any()) } coAnswers { awaitCancellation() }
        vm.updateInput("Minha pressão")
        vm.sendMessage()
        assertEquals("Minha pressão", vm.state.value.messages.single().text)
        assertEquals("", vm.state.value.input)
        assertTrue(vm.state.value.isStreaming)
        runCurrent()
        vm.cancelGeneration()
    }
    @Test fun test_streamChunk_appendsText() = runTest(dispatcher) {
        coEvery { gateway.send(any(), any()) } coAnswers {
            secondArg<(String) -> Unit>()("Confira")
            yield()
            secondArg<(String) -> Unit>()("Confira os valores")
            awaitCancellation()
        }
        vm.sendMessage("pressão?")
        runCurrent()
        assertEquals("Confira os valores", vm.state.value.partialText)
        vm.cancelGeneration()
    }
    @Test fun test_streamDone_delegatesRegistrationsToOrb() = runTest(dispatcher) {
        coEvery { gateway.send(any(), any()) } returns reply
        vm.sendMessage("minha pressão deu 12 por 8")
        runCurrent()
        val answer = vm.state.value.messages.last()
        // No chat com Agente B1, ações de registro geram cards para confirmação no app
        assertEquals("REGISTRAR_PRESSAO", answer.action)
        assertFalse(vm.state.value.isStreaming)
        assertEquals("received", vm.state.value.messages.first().status)
    }
    @Test fun test_streamDone_ignoresActionWhenNoValuesProvided() = runTest(dispatcher) {
        val replyWithoutAction = OrbReply("""{"fala":"Como posso ajudar?","acao":null,"parametros":{}}""")
        coEvery { gateway.send(any(), any()) } returns replyWithoutAction
        vm.sendMessage("como está minha pressão?")
        runCurrent()
        val answer = vm.state.value.messages.last()
        assertNull(answer.action)
        assertFalse(vm.state.value.isStreaming)
    }
    @Test fun test_error_showsMessage() = runTest(dispatcher) {
        coEvery { gateway.send(any(), any()) } throws java.io.IOException("Sem rede")
        vm.sendMessage("Olá")
        runCurrent()
        assertEquals("Sem rede", vm.state.value.error)
        assertFalse(vm.state.value.isStreaming)
    }
    @Test fun test_cancel_stopsStreaming() = runTest(dispatcher) {
        var cancelled = false
        coEvery { gateway.send(any(), any()) } coAnswers {
            try { awaitCancellation() } finally { cancelled = true }
        }
        vm.sendMessage("Olá")
        runCurrent()
        vm.cancelGeneration()
        runCurrent()
        assertTrue(cancelled)
        assertFalse(vm.state.value.isStreaming)
        assertEquals("cancelled", vm.state.value.messages.last().status)
        assertNull(vm.state.value.messages.last().action)
    }
    @Test fun longMessageDoesNotReachGateway() = runTest(dispatcher) {
        vm.sendMessage("x".repeat(16001))
        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.messages.isEmpty())
        coVerify(exactly = 0) { gateway.send(any(), any()) }
    }
}
