package com.rohan.portfolio.auth.config;

import com.rohan.portfolio.auth.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    // Define allowed origins here, matching your WebConfig.
    private static final String[] ALLOWED_ORIGINS_ARRAY = {
            "https://www.rcxdev.com",
            "https://rcxdev.com",
            "https://rohan3004.github.io",
            "https://apis.byrohan.in",
            "https://portfolio.byrohan.in",
            "https://dashboards.byrohan.in",
            "https://stream.byrohan.in"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Disable CSRF (using JWTs and stateless)
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Enable CORS using the bean defined below. This is crucial for fixing the 403 on login.
                .cors(Customizer.withDefaults())

                // 3. Authorize Requests
                .authorizeHttpRequests(auth -> auth
                        // Use AntPathRequestMatcher for clarity and compatibility
                        // This ensures ALL methods (GET, POST, OPTIONS) on this path are open
                        .requestMatchers(new AntPathRequestMatcher("/v1/auth/**")).permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/contact").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/your_ip").permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/v1/github/**")).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/favicon.ico")).permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/reports/**").permitAll()
                        .requestMatchers("/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/error","/assets/**").permitAll()  // ← ADD THIS
//                        .requestMatchers(new AntPathRequestMatcher("/test/**")).permitAll()
                        // Protect everything else
                        .anyRequest().authenticated()
                )
                // 4. Stateless Session Policy
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 5. Authentication Provider (DB integration)
                .authenticationProvider(authenticationProvider)

                // 6. JWT Filter: Intercepts request to validate Access Token
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.accessDeniedHandler(accessDeniedHandler()));

        return http.build();
    }

    // Define the global CORS configuration Bean for Spring Security
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Use the list of allowed origins
        configuration.setAllowedOrigins(List.of(ALLOWED_ORIGINS_ARRAY));

        // Allow all necessary methods for auth (POST) and APIs
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Allow necessary headers, including Authorization for the Access Token
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Cache-Control", "X-Requested-With"));

        // ESSENTIAL for sending the HttpOnly Refresh Token cookie
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Apply this comprehensive CORS configuration to ALL paths
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            request.getRequestDispatcher("/error").forward(request, response);
        };
    }
}