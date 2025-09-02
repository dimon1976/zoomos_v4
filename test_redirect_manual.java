import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

public class TestRedirect {
    public static void main(String[] args) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(10000);
        factory.setInstanceFollowRedirects(false);
        
        RestTemplate restTemplate = new RestTemplate(factory);
        
        try {
            String url = "https://httpbin.org/redirect/2";
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
            
            System.out.println("Status: " + response.getStatusCodeValue());
            System.out.println("Location: " + response.getHeaders().getFirst("Location"));
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}