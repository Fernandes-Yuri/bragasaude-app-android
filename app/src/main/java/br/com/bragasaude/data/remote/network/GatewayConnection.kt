package br.com.bragasaude.data.remote.network

import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** Ponte dos contratos existentes para o transporte OkHttp injetado.
 * Todos os verbos, multipart e downloads preservam TLS, pinning e autenticação.
 */
internal class GatewayConnection(url: URL, private val client: OkHttpClient) : HttpURLConnection(url) {
    private val requestHeaders = Headers.Builder()
    private val output = ByteArrayOutputStream()
    private var response: Response? = null

    init { instanceFollowRedirects = false }

    override fun setRequestMethod(method: String) {
        check(!connected)
        require(method in setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"))
        this.method = method
    }

    override fun setRequestProperty(key: String, value: String) {
        check(!connected)
        requestHeaders.set(key, value)
    }

    override fun addRequestProperty(key: String, value: String) {
        check(!connected)
        requestHeaders.add(key, value)
    }

    override fun getRequestProperty(key: String): String? = requestHeaders.build()[key]
    override fun getRequestProperties(): Map<String, List<String>> = requestHeaders.build().toMultimap()

    override fun getOutputStream(): OutputStream {
        check(!connected)
        doOutput = true
        return output
    }

    override fun connect() {
        if (response != null) return
        val body = if (doOutput || method in setOf("POST", "PUT", "PATCH")) {
            output.toByteArray().toRequestBody(requestHeaders.build()["Content-Type"]?.toMediaTypeOrNull())
        } else null
        val request = Request.Builder().url(url).headers(requestHeaders.build()).method(method, body).build()
        // newBuilder mantém pool, interceptors, authenticator e pins do cliente comum.
        // Sem redirects: nenhuma credencial/dado sai para outra origem por um 3xx.
        response = client.newBuilder()
            .connectTimeout(connectTimeout.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(readTimeout.toLong(), TimeUnit.MILLISECONDS)
            .followRedirects(false).followSslRedirects(false)
            .build().newCall(request).execute()
        connected = true
    }

    override fun getResponseCode(): Int { connect(); return response!!.code }
    override fun getResponseMessage(): String { connect(); return response!!.message }
    override fun getHeaderField(name: String?): String? {
        connect()
        return if (name == null) "HTTP/1.1 ${response!!.code} ${response!!.message}" else response!!.header(name)
    }
    override fun getHeaderFields(): Map<String, List<String>> { connect(); return response!!.headers.toMultimap() }
    override fun getInputStream(): InputStream {
        connect()
        if (!response!!.isSuccessful) throw IOException("HTTP ${response!!.code}")
        return response!!.body?.byteStream() ?: byteArrayOf().inputStream()
    }
    override fun getErrorStream(): InputStream? {
        connect()
        return if (response!!.code >= 400) response!!.body?.byteStream() else null
    }
    override fun disconnect() { response?.close(); response = null; connected = false }
    override fun usingProxy(): Boolean = client.proxy != null
}
