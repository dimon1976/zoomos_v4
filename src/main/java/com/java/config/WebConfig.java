package com.java.config;

import com.java.constants.ApplicationConstants;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Configuration
@EnableWebMvc
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }

    /**
     * Настройка CORS для обработки запросов
     * ВНИМАНИЕ: Для production настройте конкретные allowedOrigins вместо allowedOriginPatterns("*")
     * Например: .allowedOrigins("https://yourdomain.com", "https://www.yourdomain.com")
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // Для локальной разработки
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowCredentials(true)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(ApplicationConstants.Network.CORS_MAX_AGE_SECONDS);
    }

    /**
     * Настройка конвертеров сообщений для корректной обработки JSON и бинарных данных
     */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // Конвертер для JSON
        converters.add(new MappingJackson2HttpMessageConverter());

        // Конвертер для массивов байтов (byte[])
        ByteArrayHttpMessageConverter byteArrayConverter = new ByteArrayHttpMessageConverter();
        List<MediaType> supportedMediaTypes = new ArrayList<>();
        supportedMediaTypes.add(MediaType.APPLICATION_OCTET_STREAM);
        supportedMediaTypes.add(MediaType.parseMediaType("text/csv"));
        supportedMediaTypes.add(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        supportedMediaTypes.add(MediaType.parseMediaType("application/vnd.ms-excel"));
        byteArrayConverter.setSupportedMediaTypes(supportedMediaTypes);
        converters.add(byteArrayConverter);


        // Конвертер для ресурсов (Resource)
        ResourceHttpMessageConverter resourceConverter = new ResourceHttpMessageConverter();
        resourceConverter.setSupportedMediaTypes(supportedMediaTypes);
        converters.add(resourceConverter);
    }

    @Bean
    public SpringResourceTemplateResolver templateResolver() {
        SpringResourceTemplateResolver templateResolver = new SpringResourceTemplateResolver();
        templateResolver.setPrefix("classpath:/templates/");
        templateResolver.setCharacterEncoding("UTF-8");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode(TemplateMode.HTML);
        return templateResolver; //
    }

    @Bean
    public SpringTemplateEngine templateEngine() {
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(templateResolver());
        return templateEngine;
    }

    @Bean
    public ThymeleafViewResolver viewResolver() {
        ThymeleafViewResolver viewResolver = new ThymeleafViewResolver();
        viewResolver.setTemplateEngine(templateEngine());
        viewResolver.setCharacterEncoding("UTF-8");
        return viewResolver;
    }

    /**
     * Конфигурация MessageSource для i18n
     */
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:messages");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setCacheSeconds(ApplicationConstants.Cache.MESSAGE_CACHE_SECONDS);
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setDefaultLocale(new Locale("ru"));
        return messageSource;
    }

    /**
     * Resolver для определения локали из заголовка Accept-Language
     * Переопределяем стандартный бин LocaleResolver
     */
    @Bean
    public org.springframework.web.servlet.LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver localeResolver = new AcceptHeaderLocaleResolver();
        localeResolver.setDefaultLocale(new Locale("ru"));
        localeResolver.setSupportedLocales(List.of(new Locale("ru"), new Locale("en")));
        return localeResolver;
    }
}