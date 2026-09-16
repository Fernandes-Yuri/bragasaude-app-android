package br.com.bragasaude.ui.auth

import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.data.remote.repository.ProfileRepository
import br.com.bragasaude.data.remote.sync.SyncManager
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.SignInMethodQueryResult
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.charset.StandardCharsets

/** Google sign-in and sign-up share one Firebase account flow; role is chosen later. */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: BragaDatabase
    private lateinit var repository: ProfileRepository
    private lateinit var syncManager: SyncManager
    private lateinit var movementManager: br.com.bragasaude.data.util.MovementManager
    private lateinit var medicationRepository: br.com.bragasaude.data.remote.repository.MedicationRepository
    private lateinit var appContext: android.content.Context
    private lateinit var viewModel: AuthViewModel

    private val mainDispatcher = UnconfinedTestDispatcher()

    private var currentTestEmail: String = ""

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)

        mockkStatic(android.util.Base64::class)
        every {
            android.util.Base64.decode(any<String>(), any<Int>())
        } answers {
            val input = firstArg<String>()
            val padded = when (input.length % 4) {
                2 -> "$input=="
                3 -> "$input="
                else -> input
            }
            java.util.Base64.getUrlDecoder().decode(padded)
        }
        every {
            android.util.Base64.decode(any<ByteArray>(), any<Int>())
        } answers {
            val input = String(firstArg<ByteArray>(), StandardCharsets.UTF_8)
            val padded = when (input.length % 4) {
                2 -> "$input=="
                3 -> "$input="
                else -> input
            }
            java.util.Base64.getUrlDecoder().decode(padded)
        }

        mockkConstructor(org.json.JSONObject::class)
        every { anyConstructed<org.json.JSONObject>().optString("email", any()) } answers {
            currentTestEmail
        }

        mockkStatic(GoogleAuthProvider::class)
        val dummyCredential = mockk<AuthCredential>(relaxed = true)
        every { GoogleAuthProvider.getCredential(any(), any()) } returns dummyCredential

        auth = mockk(relaxed = true)
        database = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        syncManager = mockk(relaxed = true)
        movementManager = mockk(relaxed = true)
        medicationRepository = mockk(relaxed = true)
        appContext = mockk(relaxed = true)
        every { auth.currentUser } returns null

        viewModel = AuthViewModel(auth, database, repository, syncManager, movementManager, medicationRepository, mockk(relaxed = true), appContext)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkConstructor(org.json.JSONObject::class)
        unmockkStatic(android.util.Base64::class)
        unmockkStatic(GoogleAuthProvider::class)
    }

    // ------------------------- Helpers -------------------------

    /**
     * Constrói um JWT-like `header.payload.signature` onde payload tem o e-mail.
     * Usa `java.util.Base64.getUrlEncoder().withoutPadding()` — mesmo resultado do
     * `android.util.Base64.URL_SAFE | NO_PADDING | NO_WRAP`.
     */
    private fun makeGoogleIdToken(email: String): String {
        currentTestEmail = email
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val payload = """{"email":"$email","email_verified":true,"sub":"1234567890"}"""
        val enc = { s: String ->
            String(java.util.Base64.getUrlEncoder().withoutPadding().encode(s.toByteArray(StandardCharsets.UTF_8)))
        }
        return enc(header) + "." + enc(payload) + ".signature"
    }

    private fun <T> mockCompletedTask(result: T): Task<T> {
        return Tasks.forResult(result)
    }

    private fun mockSignInMethods(methods: List<String>) {
        val result = mockk<SignInMethodQueryResult>(relaxed = true)
        every { result.signInMethods } returns methods
        every { auth.fetchSignInMethodsForEmail(any<String>()) } returns mockCompletedTask(result)
    }

    // ==================================================================
    // FASE B — signInWithGoogle (LOGIN real)
    // ==================================================================
    @Test
    fun `signInWithGoogle delegates account creation to Firebase without prechecking email`() {
        val user = mockk<FirebaseUser>(relaxed = true)
        every { user.uid } returns "new-uid"
        val result = mockk<AuthResult>(relaxed = true)
        every { result.user } returns user
        every { auth.signInWithCredential(any<AuthCredential>()) } returns Tasks.forResult(result)
        viewModel.signInWithGoogle(makeGoogleIdToken("novo@exemplo.com"))
        verify(exactly = 1) { auth.signInWithCredential(any<AuthCredential>()) }
        verify(exactly = 0) { auth.fetchSignInMethodsForEmail(any<String>()) }
        assertEquals(AuthViewModel.AuthState.Idle, viewModel.authState.value)
    }

    @Test
    fun `signInWithGoogle with existing account calls signInWithCredential`() {
        val token = makeGoogleIdToken("velho@exemplo.com")
        mockSignInMethods(listOf("google.com"))

        val fakeUser = mockk<FirebaseUser>(relaxed = true)
        every { fakeUser.uid } returns "user-velho"
        val authResult = mockk<AuthResult>(relaxed = true)
        every { authResult.user } returns fakeUser
        every { auth.currentUser } returns fakeUser
        every { auth.signInWithCredential(any<AuthCredential>()) } returns mockCompletedTask(authResult)

        viewModel.signInWithGoogle(token)

        verify(exactly = 1) { auth.signInWithCredential(any<AuthCredential>()) }
    }

    // ==================================================================
    // FASE B — signUpWithGoogle (CADASTRO real)
    // ==================================================================
    @Test
    fun `signUpWithGoogle uses existing Firebase account without creating another identity`() {
        val user = mockk<FirebaseUser>(relaxed = true)
        every { user.uid } returns "existing-uid"
        val result = mockk<AuthResult>(relaxed = true)
        every { result.user } returns user
        every { auth.signInWithCredential(any<AuthCredential>()) } returns Tasks.forResult(result)
        viewModel.signUpWithGoogle(makeGoogleIdToken("jaexiste@exemplo.com"))
        verify(exactly = 1) { auth.signInWithCredential(any<AuthCredential>()) }
        verify(exactly = 0) { auth.fetchSignInMethodsForEmail(any<String>()) }
        coVerify { syncManager.downloadAllUserData("existing-uid") }
        assertEquals(AuthViewModel.AuthState.Idle, viewModel.authState.value)
    }

    @Test
    fun `signUpWithGoogle with fresh account calls signInWithCredential`() {
        val token = makeGoogleIdToken("novo@exemplo.com")
        mockSignInMethods(emptyList())

        val fakeUser = mockk<FirebaseUser>(relaxed = true)
        every { fakeUser.uid } returns "user-novo"
        val authResult = mockk<AuthResult>(relaxed = true)
        every { authResult.user } returns fakeUser
        every { auth.currentUser } returns fakeUser
        every { auth.signInWithCredential(any<AuthCredential>()) } returns mockCompletedTask(authResult)

        viewModel.signUpWithGoogle(token)

        verify(exactly = 1) { auth.signInWithCredential(any<AuthCredential>()) }
    }

    // ==================================================================
    // Edge case: idToken malformado
    // ==================================================================
    @Test
    fun `signInWithGoogle with malformed token emits Error and never hits fetchSignInMethods`() {
        every { auth.signInWithCredential(any<AuthCredential>()) } returns Tasks.forException(IllegalArgumentException("Token rejeitado pelo Firebase"))
        viewModel.signInWithGoogle("not-a-jwt")

        assertTrue(
            "Estado final deveria ser Error",
            viewModel.authState.value is AuthViewModel.AuthState.Error
        )
        verify(exactly = 0) { auth.fetchSignInMethodsForEmail(any<String>()) }
    }
}
