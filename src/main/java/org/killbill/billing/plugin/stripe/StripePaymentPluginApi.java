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

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillLogService;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.plugin.api.GatewayNotification;
import org.killbill.billing.payment.plugin.api.HostedPaymentPageFormDescriptor;
import org.killbill.billing.payment.plugin.api.PaymentMethodInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.plugin.api.PluginProperties;
import org.killbill.billing.plugin.api.core.PluginCustomField;
import org.killbill.billing.plugin.api.payment.PluginHostedPaymentPageFormDescriptor;
import org.killbill.billing.plugin.api.payment.PluginPaymentMethodInfoPlugin;
import org.killbill.billing.plugin.api.payment.PluginPaymentPluginApi;
import org.killbill.billing.plugin.stripe.dao.StripeDao;
import org.killbill.billing.plugin.stripe.dao.gen.tables.StripePaymentMethods;
import org.killbill.billing.plugin.stripe.dao.gen.tables.StripeResponses;
import org.killbill.billing.plugin.stripe.dao.gen.tables.records.StripeHppRequestsRecord;
import org.killbill.billing.plugin.stripe.dao.gen.tables.records.StripePaymentMethodsRecord;
import org.killbill.billing.plugin.stripe.dao.gen.tables.records.StripeResponsesRecord;
import org.killbill.billing.plugin.util.KillBillMoney;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.HasId;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.PaymentSource;
import com.stripe.model.Refund;
import com.stripe.model.Source;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;

public class StripePaymentPluginApi extends PluginPaymentPluginApi<StripeResponsesRecord, StripeResponses, StripePaymentMethodsRecord, StripePaymentMethods> {

    private static final Logger logger = LoggerFactory.getLogger(StripePaymentPluginApi.class);

    public static final String PROPERTY_FROM_HPP = "fromHPP";
    public static final String PROPERTY_HPP_COMPLETION = "fromHPPCompletion";
    public static final String PROPERTY_OVERRIDDEN_TRANSACTION_STATUS = "overriddenTransactionStatus";

    private final StripeConfigPropertiesConfigurationHandler stripeConfigPropertiesConfigurationHandler;
    private final StripeDao dao;

    public StripePaymentPluginApi(final StripeConfigPropertiesConfigurationHandler stripeConfigPropertiesConfigurationHandler,
                                  final OSGIKillbillAPI killbillAPI,
                                  final OSGIConfigPropertiesService configProperties,
                                  final OSGIKillbillLogService logService,
                                  final Clock clock,
                                  final StripeDao dao) {
        super(killbillAPI, configProperties, logService, clock, dao);
        this.stripeConfigPropertiesConfigurationHandler = stripeConfigPropertiesConfigurationHandler;
        this.dao = dao;
    }


    @Override
    public List<PaymentTransactionInfoPlugin> getPaymentInfo(final UUID kbAccountId,
                                                             final UUID kbPaymentId,
                                                             final Iterable<PluginProperty> properties,
                                                             final TenantContext context) throws PaymentPluginApiException {
        final List<PaymentTransactionInfoPlugin> transactions = super.getPaymentInfo(kbAccountId, kbPaymentId, properties, context);
        if (transactions.isEmpty()) {
            // We don't know about this payment (maybe it was aborted in a control plugin)
            return transactions;
        }

        // Check if a HPP payment needs to be canceled
        final ExpiredPaymentPolicy expiredPaymentPolicy = new ExpiredPaymentPolicy(clock, stripeConfigPropertiesConfigurationHandler.getConfigurable(context.getTenantId()));
        final StripePaymentTransactionInfoPlugin transactionToExpire = expiredPaymentPolicy.isExpired(transactions);
        if (transactionToExpire != null) {
            logger.info("Canceling expired Stripe transaction {} (created {})", transactionToExpire.getStripeResponseRecord().getStripeId(), transactionToExpire.getStripeResponseRecord().getCreatedDate());
            final Map additionalMetadata = ImmutableMap.builder()
                                                       .put(PROPERTY_OVERRIDDEN_TRANSACTION_STATUS,
                                                            PaymentPluginStatus.CANCELED.toString())
                                                       .put("message",
                                                            "Payment Expired - Cancelled by Janitor")
                                                       .build();
            try {
                dao.updateResponse(transactionToExpire.getStripeResponseRecord(), additionalMetadata);
            } catch (final SQLException e) {
                throw new PaymentPluginApiException("Unable to update expired payment", e);
            }

            // Reload payment
            return super.getPaymentInfo(kbAccountId, kbPaymentId, properties, context);
        }

        // Refresh, if needed
        boolean wasRefreshed = false;
        final RequestOptions requestOptions = buildRequestOptions(context);
        for (final PaymentTransactionInfoPlugin transaction : transactions) {
            if (transaction.getStatus() == PaymentPluginStatus.PENDING) {
                final String paymentIntentId = PluginProperties.findPluginPropertyValue("id", transaction.getProperties());
                try {
                    final PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId, requestOptions);
                    // 3DS validated: must confirm the PaymentIntent
                    if ("requires_confirmation".equals(intent.getStatus())) {
                        logger.info("Confirming Stripe transaction {}", intent.getId());
                        final PaymentIntent updatedIntent = intent.confirm(requestOptions);
                        dao.updateResponse(transaction.getKbTransactionPaymentId(), updatedIntent, context.getTenantId());
                        wasRefreshed = true;
                    }
                } catch (final StripeException e) {
                    logger.warn("Unable to fetch latest payment state in Stripe, data might be stale", e);
                } catch (final SQLException e) {
                    throw new PaymentPluginApiException("Unable to refresh payment", e);
                }
            }
        }

        return wasRefreshed ? super.getPaymentInfo(kbAccountId, kbPaymentId, properties, context) : transactions;
    }

    @Override
    protected PaymentTransactionInfoPlugin buildPaymentTransactionInfoPlugin(final StripeResponsesRecord record) {
        return StripePaymentTransactionInfoPlugin.build(record);
    }

    @Override
    public PaymentMethodPlugin getPaymentMethodDetail(final UUID kbAccountId, final UUID kbPaymentMethodId, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentPluginApiException {
        final StripePaymentMethodsRecord record;
        try {
            record = dao.getPaymentMethod(kbPaymentMethodId, context.getTenantId());
        } catch (final SQLException e) {
            throw new PaymentPluginApiException("Unable to retrieve payment method for kbPaymentMethodId " + kbPaymentMethodId, e);
        }

        if (record == null) {
            // Known in KB but deleted in Stripe?
            return new StripePaymentMethodPlugin(kbPaymentMethodId,
                                                 null,
                                                 ImmutableList.<PluginProperty>of());
        } else {
            return buildPaymentMethodPlugin(record);
        }
    }

    @Override
    protected PaymentMethodPlugin buildPaymentMethodPlugin(final StripePaymentMethodsRecord record) {
        return StripePaymentMethodPlugin.build(record);
    }

    @Override
    protected PaymentMethodInfoPlugin buildPaymentMethodInfoPlugin(final StripePaymentMethodsRecord record) {
        return new PluginPaymentMethodInfoPlugin(UUID.fromString(record.getKbAccountId()),
                                                 UUID.fromString(record.getKbPaymentMethodId()),
                                                 false,
                                                 record.getStripeId());
    }

    @Override
    public void addPaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final PaymentMethodPlugin paymentMethodProps, final boolean setDefault, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        final RequestOptions requestOptions = buildRequestOptions(context);

        String paymentMethodIdInStripe = paymentMethodProps.getExternalPaymentMethodId();

        final String sessionId = PluginProperties.findPluginPropertyValue("sessionId", properties);
        if (sessionId != null) {
            // Checkout flow
            try {
                final StripeHppRequestsRecord hppRecord = dao.getHppRequest(sessionId, context.getTenantId().toString());
                if (hppRecord == null) {
                    throw new PaymentPluginApiException("INTERNAL", "Unable to add payment method: missing StripeHppRequestsRecord for sessionId " + sessionId);
                }

                final String paymentIntentId = (String) StripeDao.fromAdditionalData(hppRecord.getAdditionalData()).get("payment_intent_id");
                final PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId, requestOptions);
                if ("requires_capture".equals(paymentIntent.getStatus())) {
                    // Void it
                    logger.info("Voiding Stripe transaction {}", paymentIntent.getId());
                    paymentIntent.cancel(requestOptions);

                    final String existingCustomerId = getCustomerIdNoException(kbAccountId, context);
                    if (existingCustomerId == null) {
                        // Add magic custom field
                        logger.info("Mapping kbAccountId {} to Stripe customer {}", kbAccountId, paymentIntent.getCustomer());
                        killbillAPI.getCustomFieldUserApi().addCustomFields(ImmutableList.of(new PluginCustomField(kbAccountId,
                                                                                                                   ObjectType.ACCOUNT,
                                                                                                                   "STRIPE_CUSTOMER_ID",
                                                                                                                   paymentIntent.getCustomer(),
                                                                                                                   clock.getUTCNow())), context);
                    } else if (!existingCustomerId.equals(paymentIntent.getCustomer())) {
                        throw new PaymentPluginApiException("USER", "Unable to add payment method : paymentIntent customerId is " + paymentIntent.getCustomer() + " but account already mapped to " + existingCustomerId);
                    }

                    // Used below to create the row in the plugin
                    // TODO This implicitly assumes the payment method type if "payment_method", is this always true?
                    paymentMethodIdInStripe = paymentIntent.getPaymentMethod();
                } else {
                    throw new PaymentPluginApiException("EXTERNAL", "Unable to add payment method: paymentIntent is " + paymentIntent.getStatus());
                }
            } catch (final SQLException e) {
                throw new PaymentPluginApiException("Unable to add payment method", e);
            } catch (final CustomFieldApiException e) {
                throw new PaymentPluginApiException("Unable to add custom field", e);
            } catch (final StripeException e) {
                throw new PaymentPluginApiException("Error calling Stripe while adding payment method", e);
            }
        }

        final Map<String, Object> additionalDataMap;
        final String stripeId;
        if (paymentMethodIdInStripe != null) {
            final String objectType = PluginProperties.getValue("object", "payment_method", paymentMethodProps.getProperties());
            if ("payment_method".equals(objectType)) {
                try {
                    // The Stripe paymentMethodId must be passed as the PaymentMethodPlugin#getExternalPaymentMethodId
                    final PaymentMethod stripePaymentMethod = PaymentMethod.retrieve(paymentMethodIdInStripe, requestOptions);
                    additionalDataMap = StripePluginProperties.toAdditionalDataMap(stripePaymentMethod);
                    stripeId = stripePaymentMethod.getId();
                } catch (final StripeException e) {
                    throw new PaymentPluginApiException("Error calling Stripe while adding payment method", e);
                }
            } else if ("source".equals(objectType)) {
                try {
                    // The Stripe sourceId must be passed as the PaymentMethodPlugin#getExternalPaymentMethodId
                    final Source stripeSource = Source.retrieve(paymentMethodIdInStripe, requestOptions);
                    additionalDataMap = StripePluginProperties.toAdditionalDataMap(stripeSource);
                    stripeId = stripeSource.getId();
                } catch (final StripeException e) {
                    throw new PaymentPluginApiException("Error calling Stripe while adding payment method", e);
                }
            } else if ("bank_account".equals(objectType)) {
                try {
                    // The Stripe bankAccountId must be passed as the PaymentMethodPlugin#getExternalPaymentMethodId
                    final String existingCustomerId = getCustomerId(kbAccountId, context);
                    final PaymentSource paymentSource = Customer.retrieve(existingCustomerId, requestOptions)
                                                                .getSources()
                                                                .retrieve(paymentMethodIdInStripe, requestOptions);
                    additionalDataMap = StripePluginProperties.toAdditionalDataMap(paymentSource);
                    stripeId = paymentSource.getId();
                } catch (final StripeException e) {
                    throw new PaymentPluginApiException("Error calling Stripe while adding payment method", e);
                }
            } else {
                throw new UnsupportedOperationException("Payment Method type not yet supported: " + objectType);
            }
        } else {
            throw new PaymentPluginApiException("USER", "PaymentMethodPlugin#getExternalPaymentMethodId or sessionId plugin property must be passed");
        }

        final DateTime utcNow = clock.getUTCNow();
        try {
            dao.addPaymentMethod(kbAccountId, kbPaymentMethodId, additionalDataMap, stripeId, utcNow, context.getTenantId());
        } catch (final SQLException e) {
            throw new PaymentPluginApiException("Unable to add payment method", e);
        }
    }

    @Override
    protected String getPaymentMethodId(final StripePaymentMethodsRecord record) {
        return record.getKbPaymentMethodId();
    }

    @Override
    public void deletePaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {

        // Retrieve our currently known payment method
        final StripePaymentMethodsRecord stripePaymentMethodsRecord;
        try {
            stripePaymentMethodsRecord = dao.getPaymentMethod(kbPaymentMethodId, context.getTenantId());
        } catch (final SQLException e) {
            throw new PaymentPluginApiException("Unable to retrieve payment method", e);
        }

        // Delete in Stripe
        final RequestOptions requestOptions = buildRequestOptions(context);
        try {
            PaymentMethod.retrieve(stripePaymentMethodsRecord.getStripeId(), requestOptions).detach(requestOptions);
        } catch (final StripeException e) {
            throw new PaymentPluginApiException("Unable to delete Stripe payment method", e);
        }

        super.deletePaymentMethod(kbAccountId, kbPaymentMethodId, properties, context);
    }

    @Override
    public List<PaymentMethodInfoPlugin> getPaymentMethods(final UUID kbAccountId, final boolean refreshFromGateway, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {

        // If refreshFromGateway isn't set, simply read our tables
        if (!refreshFromGateway) {
            return super.getPaymentMethods(kbAccountId, refreshFromGateway, properties, context);
        }

        // Retrieve our currently known payment methods
        final Map<String, StripePaymentMethodsRecord> existingPaymentMethodByStripeId = new HashMap<String, StripePaymentMethodsRecord>();
        try {
            final List<StripePaymentMethodsRecord> existingStripePaymentMethodRecords = dao.getPaymentMethods(kbAccountId, context.getTenantId());
            for (final StripePaymentMethodsRecord existingStripePaymentMethodRecord : existingStripePaymentMethodRecords) {
                existingPaymentMethodByStripeId.put(existingStripePaymentMethodRecord.getStripeId(), existingStripePaymentMethodRecord);
            }
        } catch (final SQLException e) {
            throw new PaymentPluginApiException("Unable to retrieve existing payment methods", e);
        }

        // To retrieve all payment methods in Stripe, retrieve the Stripe customer id (custom field on the account)
        final String stripeCustomerId = getCustomerId(kbAccountId, context);

        // Sync Stripe payment methods (source of truth)
        final RequestOptions requestOptions = buildRequestOptions(context);

        // Track the objects (the various Stripe APIs can return the same objects under a different type)
        final Set<String> stripeObjectsTreated = new HashSet<String>();
        try {
            // Start with PaymentMethod...
            final Map<String, Object> paymentMethodParams = new HashMap<String, Object>();
            paymentMethodParams.put("customer", stripeCustomerId);
            // Only supported type by Stripe for now
            paymentMethodParams.put("type", "card");
            final Iterable<PaymentMethod> stripePaymentMethods = PaymentMethod.list(paymentMethodParams, requestOptions).autoPagingIterable();
            syncPaymentMethods(kbAccountId, stripePaymentMethods, existingPaymentMethodByStripeId, stripeObjectsTreated, context);

            // Then go through the sources
            final Iterable<? extends HasId> stripeSources = Customer.retrieve(stripeCustomerId, requestOptions).getSources().autoPagingIterable();
            syncPaymentMethods(kbAccountId, stripeSources, existingPaymentMethodByStripeId, stripeObjectsTreated, context);
        } catch (final StripeException e) {
            throw new PaymentPluginApiException("Error connecting to Stripe", e);
        } catch (final PaymentApiException e) {
            throw new PaymentPluginApiException("Error creating payment method", e);
        } catch (final SQLException e) {
            throw new PaymentPluginApiException("Error creating payment method", e);
        }

        for (final StripePaymentMethodsRecord stripePaymentMethodsRecord : existingPaymentMethodByStripeId.values()) {
            logger.info("Deactivating local Stripe payment method {} - not found in Stripe", stripePaymentMethodsRecord.getStripeId());
            super.deletePaymentMethod(kbAccountId, UUID.fromString(stripePaymentMethodsRecord.getKbPaymentMethodId()), properties, context);
        }

        // Refresh the state
        return super.getPaymentMethods(kbAccountId, false, properties, context);
    }

    private void syncPaymentMethods(final UUID kbAccountId, final Iterable<? extends HasId> stripeObjects, final Map<String, StripePaymentMethodsRecord> existingPaymentMethodByStripeId, final Set<String> stripeObjectsTreated, final CallContext context) throws PaymentApiException, SQLException {
        for (final HasId stripeObject : stripeObjects) {
            if (stripeObjectsTreated.contains(stripeObject.getId())) {
                continue;
            } else {
                stripeObjectsTreated.add(stripeObject.getId());
            }

            final Map<String, Object> additionalDataMap;
            if (stripeObject instanceof PaymentMethod) {
                additionalDataMap = StripePluginProperties.toAdditionalDataMap((PaymentMethod) stripeObject);
            } else if (stripeObject instanceof PaymentSource) {
                additionalDataMap = StripePluginProperties.toAdditionalDataMap((PaymentSource) stripeObject);
            } else {
                throw new UnsupportedOperationException("Unsupported object: " + stripeObject);
            }

            // We remove it here to build the list of local payment methods to delete
            final StripePaymentMethodsRecord existingPaymentMethodRecord = existingPaymentMethodByStripeId.remove(stripeObject.getId());
            if (existingPaymentMethodRecord == null) {
                // We don't know about it yet, create it
                logger.info("Creating new local Stripe payment method {}", stripeObject.getId());
                final List<PluginProperty> properties = PluginProperties.buildPluginProperties(additionalDataMap);
                final StripePaymentMethodPlugin paymentMethodInfo = new StripePaymentMethodPlugin(null,
                                                                                                  stripeObject.getId(),
                                                                                                  properties);
                killbillAPI.getPaymentApi().addPaymentMethod(getAccount(kbAccountId, context),
                                                             stripeObject.getId(),
                                                             StripeActivator.PLUGIN_NAME,
                                                             false,
                                                             paymentMethodInfo,
                                                             ImmutableList.<PluginProperty>of(),
                                                             context);
            } else {
                logger.info("Updating existing local Stripe payment method {}", stripeObject.getId());
                dao.updatePaymentMethod(UUID.fromString(existingPaymentMethodRecord.getKbPaymentMethodId()),
                                        additionalDataMap,
                                        stripeObject.getId(),
                                        clock.getUTCNow(),
                                        context.getTenantId());
            }
        }
    }

    @Override
    public PaymentTransactionInfoPlugin authorizePayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {

        final StripeResponsesRecord stripeResponsesRecord;
        try {
            stripeResponsesRecord = dao.getSuccessfulAuthorizationResponse(kbPaymentId, context.getTenantId());
        } catch (final SQLException e) {
            throw new PaymentPluginApiException("SQL exception when fetching response", e);
        }

        final boolean isHPPCompletion = stripeResponsesRecord != null && Boolean.valueOf(MoreObjects.firstNonNull(StripeDao.fromAdditionalData(stripeResponsesRecord.getAdditionalData()).get(PROPERTY_FROM_HPP), false).toString());
        if (!isHPPCompletion) {
            updateResponseWithAdditionalProperties(kbTransactionId, properties, context.getTenantId());
            // We don't have any record for that payment: we want to trigger an actual authorization call (or complete a 3D-S authorization)
            return executeInitialTransaction(TransactionType.AUTHORIZE, kbAccountId, kbPaymentId, kbTransactionId, kbPaymentMethodId, amount, currency, properties, context);
        } else {
            // We already have a record for that payment transaction: we just update the response row with additional properties
            // (the API can be called for instance after the user is redirected back from the HPP)
            updateResponseWithAdditionalProperties(kbTransactionId, PluginProperties.merge(ImmutableMap.of(PROPERTY_HPP_COMPLETION, true), properties), context.getTenantId());
        }

        return buildPaymentTransactionInfoPlugin(stripeResponsesRecord);
    }

    private void updateResponseWithAdditionalProperties(final UUID kbTransactionId, final Iterable<PluginProperty> properties, final UUID tenantId) throws PaymentPluginApiException {
        try {
            dao.updateResponse(kbTransactionId, properties, tenantId);
        } catch (final SQLException e) {
            throw new PaymentPluginApiException("SQL exception when updating response", e);
        }
    }

    @Override
    public PaymentTransactionInfoPlugin capturePayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {

        return executeFollowUpTransaction(TransactionType.CAPTURE,
                                          new TransactionExecutor<PaymentIntent>() {
                                              @Override
                                              public PaymentIntent execute(final Account account, final StripePaymentMethodsRecord paymentMethodsRecord, final StripeResponsesRecord previousResponse) throws StripeException {
                                                  final RequestOptions requestOptions = buildRequestOptions(context);

                                                  final PaymentIntent intent = PaymentIntent.retrieve((String) StripeDao.fromAdditionalData(previousResponse.getAdditionalData()).get("id"), requestOptions);
                                                  Map<String, Object> paymentIntentParams = new HashMap<String, Object>();
                                                  paymentIntentParams.put("amount_to_capture", KillBillMoney.toMinorUnits(currency.toString(), amount));
                                                  return intent.capture(paymentIntentParams, requestOptions);
                                              }
                                          },
                                          kbAccountId,
                                          kbPaymentId,
                                          kbTransactionId,
                                          kbPaymentMethodId,
                                          amount,
                                          currency,
                                          properties,
                                          context);
    }

    @Override
    public PaymentTransactionInfoPlugin purchasePayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {

        final StripeResponsesRecord stripeResponsesRecord;
        try {
            stripeResponsesRecord = dao.updateResponse(kbTransactionId, properties, context.getTenantId());
        } catch (final SQLException e) {
            throw new PaymentPluginApiException("HPP notification came through, but we encountered a database error", e);
        }

        if (stripeResponsesRecord == null) {
            // We don't have any record for that payment: we want to trigger an actual purchase (auto-capture) call
            return executeInitialTransaction(TransactionType.PURCHASE, kbAccountId, kbPaymentId, kbTransactionId, kbPaymentMethodId, amount, currency, properties, context);
        } else {
            // We already have a record for that payment transaction and we just updated the response row with additional properties
            // (the API can be called for instance after the user is redirected back from the HPP)
        }

        return buildPaymentTransactionInfoPlugin(stripeResponsesRecord);
    }

    @Override
    public PaymentTransactionInfoPlugin voidPayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {

        return executeFollowUpTransaction(TransactionType.VOID,
                                          new TransactionExecutor<PaymentIntent>() {
                                              @Override
                                              public PaymentIntent execute(final Account account, final StripePaymentMethodsRecord paymentMethodsRecord, final StripeResponsesRecord previousResponse) throws StripeException {
                                                  final RequestOptions requestOptions = buildRequestOptions(context);

                                                  final PaymentIntent intent = PaymentIntent.retrieve((String) StripeDao.fromAdditionalData(previousResponse.getAdditionalData()).get("id"), requestOptions);
                                                  return intent.cancel(requestOptions);

                                              }
                                          },
                                          kbAccountId,
                                          kbPaymentId,
                                          kbTransactionId,
                                          kbPaymentMethodId,
                                          null,
                                          null,
                                          properties,
                                          context);
    }

    @Override
    public PaymentTransactionInfoPlugin creditPayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        throw new PaymentPluginApiException("INTERNAL", "#creditPayment not yet implemented, please contact support@killbill.io");
    }

    @Override
    public PaymentTransactionInfoPlugin refundPayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {

        return executeFollowUpTransaction(TransactionType.REFUND,
                                          new TransactionExecutor<PaymentIntent>() {
                                              @Override
                                              public PaymentIntent execute(final Account account, final StripePaymentMethodsRecord paymentMethodsRecord, final StripeResponsesRecord previousResponse) throws StripeException {
                                                  final RequestOptions requestOptions = buildRequestOptions(context);
                                                  final Map additionalData = StripeDao.fromAdditionalData(previousResponse.getAdditionalData());

                                                  final String paymentIntent = (String) additionalData.get("id");
                                                  // The PaymentIntent API doesn't have a refund API - refund the charge created behind the scenes instead
                                                  final String lastChargeId = (String) additionalData.get("last_charge_id");

                                                  Map<String, Object> params = new HashMap<>();
                                                  params.put("charge", lastChargeId);
                                                  params.put("amount", KillBillMoney.toMinorUnits(currency.toString(), amount));

                                                  final Refund refund = Refund.create(params, requestOptions);

                                                  return PaymentIntent.retrieve(paymentIntent, requestOptions);
                                              }
                                          },
                                          kbAccountId,
                                          kbPaymentId,
                                          kbTransactionId,
                                          kbPaymentMethodId,
                                          amount,
                                          currency,
                                          properties,
                                          context);
    }

    @VisibleForTesting
    RequestOptions buildRequestOptions(final TenantContext context) {
        final StripeConfigProperties stripeConfigProperties = stripeConfigPropertiesConfigurationHandler.getConfigurable(context.getTenantId());
        return RequestOptions.builder()
                             .setConnectTimeout(Integer.valueOf(stripeConfigProperties.getConnectionTimeout()))
                             .setReadTimeout(Integer.valueOf(stripeConfigProperties.getReadTimeout()))
                             .setApiKey(stripeConfigProperties.getApiKey())
                             .build();
    }

    @Override
    public HostedPaymentPageFormDescriptor buildFormDescriptor(final UUID kbAccountId, final Iterable<PluginProperty> customFields, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {

        final Account account = getAccount(kbAccountId, context);
        String defaultCurrency = account.getCurrency() != null ? account.getCurrency().name() : "USD";

        Map<String, Object> params = new HashMap<String, Object>();

        // Stripe doesn't support anything else yet
        ArrayList<String> paymentMethodTypes = new ArrayList<>();
        paymentMethodTypes.add("card");
        params.put("payment_method_types", paymentMethodTypes);

        ArrayList<HashMap<String, Object>> lineItems = new ArrayList<>();
        HashMap<String, Object> lineItem = new HashMap<String, Object>();
        lineItem.put("name", PluginProperties.getValue("line_item_name", "Authorization charge", customFields));
        lineItem.put("amount", PluginProperties.getValue("line_item_amount", "100", customFields));
        lineItem.put("currency", PluginProperties.getValue("line_item_currency", defaultCurrency, customFields));
        lineItem.put("quantity", PluginProperties.getValue("line_item_quantity", "1", customFields));
        lineItems.add(lineItem);
        params.put("line_items", lineItems);

        HashMap<String, Object> paymentIntentData = new HashMap<String, Object>();
        // Auth only
        paymentIntentData.put("capture_method", "manual");
        params.put("payment_intent_data", paymentIntentData);

        params.put("success_url", PluginProperties.getValue("success_url", "https://example.com/success", customFields));
        params.put("cancel_url", PluginProperties.getValue("cancel_url", "https://example.com/cancel", customFields));

        try {
            logger.info("Creating Stripe session");
            final Session session = Session.create(params, buildRequestOptions(context));

            dao.addHppRequest(kbAccountId,
                              null,
                              null,
                              session,
                              clock.getUTCNow(),
                              context.getTenantId());
            return new PluginHostedPaymentPageFormDescriptor(kbAccountId, null, PluginProperties.buildPluginProperties(StripePluginProperties.toAdditionalDataMap(session)));
        } catch (final StripeException e) {
            throw new PaymentPluginApiException("Unable to create Stripe session", e);
        } catch (final SQLException e) {
            throw new PaymentPluginApiException("Unable to save Stripe session", e);
        }
    }

    @Override
    public GatewayNotification processNotification(final String notification, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        throw new PaymentPluginApiException("INTERNAL", "#processNotification not yet implemented, please contact support@killbill.io");
    }

    private abstract static class TransactionExecutor<T> {

        public T execute(final Account account, final StripePaymentMethodsRecord paymentMethodsRecord) throws StripeException {
            throw new UnsupportedOperationException();

        }

        public T execute(final Account account, final StripePaymentMethodsRecord paymentMethodsRecord, final StripeResponsesRecord previousResponse) throws StripeException {
            throw new UnsupportedOperationException();
        }
    }

    private PaymentTransactionInfoPlugin executeInitialTransaction(final TransactionType transactionType,
                                                                   final UUID kbAccountId,
                                                                   final UUID kbPaymentId,
                                                                   final UUID kbTransactionId,
                                                                   final UUID kbPaymentMethodId,
                                                                   final BigDecimal amount,
                                                                   final Currency currency,
                                                                   final Iterable<PluginProperty> properties,
                                                                   final CallContext context) throws PaymentPluginApiException {
        final String customerId = getCustomerId(kbAccountId, context);
        return executeInitialTransaction(transactionType,
                                         new TransactionExecutor<PaymentIntent>() {
                                             @Override
                                             public PaymentIntent execute(final Account account, final StripePaymentMethodsRecord paymentMethodsRecord) throws StripeException {
                                                 final RequestOptions requestOptions = buildRequestOptions(context);

                                                 Map<String, Object> paymentIntentParams = new HashMap<>();
                                                 paymentIntentParams.put("amount", KillBillMoney.toMinorUnits(currency.toString(), amount));
                                                 paymentIntentParams.put("currency", currency.toString());
                                                 paymentIntentParams.put("capture_method", transactionType == TransactionType.AUTHORIZE ? "manual" : "automatic");
                                                 // TODO Do we need to switch to manual confirmation to be able to set off_session=recurring?
                                                 paymentIntentParams.put("confirm", true);
                                                 paymentIntentParams.put("confirmation_method", "manual");
                                                 paymentIntentParams.put("customer", customerId);
                                                 paymentIntentParams.put("metadata", ImmutableMap.of("kbAccountId", kbAccountId,
                                                                                                     "kbPaymentId", kbPaymentId,
                                                                                                     "kbTransactionId", kbTransactionId,
                                                                                                     "kbPaymentMethodId", kbPaymentMethodId));

                                                 final Map additionalData = StripeDao.fromAdditionalData(paymentMethodsRecord.getAdditionalData());
                                                 final String objectType = MoreObjects.firstNonNull((String) additionalData.get("object"), "payment_method");
                                                 if ("payment_method".equals(objectType)) {
                                                     paymentIntentParams.put(objectType, paymentMethodsRecord.getStripeId());
                                                 } else {
                                                     paymentIntentParams.put("source", paymentMethodsRecord.getStripeId());
                                                 }
                                                 paymentIntentParams.put("payment_method_types", ImmutableList.of("card", "ach_debit"));

                                                 final StripeConfigProperties stripeConfigProperties = stripeConfigPropertiesConfigurationHandler.getConfigurable(context.getTenantId());
                                                 paymentIntentParams.put("description", stripeConfigProperties.getChargeDescription());
                                                 paymentIntentParams.put("statement_descriptor", stripeConfigProperties.getChargeStatementDescriptor());

                                                 logger.info("Creating Stripe PaymentIntent");
                                                 return PaymentIntent.create(paymentIntentParams, requestOptions);
                                             }
                                         },
                                         kbAccountId,
                                         kbPaymentId,
                                         kbTransactionId,
                                         kbPaymentMethodId,
                                         amount,
                                         currency,
                                         properties,
                                         context);
    }

    private PaymentTransactionInfoPlugin executeInitialTransaction(final TransactionType transactionType,
                                                                   final TransactionExecutor<PaymentIntent> transactionExecutor,
                                                                   final UUID kbAccountId,
                                                                   final UUID kbPaymentId,
                                                                   final UUID kbTransactionId,
                                                                   final UUID kbPaymentMethodId,
                                                                   final BigDecimal amount,
                                                                   final Currency currency,
                                                                   final Iterable<PluginProperty> properties,
                                                                   final TenantContext context) throws PaymentPluginApiException {
        final Account account = getAccount(kbAccountId, context);
        final StripePaymentMethodsRecord nonNullPaymentMethodsRecord = getStripePaymentMethodsRecord(kbPaymentMethodId, context);
        final DateTime utcNow = clock.getUTCNow();

        final PaymentIntent response;
        if (shouldSkipStripe(properties)) {
            throw new UnsupportedOperationException("TODO");
        } else {
            try {
                response = transactionExecutor.execute(account, nonNullPaymentMethodsRecord);
            } catch (final StripeException e) {
                throw new PaymentPluginApiException("Error connecting to Stripe", e);
            }
        }

        try {
            final StripeResponsesRecord responsesRecord = dao.addResponse(kbAccountId, kbPaymentId, kbTransactionId, transactionType, amount, currency, response, utcNow, context.getTenantId());
            return StripePaymentTransactionInfoPlugin.build(responsesRecord);
        } catch (final SQLException e) {
            throw new PaymentPluginApiException("Payment went through, but we encountered a database error. Payment details: " + response.toString(), e);
        }
    }

    private PaymentTransactionInfoPlugin executeFollowUpTransaction(final TransactionType transactionType,
                                                                    final TransactionExecutor<PaymentIntent> transactionExecutor,
                                                                    final UUID kbAccountId,
                                                                    final UUID kbPaymentId,
                                                                    final UUID kbTransactionId,
                                                                    final UUID kbPaymentMethodId,
                                                                    @Nullable final BigDecimal amount,
                                                                    @Nullable final Currency currency,
                                                                    final Iterable<PluginProperty> properties,
                                                                    final TenantContext context) throws PaymentPluginApiException {
        final Account account = getAccount(kbAccountId, context);
        final StripePaymentMethodsRecord nonNullPaymentMethodsRecord = getStripePaymentMethodsRecord(kbPaymentMethodId, context);

        final StripeResponsesRecord previousResponse;
        try {
            previousResponse = dao.getSuccessfulAuthorizationResponse(kbPaymentId, context.getTenantId());
            if (previousResponse == null) {
                throw new PaymentPluginApiException(null, "Unable to retrieve previous payment response for kbTransactionId " + kbTransactionId);
            }
        } catch (final SQLException e) {
            throw new PaymentPluginApiException("Unable to retrieve previous payment response for kbTransactionId " + kbTransactionId, e);
        }

        final DateTime utcNow = clock.getUTCNow();

        final PaymentIntent response;
        if (shouldSkipStripe(properties)) {
            throw new UnsupportedOperationException("TODO");
        } else {
            try {
                response = transactionExecutor.execute(account, nonNullPaymentMethodsRecord, previousResponse);
            } catch (final StripeException e) {
                throw new PaymentPluginApiException("Error connecting to Stripe", e);
            }
        }

        try {
            final StripeResponsesRecord responsesRecord = dao.addResponse(kbAccountId, kbPaymentId, kbTransactionId, transactionType, amount, currency, response, utcNow, context.getTenantId());
            return StripePaymentTransactionInfoPlugin.build(responsesRecord);
        } catch (final SQLException e) {
            throw new PaymentPluginApiException("Payment went through, but we encountered a database error. Payment details: " + (response.toString()), e);
        }
    }

    private String getCustomerId(final UUID kbAccountId, final CallContext context) throws PaymentPluginApiException {
        final String stripeCustomerId = getCustomerIdNoException(kbAccountId, context);
        if (stripeCustomerId == null) {
            throw new PaymentPluginApiException("INTERNAL", "Missing STRIPE_CUSTOMER_ID custom field");
        }
        return stripeCustomerId;
    }

    private String getCustomerIdNoException(final UUID kbAccountId, final CallContext context) {
        final List<CustomField> customFields = killbillAPI.getCustomFieldUserApi().getCustomFieldsForAccountType(kbAccountId, ObjectType.ACCOUNT, context);
        String stripeCustomerId = null;
        for (final CustomField customField : customFields) {
            if (customField.getFieldName().equals("STRIPE_CUSTOMER_ID")) {
                stripeCustomerId = customField.getFieldValue();
                break;
            }
        }
        return stripeCustomerId;
    }

    private StripePaymentMethodsRecord getStripePaymentMethodsRecord(@Nullable final UUID kbPaymentMethodId, final TenantContext context) throws PaymentPluginApiException {
        StripePaymentMethodsRecord paymentMethodsRecord = null;

        if (kbPaymentMethodId != null) {
            try {
                paymentMethodsRecord = dao.getPaymentMethod(kbPaymentMethodId, context.getTenantId());
            } catch (final SQLException e) {
                throw new PaymentPluginApiException("Failed to retrieve payment method", e);
            }
        }

        return MoreObjects.firstNonNull(paymentMethodsRecord, emptyRecord(kbPaymentMethodId));
    }

    private StripePaymentMethodsRecord emptyRecord(@Nullable final UUID kbPaymentMethodId) {
        final StripePaymentMethodsRecord record = new StripePaymentMethodsRecord();
        if (kbPaymentMethodId != null) {
            record.setKbPaymentMethodId(kbPaymentMethodId.toString());
        }
        return record;
    }

    private boolean shouldSkipStripe(final Iterable<PluginProperty> properties) {
        return "true".equals(PluginProperties.findPluginPropertyValue("skipGw", properties)) || "true".equals(PluginProperties.findPluginPropertyValue("skip_gw", properties));
    }

}