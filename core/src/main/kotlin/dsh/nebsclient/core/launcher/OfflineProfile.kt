package dsh.nebsclient.core.launcher

import java.util.UUID
import kotlin.random.Random

/**
 * The identity a launched client uses. Clients never authenticate with Mojang/Microsoft, so they can
 * only join offline-mode servers (`online-mode=false`).
 */
data class OfflineProfile(val name: String, val uuid: UUID) {
    init {
        require(NAME.matches(name)) { "Player name must be 3-16 characters of A-Z, a-z, 0-9 or _, was '$name'" }
    }

    companion object {
        private val NAME = Regex("[A-Za-z0-9_]{3,16}")

        /**
         * Fills in whatever is missing: a random `NebsNNNN` name, and the UUID an offline-mode server
         * would assign to that name.
         */
        fun create(name: String? = null, uuid: UUID? = null): OfflineProfile {
            val resolvedName = name ?: "Nebs" + Random.nextInt(10_000).toString().padStart(4, '0')
            return OfflineProfile(resolvedName, uuid ?: offlineUuid(resolvedName))
        }

        /** Same algorithm vanilla servers use for players in offline mode. */
        fun offlineUuid(name: String): UUID = UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8))
    }
}
