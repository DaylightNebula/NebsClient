package com.nebs.core

import com.nebs.core.message.Message
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.Writer

/**
 * Wire format: newline-delimited JSON, one [Message] per line, UTF-8.
 *
 * The format is deliberately plain so the socket can also be driven by hand, e.g.
 * `echo '{"type":"connect","host":"localhost"}' | nc -U <socket>`.
 */
object MessageCodec {
    private val json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(message: Message): String = json.encodeToString(Message.serializer(), message)

    /** @throws kotlinx.serialization.SerializationException if [line] is not a valid message. */
    fun decode(line: String): Message = json.decodeFromString(Message.serializer(), line)

    fun write(writer: Writer, message: Message) {
        writer.write(encode(message))
        writer.write("\n")
        writer.flush()
    }

    /** Reads the next message, skipping blank lines. Returns `null` at end of stream. */
    fun read(reader: BufferedReader): Message? {
        while (true) {
            val line = reader.readLine() ?: return null
            if (line.isNotBlank()) return decode(line)
        }
    }
}
