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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.plugin.api.PluginProperties;
import org.killbill.billing.plugin.api.payment.PluginPaymentTransactionInfoPlugin;

import com.google.common.base.Strings;
import org.killbill.billing.plugin.stripe.dao.StripeDao;
import org.killbill.billing.plugin.stripe.dao.gen.tables.records.StripeResponsesRecord;

public class StripePaymentTransactionInfoPlugin extends PluginPaymentTransactionInfoPlugin {

    // Kill Bill limits the field size to 32
    private static final int ERROR_CODE_MAX_LENGTH = 32;

    private final StripeResponsesRecord stripeResponseRecord;

    public static StripePaymentTransactionInfoPlugin build(final StripeResponsesRecord stripeResponsesRecord) {
        final Map additionalData = StripeDao.fromAdditionalData(stripeResponsesRecord.getAdditionalData());
        final String firstPaymentReferenceId = (String) additionalData.get("last_charge_id");
        final String secondPaymentReferenceId = (String) additionalData.get("last_charge_authorization_code");

        final DateTime responseDate = new DateTime(stripeResponsesRecord.getCreatedDate(), DateTimeZone.UTC);

        return new StripePaymentTransactionInfoPlugin(stripeResponsesRecord,
                                                      UUID.fromString(stripeResponsesRecord.getKbPaymentId()),
                                                      UUID.fromString(stripeResponsesRecord.getKbPaymentTransactionId()),
                                                      TransactionType.valueOf(stripeResponsesRecord.getTransactionType()),
                                                      stripeResponsesRecord.getAmount(),
                                                      Strings.isNullOrEmpty(stripeResponsesRecord.getCurrency()) ? null : Currency.valueOf(stripeResponsesRecord.getCurrency()),
                                                      getPaymentPluginStatus(additionalData),
                                                      getGatewayError(additionalData),
                                                      truncate(getGatewayErrorCode(additionalData)),
                                                      firstPaymentReferenceId,
                                                      secondPaymentReferenceId,
                                                      responseDate,
                                                      responseDate,
                                                      PluginProperties.buildPluginProperties(additionalData));
    }

    private static PaymentPluginStatus getPaymentPluginStatus(final Map additionalData) {
        final String overriddenTransactionStatus = (String) additionalData.get(StripePaymentPluginApi.PROPERTY_OVERRIDDEN_TRANSACTION_STATUS);
        if (overriddenTransactionStatus != null) {
            return PaymentPluginStatus.valueOf(overriddenTransactionStatus);
        }

        final String status = (String) additionalData.get("status");
        final String lastChargeStatus = (String) additionalData.get("last_charge_status");
        if ("succeeded".equals(lastChargeStatus)) {
            return PaymentPluginStatus.PROCESSED;
        } else if ("pending".equals(lastChargeStatus)) {
            // Untestable - see https://stripe.com/docs/ach#ach-payments-workflow
            return PaymentPluginStatus.PENDING;
        } else if ("failed".equals(lastChargeStatus)) {
            // TODO Do better (look at the type of error to narrow down on CANCELED)!
            return PaymentPluginStatus.ERROR;
        } else if (lastChargeStatus == null) {
            if ("requires_action".equals(status)) {
                // 3DS
                return PaymentPluginStatus.PENDING;
            }
            if ("canceled".equals(status)) {
                // Intent has been cancelled, mark this as error.
                return PaymentPluginStatus.ERROR;
            }
            return PaymentPluginStatus.UNDEFINED;
        } else {
            return PaymentPluginStatus.UNDEFINED;
        }
    }

    private static String getGatewayError(final Map additionalData) {
        return (String) additionalData.get("last_charge_failure_message");
    }

    private static String getGatewayErrorCode(final Map additionalData) {
        return (String) additionalData.get("last_charge_failure_code");
    }

    private static String truncate(@Nullable final String string) {
        if (string == null) {
            return null;
        } else if (string.length() <= ERROR_CODE_MAX_LENGTH) {
            return string;
        } else {
            return string.substring(0, ERROR_CODE_MAX_LENGTH);
        }
    }

    public StripePaymentTransactionInfoPlugin(final StripeResponsesRecord stripeResponsesRecord,
                                              final UUID kbPaymentId,
                                              final UUID kbTransactionPaymentPaymentId,
                                              final TransactionType transactionType,
                                              final BigDecimal amount,
                                              final Currency currency,
                                              final PaymentPluginStatus pluginStatus,
                                              final String gatewayError,
                                              final String gatewayErrorCode,
                                              final String firstPaymentReferenceId,
                                              final String secondPaymentReferenceId,
                                              final DateTime createdDate,
                                              final DateTime effectiveDate,
                                              final List<PluginProperty> properties) {
        super(kbPaymentId,
              kbTransactionPaymentPaymentId,
              transactionType,
              amount,
              currency,
              pluginStatus,
              gatewayError,
              gatewayErrorCode,
              firstPaymentReferenceId,
              secondPaymentReferenceId,
              createdDate,
              effectiveDate,
              properties);
        this.stripeResponseRecord = stripeResponsesRecord;
    }

    public StripeResponsesRecord getStripeResponseRecord() {
        return stripeResponseRecord;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final StripePaymentTransactionInfoPlugin that = (StripePaymentTransactionInfoPlugin) o;

        return stripeResponseRecord != null ? stripeResponseRecord.equals(that.stripeResponseRecord) : that.stripeResponseRecord == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (stripeResponseRecord != null ? stripeResponseRecord.hashCode() : 0);
        return result;
    }
}
