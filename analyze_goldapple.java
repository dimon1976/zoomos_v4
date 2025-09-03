import java.net.HttpURLConnection;
import java.net.URL;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Анализ HTML контента от goldapple.ru для поиска JavaScript редиректов
 */
public class analyze_goldapple {
    
    public static void main(String[] args) {
        String testUrl = "https://goldapple.ru/qr/19000180718";
        
        System.out.println("=== Анализ HTML контента от goldapple.ru ===");
        System.out.println("URL: " + testUrl);
        
        try {
            URL url = new URL(testUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(false);
            
            connection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            
            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);
            
            if (responseCode == 200) {
                StringBuilder content = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    int linesRead = 0;
                    while ((line = reader.readLine()) != null && linesRead < 100) {
                        content.append(line).append("\n");
                        linesRead++;
                    }
                }
                
                String htmlContent = content.toString();
                System.out.println("\n=== HTML контент (первые 100 строк) ===");
                System.out.println(htmlContent);
                
                System.out.println("\n=== Поиск JavaScript редиректов ===");
                findJavaScriptRedirects(htmlContent, testUrl);
            }
            
            connection.disconnect();
            
        } catch (IOException e) {
            System.out.println("Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void findJavaScriptRedirects(String htmlContent, String baseUrl) {
        String[] patterns = {
            "(?i)window\\.location\\s*=\\s*[\"']([^\"']+)[\"']",
            "(?i)window\\.location\\.href\\s*=\\s*[\"']([^\"']+)[\"']", 
            "(?i)document\\.location\\s*=\\s*[\"']([^\"']+)[\"']",
            "(?i)location\\.href\\s*=\\s*[\"']([^\"']+)[\"']",
            "(?i)location\\s*=\\s*[\"']([^\"']+)[\"']",
            "(?i)<meta[^>]*http-equiv=[\"']refresh[\"'][^>]*content=[\"']\\d+;\\s*url=([^\"']+)[\"'][^>]*>"
        };
        
        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(htmlContent);
            if (matcher.find()) {
                String redirectUrl = matcher.group(1);
                System.out.println("Найден редирект (" + patternStr.substring(5, Math.min(25, patternStr.length())) + "...): " + redirectUrl);
                
                String resolvedUrl = resolveUrl(baseUrl, redirectUrl);
                System.out.println("Разрешенный URL: " + resolvedUrl);
                return;
            }
        }
        
        System.out.println("JavaScript редиректы не найдены в стандартных паттернах");
        
        // Поиск более сложных паттернов
        if (htmlContent.contains("location")) {
            System.out.println("\nВ HTML найдено слово 'location', проверяем контекст:");
            String[] lines = htmlContent.split("\n");
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].toLowerCase().contains("location") && lines[i].length() < 200) {
                    System.out.println("Строка " + (i+1) + ": " + lines[i].trim());
                }
            }
        }
        
        // Поиск Nuxt.js или других SPA редиректов
        if (htmlContent.contains("nuxt") || htmlContent.contains("__NUXT__")) {
            System.out.println("\nОбнаружено Nuxt.js приложение - редирект может происходить через JavaScript-рендеринг");
        }
    }
    
    private static String resolveUrl(String baseUrl, String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        
        try {
            URL base = new URL(baseUrl);
            if (url.startsWith("/")) {
                return base.getProtocol() + "://" + base.getHost() + 
                       (base.getPort() != -1 ? ":" + base.getPort() : "") + url;
            } else {
                URL resolved = new URL(base, url);
                return resolved.toString();
            }
        } catch (Exception e) {
            return url;
        }
    }
}