package dsh.nebsclient.core.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Instructs the client to join the multiplayer server at [host]:[port].
 *
 * If the client is already in a world it disconnects from it first.
 *
 * Wire form: `{"type":"connect","host":"mc.example.com","port":25565}`
 */
@Serializable
@SerialName("connect")
data class ConnectToServer(
    val host: String,
    val port: Int = DEFAULT_PORT,
) : Message {
    init {
        require(host.isNotBlank()) { "host must not be blank" }
        require(port in 1..65535) { "port must be between 1 and 65535, was $port" }
    }

    companion object {
        const val DEFAULT_PORT = 25565
    }
}
