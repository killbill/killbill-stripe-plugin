/*
 * Copyright 2014-2019 The Billing Project, LLC
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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import javax.annotation.Nullable;

import org.joda.time.Period;

import com.google.common.base.Ascii;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;

public class StripeConfigProperties {

    private static final String PROPERTY_PREFIX = "org.killbill.billing.plugin.stripe.";

    public static final String DEFAULT_PENDING_PAYMENT_EXPIRATION_PERIOD = "P3d";
    public static final String DEFAULT_PENDING_3DS_PAYMENT_EXPIRATION_PERIOD = "PT3h";
    public static final String DEFAULT_PENDING_HPP_PAYMENT_WITHOUT_COMPLETION_EXPIRATION_PERIOD = "PT1h";

    private static final String ENTRY_DELIMITER = "|";
    private static final String KEY_VALUE_DELIMITER = "#";
    private static final String DEFAULT_CONNECTION_TIMEOUT = "30000";
    private static final String DEFAULT_READ_TIMEOUT = "60000";

    private final String region;
    private final String apiKey;
    private final String connectionTimeout;
    private final String readTimeout;
    private final Period pendingPaymentExpirationPeriod;
    private final Period pendingHppPaymentWithoutCompletionExpirationPeriod;
    private final Period pending3DsPaymentExpirationPeriod;
    private final Map<String, Period> paymentMethodToExpirationPeriod = new LinkedHashMap<String, Period>();
    private final String chargeDescription;
    private final String chargeStatementDescriptor;

    public StripeConfigProperties(final Properties properties, final String region) {
        this.region = region;
        this.apiKey = properties.getProperty(PROPERTY_PREFIX + "apiKey");
        this.connectionTimeout = properties.getProperty(PROPERTY_PREFIX + "connectionTimeout", DEFAULT_CONNECTION_TIMEOUT);
        this.readTimeout = properties.getProperty(PROPERTY_PREFIX + "readTimeout", DEFAULT_READ_TIMEOUT);
        this.pendingPaymentExpirationPeriod = readPendingExpirationProperty(properties);
        this.pending3DsPaymentExpirationPeriod = read3DsPendingExpirationProperty(properties);
        this.pendingHppPaymentWithoutCompletionExpirationPeriod = readPendingHppPaymentWithoutCompletionExpirationPeriod(properties);
        this.chargeDescription = Ascii.truncate(MoreObjects.firstNonNull(properties.getProperty(PROPERTY_PREFIX + "chargeDescription"), "Kill Bill charge"), 22, "...");
        this.chargeStatementDescriptor = Ascii.truncate(MoreObjects.firstNonNull(properties.getProperty(PROPERTY_PREFIX + "chargeStatementDescriptor"), "Kill Bill charge"), 22, "...");
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getConnectionTimeout() {
        return connectionTimeout;
    }

    public String getReadTimeout() {
        return readTimeout;
    }

    public String getChargeDescription() {
        return chargeDescription;
    }

    public String getChargeStatementDescriptor() {
        return chargeStatementDescriptor;
    }

    public Period getPendingPaymentExpirationPeriod(@Nullable final String paymentMethod) {
        if (paymentMethod != null && paymentMethodToExpirationPeriod.get(paymentMethod.toLowerCase()) != null) {
            return paymentMethodToExpirationPeriod.get(paymentMethod.toLowerCase());
        } else {
            return pendingPaymentExpirationPeriod;
        }
    }

    public Period getPending3DsPaymentExpirationPeriod() {
        return pending3DsPaymentExpirationPeriod;
    }

    public Period getPendingHppPaymentWithoutCompletionExpirationPeriod() {
        return pendingHppPaymentWithoutCompletionExpirationPeriod;
    }

    private Period readPendingExpirationProperty(final Properties properties) {
        final String pendingExpirationPeriods = properties.getProperty(PROPERTY_PREFIX + "pendingPaymentExpirationPeriod");
        final Map<String, String> paymentMethodToExpirationPeriodString = new HashMap<String, String>();
        refillMap(paymentMethodToExpirationPeriodString, pendingExpirationPeriods);
        // No per-payment method override, just a global setting
        if (pendingExpirationPeriods != null && paymentMethodToExpirationPeriodString.isEmpty()) {
            try {
                return Period.parse(pendingExpirationPeriods);
            } catch (final IllegalArgumentException e) { /* Ignore */ }
        }

        // User has defined per-payment method overrides
        for (final String paymentMethod : paymentMethodToExpirationPeriodString.keySet()) {
            try {
                paymentMethodToExpirationPeriod.put(paymentMethod.toLowerCase(), Period.parse(paymentMethodToExpirationPeriodString.get(paymentMethod)));
            } catch (final IllegalArgumentException e) { /* Ignore */ }
        }

        return Period.parse(DEFAULT_PENDING_PAYMENT_EXPIRATION_PERIOD);
    }

    private Period read3DsPendingExpirationProperty(final Properties properties) {
        final String value = properties.getProperty(PROPERTY_PREFIX + "pending3DsPaymentExpirationPeriod");
        if (value != null) {
            try {
                return Period.parse(value);
            } catch (IllegalArgumentException e) { /* Ignore */ }
        }

        return Period.parse(DEFAULT_PENDING_3DS_PAYMENT_EXPIRATION_PERIOD);
    }

    private Period readPendingHppPaymentWithoutCompletionExpirationPeriod(final Properties properties) {
        final String value = properties.getProperty(PROPERTY_PREFIX + "pendingHppPaymentWithoutCompletionExpirationPeriod");
        if (value != null) {
            try {
                return Period.parse(value);
            } catch (final IllegalArgumentException e) { /* Ignore */ }
        }

        return Period.parse(DEFAULT_PENDING_HPP_PAYMENT_WITHOUT_COMPLETION_EXPIRATION_PERIOD);
    }

    private synchronized void refillMap(final Map<String, String> map, final String stringToSplit) {
        map.clear();
        if (!Strings.isNullOrEmpty(stringToSplit)) {
            for (final String entry : stringToSplit.split("\\" + ENTRY_DELIMITER)) {
                final String[] split = entry.split(KEY_VALUE_DELIMITER);
                if (split.length > 1) {
                    map.put(split[0], split[1]);
                }
            }
        }
    }
}
