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
import com.ning.billing.account.api.Account;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.payment.plugin.stripe.client.StripeClient;
import com.ning.billing.payment.plugin.stripe.client.StripeObjectFactory;
import com.stripe.model.Card;
import com.stripe.model.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class StripePaymentPluginApi implements PaymentPluginApi {
    private static final Logger log = LoggerFactory.getLogger(StripePaymentPluginApi.class);

    private final String instanceName;
    private final StripeClient client;

    public StripePaymentPluginApi(final String instanceName, final StripeClient client) {
        this.instanceName = instanceName;
        this.client = client;
    }

    @Override
    public String getName() {
        return instanceName;
    }

    @Override
    public PaymentInfoPlugin processPayment(final String externalAccountKey, final UUID paymentId, final BigDecimal amount) throws PaymentPluginApiException {
        return null;
    }

    @Override
    public PaymentInfoPlugin getPaymentInfo(final UUID paymentId) throws PaymentPluginApiException {
        return null;
    }

    @Override
    public List<PaymentInfoPlugin> processRefund(final Account account) throws PaymentPluginApiException {
        return null;
    }

    @Override
    public String createPaymentProviderAccount(final Account account) throws PaymentPluginApiException {
        final Customer customer = client.createCustomer(StripeObjectFactory.createCustomerDataFromKillbill(account));
        if (customer != null) {
            return customer.getId();
        } else {
            log.warn("Unable to create Stripe account for account key {}", account.getExternalKey());
            return null;
        }
    }

    @Override
    public List<PaymentMethodPlugin> getPaymentMethodDetails(final String accountKey) throws PaymentPluginApiException {
        // Stripe supports a single active card per customer
        final Card card = client.getActiveCard(accountKey);
        return ImmutableList.<PaymentMethodPlugin>of(new StripePaymentMethodPlugin(card));
    }

    @Override
    public PaymentMethodPlugin getPaymentMethodDetail(final String accountKey, final String externalPaymentMethodId) throws PaymentPluginApiException {
        return getPaymentMethodDetails(accountKey).get(0);
    }

    @Override
    public String addPaymentMethod(final String accountKey, final PaymentMethodPlugin paymentMethodPlugin, final boolean setDefault) throws PaymentPluginApiException {
        final Card card = client.createOrUpdateCard(accountKey, StripeObjectFactory.createCardDataFromKillbill(paymentMethodPlugin));
        if (card != null) {
            return StripeObjectFactory.getExternalPaymentIdFromCard(card);
        } else {
            log.warn("Unable to add a Stripe payment method for account key {}", accountKey);
            return null;
        }
    }

    @Override
    public void updatePaymentMethod(final String accountKey, final PaymentMethodPlugin paymentMethodPlugin) throws PaymentPluginApiException {
        addPaymentMethod(accountKey, paymentMethodPlugin, true);
    }

    @Override
    public void deletePaymentMethod(final String accountKey, final String externalPaymentMethodId) throws PaymentPluginApiException {
        // TODO - couldn't find a way to unset the active card on the customer object
        throw new UnsupportedOperationException(String.format("Cannot unset an active card on customer %s", accountKey));
    }

    @Override
    public void setDefaultPaymentMethod(final String accountKey, final String externalPaymentId) throws PaymentPluginApiException {
        // No-op
    }
}
