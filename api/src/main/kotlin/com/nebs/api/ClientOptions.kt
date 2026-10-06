package com.nebs.api

import com.nebs.core.launcher.ProgressListener
import java.nio.file.Path
import java.time.Duration
import java.util.UUID

/**
 * How [NebsClient] launches (or [NebsClient.spawn] starts) a client. Every setting is optional.
 *
 * Java: `new ClientOptions().name("Bob").waitForReady(false)`.
 * Kotlin: `NebsClient { name = "Bob"; waitForReady = false }`.
 */
public class ClientOptions {
    /** Player name; default a random `NebsNNNN`. */
    public var name: String? = null

    /** Player UUID; default the offline-mode UUID for the name. */
    public var uuid: UUID? = null

    /** nebs home; default `$NEBS_HOME` or `./.nebs`. */
    public var home: Path? = null

    /** Client template; default `<home>/template`. Installed automatically if missing. */
    public var template: Path? = null

    /** Game folder for this client; default `<home>/instances/<name>`. */
    public var instance: Path? = null

    /** Whether creating the client blocks until it has finished loading. */
    public var waitForReady: Boolean = true

    /** How long to wait for the client to finish loading. */
    public var readyTimeout: Duration = Duration.ofMinutes(5)

    /** Install the template first if it isn't installed (about 700 MB the first time). */
    public var installIfMissing: Boolean = true

    /** Whether [NebsClient.close] quits the game. */
    public var quitOnClose: Boolean = true

    /** Maximum JVM heap for the game, e.g. `2G`. */
    public var memory: String = "2G"

    /** Window size in screen points; default the game's. */
    public var width: Int? = null
    public var height: Int? = null

    /** Progress of an automatic template install; by default printed to stderr. */
    public var installProgress: ProgressListener = ProgressListener { stage, done, total ->
        if (done == total) System.err.println("nebs: $stage: $total files")
    }

    // Fluent setters for Java.
    public fun name(value: String?): ClientOptions = apply { name = value }
    public fun uuid(value: UUID?): ClientOptions = apply { uuid = value }
    public fun home(value: Path?): ClientOptions = apply { home = value }
    public fun template(value: Path?): ClientOptions = apply { template = value }
    public fun instance(value: Path?): ClientOptions = apply { instance = value }
    public fun waitForReady(value: Boolean): ClientOptions = apply { waitForReady = value }
    public fun readyTimeout(value: Duration): ClientOptions = apply { readyTimeout = value }
    public fun installIfMissing(value: Boolean): ClientOptions = apply { installIfMissing = value }
    public fun quitOnClose(value: Boolean): ClientOptions = apply { quitOnClose = value }
    public fun memory(value: String): ClientOptions = apply { memory = value }
    public fun windowSize(width: Int, height: Int): ClientOptions = apply {
        this.width = width
        this.height = height
    }
    public fun installProgress(value: ProgressListener): ClientOptions = apply { installProgress = value }
}
