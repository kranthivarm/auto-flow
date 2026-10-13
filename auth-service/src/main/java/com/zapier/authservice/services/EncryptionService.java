package com.zapier.authservice.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class EncryptionService {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    @Value("${google.calendar.token.encryption.key}")
    private String encryptionKey;

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encryptedBase64) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            byte[] ciphertext = new byte[combined.length - iv.length];
            System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));

            return new String(cipher.doFinal(ciphertext));
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    // SHA-256 hash of the key string → always exactly 32 bytes for AES-256
    private SecretKeySpec deriveKey() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(encryptionKey.getBytes());
        return new SecretKeySpec(hash, "AES");
    }
}
