package com.ledgerpay.security;

import com.ledgerpay.config.JwtProperties;
import com.ledgerpay.user.AppUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class JwtService {

    static final String INVALID_TOKEN = "Invalid or expired token.";

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final JwtProperties properties;
    private final Clock clock;
    private final JsonMapper jsonMapper;
    private final byte[] secret;

    public JwtService(JwtProperties properties, Clock clock, JsonMapper jsonMapper) {
        this.properties = properties;
        this.clock = clock;
        this.jsonMapper = jsonMapper;
        this.secret = properties.secret().getBytes(StandardCharsets.UTF_8);
    }

    public String issue(AppUser user) {
        Instant now = clock.instant();
        Instant expires = now.plus(properties.ttl());
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = encode(jsonMapper.writeValueAsString(java.util.Map.of(
                "sub", user.getId().toString(),
                "email", user.getEmail(),
                "iat", now.getEpochSecond(),
                "exp", expires.getEpochSecond(),
                "jti", UUID.randomUUID().toString())));
        String signingInput = header + "." + payload;
        return signingInput + "." + encode(sign(signingInput));
    }

    public long expiresInSeconds() {
        return properties.ttl().toSeconds();
    }

    public JwtPrincipal parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new InvalidJwtException(INVALID_TOKEN);
            }
            byte[] expected = sign(parts[0] + "." + parts[1]);
            byte[] actual = DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new InvalidJwtException(INVALID_TOKEN);
            }
            JsonNode claims = jsonMapper.readTree(DECODER.decode(parts[1]));
            JsonNode algorithm = jsonMapper.readTree(DECODER.decode(parts[0])).get("alg");
            if (algorithm == null || !"HS256".equals(algorithm.asString())) {
                throw new InvalidJwtException(INVALID_TOKEN);
            }
            JsonNode expiration = claims.get("exp");
            JsonNode subject = claims.get("sub");
            if (expiration == null || subject == null || !expiration.isNumber()) {
                throw new InvalidJwtException(INVALID_TOKEN);
            }
            if (expiration.asLong() <= clock.instant().getEpochSecond()) {
                throw new InvalidJwtException(INVALID_TOKEN);
            }
            String email = claims.path("email").asString("");
            return new JwtPrincipal(UUID.fromString(subject.asString()), email);
        } catch (InvalidJwtException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InvalidJwtException(INVALID_TOKEN, exception);
        }
    }

    private byte[] sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign authentication token", exception);
        }
    }

    private static String encode(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(byte[] value) {
        return ENCODER.encodeToString(value);
    }
}
