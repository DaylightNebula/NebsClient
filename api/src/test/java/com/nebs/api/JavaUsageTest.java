package com.nebs.api;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The API as a Java program sees it: overloads, fluent options, getters, try-with-resources. */
class JavaUsageTest {
    @Test
    void javaUsage() throws Exception {
        try (FakeClient fake = new FakeClient("Alice");
             NebsClient client = NebsClient.attach("Alice", fake.getHome())) {
            client.connect("localhost");
            client.connect("localhost", 25566);
            client.lookAt(0, 64, 0);
            client.lookAt(0.5, 64.0, 0.5);
            client.move(MoveDirection.FORWARD, 20);
            client.walkTo(10, -4);
            client.useOn(1, 2, 3, BlockFace.NORTH);
            client.clickSlot(36, 0, ClickMode.QUICK_MOVE);
            client.setOption("fov", 90);
            client.waitFor(ClientState.IN_WORLD, Duration.ofSeconds(5));

            PlayerStatus status = client.status();
            assertEquals(20, status.getFood());
            assertEquals(42, client.inventory().count("minecraft:dirt"));
            List<EntityInfo> pigs = client.entities(10.0, "pig");
            assertEquals(23, pigs.get(0).getId());
            assertThrows(NebsException.class, () -> client.mine(1, 2, 3));
        }

        ClientOptions options = new ClientOptions().name("Bob").waitForReady(false).readyTimeout(Duration.ofMinutes(1));
        assertEquals("Bob", options.getName());
    }
}
