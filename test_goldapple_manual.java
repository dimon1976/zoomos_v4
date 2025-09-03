import java.net.HttpURLConnection;
import java.net.URL;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Тестовый скрипт для проверки проблемы с goldapple.ru
 */
public class test_goldapple_manual {
    
    public static void main(String[] args) {
        String[] testUrls = {
            "https://goldapple.ru/qr/19000180718",
            "https://goldapple.ru/qr/19000180719"
        };
        
        for (String url : testUrls) {
            System.out.println("\n=== Тестирование URL: " + url + " ===");
            testUrl(url);
        }
    }
    
    public static void testUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            // Настраиваем соединение
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(false);
            
            // Добавляем заголовки
            connection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            connection.setRequestProperty("Accept", 
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8");
            
            long startTime = System.currentTimeMillis();
            
            try {
                int responseCode = connection.getResponseCode();
                long elapsedTime = System.currentTimeMillis() - startTime;
                
                System.out.println("Response Code: " + responseCode);
                System.out.println("Response Time: " + elapsedTime + "ms");
                System.out.println("Response Message: " + connection.getResponseMessage());
                
                // Выводим основные заголовки
                Map<String, List<String>> headers = connection.getHeaderFields();
                for (String key : headers.keySet()) {
                    if (key != null && (key.toLowerCase().contains("location") || 
                                       key.toLowerCase().contains("content") ||
                                       key.toLowerCase().contains("set-cookie"))) {
                        System.out.println(key + ": " + headers.get(key));
                    }
                }
                
                // Проверяем Location для редиректов
                String location = connection.getHeaderField("Location");
                if (location != null) {
                    System.out.println("Location Header: " + location);
                }
                
                // Читаем небольшую часть контента если статус 200
                if (responseCode == 200) {
                    try {
                        java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(connection.getInputStream()));
                        String line;
                        int lineCount = 0;
                        System.out.println("Первые несколько строк контента:");
                        while ((line = reader.readLine()) != null && lineCount < 5) {
                            System.out.println("  " + line.substring(0, Math.min(100, line.length())));
                            lineCount++;
                        }
                        reader.close();
                    } catch (Exception e) {
                        System.out.println("Ошибка чтения контента: " + e.getMessage());
                    }
                }
                
            } finally {
                connection.disconnect();
            }
            
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}