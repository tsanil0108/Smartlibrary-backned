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

                // CSRF
                .csrf(csrf -> csrf.disable())

                // CORS
                .cors(c -> c.configurationSource(cors))

                // STATELESS JWT
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // EXCEPTION HANDLING
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler)
                )

                // API AUTHORIZATION
                .authorizeHttpRequests(auth -> auth

                        // CORS PREFLIGHT
                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**"
                        ).permitAll()

                        // PUBLIC AUTHENTICATION
                        .requestMatchers(
                                "/api/v1/auth/**"
                        ).permitAll()

                        // RAZORPAY WEBHOOK
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/webhooks/razorpay"
                        ).permitAll()

                        // PUBLIC BOOK CATALOGUE
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/books",
                                "/api/v1/books/**",
                                "/api/v1/categories",
                                "/api/v1/categories/**"
                        ).permitAll()

                        // SWAGGER AND HEALTH CHECK
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health"
                        ).permitAll()

                        // ADMIN APIs
                        .requestMatchers(
                                "/api/v1/admin/**",
                                "/api/v1/reports/**"
                        ).hasRole("ADMIN")

                        // MEMBER APIs
                        .requestMatchers(
                                "/api/v1/member/**"
                        ).hasRole("MEMBER")

                        // PAYMENT APIs
                        .requestMatchers(
                                "/api/v1/payments/**"
                        ).hasRole("MEMBER")

                        // REMAINING APIs
                        .anyRequest().authenticated()
                )

                // JWT FILTER
                .addFilterBefore(
                        new JwtAuthFilter(
                                jwtService,
                                userRepository
                        ),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    // PASSWORD ENCODER

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    // AUTHENTICATION MANAGER

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config
    ) throws Exception {

        return config.getAuthenticationManager();
    }
}