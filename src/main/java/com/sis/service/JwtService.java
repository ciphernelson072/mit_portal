package com.sis.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    public String generateToken(String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);
        Key key = Keys.hmacShaKeyFor(secret.getBytes());

        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(secret.getBytes())).build().parseClaimsJws(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public String getUsername(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(secret.getBytes())).build().parseClaimsJws(token).getBody();
        return claims.getSubject();
    }

    public String getRole(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(secret.getBytes())).build().parseClaimsJws(token).getBody();
        return claims.get("role", String.class);
    }
}
