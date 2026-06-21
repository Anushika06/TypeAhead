package com.anushika.typeahead.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Global CORS configuration.
 *
 * Allowed origins are driven by the CORS_ALLOWED_ORIGINS environment variable
 * so that both local development (http://localhost:5173) and Docker
 * (http://localhost) work without changing any application code.
 *
 * The per-controller @CrossOrigin annotations are intentionally left in place
 * for documentation purposes; this bean takes global precedence.
 */
@Configuration
public class CorsConfig {

    /**
     * Comma-separated list of allowed origins.
     * Default covers local Vite dev server and Docker nginx.
     */
    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost}")
    private String allowedOriginsRaw;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Split on comma, trim whitespace
        for (String origin : allowedOriginsRaw.split(",")) {
            config.addAllowedOrigin(origin.trim());
        }

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
