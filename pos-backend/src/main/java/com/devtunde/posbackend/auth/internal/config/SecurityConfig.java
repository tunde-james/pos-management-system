package com.devtunde.posbackend.auth.internal.config;

import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.devtunde.posbackend.auth.internal.jwt.JwtValidator;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtValidator jwtValidator;
    private final AuthProperties properties;

    public SecurityConfig(JwtValidator jwtValidator, AuthProperties properties) {
        this.jwtValidator = jwtValidator;
        this.properties = properties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        return http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health")
                        .permitAll()
                        .requestMatchers("/api/v1/auth/**")
                        .permitAll()
                        .requestMatchers("/api/v1/super-admin/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/v1/**")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .addFilterBefore(jwtValidator, BasicAuthenticationFilter.class)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(
                                (req, res, ex) -> res.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"))
                        .accessDeniedHandler(
                                (req, res, ex) -> res.sendError(HttpStatus.FORBIDDEN.value(), "Forbidden")))
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {

        AuthProperties.Argon2 password = properties.argon2();

        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(
                password.saltLength(),
                password.hashLength(),
                password.parallelism(),
                password.memoryKb(),
                password.iterations());

        Map<String, PasswordEncoder> encoders = Map.of("argon2", argon2);

        DelegatingPasswordEncoder delegating = new DelegatingPasswordEncoder("argon2", encoders);

        return delegating;
    }

    private UrlBasedCorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of("http://localhost:5173"));
        cfg.setAllowedMethods(List.of(""));
        cfg.setExposedHeaders(List.of("Authorization"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/***", cfg);

        return source;
    }
}
