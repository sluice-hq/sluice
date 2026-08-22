package com.sluice.api.security;

import com.sluice.api.auth.repository.ApiKeyRepository;
import com.sluice.api.project.repository.ProjectMemberRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final ProjectMemberRepository projectMemberRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final List<String> allowedOrigins;

    public SecurityConfig(JwtService jwtService, ProjectMemberRepository projectMemberRepository,
                          ApiKeyRepository apiKeyRepository,
                          @Value("${sluice.cors.allowed-origins:http://localhost:3000}") String allowedOrigins) {
        this.jwtService = jwtService;
        this.projectMemberRepository = projectMemberRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.allowedOrigins = List.of(allowedOrigins.split(",")).stream().map(String::trim)
                .filter(origin -> !origin.isEmpty()).toList();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtAuthFilter = new JwtAuthenticationFilter(jwtService, projectMemberRepository);
        ApiKeyAuthenticationFilter apiKeyAuthFilter = new ApiKeyAuthenticationFilter(apiKeyRepository);
        
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Add both filters. They will selectively authenticate based on the presence of headers.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(apiKeyAuthFilter, JwtAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/login").permitAll()
                .anyRequest().authenticated()
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("authorization", "content-type", "x-api-key", "x-project-id"));
        configuration.setExposedHeaders(List.of("x-api-key"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
