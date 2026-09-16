package com.devtunde.posbackend.auth.internal.jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import com.devtunde.posbackend.auth.internal.config.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey key;
    private final AuthProperties.Jwt jwtProps;

    public JwtService(AuthProperties properties) {
        this.jwtProps = properties.jwt();
        if (jwtProps.secret() == null || jwtProps.secret().length() < 32) {
            throw new IllegalStateException("AUTH_JWT_SECRET must be set and at least 32 characters");
        }

        this.key = Keys.hmacShaKeyFor(jwtProps.secret().getBytes());
    }

    public String generateToken(String email, Collection<? extends GrantedAuthority> authorities) {

        List<String> authorityNames =
                authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList());

        String commaSeparatedAuthorities = String.join(",", authorityNames);

        Instant now = Instant.now();

        return Jwts.builder()
                .issuer(jwtProps.issuer())
                .subject(email)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(jwtProps.expirationMinutes() * 60)))
                .claim("email", email)
                .claim("authorities", commaSeparatedAuthorities)
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {

        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(jwtProps.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
