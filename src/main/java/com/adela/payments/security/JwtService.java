package com.adela.payments.security;

import com.adela.payments.exception.BadRequestException;
import com.adela.payments.utils.KeyUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private static final String TOKEN_TYPE = "token_type";
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    
    @Value("${app.security.jwt.access-token-expiration}")
    private long accessTokenExpiration;
    @Value("${app.security.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    public JwtService() throws Exception {
        this.privateKey = KeyUtils.loadPrivateKey("keys/private_key.pem");
        this.publicKey = KeyUtils.loadPublicKey("keys/public_key.pem");
    }
    
    public String generateAccessToken(String username) {
        Map<String, Object> claims = Map.of(TOKEN_TYPE, "ACCESS_TOKEN");
        return buildToken(username, claims, this.accessTokenExpiration);
    }

    public String generateRefreshToken(String username) {
        Map<String, Object> claims = Map.of(TOKEN_TYPE, "REFRESH_TOKEN");
        return buildToken(username, claims, this.refreshTokenExpiration);
    }

    private String buildToken(String username, Map<String, Object> claims, long tokenExpiration) {
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + tokenExpiration))
                .signWith(privateKey)
                .compact();
    }

    public boolean isTokenValid(String token, String expectedUsername) {
        String username = extractUsername(token);
        return username.equals(expectedUsername) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }

    private Claims extractClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            throw new BadRequestException("Invalid JWT token");
        }
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public String refreshAccessToken(String refreshToken) {
        Claims claims = extractClaims(refreshToken);
        if (!claims.get(TOKEN_TYPE).equals("REFRESH_TOKEN")) {
            throw new BadRequestException("Invalid token type");
        }
        if (isTokenExpired(refreshToken)) {
            throw new BadRequestException("Refresh token expired");
        }
        return generateAccessToken(claims.getSubject());
    }
}
