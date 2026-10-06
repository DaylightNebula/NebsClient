package com.nebs.mod.socket

import com.nebs.core.MessageCodec
import com.nebs.core.message.Response
import com.nebs.core.message.Subscribe
import com.nebs.mod.events.EventHub
import com.nebs.mod.NebsClientMod.logger
import com.nebs.mod.handler.MessageDispatcher
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ClosedChannelException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path

/**
 * Listens on a Unix domain socket at [path] and feeds every received message to [MessageDispatcher].
 *
 * Each connection may send any number of newline-delimited messages (see [MessageCodec]); every one
 * is answered with a [Response] on the same connection.
 */
class SocketBridge(private val path: Path) : AutoCloseable {
    private var server: ServerSocketChannel? = null

    fun start() {
        // A previous run that crashed can leave the socket file behind, which would make bind() fail.
        Files.deleteIfExists(path)
        path.parent?.let(Files::createDirectories)

        val channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
        channel.bind(UnixDomainSocketAddress.of(path))
        server = channel

        Thread.ofPlatform().daemon().name("nebs-socket-accept").start { acceptLoop(channel) }
        logger.info("Socket communication listening on {}", path)
    }

    private fun acceptLoop(channel: ServerSocketChannel) {
        while (channel.isOpen) {
            try {
                val client = channel.accept()
                Thread.ofVirtual().name("nebs-socket-client").start { serve(client) }
            } catch (_: ClosedChannelException) {
                break
            } catch (e: IOException) {
                logger.warn("Failed to accept socket connection", e)
            }
        }
    }

    private fun serve(client: SocketChannel) = client.use {
        val reader = Channels.newReader(client, Charsets.UTF_8).buffered()
        val writer = Channels.newWriter(client, Charsets.UTF_8)
        try {
            while (true) {
                val response = try {
                    val message = MessageCodec.read(reader) ?: break
                    logger.info("Received {}", message)
                    if (message is Subscribe) {
                        stream(message, reader, writer)
                        break
                    }
                    MessageDispatcher.dispatch(message)
                } catch (e: SerializationException) {
                    Response.error("Malformed message: ${e.message}")
                } catch (e: IllegalArgumentException) {
                    Response.error("Invalid message: ${e.message}")
                }
                MessageCodec.write(writer, response)
            }
        } catch (e: IOException) {
            logger.debug("Socket connection closed", e)
        }
    }

    /** Turns the connection into an event stream until the other side closes it. */
    private fun stream(subscribe: Subscribe, reader: java.io.BufferedReader, writer: java.io.Writer) {
        val types = subscribe.events.ifEmpty { com.nebs.core.message.Event.TYPES }
        MessageCodec.write(writer, Response.ok("Subscribed to ${types.joinToString()}"))
        val subscription = EventHub.subscribe(types) { line ->
            synchronized(writer) {
                writer.write(line)
                writer.write("\n")
                writer.flush()
            }
        }
        subscription.use {
            try {
                // Nothing more is expected from the other side; reading just tells us when it hangs up.
                while (MessageCodec.read(reader) != null) Unit
            } catch (_: Exception) {
            }
        }
    }

    override fun close() {
        server?.close()
        server = null
        Files.deleteIfExists(path)
        logger.info("Socket communication stopped")
    }
}
