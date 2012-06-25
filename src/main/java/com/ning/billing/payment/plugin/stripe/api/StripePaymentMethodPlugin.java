/*
 * Copyright 2010-2012 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.payment.plugin.stripe.api;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.payment.plugin.stripe.client.StripeObjectFactory;
import com.stripe.model.Card;

import java.util.List;
import java.util.Map;

public class StripePaymentMethodPlugin implements PaymentMethodPlugin {
    public static final String EXP_MONTH = "expMonth";
    public static final String EXP_YEAR = "expYear";
    public static final String LAST_4 = "last4";
    public static final String COUNTRY = "country";
    public static final String TYPE = "type";
    public static final String NAME = "name";
    public static final String NUMBER = "number";
    public static final String ADDRESS_LINE_1 = "addressLine1";
    public static final String ADDRESS_LINE_2 = "addressLine2";
    public static final String ADDRESS_ZIP = "addressZip";
    public static final String ADDRESS_STATE = "addressState";
    public static final String ADDRESS_COUNTRY = "addressCountry";
    public static final String ADDRESS_ZIP_CHECK = "addressZipCheck";
    public static final String ADDRESS_LINE_1_CHECK = "addressLine1Check";
    public static final String CVC_CHECK = "cvcCheck";
    public static final String FINGERPRINT = "fingerprint";

    // A single unique payment method is stored in Stripe
    private final boolean isDefaultPaymentMethod = true;
    private final Map<String, PaymentMethodKVInfo> properties;

    private final String externalPaymentMethodId;

    public StripePaymentMethodPlugin(final Map<String, PaymentMethodKVInfo> properties, final String externalPaymentMethodId) {
        this.properties = properties;
        this.externalPaymentMethodId = externalPaymentMethodId;
    }

    public StripePaymentMethodPlugin(final Card card) {
        this.externalPaymentMethodId = StripeObjectFactory.getExternalPaymentIdFromCard(card);

        final ImmutableMap.Builder<String, PaymentMethodKVInfo> builder = new ImmutableMap.Builder<String, PaymentMethodKVInfo>();
        builder.put(EXP_MONTH, new PaymentMethodKVInfo(EXP_MONTH, card.getExpMonth(), true));
        builder.put(EXP_YEAR, new PaymentMethodKVInfo(EXP_YEAR, card.getExpYear(), true));
        builder.put(LAST_4, new PaymentMethodKVInfo(LAST_4, card.getLast4(), true));
        builder.put(COUNTRY, new PaymentMethodKVInfo(COUNTRY, card.getCountry(), true));
        builder.put(TYPE, new PaymentMethodKVInfo(TYPE, card.getType(), true));
        builder.put(NAME, new PaymentMethodKVInfo(NAME, card.getName(), true));
        builder.put(ADDRESS_LINE_1, new PaymentMethodKVInfo(ADDRESS_LINE_1, card.getAddressLine1(), true));
        builder.put(ADDRESS_LINE_2, new PaymentMethodKVInfo(ADDRESS_LINE_2, card.getAddressLine2(), true));
        builder.put(ADDRESS_ZIP, new PaymentMethodKVInfo(ADDRESS_ZIP, card.getAddressZip(), true));
        builder.put(ADDRESS_STATE, new PaymentMethodKVInfo(ADDRESS_STATE, card.getAddressState(), true));
        builder.put(ADDRESS_COUNTRY, new PaymentMethodKVInfo(ADDRESS_COUNTRY, card.getAddressCountry(), true));
        builder.put(ADDRESS_ZIP_CHECK, new PaymentMethodKVInfo(ADDRESS_ZIP_CHECK, card.getAddressZipCheck(), true));
        builder.put(ADDRESS_LINE_1_CHECK, new PaymentMethodKVInfo(ADDRESS_LINE_1_CHECK, card.getAddressLine1Check(), true));
        builder.put(CVC_CHECK, new PaymentMethodKVInfo(CVC_CHECK, card.getCvcCheck(), true));
        builder.put(FINGERPRINT, new PaymentMethodKVInfo(FINGERPRINT, card.getFingerprint(), true));

        properties = builder.build();
    }

    @Override
    public String getExternalPaymentMethodId() {
        return externalPaymentMethodId;
    }

    @Override
    public boolean isDefaultPaymentMethod() {
        return isDefaultPaymentMethod;
    }

    @Override
    public List<PaymentMethodKVInfo> getProperties() {
        return ImmutableList.<PaymentMethodKVInfo>copyOf(properties.values());
    }

    @Override
    public String getValueString(final String key) {
        final PaymentMethodKVInfo paymentMethodKVInfo = properties.get(key);
        if (paymentMethodKVInfo == null || paymentMethodKVInfo.getValue() == null) {
            return null;
        } else {
            return paymentMethodKVInfo.getValue().toString();
        }
    }
}
