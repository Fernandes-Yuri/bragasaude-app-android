package br.com.bragasaude.data.avatar

import br.com.bragasaude.data.remote.api.BragaApiClient
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AvatarPhotoUploaderTest {
    @Test fun localRejectionNeverSendsImage() = runBlocking {
        val processor=mockk<LocalAvatarPhotoProcessor>()
        val api=mockk<BragaApiClient>()
        val auth=mockk<FirebaseAuth>(relaxed=true)
        every { auth.currentUser?.uid } returns "owner"
        coEvery { processor.prepare(any()) } throws IllegalStateException("Documento")
        val uploader=AvatarPhotoUploader(processor, api, auth)
        assertTrue(runCatching { uploader.upload("owner", byteArrayOf(1)) }.isFailure)
        coVerify(exactly=0) { api.uploadProfileAvatar(any(), any(), any(), any()) }
    }
    @Test fun changedAccountAfterLocalAnalysisNeverUploads() = runBlocking {
        val processor=mockk<LocalAvatarPhotoProcessor>()
        val api=mockk<BragaApiClient>()
        val auth=mockk<FirebaseAuth>(relaxed=true)
        every { auth.currentUser?.uid } returns "owner"
        coEvery { processor.prepare(any()) } coAnswers {
            every { auth.currentUser?.uid } returns "other"
            byteArrayOf(2)
        }
        assertTrue(runCatching { AvatarPhotoUploader(processor, api, auth).upload("owner", byteArrayOf(1)) }.isFailure)
        coVerify(exactly=0) { api.uploadProfileAvatar(any(), any(), any(), any()) }
    }
    @Test fun serverRejectionIsPreservedAfterLocalChecksPass() = runBlocking {
        val processor=mockk<LocalAvatarPhotoProcessor>()
        val api=mockk<BragaApiClient>()
        val auth=mockk<FirebaseAuth>(relaxed=true)
        every { auth.currentUser?.uid } returns "owner"
        coEvery { processor.prepare(any()) } returns byteArrayOf(2)
        val rejected=BragaApiClient.AvatarUploadResult(null, 422, "Conteúdo não adequado")
        coEvery { api.uploadProfileAvatar("owner", "avatar.jpg", "image/jpeg", any()) } returns rejected
        assertEquals(rejected, AvatarPhotoUploader(processor, api, auth).upload("owner", byteArrayOf(1)))
    }
}
