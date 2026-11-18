package com.ledgerpay.security;

import com.ledgerpay.user.AppUser;
import com.ledgerpay.user.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserRepository users;
    private final ApiAuthenticationEntryPoint entryPoint;

    public JwtAuthenticationFilter(
            JwtService jwtService, AppUserRepository users, ApiAuthenticationEntryPoint entryPoint) {
        this.jwtService = jwtService;
        this.users = users;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = header.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            entryPoint.commence(request, response, new BadCredentialsException(JwtService.INVALID_TOKEN));
            return;
        }
        boolean authenticated = false;
        try {
            JwtPrincipal parsed = jwtService.parse(token);
            AppUser user = users.findById(parsed.userId())
                    .filter(candidate -> !candidate.isSystemAccount())
                    .orElseThrow(() -> new InvalidJwtException(JwtService.INVALID_TOKEN));
            JwtPrincipal principal = new JwtPrincipal(user.getId(), user.getEmail());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            authenticated = true;
            filterChain.doFilter(request, response);
        } catch (InvalidJwtException exception) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, new BadCredentialsException(JwtService.INVALID_TOKEN, exception));
        } finally {
            if (authenticated) {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
