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

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.clock.Clock;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Iterables;
import org.killbill.billing.plugin.stripe.dao.StripeDao;

import static org.killbill.billing.plugin.stripe.StripePaymentPluginApi.PROPERTY_FROM_HPP;
import static org.killbill.billing.plugin.stripe.StripePaymentPluginApi.PROPERTY_HPP_COMPLETION;

public class ExpiredPaymentPolicy {

    private final Clock clock;

    private final StripeConfigProperties stripeProperties;

    public ExpiredPaymentPolicy(final Clock clock, final StripeConfigProperties stripeProperties) {
        this.clock = clock;
        this.stripeProperties = stripeProperties;
    }

    public StripePaymentTransactionInfoPlugin isExpired(final List<PaymentTransactionInfoPlugin> paymentTransactions) {
        if (!containOnlyAuthsOrPurchases(paymentTransactions)) {
            return null;
        }

        final StripePaymentTransactionInfoPlugin transaction = (StripePaymentTransactionInfoPlugin) latestTransaction(paymentTransactions);
        if (transaction.getCreatedDate() == null) {
            return null;
        }

        if (transaction.getStatus() == PaymentPluginStatus.PENDING) {
            final DateTime expirationDate = expirationDateForInitialTransactionType(transaction);
            if (clock.getNow(expirationDate.getZone()).isAfter(expirationDate)) {
                return transaction;
            }
        }

        return null;
    }

    private PaymentTransactionInfoPlugin latestTransaction(final List<PaymentTransactionInfoPlugin> paymentTransactions) {
        return Iterables.getLast(paymentTransactions);
    }

    private boolean containOnlyAuthsOrPurchases(final List<PaymentTransactionInfoPlugin> transactions) {
        for (final PaymentTransactionInfoPlugin transaction : transactions) {
            if (transaction.getTransactionType() != TransactionType.AUTHORIZE &&
                transaction.getTransactionType() != TransactionType.PURCHASE) {
                return false;
            }
        }
        return true;
    }

    private DateTime expirationDateForInitialTransactionType(final StripePaymentTransactionInfoPlugin transaction) {
        if (transaction.getStripeResponseRecord() == null) {
            return transaction.getCreatedDate().plus(stripeProperties.getPendingPaymentExpirationPeriod(null));
        }

        final Map stripeResponseAdditionalData = StripeDao.fromAdditionalData(transaction.getStripeResponseRecord().getAdditionalData());

        if (is3ds(stripeResponseAdditionalData)) {
            return transaction.getCreatedDate().plus(stripeProperties.getPending3DsPaymentExpirationPeriod());
        } else if (isHppBuildFormTransaction(stripeResponseAdditionalData)) {
            return transaction.getCreatedDate().plus(stripeProperties.getPendingHppPaymentWithoutCompletionExpirationPeriod());
        }

        final String paymentMethod = getPaymentMethod(stripeResponseAdditionalData);
        return transaction.getCreatedDate().plus(stripeProperties.getPendingPaymentExpirationPeriod(paymentMethod));
    }

    private boolean isHppBuildFormTransaction(final Map stripeResponseAdditionalData) {
        return isHppPayment(stripeResponseAdditionalData) && !isHppCompletionTransaction(stripeResponseAdditionalData);
    }

    private boolean is3ds(final Map stripeResponseAdditionalData) {
        // See https://stripe.com/docs/payments/payment-intents/status
        return "requires_action".equals(stripeResponseAdditionalData.get("status"));
    }

    private boolean isHppCompletionTransaction(final Map stripeResponseAdditionalData) {
        return Boolean.valueOf(MoreObjects.firstNonNull(stripeResponseAdditionalData.get(PROPERTY_HPP_COMPLETION), false).toString());
    }

    private boolean isHppPayment(final Map stripeResponseAdditionalData) {
        return Boolean.valueOf(MoreObjects.firstNonNull(stripeResponseAdditionalData.get(PROPERTY_FROM_HPP), false).toString());
    }

    private String getPaymentMethod(final Map stripeResponseAdditionalData) {
        return (String) stripeResponseAdditionalData.get("last_charge_payment_method_type");
    }
}