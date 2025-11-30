package com.ismile.core.chronovcs.web;

import com.ismile.core.chronovcs.config.security.ChronoUserPrincipal;
import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        // Yalnız @CurrentUser annotasiyası olan və tipi AuthenticatedUser olan parametrləri emal edir
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && parameter.getParameterType().isAssignableFrom(AuthenticatedUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof ChronoUserPrincipal) {
            ChronoUserPrincipal principal = (ChronoUserPrincipal) authentication.getPrincipal();
            return principal.getUser();
        }

        return null; // Və ya istəyərsən throw new UnauthorizedException(...)
    }
}