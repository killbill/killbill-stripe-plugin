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

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

public class TestStripeConfigPropertyResolver {

    private static final String SYSTEM_PROPERTY_KEY = "org.killbill.billing.plugin.stripe.encryptionKey";

    @AfterMethod(groups = "fast")
    public void tearDown() {
        System.clearProperty(SYSTEM_PROPERTY_KEY);
    }

    @Test(groups = "fast")
    public void testPlaintextPassthrough() {
        Assert.assertEquals(StripeConfigPropertyResolver.resolve("sk_test_XXX"), "sk_test_XXX");
        Assert.assertEquals(StripeConfigPropertyResolver.resolve("plain value"), "plain value");
        Assert.assertEquals(StripeConfigPropertyResolver.resolve(""), "");
    }

    @Test(groups = "fast")
    public void testNullPassthrough() {
        Assert.assertNull(StripeConfigPropertyResolver.resolve(null));
    }

    @Test(groups = "fast")
    public void testEncryptDecryptRoundTrip() {
        final byte[] key = generateKey();
        System.setProperty(SYSTEM_PROPERTY_KEY, Base64.getEncoder().encodeToString(key));

        final String original = "sk_live_1234567890abcdef";
        final String encrypted = StripeConfigPropertyResolver.encrypt(original, key);
        final String resolved = StripeConfigPropertyResolver.resolve("ENC(" + encrypted + ")");

        Assert.assertEquals(resolved, original);
    }

    @Test(groups = "fast")
    public void testEncryptionProducesDifferentCiphertexts() {
        final byte[] key = generateKey();

        final String plaintext = "sk_test_repeatable";
        final String enc1 = StripeConfigPropertyResolver.encrypt(plaintext, key);
        final String enc2 = StripeConfigPropertyResolver.encrypt(plaintext, key);

        Assert.assertNotEquals(enc1, enc2, "Random IVs should produce different ciphertexts");
    }

    @Test(groups = "fast", expectedExceptions = IllegalStateException.class,
          expectedExceptionsMessageRegExp = ".*Encryption key not configured.*")
    public void testDecryptWithoutKeyFails() {
        // Ensure no key is set
        System.clearProperty(SYSTEM_PROPERTY_KEY);
        // Use a valid base64 string that looks like ENC(...) — the key lookup should fail first
        StripeConfigPropertyResolver.resolve("ENC(AQIDBAUGBwgJCgsMDQ4PEA==)");
    }

    @Test(groups = "fast", expectedExceptions = IllegalStateException.class,
          expectedExceptionsMessageRegExp = ".*Environment variable.*is not set.*")
    public void testEnvVarNotSetFails() {
        // Use a var name that is extremely unlikely to exist
        StripeConfigPropertyResolver.resolve("${env:KILLBILL_STRIPE_TEST_NONEXISTENT_VAR_12345}");
    }

    @Test(groups = "fast")
    public void testBackwardCompatibilityWithStripeConfigProperties() {
        final Properties properties = new Properties();
        properties.setProperty("org.killbill.billing.plugin.stripe.apiKey", "sk_test_plaintext");
        properties.setProperty("org.killbill.billing.plugin.stripe.publicKey", "pk_test_plaintext");

        final StripeConfigProperties config = new StripeConfigProperties(properties, "");

        Assert.assertEquals(config.getApiKey(), "sk_test_plaintext");
        Assert.assertEquals(config.getPublicKey(), "pk_test_plaintext");
    }

    @Test(groups = "fast")
    public void testEncryptedValuesInStripeConfigProperties() {
        final byte[] key = generateKey();
        System.setProperty(SYSTEM_PROPERTY_KEY, Base64.getEncoder().encodeToString(key));

        final String apiKeyPlain = "sk_test_encrypted_api_key";
        final String publicKeyPlain = "pk_test_encrypted_public_key";

        final Properties properties = new Properties();
        properties.setProperty("org.killbill.billing.plugin.stripe.apiKey",
                               "ENC(" + StripeConfigPropertyResolver.encrypt(apiKeyPlain, key) + ")");
        properties.setProperty("org.killbill.billing.plugin.stripe.publicKey",
                               "ENC(" + StripeConfigPropertyResolver.encrypt(publicKeyPlain, key) + ")");

        final StripeConfigProperties config = new StripeConfigProperties(properties, "");

        Assert.assertEquals(config.getApiKey(), apiKeyPlain);
        Assert.assertEquals(config.getPublicKey(), publicKeyPlain);
    }

    private static byte[] generateKey() {
        final byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }
}
