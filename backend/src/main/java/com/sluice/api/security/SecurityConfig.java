package com.sluice.api.security;

import com.sluice.api.auth.repository.ApiKeyRepository;
import com.sluice.api.project.repository.ProjectMemberRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final ProjectMemberRepository projectMemberRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final List<String> allowedOrigins;
    private final boolean publicPrometheus;

    public SecurityConfig(JwtService jwtService, ProjectMemberRepository projectMemberRepository,
                          ApiKeyRepository apiKeyRepository,
                          @Value("${sluice.cors.allowed-origins:http://localhost:3000}") String allowedOrigins,
                          @Value("${sluice.actuator.public-prometheus:false}") boolean publicPrometheus) {
        this.jwtService = jwtService;
        this.projectMemberRepository = projectMemberRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.allowedOrigins = List.of(allowedOrigins.split(",")).stream().map(String::trim)
                .filter(origin -> !origin.isEmpty()).toList();
        this.publicPrometheus = publicPrometheus;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtAuthFilter = new JwtAuthenticationFilter(jwtService, projectMemberRepository);
        ApiKeyAuthenticationFilter apiKeyAuthFilter = new ApiKeyAuthenticationFilter(apiKeyRepository);
        
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) ->
                    writeProblem(response, HttpStatus.UNAUTHORIZED, "Authentication is required.", "unauthenticated"))
                .accessDeniedHandler((request, response, exception) ->
                    writeProblem(response, HttpStatus.FORBIDDEN,
                            "You do not have permission for this operation.", "forbidden")))
            // Add both filters. They will selectively authenticate based on the presence of headers.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(apiKeyAuthFilter, JwtAuthenticationFilter.class)
            .authorizeHttpRequests(authorize -> {
                authorize.requestMatchers("/api/v1/auth/login", "/api/v1/auth/signup", "/actuator/health", "/actuator/info").permitAll();
                if (publicPrometheus) authorize.requestMatchers("/actuator/prometheus").permitAll();
                authorize.anyRequest().authenticated();
            });

        return http.build();
    }

    private void writeProblem(HttpServletResponse response, HttpStatus status, String detail, String code)
            throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"status\":" + status.value()
                + ",\"detail\":\"" + detail + "\",\"code\":\"" + code + "\"}");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
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
