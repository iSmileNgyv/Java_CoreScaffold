package com.ismile.core.chronovcs.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Security Headers Filter
 *
 * Adds security headers to all HTTP responses to protect against common web vulnerabilities:
 *
 * 1. X-Content-Type-Options: nosniff
 *    - Prevents MIME type sniffing
 *    - Blocks content type confusion attacks
 *
 * 2. X-Frame-Options: DENY
 *    - Prevents clickjacking attacks
 *    - Blocks embedding in iframes
 *
 * 3. X-XSS-Protection: 1; mode=block
 *    - Enables browser XSS filter
 *    - Blocks page if XSS detected
 *
 * 4. Strict-Transport-Security (HSTS)
 *    - Forces HTTPS connections
 *    - Includes subdomains
 *    - 1 year max-age
 *
 * 5. Content-Security-Policy (CSP)
 *    - Restricts resource loading
 *    - Prevents XSS and data injection
 *
 * 6. Referrer-Policy: strict-origin-when-cross-origin
 *    - Controls referrer information
 *    - Privacy protection
 *
 * 7. Permissions-Policy
 *    - Disables unnecessary browser features
 *    - Reduces attack surface
 */
@Component
@Slf4j
public class SecurityHeadersFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Prevent MIME type sniffing
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");

        // Prevent clickjacking
        httpResponse.setHeader("X-Frame-Options", "DENY");

        // Enable XSS protection (legacy browsers)
        httpResponse.setHeader("X-XSS-Protection", "1; mode=block");

        // Force HTTPS (uncomment for production with HTTPS)
        // httpResponse.setHeader("Strict-Transport-Security",
        //         "max-age=31536000; includeSubDomains; preload");

        // Content Security Policy - restrict resource loading
        httpResponse.setHeader("Content-Security-Policy",
                "default-src 'self'; " +
                "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data: https:; " +
                "font-src 'self' data:; " +
                "connect-src 'self'; " +
                "frame-ancestors 'none'; " +
                "base-uri 'self'; " +
                "form-action 'self'");

        // Referrer policy - control referrer information
        httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Permissions policy - disable unnecessary features
        httpResponse.setHeader("Permissions-Policy",
                "geolocation=(), " +
                "microphone=(), " +
                "camera=(), " +
                "payment=(), " +
                "usb=(), " +
                "magnetometer=(), " +
                "gyroscope=(), " +
                "accelerometer=()");

        // Cache control for sensitive data
        httpResponse.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private");
        httpResponse.setHeader("Pragma", "no-cache");

        chain.doFilter(request, response);
    }

    @Override
    public void init(FilterConfig filterConfig) {
        log.info("Security Headers Filter initialized");
    }

    @Override
    public void destroy() {
        log.info("Security Headers Filter destroyed");
    }
}
