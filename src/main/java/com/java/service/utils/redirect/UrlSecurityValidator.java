package com.java.service.utils.redirect;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@Slf4j
public class UrlSecurityValidator {
    
    private static final Set<String> BLOCKED_SCHEMES = Set.of(
        "file", "ftp", "jar", "data", "javascript", "vbscript"
    );
    
    private static final Set<String> BLOCKED_HOSTS = Set.of(
        "localhost", "127.0.0.1", "::1", "0.0.0.0"
    );
    
    private static final Pattern PRIVATE_IP_PATTERN = Pattern.compile(
        "^(10\\.|172\\.(1[6-9]|2\\d|3[01])\\.|192\\.168\\.|169\\.254\\.)"
    );
    
    private static final Pattern LOOPBACK_PATTERN = Pattern.compile(
        "^(127\\.|::1$|localhost$)"
    );
    
    public void validateUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new SecurityException("URL не может быть пустым");
        }
        
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new SecurityException("Некорректный формат URL: " + e.getMessage());
        }
        
        validateScheme(uri);
        validateHost(uri);
        
        log.debug("URL прошел валидацию безопасности: {}", url);
    }
    
    private void validateScheme(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new SecurityException("URL должен содержать протокол (http/https)");
        }
        
        scheme = scheme.toLowerCase();
        
        if (BLOCKED_SCHEMES.contains(scheme)) {
            throw new SecurityException("Запрещенный протокол: " + scheme);
        }
        
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new SecurityException("Разрешены только HTTP и HTTPS протоколы");
        }
    }
    
    private void validateHost(URI uri) {
        String host = uri.getHost();
        if (host == null || host.trim().isEmpty()) {
            throw new SecurityException("URL должен содержать валидный хост");
        }
        
        host = host.toLowerCase().trim();
        
        if (BLOCKED_HOSTS.contains(host)) {
            throw new SecurityException("Запрещенный хост: " + host);
        }
        
        if (LOOPBACK_PATTERN.matcher(host).find()) {
            throw new SecurityException("Запрещен доступ к loopback адресам");
        }
        
        // Проверка IPv6 loopback адреса (может быть в скобках в URI)
        if (host.equals("::1") || host.equals("[::1]")) {
            throw new SecurityException("Запрещен доступ к IPv6 loopback адресу");
        }
        
        if (isPrivateOrLocalAddress(host)) {
            throw new SecurityException("Запрещен доступ к внутренним сетевым адресам");
        }
        
        validatePortRange(uri.getPort());
    }
    
    private boolean isPrivateOrLocalAddress(String host) {
        if (PRIVATE_IP_PATTERN.matcher(host).find()) {
            return true;
        }
        
        try {
            InetAddress inetAddress = InetAddress.getByName(host);
            return inetAddress.isLoopbackAddress() || 
                   inetAddress.isSiteLocalAddress() || 
                   inetAddress.isLinkLocalAddress() ||
                   inetAddress.isMulticastAddress();
        } catch (UnknownHostException e) {
            log.warn("Не удалось разрешить хост: {}", host, e);
            return false;
        }
    }
    
    private void validatePortRange(int port) {
        if (port == -1) {
            return;
        }
        
        if (port <= 0 || port > 65535) {
            throw new SecurityException("Некорректный порт: " + port);
        }
        
        if (isRestrictedPort(port)) {
            throw new SecurityException("Запрещенный порт: " + port);
        }
    }
    
    private boolean isRestrictedPort(int port) {
        int[] restrictedPorts = {
            21, 22, 23, 25, 53, 110, 143, 443, 993, 995,  // Common services
            3306, 5432, 6379, 27017,                       // Databases  
            5060, 5061,                                     // SIP
            1433, 1521,                                     // SQL Server, Oracle
            135, 139, 445                                   // Windows services
        };
        
        for (int restrictedPort : restrictedPorts) {
            if (port == restrictedPort) {
                return true;
            }
        }
        
        return port < 1024;
    }
}