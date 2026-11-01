package com.xinji.erp.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${xinji.jwt.secret}")
    private String secret;

    @Value("${xinji.jwt.expire-minutes:720}")
    private long expireMinutes;

    private Key key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String create(UserPrincipal p) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireMinutes * 60_000L);
        return Jwts.builder()
                .setSubject(String.valueOf(p.getUserId()))
                .claim("username", p.getUsername())
                .claim("userType", p.getUserType())
                .claim("role", p.getRole())
                .claim("customerId", p.getCustomerId())
                .claim("displayName", p.getDisplayName())
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public UserPrincipal parse(String token) {
        Claims c = Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody();
        UserPrincipal p = new UserPrincipal();
        p.setUserId(Long.valueOf(c.getSubject()));
        p.setUsername(c.get("username", String.class));
        p.setUserType(c.get("userType", String.class));
        p.setRole(c.get("role", String.class));
        Object cid = c.get("customerId");
        if (cid instanceof Number) {
            p.setCustomerId(((Number) cid).longValue());
        }
        p.setDisplayName(c.get("displayName", String.class));
        return p;
    }

    public long getExpireMinutes() {
        return expireMinutes;
    }
}
