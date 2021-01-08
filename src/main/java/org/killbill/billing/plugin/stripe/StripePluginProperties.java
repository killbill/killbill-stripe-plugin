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

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.stripe.model.BankAccount;
import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.PaymentMethod.Card;
import com.stripe.model.PaymentSource;
import com.stripe.model.Source;
import com.stripe.model.Source.AchDebit;
import com.stripe.model.Token;
import com.stripe.model.checkout.Session;

// Stripe .toJson() is definitively not GDPR-friendly...
public abstract class StripePluginProperties {

    public static Map<String, Object> toAdditionalDataMap(final PaymentSource stripePaymentSource) {
        final Map<String, Object> additionalDataMap = new HashMap<String, Object>();

        if (stripePaymentSource instanceof com.stripe.model.Card) {
            final com.stripe.model.Card card = (com.stripe.model.Card) stripePaymentSource;
            additionalDataMap.put("card_brand", card.getBrand());
            additionalDataMap.put("card_address_line1_check", card.getAddressLine1Check());
            additionalDataMap.put("card_address_postal_code_check", card.getAddressZipCheck());
            additionalDataMap.put("card_cvc_check", card.getCvcCheck());
            additionalDataMap.put("card_country", card.getCountry());
            additionalDataMap.put("card_description", card.getName());
            additionalDataMap.put("card_exp_month", card.getExpMonth());
            additionalDataMap.put("card_exp_year", card.getExpYear());
            additionalDataMap.put("card_fingerprint", card.getFingerprint());
            additionalDataMap.put("card_funding", card.getFunding());
            additionalDataMap.put("card_last4", card.getLast4());
        } else if (stripePaymentSource instanceof Source) {
            final Source stripeSource = (Source) stripePaymentSource;
            final Source.Card card = stripeSource.getCard();
            if (card != null) {
                additionalDataMap.put("card_brand", card.getBrand());
                additionalDataMap.put("card_address_line1_check", card.getAddressLine1Check());
                additionalDataMap.put("card_address_postal_code_check", card.getAddressZipCheck());
                additionalDataMap.put("card_cvc_check", card.getCvcCheck());
                additionalDataMap.put("card_country", card.getCountry());
                additionalDataMap.put("card_description", card.getName());
                additionalDataMap.put("card_exp_month", card.getExpMonth());
                additionalDataMap.put("card_exp_year", card.getExpYear());
                additionalDataMap.put("card_fingerprint", card.getFingerprint());
                additionalDataMap.put("card_funding", card.getFunding());
                additionalDataMap.put("card_last4", card.getLast4());
                additionalDataMap.put("card_three_d_secure_usage_support", card.getThreeDSecure());
            }
            final AchDebit achDebit = stripeSource.getAchDebit();
            if (achDebit != null) {
                additionalDataMap.put("ach_debit_bank_name", achDebit.getBankName());
                additionalDataMap.put("ach_debit_country", achDebit.getCountry());
                additionalDataMap.put("ach_debit_fingerprint", achDebit.getFingerprint());
                additionalDataMap.put("ach_debit_last4", achDebit.getLast4());
                additionalDataMap.put("ach_debit_routing_number", achDebit.getRoutingNumber());
                additionalDataMap.put("ach_debit_type", achDebit.getType());
            }
            additionalDataMap.put("created", stripeSource.getCreated());
            additionalDataMap.put("customer_id", stripeSource.getCustomer());
            additionalDataMap.put("id", stripeSource.getId());
            additionalDataMap.put("livemode", stripeSource.getLivemode());
            additionalDataMap.put("metadata", stripeSource.getMetadata());
            additionalDataMap.put("object", stripeSource.getObject());
            additionalDataMap.put("type", stripeSource.getType());
        } else if (stripePaymentSource instanceof BankAccount) {
            final BankAccount stripeBankAccount = (BankAccount) stripePaymentSource;
            additionalDataMap.put("account_holder_type", stripeBankAccount.getAccountHolderType());
            additionalDataMap.put("bank_name", stripeBankAccount.getBankName());
            additionalDataMap.put("country", stripeBankAccount.getCountry());
            additionalDataMap.put("currency", stripeBankAccount.getCurrency());
            additionalDataMap.put("fingerprint", stripeBankAccount.getFingerprint());
            additionalDataMap.put("last4", stripeBankAccount.getLast4());
            additionalDataMap.put("routing_number", stripeBankAccount.getRoutingNumber());
            additionalDataMap.put("status", stripeBankAccount.getStatus());
            additionalDataMap.put("customer_id", stripeBankAccount.getCustomer());
            additionalDataMap.put("id", stripeBankAccount.getId());
            additionalDataMap.put("metadata", stripeBankAccount.getMetadata());
            additionalDataMap.put("object", stripeBankAccount.getObject());
        } else {
            throw new UnsupportedOperationException("Not yet supported: " + stripePaymentSource);
        }

        return additionalDataMap;
    }

    public static Map<String, Object> toAdditionalDataMap(final Token token) {
        if (token.getCard() != null) {
            return toAdditionalDataMap(token.getCard());
        } else if (token.getBankAccount() != null) {
            return toAdditionalDataMap(token.getBankAccount());
        } else {
            throw new UnsupportedOperationException("Not yet supported: " + token);
        }
    }

    public static Map<String, Object> toAdditionalDataMap(final PaymentMethod stripePaymentMethod) {
        final Map<String, Object> additionalDataMap = new HashMap<String, Object>();

        final Card card = stripePaymentMethod.getCard();
        if (card != null) {
            additionalDataMap.put("card_brand", card.getBrand());
            if (card.getChecks() != null) {
                additionalDataMap.put("card_address_line1_check", card.getChecks().getAddressLine1Check());
                additionalDataMap.put("card_address_postal_code_check", card.getChecks().getAddressPostalCodeCheck());
                additionalDataMap.put("card_cvc_check", card.getChecks().getCvcCheck());
            }
            additionalDataMap.put("card_country", card.getCountry());
            additionalDataMap.put("card_description", card.getDescription());
            additionalDataMap.put("card_exp_month", card.getExpMonth());
            additionalDataMap.put("card_exp_year", card.getExpYear());
            additionalDataMap.put("card_fingerprint", card.getFingerprint());
            additionalDataMap.put("card_funding", card.getFunding());
            additionalDataMap.put("card_iin", card.getIin());
            additionalDataMap.put("card_issuer", card.getIssuer());
            additionalDataMap.put("card_last4", card.getLast4());
            if (card.getThreeDSecureUsage() != null) {
                additionalDataMap.put("card_three_d_secure_usage_support", card.getThreeDSecureUsage().getSupported());
            }
            if (card.getWallet() != null) {
                additionalDataMap.put("card_wallet_type", card.getWallet().getType());
            }
        }
        additionalDataMap.put("created", stripePaymentMethod.getCreated());
        additionalDataMap.put("customer_id", stripePaymentMethod.getCustomer());
        additionalDataMap.put("id", stripePaymentMethod.getId());
        additionalDataMap.put("livemode", stripePaymentMethod.getLivemode());
        additionalDataMap.put("metadata", stripePaymentMethod.getMetadata());
        additionalDataMap.put("object", stripePaymentMethod.getObject());
        additionalDataMap.put("type", stripePaymentMethod.getType());

        return additionalDataMap;
    }

    public static Map<String, Object> toAdditionalDataMap(final PaymentIntent stripePaymentIntent) {
        final Map<String, Object> additionalDataMap = new HashMap<String, Object>();

        additionalDataMap.put("amount", stripePaymentIntent.getAmount());
        additionalDataMap.put("amount_capturable", stripePaymentIntent.getAmountCapturable());
        additionalDataMap.put("amount_received", stripePaymentIntent.getAmountReceived());
        additionalDataMap.put("application", stripePaymentIntent.getApplication());
        additionalDataMap.put("application_fee_amount", stripePaymentIntent.getApplicationFeeAmount());
        additionalDataMap.put("canceled_at", stripePaymentIntent.getCanceledAt());
        additionalDataMap.put("cancellation_reason", stripePaymentIntent.getCancellationReason());
        additionalDataMap.put("capture_method", stripePaymentIntent.getCaptureMethod());
        if (stripePaymentIntent.getCharges() != null) {
            Charge lastCharge = null;
            for (final Charge charge : stripePaymentIntent.getCharges().autoPagingIterable()) {
                if (lastCharge == null || lastCharge.getCreated() < charge.getCreated()) {
                    lastCharge = charge;
                }
            }
            if (lastCharge != null) {
                // Keep the state for the last charge (maps to our payment transaction)
                additionalDataMap.put("last_charge_amount", lastCharge.getAmount());
                additionalDataMap.put("last_charge_authorization_code", lastCharge.getAuthorizationCode());
                additionalDataMap.put("last_charge_balance_transaction_id", lastCharge.getBalanceTransaction());
                additionalDataMap.put("last_charge_created", lastCharge.getCreated());
                additionalDataMap.put("last_charge_currency", lastCharge.getCurrency());
                additionalDataMap.put("last_charge_description", lastCharge.getDescription());
                additionalDataMap.put("last_charge_failure_code", lastCharge.getFailureCode());
                additionalDataMap.put("last_charge_failure_message", lastCharge.getFailureMessage());
                additionalDataMap.put("last_charge_id", lastCharge.getId());
                additionalDataMap.put("last_charge_metadata", lastCharge.getMetadata());
                additionalDataMap.put("last_charge_object", lastCharge.getObject());
                additionalDataMap.put("last_charge_outcome", lastCharge.getOutcome());
                additionalDataMap.put("last_charge_paid", lastCharge.getPaid());
                additionalDataMap.put("last_charge_payment_method_id", lastCharge.getPaymentMethod());
                if (lastCharge.getPaymentMethodDetails() != null) {
                    additionalDataMap.put("last_charge_payment_method_type", lastCharge.getPaymentMethodDetails().getType());
                }
                additionalDataMap.put("last_charge_statement_descriptor", lastCharge.getStatementDescriptor());
                additionalDataMap.put("last_charge_status", lastCharge.getStatus());
            }
        }
        additionalDataMap.put("confirmation_method", stripePaymentIntent.getConfirmationMethod());
        additionalDataMap.put("created", stripePaymentIntent.getCreated());
        additionalDataMap.put("currency", stripePaymentIntent.getCurrency());
        additionalDataMap.put("customer_id", stripePaymentIntent.getCustomer());
        additionalDataMap.put("description", stripePaymentIntent.getDescription());
        additionalDataMap.put("id", stripePaymentIntent.getId());
        additionalDataMap.put("invoice_id", stripePaymentIntent.getInvoice());
        additionalDataMap.put("last_payment_error", stripePaymentIntent.getLastPaymentError());
        additionalDataMap.put("livemode", stripePaymentIntent.getLivemode());
        additionalDataMap.put("metadata", stripePaymentIntent.getMetadata());
        additionalDataMap.put("next_action", stripePaymentIntent.getNextAction());
        additionalDataMap.put("object", stripePaymentIntent.getObject());
        additionalDataMap.put("on_behalf_of", stripePaymentIntent.getOnBehalfOf());
        additionalDataMap.put("payment_method_id", stripePaymentIntent.getPaymentMethod());
        additionalDataMap.put("payment_method_types", stripePaymentIntent.getPaymentMethodTypes());
        additionalDataMap.put("review_id", stripePaymentIntent.getReview());
        additionalDataMap.put("statement_descriptor", stripePaymentIntent.getStatementDescriptor());
        additionalDataMap.put("status", stripePaymentIntent.getStatus());
        additionalDataMap.put("transfer_group", stripePaymentIntent.getTransferGroup());

        return additionalDataMap;
    }

    public static Map<String, Object> toAdditionalDataMap(final Session session, @Nullable final String pk) {
        final Map<String, Object> additionalDataMap = new HashMap<String, Object>();

        additionalDataMap.put("billing_address_collection", session.getBillingAddressCollection());
        additionalDataMap.put("cancel_url", session.getCancelUrl());
        additionalDataMap.put("client_reference_id", session.getClientReferenceId());
        additionalDataMap.put("customer_id", session.getCustomer());
        additionalDataMap.put("line_items", session.getLineItems());
        additionalDataMap.put("id", session.getId());
        additionalDataMap.put("livemode", session.getLivemode());
        additionalDataMap.put("locale", session.getLocale());
        additionalDataMap.put("object", session.getObject());
        additionalDataMap.put("payment_intent_id", session.getPaymentIntent());
        additionalDataMap.put("payment_method_types", session.getPaymentMethodTypes());
        additionalDataMap.put("subscription_id", session.getSubscription());
        additionalDataMap.put("success_url", session.getSuccessUrl());
        if (pk != null) {
            additionalDataMap.put("publishable_key", pk);
        }

        return additionalDataMap;
    }
}
