package com.java.service.reportfetcher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportDownloadService {

    private final ZoomosAuthService authService;

    @Value("${report-fetcher.download.timeout-hours:3}")
    private int downloadTimeoutHours = 3;

    /**
     * Downloads the report from {@code sourceUrl}, authenticating (or re-authenticating) as needed.
     * The caller is responsible for deleting {@link ReportDownloadResult#filePath()} after use.
     */
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
        String originalFileName = extractOriginalFileName(response);

        Path tempFile = Files.createTempFile("report-fetcher-", "." + format.toLowerCase());
        Files.write(tempFile, response.body());

        return new ReportDownloadResult(tempFile, format, originalFileName);
    }

    private HttpResponse<byte[]> sendGet(HttpClient client, String url)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(sanitizeUrl(url)))
                .GET()
                .timeout(Duration.ofHours(downloadTimeoutHours))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    // Characters that Zoomos report URLs commonly contain unescaped (copy-pasted from the
    // Zoomos UI, e.g. the "cols=" column-mapping parameter uses raw spaces/pipes/">" as
    // human-readable separators) but that java.net.URI's strict RFC 3986 parser rejects.
    // Browsers auto-encode these when navigating; java.net.URI does not, so we do it here.
    // Cyrillic/other non-ASCII characters and already-percent-encoded sequences (e.g. %25)
    // are left untouched — URI accepts those.
    private static final Map<Character, String> ILLEGAL_URL_CHARS = Map.ofEntries(
            Map.entry(' ', "%20"),
            Map.entry('|', "%7C"),
            Map.entry('<', "%3C"),
            Map.entry('>', "%3E"),
            Map.entry('"', "%22"),
            Map.entry('{', "%7B"),
            Map.entry('}', "%7D"),
            Map.entry('[', "%5B"),
            Map.entry(']', "%5D"),
            Map.entry('\\', "%5C"),
            Map.entry('^', "%5E"),
            Map.entry('`', "%60")
    );

    private static String sanitizeUrl(String rawUrl) {
        StringBuilder sanitized = new StringBuilder(rawUrl.length());
        for (int i = 0; i < rawUrl.length(); i++) {
            char c = rawUrl.charAt(i);
            String encoded = ILLEGAL_URL_CHARS.get(c);
            sanitized.append(encoded != null ? encoded : c);
        }
        return sanitized.toString();
    }

    // RFC 5987 extended notation, e.g. filename*=UTF-8''%D0%BE%D1%82%D1%87%D1%91%D1%82.xlsx
    private static final Pattern FILENAME_STAR_PATTERN =
            Pattern.compile("filename\\*\\s*=\\s*[^']*''([^;]+)", Pattern.CASE_INSENSITIVE);
    // Plain filename="..." or filename=... (with or without quotes)
    private static final Pattern FILENAME_PATTERN =
            Pattern.compile("filename\\s*=\\s*\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE);

    /**
     * Extracts the filename Zoomos itself suggests via the Content-Disposition response
     * header, if present — used so the file the user downloads keeps Zoomos's own naming
     * instead of our internally generated one. Returns null if the header is absent or
     * doesn't contain a recognizable filename.
     */
    private String extractOriginalFileName(HttpResponse<?> response) {
        String header = response.headers().firstValue("Content-Disposition").orElse(null);
        if (header == null) {
            return null;
        }
        Matcher starMatcher = FILENAME_STAR_PATTERN.matcher(header);
        if (starMatcher.find()) {
            try {
                return URLDecoder.decode(starMatcher.group(1).trim(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("Не удалось раскодировать filename* из Content-Disposition: {}", e.getMessage());
            }
        }
        Matcher matcher = FILENAME_PATTERN.matcher(header);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
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

    public record ReportDownloadResult(Path filePath, String format, String originalFileName) {}
}
