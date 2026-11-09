package com.platform.common.security;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guarded client checks the address it actually connects to, at connect time: a host that passes
 * {@link SsrfProtection#validate} once cannot be answered with an internal address by a second lookup, because
 * there is no second lookup outside the check.
 */
class SsrfGuardedHttpClientTest {

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();

    @BeforeEach
    void startLoopbackServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/hook", exchange -> {
            hits.incrementAndGet();
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void aConnectionToAnInternalAddressIsRefusedWhenItIsMade() {
        RestTemplate rest = new RestTemplate(
                SsrfGuardedHttpClient.requestFactory(Set.of(), Duration.ofSeconds(2), Duration.ofSeconds(2)));

        // Nobody validated this URL first: the connection itself refuses the loopback address.
        ResourceAccessException e = assertThrows(ResourceAccessException.class,
                () -> rest.getForObject(url("127.0.0.1"), String.class));
        assertTrue(e.getMessage().contains("not allowed"), e.getMessage());
        assertEquals(0, hits.get());
    }

    @Test
    void aNameIsCheckedAtConnectTimeToo() {
        RestTemplate rest = new RestTemplate(
                SsrfGuardedHttpClient.requestFactory(Set.of(), Duration.ofSeconds(2), Duration.ofSeconds(2)));

        assertThrows(ResourceAccessException.class, () -> rest.getForObject(url("localhost"), String.class));
        assertEquals(0, hits.get());
    }

    @Test
    void anAllowedHostConnects() {
        RestTemplate rest = new RestTemplate(
                SsrfGuardedHttpClient.requestFactory(Set.of("127.0.0.1"), Duration.ofSeconds(2), Duration.ofSeconds(2)));

        assertEquals("ok", rest.getForObject(url("127.0.0.1"), String.class));
        assertEquals(1, hits.get());
    }

    @Test
    void theResolverReturnsOnlyCheckedAddresses() throws Exception {
        assertEquals("8.8.8.8", SsrfGuardedHttpClient.dnsResolver(Set.of()).resolve("8.8.8.8")[0].getHostAddress());
        assertThrows(UnknownHostException.class, () -> SsrfGuardedHttpClient.dnsResolver(Set.of()).resolve("10.0.0.1"));
        assertThrows(UnknownHostException.class,
                () -> SsrfGuardedHttpClient.dnsResolver(Set.of()).resolve("no-such-host.invalid"));
    }

    private String url(String host) {
        return "http://" + host + ":" + server.getAddress().getPort() + "/hook";
    }
}
