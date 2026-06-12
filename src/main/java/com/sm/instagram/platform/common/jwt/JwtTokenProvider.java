package com.sm.instagram.platform.common.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT token provider for creating and validating backend JWT tokens.
 * These tokens are used in session cookies after Firebase authentication.
 */
@Slf4j
@Component
public class JwtTokenProvider {
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Value("${jwt.expiration-hours:8}")
    private int defaultExpirationHours;
    
    /**
     * Create JWT token with custom claims.
     * 
     * @param subject Token subject (Firebase UID)
     * @param claims Custom claims map
     * @param expirationDays Expiration in days
     * @return JWT token string
     */
    public String createTokenWithClaims(String subject, Map<String, Object> claims, int expirationDays) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (long) expirationDays * 24 * 60 * 60 * 1000);
        
        SecretKey key = getSigningKey();
        
        JwtBuilder builder = Jwts.builder()
            .setSubject(subject)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(key);
        
        // Add custom claims
        if (claims != null && !claims.isEmpty()) {
            claims.forEach(builder::claim);
        }
        
        return builder.compact();
    }
    
    /**
     * Create JWT token with expiration in minutes.
     * Used for short-lived tokens like 2FA partial authentication.
     * 
     * @param subject Token subject (Firebase UID)
     * @param claims Custom claims map
     * @param expirationMinutes Expiration in minutes
     * @return JWT token string
     */
    public String createTokenWithMinutesExpiry(String subject, Map<String, Object> claims, int expirationMinutes) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (long) expirationMinutes * 60 * 1000);
        
        SecretKey key = getSigningKey();
        
        JwtBuilder builder = Jwts.builder()
            .setSubject(subject)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(key);
        
        // Add custom claims
        if (claims != null && !claims.isEmpty()) {
            claims.forEach(builder::claim);
        }
        
        return builder.compact();
    }

    /**
     * Create JWT token with expiration in seconds.
     * Used for configurable session durations (production vs E2E testing).
     *
     * @param subject Token subject (Firebase UID)
     * @param claims Custom claims map
     * @param expirationSeconds Expiration in seconds
     * @return JWT token string
     */
    public String createTokenWithSecondsExpiry(String subject, Map<String, Object> claims, int expirationSeconds) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (long) expirationSeconds * 1000);

        SecretKey key = getSigningKey();

        JwtBuilder builder = Jwts.builder()
            .setSubject(subject)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(key);

        // Add custom claims
        if (claims != null && !claims.isEmpty()) {
            claims.forEach(builder::claim);
        }

        return builder.compact();
    }

    /**
     * Create JWT token with default expiration.
     * 
     * @param subject Token subject (Firebase UID)
     * @param role User role
     * @return JWT token string
     */
    public String createToken(String subject, String role) {
        return createTokenWithClaims(
            subject,
            Map.of("role", role),
            defaultExpirationHours / 24
        );
    }
    
    /**
     * Validate JWT token.
     * 
     * @param token JWT token to validate
     * @return true if valid
     */
    public boolean validateToken(String token) {
        try {
            SecretKey key = getSigningKey();
            
            Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
                
            return true;
            
        } catch (ExpiredJwtException e) {
            log.debug("JWT token expired");
            return false;
            
        } catch (UnsupportedJwtException e) {
            log.debug("Unsupported JWT token");
            return false;
            
        } catch (MalformedJwtException e) {
            log.debug("Malformed JWT token");
            return false;
            
        } catch (SecurityException e) {
            log.debug("Invalid JWT signature");
            return false;
            
        } catch (IllegalArgumentException e) {
            log.debug("JWT token compact of handler are invalid");
            return false;
        }
    }
    
    /**
     * Get claims from JWT token.
     * 
     * @param token JWT token
     * @return Claims object
     */
    public Claims getClaims(String token) {
        SecretKey key = getSigningKey();
        
        return Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .getBody();
    }
    
    /**
     * Get subject (Firebase UID) from token.
     * 
     * @param token JWT token
     * @return Subject string
     */
    public String getSubject(String token) {
        return getClaims(token).getSubject();
    }
    
    /**
     * Get specific claim from token.
     * 
     * @param token JWT token
     * @param claimName Claim name
     * @return Claim value
     */
    public Object getClaim(String token, String claimName) {
        return getClaims(token).get(claimName);
    }
    
    /**
     * Check if token is expired.
     * 
     * @param token JWT token
     * @return true if expired
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = getClaims(token).getExpiration();
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
    
    /**
     * Get signing key from secret.
     * 
     * @return Secret key for signing
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
