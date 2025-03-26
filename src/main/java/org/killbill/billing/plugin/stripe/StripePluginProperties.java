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

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;

import com.google.common.base.Throwables;
import com.stripe.exception.StripeException;
import com.stripe.model.BankAccount;
import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.PaymentMethod.Card;
import com.stripe.model.PaymentSource;
import com.stripe.model.SetupIntent;
import com.stripe.model.SetupIntent.PaymentMethodOptions;
import com.stripe.model.Source;
import com.stripe.model.Source.AchDebit;
import com.stripe.model.Token;
import com.stripe.model.checkout.Session;

import static org.killbill.billing.plugin.stripe.StripePaymentPluginApi.PROPERTY_OVERRIDDEN_TRANSACTION_STATUS;

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
            final Source.SepaDebit sepaDebit = stripeSource.getSepaDebit();
            if (sepaDebit != null) {
                additionalDataMap.put("sepa_debit_bank_code", sepaDebit.getBankCode());
                additionalDataMap.put("sepa_debit_branch_code", sepaDebit.getBranchCode());
                additionalDataMap.put("sepa_debit_country", sepaDebit.getCountry());
                additionalDataMap.put("sepa_debit_fingerprint", sepaDebit.getFingerprint());
                additionalDataMap.put("sepa_debit_last4", sepaDebit.getLast4());
                additionalDataMap.put("sepa_debit_mandate_reference", sepaDebit.getMandateReference());
                additionalDataMap.put("sepa_debit_mandate_url", sepaDebit.getMandateUrl());
            }
            final Source.AcssDebit acssDebit = stripeSource.getAcssDebit();
            if (acssDebit != null) {
                additionalDataMap.put("acss_debit_bank_address_city", acssDebit.getBankAddressCity());
                additionalDataMap.put("acss_debit_bank_address_line_1", acssDebit.getBankAddressLine1());
                additionalDataMap.put("acss_debit_bank_address_line_2", acssDebit.getBankAddressLine2());
                additionalDataMap.put("acss_debit_bank_address_postal_code", acssDebit.getBankAddressPostalCode());
                additionalDataMap.put("acss_debit_bank_name", acssDebit.getBankName());
                additionalDataMap.put("acss_debit_category", acssDebit.getCategory());
                additionalDataMap.put("acss_debit_country", acssDebit.getCountry());
                additionalDataMap.put("acss_debit_fingerprint", acssDebit.getFingerprint());
                additionalDataMap.put("acss_debit_last4", acssDebit.getLast4());
                additionalDataMap.put("acss_debit_routing_number", acssDebit.getRoutingNumber());
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
        final PaymentMethod.SepaDebit sepaDebit = stripePaymentMethod.getSepaDebit();
        if (sepaDebit != null) {
            additionalDataMap.put("sepa_debit_bank_code", sepaDebit.getBankCode());
            additionalDataMap.put("sepa_debit_branch_code", sepaDebit.getBranchCode());
            additionalDataMap.put("sepa_debit_country", sepaDebit.getCountry());
            additionalDataMap.put("sepa_debit_fingerprint", sepaDebit.getFingerprint());
            additionalDataMap.put("sepa_debit_last4", sepaDebit.getLast4());
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

    public static Map<String, Object> toAdditionalDataMap(final StripeException stripeException) {
        final Map<String, Object> additionalDataMap = new HashMap<String, Object>();

        if (stripeException.getStripeError() != null) {
            // See StripePaymentTransactionInfoPlugin#getGatewayError
            additionalDataMap.put("stripe_error_message", stripeException.getStripeError().getMessage());
            // See StripePaymentTransactionInfoPlugin#getGatewayErrorCode
            additionalDataMap.put("stripe_error_code", stripeException.getStripeError().getCode());
        }
        additionalDataMap.put("code", stripeException.getCode());
        additionalDataMap.put("request_id", stripeException.getRequestId());
        additionalDataMap.put("status_code", stripeException.getStatusCode());
        additionalDataMap.put("message", stripeException.getMessage());

        additionalDataMap.put(PROPERTY_OVERRIDDEN_TRANSACTION_STATUS, mapExceptionToCallResult(stripeException).toString());

        return additionalDataMap;
    }


    /**
     * Educated guess approach to transform exceptions into error status codes.
     */
    private static PaymentPluginStatus mapExceptionToCallResult(final Exception e) {
        //noinspection ThrowableResultOfMethodCallIgnored
        final Throwable rootCause = Throwables.getRootCause(e);
        final String errorMessage = rootCause.getMessage();
        if (rootCause instanceof ConnectException) {
            return PaymentPluginStatus.CANCELED;
        } else if (rootCause instanceof SocketTimeoutException) {
            // read timeout
            if (errorMessage.contains("Read timed out")) {
                return PaymentPluginStatus.UNDEFINED;
            } else if (errorMessage.contains("Unexpected end of file from server")) {
                return PaymentPluginStatus.UNDEFINED;
            }
        } else if (rootCause instanceof SocketException) {
            if (errorMessage.contains("Unexpected end of file from server")) {
                return PaymentPluginStatus.UNDEFINED;
            }
        } else if (rootCause instanceof UnknownHostException) {
            return PaymentPluginStatus.CANCELED;
        } else if (rootCause instanceof IOException) {
            if (errorMessage.contains("Invalid Http response")) {
                // unparsable data as response
                return PaymentPluginStatus.UNDEFINED;
            } else if (errorMessage.contains("Bogus chunk size")) {
                return PaymentPluginStatus.UNDEFINED;
            }
        }

        return PaymentPluginStatus.UNDEFINED;
    }

    public static Map<String, Object> toAdditionalDataMap(final PaymentIntent stripePaymentIntent, @Nullable final Charge lastCharge) {
        final Map<String, Object> additionalDataMap = new HashMap<String, Object>();

        additionalDataMap.put("amount", stripePaymentIntent.getAmount());
        additionalDataMap.put("amount_capturable", stripePaymentIntent.getAmountCapturable());
        additionalDataMap.put("amount_received", stripePaymentIntent.getAmountReceived());
        additionalDataMap.put("application", stripePaymentIntent.getApplication());
        additionalDataMap.put("application_fee_amount", stripePaymentIntent.getApplicationFeeAmount());
        additionalDataMap.put("canceled_at", stripePaymentIntent.getCanceledAt());
        additionalDataMap.put("cancellation_reason", stripePaymentIntent.getCancellationReason());
        additionalDataMap.put("capture_method", stripePaymentIntent.getCaptureMethod());
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

        return additionalDataMap;
    }

    public static Map<String, Object> toAdditionalDataMap(final SetupIntent stripeSetupIntent) {
        final Map<String, Object> additionalDataMap = new HashMap<String, Object>();

        additionalDataMap.put("application", stripeSetupIntent.getApplication());
        additionalDataMap.put("cancellation_reason", stripeSetupIntent.getCancellationReason());
        additionalDataMap.put("created", stripeSetupIntent.getCreated());
        additionalDataMap.put("customer_id", stripeSetupIntent.getCustomer());
        additionalDataMap.put("description", stripeSetupIntent.getDescription());
        additionalDataMap.put("id", stripeSetupIntent.getId());
        additionalDataMap.put("last_setup_error", stripeSetupIntent.getLastSetupError());
        additionalDataMap.put("latest_attempt", stripeSetupIntent.getLatestAttempt());
        additionalDataMap.put("livemode", stripeSetupIntent.getLivemode());
        additionalDataMap.put("mandate", stripeSetupIntent.getMandate());
        additionalDataMap.put("metadata", stripeSetupIntent.getMetadata());
        additionalDataMap.put("next_action", stripeSetupIntent.getNextAction());
        additionalDataMap.put("object", stripeSetupIntent.getObject());
        additionalDataMap.put("on_behalf_of", stripeSetupIntent.getOnBehalfOf());
        additionalDataMap.put("payment_method_id", stripeSetupIntent.getPaymentMethod());
        final PaymentMethodOptions paymentMethodOptions = stripeSetupIntent.getPaymentMethodOptions();
        if (paymentMethodOptions != null ) {
            final SetupIntent.PaymentMethodOptions.Card card = paymentMethodOptions.getCard();
            if (card != null) {
                additionalDataMap.put("payment_method_options_card_request_three_d_secure", card.getRequestThreeDSecure());
            }
            // paymentMethodOptions also contains "sepa_debit" which contains "mandate_options" that currently has
            // no properties, so it is ignored here (https://stripe.com/docs/api/setup_intents/object)
        }
        additionalDataMap.put("payment_method_types", stripeSetupIntent.getPaymentMethodTypes());
        additionalDataMap.put("single_use_mandate_id", stripeSetupIntent.getSingleUseMandate());
        additionalDataMap.put("status", stripeSetupIntent.getStatus());
        additionalDataMap.put("usage", stripeSetupIntent.getUsage());

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
        additionalDataMap.put("setup_intent_id", session.getSetupIntent());
        additionalDataMap.put("subscription_id", session.getSubscription());
        additionalDataMap.put("success_url", session.getSuccessUrl());
        if (pk != null) {
            additionalDataMap.put("publishable_key", pk);
        }

        return additionalDataMap;
    }
}
