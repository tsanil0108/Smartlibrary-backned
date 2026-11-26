package com.smartlibrary.config;

import com.smartlibrary.repository.UserRepository;
import com.smartlibrary.security.JwtAuthFilter;
import com.smartlibrary.security.JwtService;
import com.smartlibrary.security.RestAccessDeniedHandler;
import com.smartlibrary.security.RestAuthEntryPoint;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtService jwtService,
            UserRepository userRepository,
            RestAuthEntryPoint entryPoint,
            RestAccessDeniedHandler deniedHandler,
            CorsConfigurationSource cors
    ) throws Exception {

        http

                // ==========================================
                // CSRF CONFIGURATION
                // ==========================================

                .csrf(csrf -> csrf.disable())

                // ==========================================
                // CORS CONFIGURATION
                // ==========================================

                .cors(c -> c.configurationSource(cors))

                // ==========================================
                // STATELESS JWT AUTHENTICATION
                // ==========================================

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // ==========================================
                // SECURITY EXCEPTION HANDLING
                // ==========================================

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler)
                )

                // ==========================================
                // API AUTHORIZATION
                // ==========================================

                .authorizeHttpRequests(auth -> auth

                        // CORS preflight requests

                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**"
                        ).permitAll()

                        // ==================================
                        // PUBLIC AUTHENTICATION
                        // ==================================

                        .requestMatchers(
                                "/api/v1/auth/**"
                        ).permitAll()

                        // ==================================
                        // RAZORPAY WEBHOOK
                        // ==================================

                        /*
                         * Razorpay sends server-to-server
                         * payment notifications without JWT.
                         *
                         * Webhook authentication is handled
                         * using HMAC SHA256 signature
                         * verification in the controller.
                         *
                         * Only this POST endpoint is public.
                         */

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/webhooks/razorpay"
                        ).permitAll()

                        // ==================================
                        // PUBLIC BOOK CATALOGUE
                        // ==================================

                        /*
                         * Book listing
                         * Book details
                         * Book search
                         * Categories
                         * Book reviews
                         * Book rating summary
                         */

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/books",
                                "/api/v1/books/**",
                                "/api/v1/categories",
                                "/api/v1/categories/**"
                        ).permitAll()

                        // ==================================
                        // SWAGGER AND HEALTH CHECK
                        // ==================================

                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health"
                        ).permitAll()

                        // ==================================
                        // ADMIN APIs
                        // ==================================

                        /*
                         * Admin dashboard
                         * Book management
                         * Category management
                         * Member management
                         * Membership management
                         * Reservation management
                         * Library card verification
                         * Fine management
                         * Coupon management
                         * Payment management
                         * PDF payment receipts
                         * Review moderation
                         * Reports and analytics
                         */

                        .requestMatchers(
                                "/api/v1/admin/**",
                                "/api/v1/reports/**"
                        ).hasRole("ADMIN")

                        // ==================================
                        // MEMBER APIs
                        // ==================================

                        /*
                         * Member profile
                         * Change password
                         * Wishlist
                         * Book reservations
                         * Digital library card
                         * Fine history
                         * Book reviews
                         * Reading analytics
                         * Member dashboard
                         * Help and support
                         */

                        .requestMatchers(
                                "/api/v1/member/**"
                        ).hasRole("MEMBER")

                        // ==================================
                        // MEMBERSHIP PAYMENT APIs
                        // ==================================

                        /*
                         * Membership price calculation
                         * Optional coupon validation
                         * Razorpay order creation
                         * Online payment verification
                         * Free coupon redemption
                         * Payment order details
                         * Payment history
                         * PDF receipt download
                         */

                        .requestMatchers(
                                "/api/v1/payments/**"
                        ).hasRole("MEMBER")

                        // ==================================
                        // REMAINING AUTHENTICATED APIs
                        // ==================================

                        .anyRequest().authenticated()
                )

                // ==========================================
                // JWT AUTHENTICATION FILTER
                // ==========================================

                .addFilterBefore(
                        new JwtAuthFilter(
                                jwtService,
                                userRepository
                        ),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    // ==========================================
    // PASSWORD ENCODER
    // ==========================================

    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder(12);
    }

    // ==========================================
    // AUTHENTICATION MANAGER
    // ==========================================

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config
    ) throws Exception {

        return config.getAuthenticationManager();
    }
}