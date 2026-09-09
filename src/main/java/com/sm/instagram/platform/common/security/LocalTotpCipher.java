package com.sm.instagram.platform.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM with a key from configuration, for runs that have no Cloud KMS.
 *
 * <p>TOTP secrets are encrypted at rest with Cloud KMS. A test run on a public CI runner has no
 * Google credential and therefore no KMS, and {@link KMSValidationService} throws rather than
 * quietly weakening anything - which is right in production and useless in a test. This is the
 * other implementation: a real AEAD cipher whose key comes from {@code totp.local-key}, used only
 * when {@code gcp.kms.enabled} is false.
 *
 * <p>It is not a KMS substitute and does not pretend to be one. The key sits in configuration, so
 * anything encrypted with it is only as protected as that file. Production keeps
 * {@code gcp.kms.enabled=true} and never reaches this class; the boot log says which one is live.
 *
 * <p>Format: {@code v1:<base64 of 12-byte IV || ciphertext || 16-byte tag>}. The version prefix
 * exists so a ciphertext written by this class can never be mistaken for a KMS one, in either
 * direction - a run that switches back to KMS will fail loudly on an old value instead of
 * returning rubbish.
 */
@Slf4j
@Component
public class LocalTotpCipher {

    private static final String PREFIX = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public LocalTotpCipher(@Value("${totp.local-key:}") String configuredKey) {
        // SHA-256 of whatever is configured, so any length of input yields a valid 256-bit key. An empty
        // value is allowed: the bean still constructs, and any attempt to use it fails at encrypt time
        // rather than at boot, so a production context that never touches TOTP is unaffected.
        byte[] material = configuredKey == null ? new byte[0] : configuredKey.getBytes(StandardCharsets.UTF_8);
        this.key = material.length == 0 ? null : new SecretKeySpec(sha256(material), "AES");
    }

    public boolean isConfigured() {
        return key != null;
    }

    public String encrypt(String plaintext) {
        requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(sealed, 0, out, iv.length, sealed.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("local TOTP encryption failed", e);
        }
    }

    public String decrypt(String ciphertext) {
        requireKey();
        if (ciphertext == null || !ciphertext.startsWith(PREFIX)) {
            throw new IllegalStateException(
                    "this ciphertext was not written by the local cipher (no " + PREFIX + " prefix): "
                            + "it is a KMS value, and this run has no KMS");
        }
        try {
            byte[] raw = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(raw, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(raw, IV_BYTES, raw.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("local TOTP decryption failed", e);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException(
                    "gcp.kms.enabled is false and totp.local-key is empty: there is nothing to encrypt with");
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
