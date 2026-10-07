package dsh.nebsclient.core.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Sends a chat message. A leading `/` runs it as a command instead. */
@Serializable
@SerialName("chat")
data class SendChat(val message: String) : Message {
    init {
        require(message.isNotBlank()) { "message must not be blank" }
    }
}

/** Runs a command, with or without the leading `/`. */
@Serializable
@SerialName("command")
data class RunCommand(val command: String) : Message {
    init {
        require(command.isNotBlank()) { "command must not be blank" }
    }
}

/**
 * Waits for a chat/system message matching the regex [pattern], received after sequence number
 * [since] (default: after this request). Fails after [timeout] seconds.
 */
@Serializable
@SerialName("wait-for-chat")
data class WaitForChat(val pattern: String, val timeout: Double = 30.0, val since: Long? = null) : Message {
    init {
        Regex(pattern) // validate early
        require(timeout > 0) { "timeout must be positive" }
    }
}
