package com.nebs.core

import com.nebs.core.message.Message
import com.nebs.core.message.Response
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

    override fun close() = channel.close()
}
