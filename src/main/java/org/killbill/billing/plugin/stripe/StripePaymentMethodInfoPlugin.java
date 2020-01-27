/*
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

import java.util.UUID;

import org.killbill.billing.plugin.api.payment.PluginPaymentMethodInfoPlugin;
import org.killbill.billing.plugin.stripe.dao.StripeDao;
import org.killbill.billing.plugin.stripe.dao.gen.tables.records.StripePaymentMethodsRecord;

public class StripePaymentMethodInfoPlugin extends PluginPaymentMethodInfoPlugin {

    public static StripePaymentMethodInfoPlugin build(final StripePaymentMethodsRecord stripePaymentMethodsRecord) {
        return new StripePaymentMethodInfoPlugin(UUID.fromString(stripePaymentMethodsRecord.getKbAccountId()),
                                                 UUID.fromString(stripePaymentMethodsRecord.getKbPaymentMethodId()),
                                                 stripePaymentMethodsRecord.getIsDefault() == StripeDao.TRUE,
                                                 stripePaymentMethodsRecord.getStripeId());
    }

    public StripePaymentMethodInfoPlugin(final UUID accountId,
                                         final UUID paymentMethodId,
                                         final boolean isDefault,
                                         final String externalPaymentMethodId) {
        super(accountId,
              paymentMethodId,
              isDefault,
              externalPaymentMethodId);
    }
}
