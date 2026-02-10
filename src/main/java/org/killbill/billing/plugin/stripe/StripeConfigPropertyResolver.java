/*
 * Copyright 2020-2020 Equinix, Inc
 * Copyright 2014-2020 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.killbill.billing.plugin.stripe;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Resolves config property values that may be plaintext, encrypted ({@code ENC(base64)}),
 * or environment variable references ({@code ${env:VAR_NAME}}).
 *
 * <ul>
 *   <li><b>Plaintext</b> — returned as-is (backward compatible)</li>
 *   <li><b>{@code ENC(base64)}</b> — decrypted with AES-256-GCM using a key from
 *       system property {@code org.killbill.billing.plugin.stripe.encryptionKey}
 *       or env var {@code KILLBILL_STRIPE_ENCRYPTION_KEY} (Base64-encoded 256-bit key)</li>
 *   <li><b>{@code ${env:VAR_NAME}}</b> — resolved via {@link System#getenv(String)}</li>
 * </ul>
 */
public class StripeConfigPropertyResolver {

    private static final Pattern ENC_PATTERN = Pattern.compile("^ENC\\(([A-Za-z0-9+/=]+)\\)$");
    private static final Pattern ENV_PATTERN = Pattern.compile("^\\$\\{env:([^}]+)}$");

    private static final String SYSTEM_PROPERTY_KEY = "org.killbill.billing.plugin.stripe.encryptionKey";
    private static final String ENV_VAR_KEY = "KILLBILL_STRIPE_ENCRYPTION_KEY";

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    /**
     * Resolve a configuration value. Supports plaintext, {@code ENC(base64)}, and {@code ${env:VAR}}.
     *
     * @param value the raw property value (may be {@code null})
     * @return the resolved plaintext value, or {@code null} if input is {@code null}
     */
    public static @Nullable String resolve(@Nullable final String value) {
        if (value == null) {
            return null;
        }

        final Matcher envMatcher = ENV_PATTERN.matcher(value);
        if (envMatcher.matches()) {
            final String varName = envMatcher.group(1);
            final String envValue = resolveEnvVar(varName);
            if (envValue == null) {
                throw new IllegalStateException("Environment variable '" + varName + "' is not set");
            }
            return envValue;
        }

        final Matcher encMatcher = ENC_PATTERN.matcher(value);
        if (encMatcher.matches()) {
            return decrypt(encMatcher.group(1));
        }

        return value;
    }

    /**
     * Encrypt a plaintext value with AES-256-GCM. The returned Base64 string contains
     * the 12-byte IV prepended to the ciphertext and can be wrapped in {@code ENC(...)}.
     *
     * @param plaintext the value to encrypt
     * @param key       a 256-bit (32-byte) AES key
     * @return Base64-encoded IV + ciphertext
     */
    public static String encrypt(final String plaintext, final byte[] key) {
        try {
            final byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            final Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            final byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            final ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (final Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    private static String decrypt(final String base64Ciphertext) {
        try {
            final byte[] key = getEncryptionKey();
            final byte[] decoded = Base64.getDecoder().decode(base64Ciphertext);

            final byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(decoded, 0, iv, 0, GCM_IV_LENGTH);

            final Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            final byte[] plaintext = cipher.doFinal(decoded, GCM_IV_LENGTH, decoded.length - GCM_IV_LENGTH);

            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (final IllegalStateException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }

    private static byte[] getEncryptionKey() {
        String keyBase64 = System.getProperty(SYSTEM_PROPERTY_KEY);
        if (keyBase64 == null || keyBase64.isEmpty()) {
            keyBase64 = System.getenv(ENV_VAR_KEY);
        }
        if (keyBase64 == null || keyBase64.isEmpty()) {
            throw new IllegalStateException(
                    "Encryption key not configured. Set system property '" + SYSTEM_PROPERTY_KEY +
                    "' or environment variable '" + ENV_VAR_KEY + "' to a Base64-encoded 256-bit key.");
        }
        return Base64.getDecoder().decode(keyBase64);
    }

    // Package-private for test overriding
    static @Nullable String resolveEnvVar(final String name) {
        return System.getenv(name);
    }
}
