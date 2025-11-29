package com.ismile.core.chronovcs.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcs.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ChronoAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    @SneakyThrows
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) {

        String code = (String) request.getAttribute(ChronoAuthFilter.ATTR_AUTH_ERROR_CODE);
        String message = (String) request.getAttribute(ChronoAuthFilter.ATTR_AUTH_ERROR_MESSAGE);

        if (code == null) {
            code = "UNAUTHORIZED";
        }
        if (message == null) {
            message = "Unauthorized";
        }

        ApiErrorResponse body = ApiErrorResponse.builder()
                .success(false)
                .errorCode(code)
                .message(message)
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}