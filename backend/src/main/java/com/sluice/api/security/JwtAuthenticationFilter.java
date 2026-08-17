package com.sluice.api.security;

import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.project.repository.ProjectMemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ProjectMemberRepository projectMemberRepository;

    public JwtAuthenticationFilter(JwtService jwtService, ProjectMemberRepository projectMemberRepository) {
        this.jwtService = jwtService;
        this.projectMemberRepository = projectMemberRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            
            if (jwtService.isTokenValid(token)) {
                UUID userId = jwtService.extractUserId(token);
                
                String projectIdHeader = request.getHeader("X-Project-ID");
                UUID projectId = null;
                
                if (projectIdHeader != null) {
                    try {
                        UUID requestedProjectId = UUID.fromString(projectIdHeader);
                        if (projectMemberRepository.findByUserIdAndProjectId(userId, requestedProjectId).isPresent()) {
                            projectId = requestedProjectId;
                        }
                    } catch (IllegalArgumentException e) {
                        // Ignore invalid UUID
                    }
                }

                ProjectContext context = new ProjectContext(projectId, userId, false);
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        context, null, Collections.emptyList()
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }
}
