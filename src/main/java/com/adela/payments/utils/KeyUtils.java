package com.adela.payments.utils;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class KeyUtils {

    private KeyUtils() {
    }

    public static PrivateKey loadPrivateKey(String pemPath, String envValue) throws Exception {
        String source = (envValue != null && !envValue.isBlank())
                ? envValue
                : readKeyFromResource(pemPath);

        String key = source
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        System.out.println("DEBUG key length: " + key.length());
        System.out.println("DEBUG key content: " + key);

        byte[] decoded = Base64.getDecoder().decode(key);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    public static PublicKey loadPublicKey(String pemPath, String envValue) throws Exception {
        String source = (envValue != null && !envValue.isBlank())
                ? envValue
                : readKeyFromResource(pemPath);

        String key = source
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        System.out.println("DEBUG key length: " + key.length());
        System.out.println("DEBUG key content: " + key);

        byte[] decoded = Base64.getDecoder().decode(key);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePublic(keySpec);
    }

    private static String readKeyFromResource(String path) throws Exception {
        try (InputStream is = KeyUtils.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException("Key not found: " + path);
            }
            return new String(is.readAllBytes());
        }
    }

}
