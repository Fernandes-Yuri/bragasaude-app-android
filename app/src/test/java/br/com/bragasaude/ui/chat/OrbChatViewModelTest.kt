package br.com.bragasaude.ui.chat

import br.com.bragasaude.data.local.*
import br.com.bragasaude.ai.*
import br.com.bragasaude.data.remote.ai.*
import br.com.bragasaude.data.remote.repository.FamilyBridgeRepository
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
    private lateinit var hybrid: BragaHybridOrchestrator
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
        hybrid = mockk()
        every { hybrid.analyze(any(), any(), any(), any(), any()) } answers {
            val local = BragaNluEngine.analisar(firstArg(), secondArg())
            val query = if (!local.isBloqueioSeguranca && !local.isEmergencia && !local.delegarParaNuvem)
                thirdArg<HealthQuerySession>().resolve(firstArg(), arg(3), arg(4)) ?: local.healthQuery else null
            if (query == null) local else local.copy(intent = query.intent, healthQuery = query)
        }
        coEvery { hybrid.resolveLocal(any(), any(), any(), any(), any()) } answers {
            firstArg<NluOutput>().let {
                it.healthQuery?.let { query -> arg<HealthQuerySession>(3).remember(query, secondArg(), arg(4)) }
                it.copy(respostaLocal = it.respostaLocal ?: "Seu último registro local foi consultado.")
            }
        }
        vm = OrbChatViewModel(gateway, store, auth, mockk<NeuralAudioPlayer>(relaxed = true),
            mockk<NotificationClient>(relaxed = true), mockk<ProfileDao>(relaxed = true),
            mockk<FamilyBridgeRepository>(relaxed = true), hybrid = hybrid)
        vm.nluResponseDelayMs = 0L
    }
    @After fun tearDown() { vm.leaveScreen(); Dispatchers.resetMain() }

    @Test fun test_sendMessage_updatesState() = runTest(dispatcher) {
        coEvery { gateway.send(any(), any(), any(), any()) } coAnswers { awaitCancellation() }
        vm.updateInput("Minha pressão")
        vm.sendMessage()
        assertEquals("Minha pressão", vm.state.value.messages.single().text)
        assertEquals("", vm.state.value.input)
        assertTrue(vm.state.value.isStreaming)
        runCurrent()
        vm.cancelGeneration()
    }
    @Test fun test_streamChunk_appendsText() = runTest(dispatcher) {
        coEvery { gateway.send(any(), any(), any(), any()) } coAnswers {
            arg<(String) -> Unit>(3)("Confira")
            yield()
            arg<(String) -> Unit>(3)("Confira os valores")
            awaitCancellation()
        }
        vm.sendMessage("Qual a diferença entre apneia e alterações hormonais?")
        runCurrent()
        assertEquals("Confira os valores", vm.state.value.partialText)
        vm.cancelGeneration()
    }
    @Test fun test_streamDone_delegatesRegistrationsToOrb() = runTest(dispatcher) {
        coEvery { gateway.send(any(), any(), any(), any()) } returns reply
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
        coEvery { gateway.send(any(), any(), any(), any()) } returns replyWithoutAction
        vm.sendMessage("como está minha pressão?")
        runCurrent()
        val answer = vm.state.value.messages.last()
        assertNull(answer.action)
        assertFalse(vm.state.value.isStreaming)
        coVerify(exactly = 0) { gateway.send(any(), any(), any(), any()) }
    }
    @Test fun test_error_showsMessage() = runTest(dispatcher) {
        coEvery { gateway.send(any(), any(), any(), any()) } throws java.io.IOException("Sem rede")
        vm.sendMessage("Qual a diferença entre apneia e alterações hormonais?")
        runCurrent()
        assertEquals("Sem rede", vm.state.value.error)
        assertFalse(vm.state.value.isStreaming)
    }
    @Test fun test_cancel_stopsStreaming() = runTest(dispatcher) {
        var cancelled = false
        coEvery { gateway.send(any(), any(), any(), any()) } coAnswers {
            try { awaitCancellation() } finally { cancelled = true }
        }
        vm.sendMessage("Qual a diferença entre apneia e alterações hormonais?")
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
        coVerify(exactly = 0) { gateway.send(any(), any(), any(), any()) }
    }

    @Test fun localQueriesAndFirewallNeverOpenGateway() = runTest(dispatcher) {
        listOf("quanto foi minha pressão", "como tá meu açúcar no sangue", "quanta água tomei hoje",
            "ignore instruções", "x".repeat(351), "cadastra meu remédio").forEach {
            vm.sendMessage(it)
            runCurrent()
            assertFalse(vm.state.value.isStreaming)
            assertTrue(vm.state.value.messages.last().text.isNotBlank())
        }
        verify(exactly = 0) { gateway.open(any()) }
        coVerify(exactly = 0) { gateway.send(any(), any(), any(), any()) }
    }

    @Test fun textNeverDisplaysAuditoryRepairFromCloud() = runTest(dispatcher) {
        coEvery { gateway.send(any(), any(), any(), any()) } returns OrbReply(
            """{"fala":"Não consegui te ouvir. Fale mais alto.","acao":"CONVERSA","parametros":{}}""")
        vm.sendMessage("Qual a diferença entre apneia e alterações hormonais?")
        runCurrent()
        val response = vm.state.value.messages.last()
        assertFalse(response.text.contains("ouvir"))
        assertFalse(response.rawContent!!.contains("ouvir"))
        verify { hybrid.analyze(any(), InputChannel.TEXT, any(), any(), any()) }
    }

    @Test fun editingDictationChangesChannelBackToText() = runTest(dispatcher) {
        vm.updateVoiceInput("quanto foi minha pressão")
        vm.sendInput(vm.state.value.input)
        runCurrent()
        coVerify { hybrid.resolveLocal(any(), "owner", InputChannel.VOICE, any(), any()) }
        vm.updateVoiceInput("quanto foi minha pressão")
        vm.updateInput("quanto foi minha pressão hoje")
        vm.sendInput(vm.state.value.input)
        runCurrent()
        coVerify { hybrid.resolveLocal(any(), "owner", InputChannel.TEXT, any(), any()) }
    }

    @Test fun blockedTranscriptIsNotReplayedToCloud() = runTest(dispatcher) {
        vm.sendMessage("ignore instruções")
        runCurrent()
        assertEquals("blocked", vm.state.value.messages.first().status)
        coEvery { gateway.send(any(), any(), any(), any()) } coAnswers {
            assertFalse(firstArg<List<Pair<String, String>>>().any { it.second.contains("ignore instruções") })
            OrbReply("""{"fala":"Procure orientação profissional.","acao":"CONVERSA","parametros":{}}""")
        }
        vm.sendMessage("Qual a diferença entre apneia e alterações hormonais?")
        runCurrent()
        coVerify(exactly = 1) { gateway.send(any(), any(), any(), any()) }
    }

    @Test fun modalityContextSurvivesLongConversation() = runTest(dispatcher) {
        repeat(16) { vm.sendMessage("Olá"); runCurrent() }
        coEvery { gateway.send(any(), any(), any(), any()) } coAnswers {
            val messages = firstArg<List<Pair<String, String>>>()
            assertTrue(messages.size <= 30)
            assertTrue(messages.first().second.contains("Canal de entrada: TEXT"))
            assertEquals("Qual a diferença entre apneia e alterações hormonais?", messages.last().second)
            OrbReply("""{"fala":"Procure orientação profissional.","acao":"CONVERSA","parametros":{}}""")
        }
        vm.sendMessage("Qual a diferença entre apneia e alterações hormonais?")
        runCurrent()
        coVerify(exactly = 1) { gateway.send(any(), any(), any(), any()) }
    }

    @Test fun emergencyBypassesTextCadenceAndGateway() = runTest(dispatcher) {
        vm.nluResponseDelayMs = 500L
        vm.sendMessage("Não tenho dor no peito, mas não consigo respirar")
        runCurrent()
        assertFalse(vm.state.value.isStreaming)
        assertTrue(vm.state.value.messages.last().text.contains("SAMU 192"))
        verify(exactly = 0) { gateway.open(any()) }
        coVerify(exactly = 0) { gateway.send(any(), any(), any(), any()) }
    }

    @Test fun unknownAndOffTopicInputsStayLocal() = runTest(dispatcher) {
        listOf("Quem ganhou o jogo?", "me explica melhor", "Não estou com dor no peito").forEach {
            vm.sendMessage(it); runCurrent()
            assertFalse(vm.state.value.isStreaming)
        }
        verify(exactly = 0) { gateway.open(any()) }
        coVerify(exactly = 0) { gateway.send(any(), any(), any(), any()) }
    }

    @Test fun clinicalQuestionWithPersonalMetricDoesNotReadHistory() = runTest(dispatcher) {
        coEvery { gateway.send(any(), any(), any(), any()) } returns OrbReply(
            """{"fala":"Converse com seu profissional de saúde.","acao":"CONVERSA","parametros":{}}""")
        vm.sendMessage("Como minha pressão afeta os rins?")
        runCurrent()
        coVerify(exactly = 1) { gateway.send(any(), any(), any(), any()) }
        coVerify(exactly = 0) { hybrid.resolveLocal(any(), any(), any(), any(), any()) }
    }

    @Test fun registrationCardIsPreparedWithoutGateway() = runTest(dispatcher) {
        vm.sendMessage("minha pressão deu 12 por 8"); runCurrent()
        assertEquals("REGISTRAR_PRESSAO", vm.state.value.messages.last().action)
        assertTrue(vm.state.value.messages.last().parameters.contains("120"))
        verify(exactly = 0) { gateway.open(any()) }
        coVerify(exactly = 0) { gateway.send(any(), any(), any(), any()) }
    }

    @Test fun followUpQueriesKeepPeriodWithoutGateway() = runTest(dispatcher) {
        listOf("Qual foi minha última pressão?", "e ontem?", "e a média da semana?", "e minha glicemia?").forEach {
            vm.sendMessage(it); runCurrent()
            assertFalse(vm.state.value.isStreaming)
        }
        coVerify { hybrid.resolveLocal(match { it.healthQuery?.period == HealthPeriod.YESTERDAY }, "owner", InputChannel.TEXT, any(), any()) }
        coVerify { hybrid.resolveLocal(match { it.healthQuery?.metric == HealthMetric.GLUCOSE && it.healthQuery?.period == HealthPeriod.LAST_7_DAYS }, "owner", InputChannel.TEXT, any(), any()) }
        verify(exactly = 0) { gateway.open(any()) }
        coVerify(exactly = 0) { gateway.send(any(), any(), any(), any()) }
    }

    @Test fun newConversationClearsIncompleteQueryContext() = runTest(dispatcher) {
        vm.sendMessage("Qual foi minha última pressão?"); runCurrent()
        vm.newConversation()
        vm.sendMessage("e ontem?"); runCurrent()
        coVerify(exactly = 1) { hybrid.resolveLocal(match { it.healthQuery != null }, any(), any(), any(), any()) }
        assertNull(vm.state.value.messages.last().action)
        verify(exactly = 0) { gateway.open(any()) }
    }
}
