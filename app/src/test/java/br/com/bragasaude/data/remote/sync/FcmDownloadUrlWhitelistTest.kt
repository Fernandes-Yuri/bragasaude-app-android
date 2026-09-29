package br.com.bragasaude.data.remote.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class FcmDownloadUrlWhitelistTest {

    @Test
    fun `valid official domain returns url unchanged`() {
        val url = "https://api.bragasaude.online/api/app/download"
        val result = BragaFirebaseMessagingService.sanitizeDownloadUrl(url)
        assertEquals(url, result)
    }

    @Test
    fun `valid subdomain of bragasaude online is accepted`() {
        val url = "https://releases.bragasaude.online/apk/braga-v1.2.9.apk"
        val result = BragaFirebaseMessagingService.sanitizeDownloadUrl(url)
        assertEquals(url, result)
    }

    @Test
    fun `valid github releases domain is accepted`() {
        val url = "https://github.com/Fernandes-Yuri/bragasaude-app-android/releases/download/v1.2.8/app-release.apk"
        val result = BragaFirebaseMessagingService.sanitizeDownloadUrl(url)
        assertEquals(url, result)
    }

    @Test
    fun `valid firebase hosting domain is accepted`() {
        val url = "https://braga-saude.web.app/app/download"
        val result = BragaFirebaseMessagingService.sanitizeDownloadUrl(url)
        assertEquals(url, result)
    }

    @Test
    fun `insecure http scheme is rejected and returns default url`() {
        val insecureUrl = "http://api.bragasaude.online/api/app/download"
        val result = BragaFirebaseMessagingService.sanitizeDownloadUrl(insecureUrl)
        assertEquals(BragaFirebaseMessagingService.DEFAULT_DOWNLOAD_URL, result)
    }

    @Test
    fun `unauthorized external domain is rejected and returns default url`() {
        val maliciousUrl = "https://phishing-site.com/malicious-update.apk"
        val result = BragaFirebaseMessagingService.sanitizeDownloadUrl(maliciousUrl)
        assertEquals(BragaFirebaseMessagingService.DEFAULT_DOWNLOAD_URL, result)
    }

    @Test
    fun `spoofed domain prefix is rejected`() {
        val spoofedUrl = "https://api.bragasaude.online.fake-attacker.com/app.apk"
        val result = BragaFirebaseMessagingService.sanitizeDownloadUrl(spoofedUrl)
        assertEquals(BragaFirebaseMessagingService.DEFAULT_DOWNLOAD_URL, result)
    }

    @Test
    fun `null and empty url return default url`() {
        assertEquals(BragaFirebaseMessagingService.DEFAULT_DOWNLOAD_URL, BragaFirebaseMessagingService.sanitizeDownloadUrl(null))
        assertEquals(BragaFirebaseMessagingService.DEFAULT_DOWNLOAD_URL, BragaFirebaseMessagingService.sanitizeDownloadUrl(""))
        assertEquals(BragaFirebaseMessagingService.DEFAULT_DOWNLOAD_URL, BragaFirebaseMessagingService.sanitizeDownloadUrl("   "))
    }

    @Test
    fun `malformed url returns default url without throwing`() {
        val malformed = "not a valid uri ://"
        val result = BragaFirebaseMessagingService.sanitizeDownloadUrl(malformed)
        assertEquals(BragaFirebaseMessagingService.DEFAULT_DOWNLOAD_URL, result)
    }
}
