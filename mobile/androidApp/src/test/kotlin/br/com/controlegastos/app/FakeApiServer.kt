package br.com.controlegastos.app

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** API falsa em 127.0.0.1 para exercitar o HttpURLConnection de verdade. */
class FakeApiServer : AutoCloseable {

    data class Reply(val status: Int, val body: String = "", val headers: Map<String, String> = emptyMap())

    data class Recorded(
        val method: String,
        val path: String,
        val query: String?,
        val authorization: String?,
        val cookie: String?,
        val body: String,
    )

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val routes = ConcurrentHashMap<String, ArrayDeque<Reply>>()
    val requests = CopyOnWriteArrayList<Recorded>()
    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            requests += Recorded(
                method = exchange.requestMethod,
                path = path,
                query = exchange.requestURI.rawQuery,
                authorization = exchange.requestHeaders.getFirst("Authorization"),
                cookie = exchange.requestHeaders.getFirst("Cookie"),
                body = exchange.requestBody.readBytes().toString(Charsets.UTF_8),
            )
            val queue = routes["${exchange.requestMethod} $path"]
            val reply = when {
                queue == null || queue.isEmpty() -> Reply(404)
                queue.size > 1 -> queue.removeFirst()
                else -> queue.first()
            }
            reply.headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
            val bytes = reply.body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(reply.status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
    }

    /** Respostas em ordem; a última se repete para chamadas seguintes. */
    fun on(method: String, path: String, vararg replies: Reply) {
        routes["$method $path"] = ArrayDeque(replies.toList())
    }

    fun requestsTo(method: String, path: String) = requests.filter { it.method == method && it.path == path }

    override fun close() = server.stop(0)
}

class InMemoryRefreshStore(var cookie: String? = null) : RefreshTokenStore {
    override fun save(cookie: String) { this.cookie = cookie }
    override fun load(): String? = cookie
    override fun clear() { cookie = null }
}

fun problem(code: String, detail: String) = """{"code": "$code", "detail": "$detail", "title": "Erro"}"""
