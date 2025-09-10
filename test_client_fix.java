import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

public class test_client_fix {
    public static void main(String[] args) {
        try {
            // Тест 1: Проверяем страницу статистики клиента
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8081/statistics/client/2"))
                    .build();
                    
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Status: " + response.statusCode());
            
            if (response.statusCode() == 200) {
                System.out.println("✅ Страница статистики клиента загружается");
                
                // Проверяем наличие важных элементов
                String body = response.body();
                if (body.contains("clientId") && body.contains("statistics")) {
                    System.out.println("✅ Страница содержит необходимые данные о клиенте");
                } else {
                    System.out.println("❌ Страница не содержит данных о клиенте");
                }
            } else {
                System.out.println("❌ Ошибка загрузки страницы: " + response.statusCode());
            }
            
        } catch (Exception e) {
            System.out.println("❌ Ошибка соединения: " + e.getMessage());
            System.out.println("Убедитесь что приложение запущено на http://localhost:8081");
        }
    }
}