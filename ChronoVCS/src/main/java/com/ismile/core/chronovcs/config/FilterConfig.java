package com.ismile.core.chronovcs.config;

import com.ismile.core.chronovcs.security.RequestSanitizationFilter;
import com.ismile.core.chronovcs.security.SecurityHeadersFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Filter Configuration
 *
 * Registers servlet filters with proper ordering:
 * 1. Security Headers Filter (highest priority)
 * 2. Request Sanitization Filter (defense in depth)
 * 3. Other filters...
 */
@Configuration
@RequiredArgsConstructor
public class FilterConfig {

    private final SecurityHeadersFilter securityHeadersFilter;
    private final RequestSanitizationFilter requestSanitizationFilter;

    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilterRegistration() {
        FilterRegistrationBean<SecurityHeadersFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(securityHeadersFilter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("SecurityHeadersFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RequestSanitizationFilter> requestSanitizationFilterRegistration() {
        FilterRegistrationBean<RequestSanitizationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(requestSanitizationFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.setName("RequestSanitizationFilter");
        return registration;
    }
}
