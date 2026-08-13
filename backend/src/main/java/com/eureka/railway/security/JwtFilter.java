package com.eureka.railway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
    	System.out.println("================ INCOMING PROXIED REQUEST ================");
        System.out.println("HTTP Method : " + request.getMethod());
        System.out.println("Request URI : " + request.getRequestURI());
        System.out.println("Query String: " + request.getQueryString());
        System.out.println("Remote Addr : " + request.getRemoteAddr());
        System.out.println("--- ALL HEADERS RECEIVED FROM EXPRESS ---");

        java.util.Collections.list(request.getHeaderNames()).forEach(headerName -> {
            System.out.println(headerName + ": " + request.getHeader(headerName));
        });

        System.out.println("==========================================================");
    	
    	
    	
    	String header = request.getHeader("Authorization");
       
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtUtil.parseToken(token);
                String email = claims.getSubject();
                String role = claims.get("role", String.class);

                var authority = new SimpleGrantedAuthority("ROLE_" + role);
                var authentication = new UsernamePasswordAuthenticationToken(email, null, List.of(authority));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException e) {
                // invalid/expired token -> request stays unauthenticated
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
