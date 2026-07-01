package com.java.service.reportfetcher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportDownloadService {

    private final ZoomosAuthService authService;

    @Value("${report-fetcher.download.timeout-hours:3}")
    private int downloadTimeoutHours = 3;

    public ReportDownloadResult download(String sourceUrl) throws IOException, InterruptedException {
        HttpClient client = authService.buildAuthenticatedClient();
        HttpResponse<byte[]> response = sendGet(client, sourceUrl);

        if (authService.isLoginPage(response.uri())) {
            log.info("Сессия Zoomos недействительна, выполняется повторная авторизация");
            authService.login(client);
            response = sendGet(client, sourceUrl);
            if (authService.isLoginPage(response.uri())) {
                throw new ZoomosAuthException(
                        "Авторизация не удалась — проверьте логин/пароль в настройках zoomos.*");
            }
        } else {
            authService.saveCookies(client);
        }

        if (response.statusCode() != 200) {
            throw new IOException("Zoomos вернул HTTP " + response.statusCode());
        }
        if (response.body() == null || response.body().length == 0) {
            throw new IOException("Zoomos вернул пустой ответ");
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String format = detectFormat(sourceUrl, contentType);

        Path tempFile = Files.createTempFile("report-fetcher-", "." + format.toLowerCase());
        Files.write(tempFile, response.body());

        return new ReportDownloadResult(tempFile, format);
    }

    private HttpResponse<byte[]> sendGet(HttpClient client, String url)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofHours(downloadTimeoutHours))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private String detectFormat(String sourceUrl, String contentType) {
        String ct = contentType.toLowerCase();
        if (ct.contains("spreadsheetml")) return "XLSX";
        if (ct.contains("ms-excel")) return "XLS";
        if (ct.contains("csv")) return "CSV";

        String lowerUrl = sourceUrl.toLowerCase();
        if (lowerUrl.contains("xlsx")) return "XLSX";
        if (lowerUrl.contains("xls")) return "XLS";
        if (lowerUrl.contains("csv")) return "CSV";
        return "XLSX";
    }

    public record ReportDownloadResult(Path filePath, String format) {}
}
