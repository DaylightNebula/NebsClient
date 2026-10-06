package com.nebs.core.message

import kotlinx.serialization.Serializable

/**
 * Base type for everything sent over the nebs socket.
 *
 * Every message is a `@Serializable` class implementing this interface. Because the interface is
 * sealed, adding a new implementation in this package automatically makes it encodable by
 * [MessageCodec], and any exhaustive `when` over [Message] (such as the mod's dispatcher) will fail
 * to compile until the new message is handled.
 *
 * On the wire the concrete type is identified by the `type` field, taken from each class's
 * [kotlinx.serialization.SerialName].
 */
@Serializable
sealed interface Message
