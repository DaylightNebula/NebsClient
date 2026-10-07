// Launches a client, joins a server, walks around and takes a screenshot. From the repository root:
//
//   ./gradlew :api:allJar
//   kotlinc -cp api/build/libs/nebs-api-0.1.0-all.jar -script examples/hello.kts [host] [port]
//
// Uses the nebs home in $NEBS_HOME, or ./.nebs (the client template is installed there the first time).

import dsh.nebsclient.api.NebsClient

val host = args.getOrElse(0) { "localhost" }
val port = args.getOrNull(1)?.toInt() ?: 25565

NebsClient { name = "Scripty" }.use { client ->
    client.connect(host, port)
    val start = client.status()
    println("${client.name} joined at ${start.blockX} ${start.blockY} ${start.blockZ}")

    client.chat("Hello from a Kotlin script!")
    client.walkTo(start.blockX + 5, start.blockZ + 5)
    client.lookAt(start.x, start.y + 1.6, start.z)

    val shot = client.screenshot("hello-from-script")
    println("Screenshot: ${shot.path} (${shot.width}x${shot.height})")
    println("Nearby: " + client.entities(radius = 16.0).joinToString { it.name })
} // closing the client quits the game
