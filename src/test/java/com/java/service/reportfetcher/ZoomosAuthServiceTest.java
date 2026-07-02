package com.java.service.reportfetcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.model.entity.ZoomosAuthSession;
import com.java.repository.ZoomosAuthSessionRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ZoomosAuthServiceTest {

    private HttpServer server;
    private String baseUrl;
    private ZoomosAuthSessionRepository sessionRepository;
    private ZoomosAuthService authService;
    private final AtomicBoolean sessionCookieValid = new AtomicBoolean(false);

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/login", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                exchange.getRequestBody().readAllBytes(); // drain
                sessionCookieValid.set(true);
                exchange.getResponseHeaders().add("Set-Cookie", "JSESSIONID=valid-session; Path=/");
                exchange.getResponseHeaders().add("Location", "/");
                exchange.sendResponseHeaders(302, -1);
            } else {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().close();
            }
            exchange.close();
        });
        server.createContext("/report", exchange -> {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            boolean authenticated = cookieHeader != null && cookieHeader.contains("JSESSIONID=valid-session");
            if (authenticated) {
                byte[] body = "col1;col2\nval1;val2\n".getBytes();
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } else {
                exchange.getResponseHeaders().add("Location", "/login");
                exchange.sendResponseHeaders(302, -1);
            }
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();

        sessionRepository = mock(ZoomosAuthSessionRepository.class);
        when(sessionRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.empty());
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService = new ZoomosAuthService(sessionRepository, new ObjectMapper());
        authService.setBaseUrl(baseUrl);
        authService.setUsername("test-user");
        authService.setPassword("test-pass");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldDetectLoginRedirect() {
        assertTrue(authService.isLoginPage(java.net.URI.create(baseUrl + "/login")));
        assertFalse(authService.isLoginPage(java.net.URI.create(baseUrl + "/report")));
    }

    @Test
    void shouldLoginAndSaveCookies() throws Exception {
        HttpClient client = authService.buildAuthenticatedClient();
        authService.login(client);

        verify(sessionRepository).save(argThat(session -> session.getCookies().contains("JSESSIONID")));
    }

    @Test
    void shouldThrowWhenLoginFailsForBadCredentials() throws Exception {
        server.removeContext("/login");
        server.createContext("/login", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Location", "/login");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        HttpClient client = authService.buildAuthenticatedClient();
        assertThrows(ZoomosAuthException.class, () -> authService.login(client));
    }

    @Test
    void shouldLoadCookiesFromExistingSession() throws Exception {
        String cookiesJson = new ObjectMapper().writeValueAsString(java.util.List.of(
                new ZoomosAuthService.SerializableCookie("JSESSIONID", "valid-session", "localhost", "/")
        ));
        when(sessionRepository.findTopByOrderByUpdatedAtDesc())
                .thenReturn(Optional.of(ZoomosAuthSession.builder().id(1L).cookies(cookiesJson).build()));

        HttpClient client = authService.buildAuthenticatedClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create(baseUrl + "/report"))
                .GET().build();
        java.net.http.HttpResponse<String> response =
                client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertFalse(authService.isLoginPage(response.uri()));
        assertTrue(response.body().contains("val1"));
    }
}
