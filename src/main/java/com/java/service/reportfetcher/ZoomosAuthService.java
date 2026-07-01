package com.java.service.reportfetcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.model.entity.ZoomosAuthSession;
import com.java.repository.ZoomosAuthSessionRepository;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Service
@Slf4j
public class ZoomosAuthService {

    private final ZoomosAuthSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    @Setter
    @Value("${zoomos.base-url}")
    private String baseUrl;

    @Setter
    @Value("${zoomos.username}")
    private String username;

    @Setter
    @Value("${zoomos.password}")
    private String password;

    public ZoomosAuthService(ZoomosAuthSessionRepository sessionRepository, ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
    }

    public HttpClient buildAuthenticatedClient() {
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        loadCookies(cookieManager);
        return HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public boolean isLoginPage(URI responseUri) {
        return responseUri.getPath() != null && responseUri.getPath().contains("/login");
    }

    public void login(HttpClient client) throws IOException, InterruptedException {
        String form = "j_username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&j_password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .timeout(Duration.ofSeconds(30))
                .build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        if (isLoginPage(response.uri())) {
            throw new ZoomosAuthException(
                    "Авторизация не удалась — проверьте логин/пароль в настройках zoomos.*");
        }
        saveCookies(client);
        log.info("Авторизация на {} выполнена успешно", baseUrl);
    }

    public void saveCookies(HttpClient client) {
        CookieManager cookieManager = (CookieManager) client.cookieHandler().orElseThrow();
        List<HttpCookie> cookies = cookieManager.getCookieStore().getCookies();
        List<SerializableCookie> serializable = cookies.stream()
                .map(c -> new SerializableCookie(c.getName(), c.getValue(), c.getDomain(), c.getPath()))
                .toList();
        try {
            String json = objectMapper.writeValueAsString(serializable);
            ZoomosAuthSession session = sessionRepository.findTopByOrderByUpdatedAtDesc()
                    .orElseGet(ZoomosAuthSession::new);
            session.setCookies(json);
            sessionRepository.save(session);
        } catch (Exception e) {
            log.warn("Не удалось сохранить куки Zoomos: {}", e.getMessage());
        }
    }

    private void loadCookies(CookieManager cookieManager) {
        sessionRepository.findTopByOrderByUpdatedAtDesc().ifPresent(session -> {
            try {
                List<SerializableCookie> cookies = objectMapper.readValue(session.getCookies(),
                        new TypeReference<List<SerializableCookie>>() {});
                URI uri = URI.create(baseUrl);
                for (SerializableCookie c : cookies) {
                    HttpCookie cookie = new HttpCookie(c.name(), c.value());
                    cookie.setVersion(0); // Netscape-style "name=value" header, matches real Set-Cookie parsing
                    if (c.domain() != null) cookie.setDomain(c.domain());
                    if (c.path() != null) cookie.setPath(c.path());
                    cookieManager.getCookieStore().add(uri, cookie);
                }
            } catch (Exception e) {
                log.warn("Не удалось загрузить куки Zoomos: {}", e.getMessage());
            }
        });
    }

    public record SerializableCookie(String name, String value, String domain, String path) {}
}
