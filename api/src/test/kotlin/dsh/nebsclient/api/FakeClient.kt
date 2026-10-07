package dsh.nebsclient.api

import dsh.nebsclient.core.ClientEntry
import dsh.nebsclient.core.ClientRegistry
import dsh.nebsclient.core.MessageCodec
import dsh.nebsclient.core.message.Event
import dsh.nebsclient.core.message.Message
import dsh.nebsclient.core.message.Response
import dsh.nebsclient.core.message.Subscribe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * A stand-in for a game client: registers in a temporary nebs home and answers messages with
 * canned replies shaped like the mod's.
 */
class FakeClient(val name: String = "Alice") : AutoCloseable {
    val home: Path = Files.createTempDirectory("nebs-api")
    val received = CopyOnWriteArrayList<Message>()
    private val socket = home.resolve("$name.sock")
    private val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(socket))

    init {
        ClientRegistry.register(home, ClientEntry.forCurrentProcess(name, "00000000-0000-0000-0000-000000000001", socket, home))
        thread(isDaemon = true) {
            while (server.isOpen) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    client.use {
                        val reader = Channels.newReader(it, Charsets.UTF_8).buffered()
                        val writer = Channels.newWriter(it, Charsets.UTF_8)
                        while (true) {
                            val message = runCatching { MessageCodec.read(reader) }.getOrNull() ?: break
                            received += message
                            if (message is Subscribe) {
                                MessageCodec.write(writer, Response.ok("Subscribed"))
                                MessageCodec.write(writer, Event("chat", buildJsonObject { put("message", "<Bob> hi") }, 1))
                                continue
                            }
                            MessageCodec.write(writer, reply(message))
                        }
                    }
                }
            }
        }
    }

    private fun result(json: String) = Json.parseToJsonElement(json)

    private fun reply(message: Message): Response = when (message.javaClass.simpleName) {
        "Ping" -> Response.ok("pong", mapOf("name" to name, "uuid" to "u", "state" to "in-world", "server" to "localhost"))
        "GetStatus" -> Response.result("status", result(STATUS))
        "GetInventory" -> Response.result("inv", result(INVENTORY))
        "GetEntities" -> Response.result("entities", result(ENTITIES))
        "GetLookTarget" -> Response.result("look", result(LOOK))
        "TakeScreenshot" -> Response.ok("Saved", mapOf("path" to "/tmp/shot.png", "width" to "1920", "height" to "1080"))
        "WaitForChat" -> Response(true, "<Bob> hello", data = mapOf("seq" to "7", "type" to "chat", "sender" to "Bob"))
        "Mine" -> Response.error("Too far: 41.72 blocks away, reach is 4.5")
        else -> Response.ok("ok")
    }

    override fun close() {
        server.close()
    }

    companion object {
        // Shapes copied from real replies of the mod.
        const val STATUS = """{"name":"Alice","x":-0.5,"y":-60.0,"z":3.5,"blockX":-1,"blockY":-60,"blockZ":3,"yaw":0.0,"pitch":0.0,
            "health":20.0,"maxHealth":20.0,"absorption":0.0,"food":20,"saturation":5.0,"xpLevel":0,"xpProgress":0.0,"gameMode":"SURVIVAL",
            "dimension":"minecraft:overworld","onGround":true,"inWater":false,"sprinting":false,"sneaking":false,"dead":false,"selectedSlot":0,
            "heldItem":{"id":"minecraft:diamond_sword","count":1,"name":"Diamond Sword","damage":0,"maxDamage":1561}}"""
        const val INVENTORY = """{"selectedSlot":0,"slots":[
            {"slot":0,"section":"hotbar","id":"minecraft:diamond_sword","count":1,"name":"Diamond Sword","damage":0,"maxDamage":1561,"enchantments":{"minecraft:sharpness":5}},
            {"slot":1,"section":"hotbar","id":"minecraft:dirt","count":32,"name":"Dirt"},
            {"slot":9,"section":"main","id":"minecraft:dirt","count":10,"name":"Dirt"}]}"""
        const val ENTITIES = """{"entities":[{"id":23,"type":"minecraft:pig","name":"Pig","uuid":"x","x":4.0,"y":-60.0,"z":1.0,"distance":2.1,"health":10.0,"maxHealth":10.0},
            {"id":1,"type":"minecraft:player","name":"Bob","uuid":"y","x":5.0,"y":-60.0,"z":1.0,"distance":3.0,"health":20.0,"maxHealth":20.0,"player":true}]}"""
        const val LOOK = """{"type":"block","face":"up","distance":1.62,"x":4,"y":-61,"z":-2,"block":"minecraft:grass_block","properties":{"snowy":"false"}}"""
    }
}
