package com.java.service.reportfetcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.repository.ZoomosAuthSessionRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportDownloadServiceTest {

    private HttpServer server;
    private String baseUrl;
    private ZoomosAuthService authService;
    private ReportDownloadService downloadService;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/login", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().add("Set-Cookie", "JSESSIONID=valid-session; Path=/");
                exchange.getResponseHeaders().add("Location", "/");
                exchange.sendResponseHeaders(302, -1);
            } else {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().close();
            }
            exchange.close();
        });
        server.createContext("/report.xls", exchange -> {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            boolean authenticated = cookieHeader != null && cookieHeader.contains("JSESSIONID=valid-session");
            if (authenticated) {
                exchange.getResponseHeaders().add("Content-Type", "application/vnd.ms-excel");
                byte[] body = "col1;col2\nval1;val2\n".getBytes();
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } else {
                exchange.getResponseHeaders().add("Location", "/login");
                exchange.sendResponseHeaders(302, -1);
            }
            exchange.close();
        });
        server.createContext("/broken.xls", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.createContext("/always-login.xls", exchange -> {
            exchange.getResponseHeaders().add("Location", "/login");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();

        ZoomosAuthSessionRepository sessionRepository = mock(ZoomosAuthSessionRepository.class);
        when(sessionRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.empty());
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService = new ZoomosAuthService(sessionRepository, new ObjectMapper());
        authService.setBaseUrl(baseUrl);
        authService.setUsername("test-user");
        authService.setPassword("test-pass");

        downloadService = new ReportDownloadService(authService);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldLoginThenDownloadWhenNotAuthenticatedYet() throws Exception {
        ReportDownloadService.ReportDownloadResult result = downloadService.download(baseUrl + "/report.xls");

        assertEquals("XLS", result.format());
        String content = Files.readString(result.filePath());
        assertTrue(content.contains("val1"));
    }

    @Test
    void shouldThrowOnServerError() {
        assertThrows(IOException.class, () -> downloadService.download(baseUrl + "/broken.xls"));
    }

    @Test
    void shouldThrowOnUnknownHost() {
        assertThrows(Exception.class, () -> downloadService.download("http://localhost:1/report.xls"));
    }

    @Test
    void shouldThrowWhenRetryStillHitsLoginAfterReauth() {
        assertThrows(ZoomosAuthException.class,
                () -> downloadService.download(baseUrl + "/always-login.xls"));
    }
}
