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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Resolves config property values that may be plaintext or environment variable
 * references ({@code ${env:VAR_NAME}}).
 *
 * <ul>
 *   <li><b>Plaintext</b> — returned as-is (backward compatible)</li>
 *   <li><b>{@code ${env:VAR_NAME}}</b> — resolved via {@link System#getenv(String)}</li>
 * </ul>
 */
public class StripeConfigPropertyResolver {

    private static final Pattern ENV_PATTERN = Pattern.compile("^\\$\\{env:([^}]+)}$");

    /**
     * Resolve a configuration value. Supports plaintext and {@code ${env:VAR}}.
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

        return value;
    }

    // Package-private for test overriding
    static @Nullable String resolveEnvVar(final String name) {
        return System.getenv(name);
    }
}
