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

package org.killbill.billing.plugin.stripe.dao;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.sql.DataSource;

import org.joda.time.DateTime;
import org.jooq.impl.DSL;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.plugin.api.PluginProperties;
import org.killbill.billing.plugin.dao.payment.PluginPaymentDao;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.common.collect.ImmutableMap;
import org.killbill.billing.plugin.stripe.StripePluginProperties;
import org.killbill.billing.plugin.stripe.dao.gen.tables.StripePaymentMethods;
import org.killbill.billing.plugin.stripe.dao.gen.tables.StripeResponses;
import org.killbill.billing.plugin.stripe.dao.gen.tables.records.StripeHppRequestsRecord;
import org.killbill.billing.plugin.stripe.dao.gen.tables.records.StripePaymentMethodsRecord;
import org.killbill.billing.plugin.stripe.dao.gen.tables.records.StripeResponsesRecord;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;

import static org.killbill.billing.plugin.stripe.dao.gen.tables.StripeHppRequests.STRIPE_HPP_REQUESTS;
import static org.killbill.billing.plugin.stripe.dao.gen.tables.StripePaymentMethods.STRIPE_PAYMENT_METHODS;
import static org.killbill.billing.plugin.stripe.dao.gen.tables.StripeResponses.STRIPE_RESPONSES;

public class StripeDao extends PluginPaymentDao<StripeResponsesRecord, StripeResponses, StripePaymentMethodsRecord, StripePaymentMethods> {

    public StripeDao(final DataSource dataSource) throws SQLException {
        super(STRIPE_RESPONSES, STRIPE_PAYMENT_METHODS, dataSource);
        // Save space in the database
        objectMapper.setSerializationInclusion(Include.NON_EMPTY);
    }

    // Payment methods

    public void addPaymentMethod(final UUID kbAccountId,
                                 final UUID kbPaymentMethodId,
                                 final Map<String, Object> additionalDataMap,
                                 final String stripeId,
                                 final DateTime utcNow,
                                 final UUID kbTenantId) throws SQLException {
        execute(dataSource.getConnection(),
                new WithConnectionCallback<StripeResponsesRecord>() {
                    @Override
                    public StripeResponsesRecord withConnection(final Connection conn) throws SQLException {
                        DSL.using(conn, dialect, settings)
                           .insertInto(STRIPE_PAYMENT_METHODS,
                                       STRIPE_PAYMENT_METHODS.KB_ACCOUNT_ID,
                                       STRIPE_PAYMENT_METHODS.KB_PAYMENT_METHOD_ID,
                                       STRIPE_PAYMENT_METHODS.STRIPE_ID,
                                       STRIPE_PAYMENT_METHODS.IS_DELETED,
                                       STRIPE_PAYMENT_METHODS.ADDITIONAL_DATA,
                                       STRIPE_PAYMENT_METHODS.CREATED_DATE,
                                       STRIPE_PAYMENT_METHODS.UPDATED_DATE,
                                       STRIPE_PAYMENT_METHODS.KB_TENANT_ID)
                           .values(kbAccountId.toString(),
                                   kbPaymentMethodId.toString(),
                                   stripeId,
                                   (short) FALSE,
                                   asString(additionalDataMap),
                                   toLocalDateTime(utcNow),
                                   toLocalDateTime(utcNow),
                                   kbTenantId.toString()
                                   )
                           .execute();

                        return null;
                    }
                });
    }

    public void updatePaymentMethod(final UUID kbPaymentMethodId,
                                    final Map<String, Object> additionalDataMap,
                                    final String stripeId,
                                    final DateTime utcNow,
                                    final UUID kbTenantId) throws SQLException {
        execute(dataSource.getConnection(),
                new WithConnectionCallback<StripeResponsesRecord>() {
                    @Override
                    public StripeResponsesRecord withConnection(final Connection conn) throws SQLException {
                        DSL.using(conn, dialect, settings)
                           .update(STRIPE_PAYMENT_METHODS)
                           .set(STRIPE_PAYMENT_METHODS.ADDITIONAL_DATA, asString(additionalDataMap))
                           .set(STRIPE_PAYMENT_METHODS.UPDATED_DATE, toLocalDateTime(utcNow))
                           .where(STRIPE_PAYMENT_METHODS.KB_PAYMENT_METHOD_ID.equal(kbPaymentMethodId.toString()))
                           .and(STRIPE_PAYMENT_METHODS.STRIPE_ID.equal(stripeId))
                           .and(STRIPE_PAYMENT_METHODS.KB_TENANT_ID.equal(kbTenantId.toString()))
                           .execute();
                        return null;
                    }
                });
    }

    // HPP requests

    public void addHppRequest(final UUID kbAccountId,
                              final UUID kbPaymentId,
                              final UUID kbPaymentTransactionId,
                              final Session stripeSession,
                              final DateTime utcNow,
                              final UUID kbTenantId) throws SQLException {
        final Map<String, Object> additionalDataMap = StripePluginProperties.toAdditionalDataMap(stripeSession, null);

        execute(dataSource.getConnection(),
                new WithConnectionCallback<Void>() {
                    @Override
                    public Void withConnection(final Connection conn) throws SQLException {
                        DSL.using(conn, dialect, settings)
                           .insertInto(STRIPE_HPP_REQUESTS,
                                       STRIPE_HPP_REQUESTS.KB_ACCOUNT_ID,
                                       STRIPE_HPP_REQUESTS.KB_PAYMENT_ID,
                                       STRIPE_HPP_REQUESTS.KB_PAYMENT_TRANSACTION_ID,
                                       STRIPE_HPP_REQUESTS.SESSION_ID,
                                       STRIPE_HPP_REQUESTS.ADDITIONAL_DATA,
                                       STRIPE_HPP_REQUESTS.CREATED_DATE,
                                       STRIPE_HPP_REQUESTS.KB_TENANT_ID)
                           .values(kbAccountId.toString(),
                                   kbPaymentId == null ? null : kbPaymentId.toString(),
                                   kbPaymentTransactionId == null ? null : kbPaymentTransactionId.toString(),
                                   stripeSession.getId(),
                                   asString(additionalDataMap),
                                   toLocalDateTime(utcNow),
                                   kbTenantId.toString())
                           .execute();
                        return null;
                    }
                });
    }

    public StripeHppRequestsRecord getHppRequest(final String sessionId,
                                                 final String kbTenantId) throws SQLException {
        return execute(dataSource.getConnection(),
                       new WithConnectionCallback<StripeHppRequestsRecord>() {
                           @Override
                           public StripeHppRequestsRecord withConnection(final Connection conn) throws SQLException {
                               return DSL.using(conn, dialect, settings)
                                         .selectFrom(STRIPE_HPP_REQUESTS)
                                         .where(STRIPE_HPP_REQUESTS.SESSION_ID.equal(sessionId))
                                         .and(STRIPE_HPP_REQUESTS.KB_TENANT_ID.equal(kbTenantId))
                                         .orderBy(STRIPE_HPP_REQUESTS.RECORD_ID.desc())
                                         .limit(1)
                                         .fetchOne();
                           }
                       });
    }

    // Responses

    public StripeResponsesRecord addResponse(final UUID kbAccountId,
                                             final UUID kbPaymentId,
                                             final UUID kbPaymentTransactionId,
                                             final TransactionType transactionType,
                                             final BigDecimal amount,
                                             final Currency currency,
                                             final PaymentIntent stripePaymentIntent,
                                             final DateTime utcNow,
                                             final UUID kbTenantId) throws SQLException {
        final Map<String, Object> additionalDataMap = StripePluginProperties.toAdditionalDataMap(stripePaymentIntent);

        return execute(dataSource.getConnection(),
                       new WithConnectionCallback<StripeResponsesRecord>() {
                           @Override
                           public StripeResponsesRecord withConnection(final Connection conn) throws SQLException {
                               return DSL.using(conn, dialect, settings)
                                         .insertInto(STRIPE_RESPONSES,
                                                     STRIPE_RESPONSES.KB_ACCOUNT_ID,
                                                     STRIPE_RESPONSES.KB_PAYMENT_ID,
                                                     STRIPE_RESPONSES.KB_PAYMENT_TRANSACTION_ID,
                                                     STRIPE_RESPONSES.TRANSACTION_TYPE,
                                                     STRIPE_RESPONSES.AMOUNT,
                                                     STRIPE_RESPONSES.CURRENCY,
                                                     STRIPE_RESPONSES.STRIPE_ID,
                                                     STRIPE_RESPONSES.ADDITIONAL_DATA,
                                                     STRIPE_RESPONSES.CREATED_DATE,
                                                     STRIPE_RESPONSES.KB_TENANT_ID)
                                         .values(kbAccountId.toString(),
                                                 kbPaymentId.toString(),
                                                 kbPaymentTransactionId.toString(),
                                                 transactionType.toString(),
                                                 amount,
                                                 currency == null ? null : currency.name(),
                                                 stripePaymentIntent.getId(),
                                                 asString(additionalDataMap),
                                                 toLocalDateTime(utcNow),
                                                 kbTenantId.toString())
                                         .returning()
                                         .fetchOne();
                           }
                       });
    }

    public StripeResponsesRecord updateResponse(final UUID kbPaymentTransactionId,
                                                final PaymentIntent stripePaymentIntent,
                                                final UUID kbTenantId) throws SQLException {
        final Map<String, Object> additionalDataMap = StripePluginProperties.toAdditionalDataMap(stripePaymentIntent);
        return updateResponse(kbPaymentTransactionId, additionalDataMap, kbTenantId);
    }

    public StripeResponsesRecord updateResponse(final UUID kbPaymentTransactionId,
                                                final Iterable<PluginProperty> additionalPluginProperties,
                                                final UUID kbTenantId) throws SQLException {
        final Map<String, Object> additionalProperties = PluginProperties.toMap(additionalPluginProperties);
        return updateResponse(kbPaymentTransactionId, additionalProperties, kbTenantId);
    }

    public StripeResponsesRecord updateResponse(final UUID kbPaymentTransactionId,
                                                final Map<String, Object> additionalProperties,
                                                final UUID kbTenantId) throws SQLException {
        return execute(dataSource.getConnection(),
                       new WithConnectionCallback<StripeResponsesRecord>() {
                           @Override
                           public StripeResponsesRecord withConnection(final Connection conn) throws SQLException {
                               final StripeResponsesRecord response = DSL.using(conn, dialect, settings)
                                                                         .selectFrom(STRIPE_RESPONSES)
                                                                         .where(STRIPE_RESPONSES.KB_PAYMENT_TRANSACTION_ID.equal(kbPaymentTransactionId.toString()))
                                                                         .and(STRIPE_RESPONSES.KB_TENANT_ID.equal(kbTenantId.toString()))
                                                                         .orderBy(STRIPE_RESPONSES.RECORD_ID.desc())
                                                                         .limit(1)
                                                                         .fetchOne();

                               if (response == null) {
                                   return null;
                               }

                               final Map originalData = new HashMap(fromAdditionalData(response.getAdditionalData()));
                               originalData.putAll(additionalProperties);

                               DSL.using(conn, dialect, settings)
                                  .update(STRIPE_RESPONSES)
                                  .set(STRIPE_RESPONSES.ADDITIONAL_DATA, asString(originalData))
                                  .where(STRIPE_RESPONSES.RECORD_ID.equal(response.getRecordId()))
                                  .execute();
                               return response;
                           }
                       });
    }

    public void updateResponse(final StripeResponsesRecord stripeResponsesRecord,
                               final Map additionalMetadata) throws SQLException {
        final Map additionalDataMap = fromAdditionalData(stripeResponsesRecord.getAdditionalData());
        additionalDataMap.putAll(additionalMetadata);

        execute(dataSource.getConnection(),
                new WithConnectionCallback<Void>() {
                    @Override
                    public Void withConnection(final Connection conn) throws SQLException {
                        DSL.using(conn, dialect, settings)
                           .update(STRIPE_RESPONSES)
                           .set(STRIPE_RESPONSES.ADDITIONAL_DATA, asString(additionalDataMap))
                           .where(STRIPE_RESPONSES.RECORD_ID.equal(stripeResponsesRecord.getRecordId()))
                           .execute();
                        return null;
                    }
                });
    }

    @Override
    public StripeResponsesRecord getSuccessfulAuthorizationResponse(final UUID kbPaymentId, final UUID kbTenantId) throws SQLException {
        return execute(dataSource.getConnection(),
                       new WithConnectionCallback<StripeResponsesRecord>() {
                           @Override
                           public StripeResponsesRecord withConnection(final Connection conn) throws SQLException {
                               return DSL.using(conn, dialect, settings)
                                         .selectFrom(responsesTable)
                                         .where(DSL.field(responsesTable.getName() + "." + KB_PAYMENT_ID).equal(kbPaymentId.toString()))
                                         .and(
                                                 DSL.field(responsesTable.getName() + "." + TRANSACTION_TYPE).equal(TransactionType.AUTHORIZE.toString())
                                                    .or(DSL.field(responsesTable.getName() + "." + TRANSACTION_TYPE).equal(TransactionType.PURCHASE.toString()))
                                             )
                                         .and(DSL.field(responsesTable.getName() + "." + KB_TENANT_ID).equal(kbTenantId.toString()))
                                         .orderBy(DSL.field(responsesTable.getName() + "." + RECORD_ID).desc())
                                         .limit(1)
                                         .fetchOne();
                           }
                       });
    }

    public static Map fromAdditionalData(@Nullable final String additionalData) {
        if (additionalData == null) {
            return ImmutableMap.of();
        }

        try {
            return objectMapper.readValue(additionalData, Map.class);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
