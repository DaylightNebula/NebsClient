package com.nebs.core.launcher

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Reports install progress: `stage` is a short label, `done`/`total` count files in that stage. */
fun interface ProgressListener {
    fun onProgress(stage: String, done: Int, total: Int)

    companion object {
        val NONE = ProgressListener { _, _, _ -> }
    }
}

internal class Downloader(private val progress: ProgressListener, private val threads: Int = 16) {
    data class Task(val url: String, val target: Path, val sha1: String? = null, val size: Long? = null)

    private val http = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    fun fetchString(url: String): String {
        val response = retry(url) {
            http.send(request(url), HttpResponse.BodyHandlers.ofString())
        }
        return response.body()
    }

    /** Downloads every task in parallel, skipping files that are already present and valid. */
    fun downloadAll(stage: String, tasks: Collection<Task>) {
        val total = tasks.size
        val done = AtomicInteger()
        progress.onProgress(stage, 0, total)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val futures = tasks.map { task ->
                pool.submit {
                    download(task)
                    progress.onProgress(stage, done.incrementAndGet(), total)
                }
            }
            futures.forEach {
                try {
                    it.get()
                } catch (e: ExecutionException) {
                    throw e.cause ?: e
                }
            }
        } finally {
            pool.shutdownNow()
        }
    }

    fun download(task: Task) {
        if (isValid(task)) return
        Files.createDirectories(task.target.parent)
        val temp = task.target.resolveSibling(task.target.fileName.toString() + ".part")
        retry(task.url) {
            val response = http.send(request(task.url), HttpResponse.BodyHandlers.ofFile(temp))
            if (task.sha1 != null && sha1(temp) != task.sha1) throw IOException("Checksum mismatch for ${task.url}")
            response
        }
        Files.move(temp, task.target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun isValid(task: Task): Boolean {
        if (!Files.isRegularFile(task.target)) return false
        if (task.size != null && Files.size(task.target) != task.size) return false
        return task.sha1 == null || sha1(task.target) == task.sha1
    }

    private fun request(url: String) = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofMinutes(5))
        .header("User-Agent", "nebs-launcher")
        .build()

    private fun <T> retry(url: String, attempts: Int = 3, block: () -> HttpResponse<T>): HttpResponse<T> {
        var last: Exception? = null
        repeat(attempts) {
            try {
                val response = block()
                if (response.statusCode() in 200..299) return response
                last = IOException("HTTP ${response.statusCode()} for $url")
            } catch (e: IOException) {
                last = e
            }
        }
        throw IOException("Failed to download $url after $attempts attempts", last)
    }

    companion object {
        fun sha1(file: Path): String {
            val digest = MessageDigest.getInstance("SHA-1")
            Files.newInputStream(file).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
