package com.adela.payments.security;

import com.adela.payments.config.RateLimitConfig;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
public class RateLimitFilter implements Filter {

    private final ProxyManager<String> proxyManager;

    public RateLimitFilter(ProxyManager<String> proxyManager) {
        this.proxyManager = proxyManager;
    }


    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/webhook") || path.startsWith("/api/v1/auth") || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.equals("/swagger-ui.html"))  {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        String key = resolveRateLimitKey(request);
        Bucket bucket = proxyManager.builder().build(key, RateLimitConfig::defaultConfig);

        if (bucket.tryConsume(1)) {
            chain.doFilter(servletRequest, servletResponse);
        } else {
            response.setStatus(429); // Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded, please try again later\"}");
        }

    }

    private String resolveRateLimitKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "ratelimit:" + authentication.getName();
        }
        return "ratelimit:ip:" + request.getRemoteAddr();
    }
}
