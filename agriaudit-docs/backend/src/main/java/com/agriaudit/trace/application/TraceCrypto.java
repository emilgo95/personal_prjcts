package com.agriaudit.trace.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Huellas SHA-512 y firmas HMAC-SHA-512 de la cadena. Todo en hexadecimal minúscula (128 caracteres). */
@Component
public class TraceCrypto {

    public static final String GENESIS_HASH = "0".repeat(128);

    private final byte[] key;

    public TraceCrypto(@Value("${agriaudit.trace.hmac-key}") String hmacKey) {
        this.key = hmacKey.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-512").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 no disponible", e);
        }
    }

    public String sign(String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key, "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA-512 no disponible", e);
        }
    }

    /** Texto canónico de una entrada: cada campo con su longitud, para que no haya ambigüedad entre campos. */
    public static String canonical(Object... fields) {
        StringBuilder sb = new StringBuilder();
        for (Object f : fields) {
            String s = f == null ? "" : String.valueOf(f);
            sb.append(s.length()).append(':').append(s).append('|');
        }
        return sb.toString();
    }
}
