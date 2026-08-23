package com.sluice.api.security;

import com.sluice.api.auth.domain.ApiKey;
import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.auth.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyAuthenticationFilter(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey != null && apiKey.startsWith("sl_live_")) {
            String keyHash = ApiKeyHasher.sha256(apiKey);
            Optional<ApiKey> validKey = apiKeyRepository.findByKeyHashAndRevokedAtIsNull(keyHash);

            if (validKey.isPresent()) {
                ApiKey key = validKey.get();
                ProjectContext context = new ProjectContext(key.getProjectId(), null, true);
                
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        context, null, Collections.emptyList()
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
                apiKeyRepository.updateLastUsedAtIfStale(
                        key.getId(), Instant.now(), Instant.now().minus(15, ChronoUnit.MINUTES));
            }
        }

        filterChain.doFilter(request, response);
    }

}
