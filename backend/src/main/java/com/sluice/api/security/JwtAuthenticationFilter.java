package com.sluice.api.security;

import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.project.repository.ProjectMemberRepository;
import com.sluice.api.auth.repository.UserRepository;
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
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, ProjectMemberRepository projectMemberRepository,
                                   UserRepository userRepository) {
        this.jwtService = jwtService;
        this.projectMemberRepository = projectMemberRepository;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            
            if (jwtService.isTokenValid(token)) {
                try {
                    UUID userId = jwtService.extractUserId(token);
                    long sessionVersion = jwtService.extractSessionVersion(token);
                    var user = userRepository.findById(userId).orElse(null);
                    if (user == null || user.getSessionVersion() != sessionVersion) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                
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

                // A user identity is valid independently of the currently selected project.
                // Project-scoped services call ProjectContext#getProjectId(), which rejects
                // requests that have no valid project selection.
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    ProjectContext context = new ProjectContext(projectId, userId, false);
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            context, null, Collections.emptyList()
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
                } catch (IllegalArgumentException ignored) {
                    // A malformed identity claim is treated as an invalid token.
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
