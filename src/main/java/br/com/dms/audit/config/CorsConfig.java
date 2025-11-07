package br.com.dms.audit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer auditCorsConfigurer(
        @Value("${dms.cors.allowed-origins:*}") String allowedOrigins,
        @Value("${dms.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}") String allowedMethods,
        @Value("${dms.cors.allowed-headers:*}") String allowedHeaders,
        @Value("${dms.cors.allow-credentials:true}") boolean allowCredentials
    ) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toArray(String[]::new);
        String[] methods = Arrays.stream(allowedMethods.split(","))
            .map(String::trim)
            .filter(method -> !method.isEmpty())
            .toArray(String[]::new);
        String[] headers = Arrays.stream(allowedHeaders.split(","))
            .map(String::trim)
            .filter(header -> !header.isEmpty())
            .toArray(String[]::new);

        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                    .allowedOrigins(origins.length == 0 ? new String[]{"*"} : origins)
                    .allowedMethods(methods.length == 0 ? new String[]{"GET"} : methods)
                    .allowedHeaders(headers.length == 0 ? new String[]{"*"} : headers)
                    .allowCredentials(allowCredentials)
                    .maxAge(3600);
            }
        };
    }
}
