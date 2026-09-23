package dev.status.web;

import dev.status.domain.ApiKeyEntity;
import dev.status.port.ApiKeyRepository;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Optional;

/**
 * Enforces X-API-Key auth on mutating /api/v1/services endpoints. Missing or
 * unknown key -> 401 (exact envelope); a known key's bound env is stored on the
 * request for the controller/service to enforce the 403 env-match rule.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String AUTH_ENV_ATTR = "statusApi.authEnv";
    public static final String AUTH_KEY_NAME_ATTR = "statusApi.authKeyName";

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyAuthFilter(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        if (!("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method))) {
            return true;
        }
        return !request.getRequestURI().startsWith("/api/v1/services");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null || apiKey.isBlank()) {
            writeError(response, 401, "Unauthorized", "Invalid or missing API key");
            return;
        }
        Optional<ApiKeyEntity> found = apiKeyRepository.findActiveByKey(apiKey);
        if (found.isEmpty()) {
            writeError(response, 401, "Unauthorized", "Invalid or missing API key");
            return;
        }
        request.setAttribute(AUTH_ENV_ATTR, found.get().getEnv());
        request.setAttribute(AUTH_KEY_NAME_ATTR, found.get().getName());
        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String title, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
    }
}
