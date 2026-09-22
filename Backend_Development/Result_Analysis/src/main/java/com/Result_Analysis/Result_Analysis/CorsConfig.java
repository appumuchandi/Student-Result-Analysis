package com.Result_Analysis.Result_Analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:http://127.0.0.1:5500,http://localhost:5500,http://localhost:8081}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Handle "*" gracefully: with allowCredentials(true), allowedOrigins("*") is illegal.
        // Use allowedOriginPatterns instead, which correctly echoes the request origin even with wildcard.
        String trimmed = allowedOrigins == null ? "" : allowedOrigins.trim();
        // If env explicitly sets "*" or "*,..." allow all origins via pattern "*"
        boolean wantsWildcard = Arrays.stream(trimmed.split(",")).map(String::trim).anyMatch(s -> "*".equals(s));
        List<String> patterns;
        if (wantsWildcard) {
            patterns = List.of("*");
        } else {
            List<String> filtered = Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .filter(s -> !"*".equals(s))
                    .collect(Collectors.toList());
            if (filtered.isEmpty()) {
                filtered = List.of("http://127.0.0.1:5500", "http://localhost:5500", "http://localhost:8081");
            }
            // Expand to cover any localhost/127.0.0.1 port (Live Server may use 5500,5501,3000 etc.)
            // Use patterns so that any localhost port is allowed while still supporting credentials.
            patterns = new ArrayList<>(filtered);
            if (patterns.stream().noneMatch(p -> p.equals("http://localhost:*"))) patterns.add("http://localhost:*");
            if (patterns.stream().noneMatch(p -> p.equals("http://127.0.0.1:*"))) patterns.add("http://127.0.0.1:*");
            // Ensure explicit defaults remain (in case filtered already had a specific port, keep it)
        }
        String[] patArr = patterns.toArray(new String[0]);
        registry.addMapping("/**")
                .allowedOriginPatterns(patArr)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Set-Cookie")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
