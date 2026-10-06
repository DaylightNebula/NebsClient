package com.nebs.core

import com.nebs.core.message.Event
import com.nebs.core.message.Message
import com.nebs.core.message.Response
import com.nebs.core.message.Subscribe
import java.io.BufferedReader
import java.io.IOException
import java.io.Writer
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.file.Path

/** A connection to the mod's Unix domain socket, for use by external applications. */
class SocketClient(path: Path) : AutoCloseable {
    private val channel = SocketChannel.open(StandardProtocolFamily.UNIX).apply {
        connect(UnixDomainSocketAddress.of(path))
    }
    private val reader: BufferedReader = Channels.newReader(channel, Charsets.UTF_8).buffered()
    private val writer: Writer = Channels.newWriter(channel, Charsets.UTF_8)

    /** Sends [message] and waits for the client's [Response]. */
    fun send(message: Message): Response {
        MessageCodec.write(writer, message)
        return when (val reply = MessageCodec.read(reader)) {
            is Response -> reply
            null -> throw IOException("Socket closed before a response was received")
            else -> throw IOException("Expected a response but got $reply")
        }
    }

    /**
     * Subscribes to events and calls [onEvent] for each one until the connection closes (returns
     * normally) or this client is closed from another thread.
     *
     * @throws IOException if the client rejects the subscription.
     */
    fun subscribe(subscribe: Subscribe, onEvent: (Event) -> Unit) {
        val reply = send(subscribe)
        if (!reply.success) throw IOException(reply.detail)
        while (true) {
            val message = try {
                MessageCodec.read(reader)
            } catch (_: IOException) {
                null
            } ?: return
            if (message is Event) onEvent(message)
        }
    }

    override fun close() = channel.close()
}
