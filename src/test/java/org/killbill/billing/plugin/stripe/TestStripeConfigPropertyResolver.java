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

import java.util.Properties;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestStripeConfigPropertyResolver {

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
    public void testEnvVarResolution() {
        // HOME is always set on Unix/macOS; use PATH as fallback
        final String varName = System.getenv("HOME") != null ? "HOME" : "PATH";
        final String expected = System.getenv(varName);
        Assert.assertNotNull(expected, varName + " should be set in the test environment");
        Assert.assertEquals(StripeConfigPropertyResolver.resolve("${env:" + varName + "}"), expected);
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
}
