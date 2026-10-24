package com.aetherauth.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AetherAuth's password and e-mail protection layer.
 *
 * <p>Passwords are never stored, not even as a plain hash. The pipeline is:</p>
 * <ol>
 *     <li>32 random salt bytes per account,</li>
 *     <li>PBKDF2-HMAC-SHA512 over the password (configurable iterations),</li>
 *     <li>the derived 64-byte value is encrypted with AES (GCM or CBC+HMAC),</li>
 *     <li>the AES key is derived from the server master secret and the account
 *     salt with HMAC-SHA256,</li>
 *     <li>the encoded blob is bound to its algorithm parameters via AAD.</li>
 * </ol>
 *
 * <p>This means a database leak alone is not enough to attack passwords: the
 * attacker also needs {@code secret.key}. AES is used as a second, per-record
 * encryption layer around the slow KDF; the KDF is what actually makes offline
 * guessing expensive.</p>
 */
public final class CryptoEngine {

    private static final String PREFIX = "aether";
    private static final int FORMAT = 1;
    private static final int DERIVED_BYTES = 64;

    private static final Base64.Encoder B64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();

    private final byte[] master;
    private final int iterations;
    private final String variant;
    private final int saltBytes;
    private final int keyBits;
    private final SecureRandom random = new SecureRandom();

    public CryptoEngine(byte[] master, int iterations, String variant, int saltBytes) {
        this.master = master.clone();
        this.iterations = iterations;
        this.variant = "CBC_HMAC".equalsIgnoreCase(variant) ? "CBC_HMAC" : "GCM";
        this.saltBytes = Math.max(16, saltBytes);
        this.keyBits = detectKeyBits();
    }

    public int iterations() {
        return iterations;
    }

    public String variant() {
        return variant;
    }

    public int keyBits() {
        return keyBits;
    }

    /** Hashes and encrypts a password. Safe to call from any worker thread. */
    public String hash(String password) throws CryptoException {
        byte[] salt = new byte[saltBytes];
        random.nextBytes(salt);
        byte[] derived = null;
        try {
            try {
                derived = pbkdf2(password, salt, iterations);
                return encode(derived, salt, iterations, variant, keyBits);
            } catch (CryptoException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new CryptoException("Şifre hashlenemedi.", exception);
            }
        } finally {
            wipe(derived);
        }
    }

    /**
     * Verifies a password against an encoded blob.
     *
     * <p>Never throws for malformed user input; inspect {@link Result#ok()}.</p>
     */
    public Result verify(String password, String encoded) {
        Parsed parsed = Parsed.parse(encoded);
        if (parsed == null) {
            return Result.failure("malformed");
        }
        byte[] derived = null;
        try {
            derived = pbkdf2(password, parsed.salt, parsed.iterations);
            byte[] decrypted = decode(parsed);
            if (decrypted == null) {
                return Result.failure("decrypt");
            }
            boolean matches = MessageDigest.isEqual(derived, decrypted);
            wipe(decrypted);
            if (!matches) {
                return Result.failure("mismatch");
            }
            boolean rehash = parsed.iterations < this.iterations
                    || !parsed.variant.equals(this.variant)
                    || parsed.keyBits != this.keyBits
                    || parsed.salt.length < this.saltBytes;
            return Result.success(rehash);
        } catch (Exception exception) {
            return Result.failure("crypto");
        } finally {
            wipe(derived);
        }
    }

    /** Burns the same amount of CPU as a real verification (unknown accounts). */
    public void burn(String password) {
        byte[] salt = new byte[saltBytes];
        random.nextBytes(salt);
        try {
            wipe(pbkdf2(password == null ? "" : password, salt, iterations));
        } catch (Exception ignored) {
            // Nothing else to do — this call is only here to equalise timings.
        }
    }

    public String encryptEmail(String email) throws CryptoException {
        if (email == null) {
            return "";
        }
        try {
            byte[] key = subkey(new byte[0], "email|v1", keyBits);
            byte[] iv = new byte[variant.equals("GCM") ? 12 : 16];
            random.nextBytes(iv);
            byte[] aad = aad("email", "v1", 1, keyBits);
            byte[] ciphertext;
            if (variant.equals("GCM")) {
                ciphertext = gcm(Cipher.ENCRYPT_MODE, key, iv, aad, email.getBytes(StandardCharsets.UTF_8));
            } else {
                ciphertext = cbc(Cipher.ENCRYPT_MODE, key, iv, email.getBytes(StandardCharsets.UTF_8));
            }
            return "e1:" + variant + ":" + keyBits + ":" + B64_ENCODER.encodeToString(iv) + ":"
                    + B64_ENCODER.encodeToString(ciphertext);
        } catch (Exception exception) {
            throw new CryptoException("E-posta şifrelenemedi.", exception);
        }
    }

    public String decryptEmail(String stored) throws CryptoException {
        if (stored == null || stored.isEmpty()) {
            return "";
        }
        if (stored.startsWith("p:")) {
            return stored.substring(2);
        }
        if (!stored.startsWith("e1:")) {
            return stored;
        }
        try {
            String[] parts = stored.split(":");
            if (parts.length != 5) {
                throw new CryptoException("E-posta formatı geçersiz.");
            }
            String storedVariant = parts[1];
            int storedBits = Integer.parseInt(parts[2]);
            byte[] iv = B64_DECODER.decode(parts[3]);
            byte[] ciphertext = B64_DECODER.decode(parts[4]);
            byte[] key = subkey(new byte[0], "email|v1", storedBits);
            byte[] plain;
            if (storedVariant.equals("GCM")) {
                plain = gcm(Cipher.DECRYPT_MODE, key, iv, aad("email", "v1", 1, storedBits), ciphertext);
            } else {
                plain = cbc(Cipher.DECRYPT_MODE, key, iv, ciphertext);
            }
            return new String(plain, StandardCharsets.UTF_8);
        } catch (CryptoException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CryptoException("E-posta çözülemedi.", exception);
        }
    }

    public String emailLookup(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(master, "HmacSHA256"));
            byte[] digest = mac.doFinal(("lookup|" + normalized).getBytes(StandardCharsets.UTF_8));
            return hex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("E-posta araması oluşturulamadı.", exception);
        }
    }

    private String encode(byte[] derived, byte[] salt, int usedIterations, String usedVariant, int usedBits)
            throws CryptoException {
        try {
            byte[] key = subkey(salt, "password|" + usedVariant, usedBits);
            byte[] iv = new byte[usedVariant.equals("GCM") ? 12 : 16];
            random.nextBytes(iv);
            byte[] aad = aad("password", usedVariant, usedIterations, usedBits);
            StringBuilder out = new StringBuilder(160);
            out.append('$').append(PREFIX).append('$').append(FORMAT).append('$')
                    .append(usedVariant).append('$').append(usedIterations).append('$').append(usedBits).append('$')
                    .append(B64_ENCODER.encodeToString(salt)).append('$')
                    .append(B64_ENCODER.encodeToString(iv));
            if (usedVariant.equals("GCM")) {
                byte[] ciphertext = gcm(Cipher.ENCRYPT_MODE, key, iv, aad, derived);
                out.append('$').append(B64_ENCODER.encodeToString(ciphertext));
            } else {
                byte[] ciphertext = cbc(Cipher.ENCRYPT_MODE, key, iv, derived);
                byte[] macKey = subkey(salt, "password|HMAC", 32);
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(macKey, "HmacSHA256"));
                mac.update(aad);
                mac.update(iv);
                byte[] tag = mac.doFinal(ciphertext);
                out.append('$').append(B64_ENCODER.encodeToString(ciphertext))
                        .append('$').append(B64_ENCODER.encodeToString(tag));
            }
            return out.toString();
        } catch (Exception exception) {
            throw new CryptoException("Şifre şifrelenemedi.", exception);
        }
    }

    private byte[] decode(Parsed parsed) throws Exception {
        byte[] key = subkey(parsed.salt, "password|" + parsed.variant, parsed.keyBits);
        byte[] aad = aad("password", parsed.variant, parsed.iterations, parsed.keyBits);
        if (parsed.variant.equals("GCM")) {
            return gcm(Cipher.DECRYPT_MODE, key, parsed.iv, aad, parsed.ciphertext);
        }
        byte[] macKey = subkey(parsed.salt, "password|HMAC", 32);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(macKey, "HmacSHA256"));
        mac.update(aad);
        mac.update(parsed.iv);
        byte[] expected = mac.doFinal(parsed.ciphertext);
        if (!MessageDigest.isEqual(expected, parsed.mac)) {
            return null;
        }
        return cbc(Cipher.DECRYPT_MODE, key, parsed.iv, parsed.ciphertext);
    }

    private byte[] pbkdf2(String password, byte[] salt, int usedIterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec((password == null ? "" : password).toCharArray(), salt,
                usedIterations, DERIVED_BYTES * 8);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
            return factory.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private byte[] subkey(byte[] salt, String info, int bits) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(master, "HmacSHA256"));
        mac.update(salt);
        mac.update(info.getBytes(StandardCharsets.UTF_8));
        byte[] full = mac.doFinal();
        int length = bits / 8;
        byte[] out = Arrays.copyOf(full, length);
        wipe(full);
        return out;
    }

    private byte[] gcm(int mode, byte[] key, byte[] iv, byte[] aad, byte[] input) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad);
        return cipher.doFinal(input);
    }

    private byte[] cbc(int mode, byte[] key, byte[] iv, byte[] input) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return cipher.doFinal(input);
    }

    private byte[] aad(String purpose, String usedVariant, int usedIterations, int usedBits) {
        String value = "AetherAuth|" + FORMAT + "|" + purpose + "|" + usedVariant + "|" + usedIterations + "|" + usedBits;
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private int detectKeyBits() {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(new byte[32], "AES"),
                    new GCMParameterSpec(128, new byte[12]));
            return 256;
        } catch (Exception ignored) {
            return 128;
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            out.append(String.format("%02x", value & 0xFF));
        }
        return out.toString();
    }

    private static void wipe(byte[] bytes) {
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return "-";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            if (email.length() <= 2) {
                return "***";
            }
            return email.charAt(0) + "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 1) {
            return "*" + domain;
        }
        return local.charAt(0) + "***" + domain;
    }

    public static final class Result {
        private final boolean ok;
        private final boolean needsRehash;
        private final String reason;

        private Result(boolean ok, boolean needsRehash, String reason) {
            this.ok = ok;
            this.needsRehash = needsRehash;
            this.reason = reason;
        }

        static Result success(boolean needsRehash) {
            return new Result(true, needsRehash, null);
        }

        static Result failure(String reason) {
            return new Result(false, false, reason);
        }

        public boolean ok() {
            return ok;
        }

        public boolean needsRehash() {
            return needsRehash;
        }

        public String reason() {
            return reason;
        }
    }

    private static final class Parsed {
        String variant;
        int iterations;
        int keyBits;
        byte[] salt;
        byte[] iv;
        byte[] ciphertext;
        byte[] mac;

        static Parsed parse(String encoded) {
            if (encoded == null || encoded.isEmpty()) {
                return null;
            }
            try {
                String[] parts = encoded.split("\\$");
                // ["", "aether", "1", variant, iterations, keyBits, salt, iv, ciphertext, (mac)]
                if (parts.length < 9 || !PREFIX.equals(parts[1]) || !"1".equals(parts[2])) {
                    return null;
                }
                Parsed parsed = new Parsed();
                parsed.variant = parts[3];
                if (!parsed.variant.equals("GCM") && !parsed.variant.equals("CBC_HMAC")) {
                    return null;
                }
                parsed.iterations = Integer.parseInt(parts[4]);
                parsed.keyBits = Integer.parseInt(parts[5]);
                if (parsed.iterations < 1000 || parsed.iterations > 5000000) {
                    return null;
                }
                if (parsed.keyBits != 128 && parsed.keyBits != 256) {
                    return null;
                }
                parsed.salt = B64_DECODER.decode(parts[6]);
                parsed.iv = B64_DECODER.decode(parts[7]);
                parsed.ciphertext = B64_DECODER.decode(parts[8]);
                if (parts.length >= 10) {
                    parsed.mac = B64_DECODER.decode(parts[9]);
                }
                if (parsed.salt.length < 8 || parsed.iv.length < 8) {
                    return null;
                }
                if (parsed.variant.equals("CBC_HMAC") && (parsed.mac == null || parsed.mac.length < 16)) {
                    return null;
                }
                return parsed;
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
