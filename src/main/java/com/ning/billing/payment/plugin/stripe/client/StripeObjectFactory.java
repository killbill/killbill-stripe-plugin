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

package com.ning.billing.payment.plugin.stripe.client;

import com.google.common.collect.ImmutableMap;
import com.ning.billing.account.api.Account;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.payment.plugin.stripe.api.StripePaymentMethodPlugin;
import com.stripe.model.Card;

import java.util.Map;

public class StripeObjectFactory {
    public static Map<String, Object> createCustomerDataFromKillbill(final Account killbillAccount) {
        final ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<String, Object>();

        builder.put("email", killbillAccount.getEmail());
        builder.put("description", killbillAccount.getExternalKey());

        return builder.build();
    }

    public static String getExternalPaymentIdFromCard(final Card card) {
        // Stripe doesn't expose it, set a dummy one
        return String.format("%s::1", card.hashCode());
    }

    public static Map<String, Object> createCardDataFromKillbill(final PaymentMethodPlugin paymentMethodPlugin) {
        final ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<String, Object>();

        final ImmutableMap.Builder<String, Object> cardData = new ImmutableMap.Builder<String, Object>();
        cardData.put("number", paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.NUMBER));
        cardData.put("exp_month", paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.EXP_MONTH));
        cardData.put("exp_year", paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.EXP_YEAR));
        cardData.put("cvc", paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.CVC_CHECK));
        cardData.put("name", paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.NAME));
        cardData.put("address_line1", paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.ADDRESS_LINE_1));
        cardData.put("address_line2", paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.ADDRESS_LINE_2));
        cardData.put("address_zip", paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.ADDRESS_ZIP));
        cardData.put("address_state", paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.ADDRESS_STATE));
        cardData.put("address_country", paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.ADDRESS_COUNTRY));

        builder.put("card", cardData);

        return builder.build();
    }

    public static Card createCardFromKillbill(final PaymentMethodPlugin paymentMethodPlugin) {
        final Card card = new Card();

        card.setExpMonth(Integer.valueOf(paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.EXP_MONTH)));
        card.setExpYear(Integer.valueOf(paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.EXP_YEAR)));
        card.setLast4(paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.LAST_4));
        card.setCountry(paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.COUNTRY));
        card.setType(paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.TYPE));
        card.setName(paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.NAME));
        card.setAddressLine1(paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.ADDRESS_LINE_1));
        card.setAddressLine2(paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.ADDRESS_LINE_2));
        card.setAddressZip(paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.ADDRESS_ZIP));
        card.setAddressState(paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.ADDRESS_STATE));
        card.setAddressCountry(paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.ADDRESS_COUNTRY));
        card.setAddressZipCheck(paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.ADDRESS_ZIP_CHECK));
        card.setAddressLine1Check(paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.ADDRESS_LINE_1_CHECK));
        card.setCvcCheck(paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.CVC_CHECK));
        card.setFingerprint(paymentMethodPlugin.getValueString(StripePaymentMethodPlugin.FINGERPRINT));

        return card;
    }
}
