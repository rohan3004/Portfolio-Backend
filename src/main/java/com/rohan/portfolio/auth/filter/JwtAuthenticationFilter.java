package com.rohan.portfolio.auth.filter;

import com.rohan.portfolio.auth.service.JwtService;
import io.jsonwebtoken.ExpiredJwtException; // NEW IMPORT
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        String userEmail = null; // Initialize to null

        // 1. Check for Authorization header and format
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // No token found, proceed down the chain (leaves SecurityContext anonymous)
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Extract JWT
        jwt = authHeader.substring(7);

        // 3. Attempt to validate the token gracefully (CRITICAL FIX)
        try {
            userEmail = jwtService.extractUsername(jwt);

        } catch (ExpiredJwtException e) {
            // Token is expired. Since /contact is public, we log and let the chain continue as anonymous.
            // If the endpoint were protected, the subsequent AuthorizationFilter would issue a 401/403.
            System.out.println("JWT Expired: Allowing chain to continue anonymously.");

        } catch (Exception e) {
            // Log other validation failures (bad signature, malformed, etc.).
            System.err.println("JWT Validation Error: " + e.getMessage());
            // Continue as anonymous (null userEmail)
        }


        // 4. If username was successfully extracted AND no user is currently authenticated
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Note: We avoid calling jwtService.isTokenValid here, as its internal call to JJWT
            // would re-throw the ExpiredJwtException, which we just caught.

            try {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                // Re-validate the token against the user details and signature (excluding expiry, as handled above)
                if (jwtService.isTokenValid(jwt, userDetails)) {

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Set the SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (Exception e) {
                // User not found in DB or other fatal error during context setup
                System.err.println("Error setting security context: " + e.getMessage());
            }
        }

        // 5. Proceed down the filter chain
        filterChain.doFilter(request, response);
    }
}