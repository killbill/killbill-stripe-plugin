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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Named;
import javax.inject.Singleton;

import org.jooby.MediaType;
import org.jooby.Result;
import org.jooby.Results;
import org.jooby.Status;
import org.jooby.mvc.Local;
import org.jooby.mvc.POST;
import org.jooby.mvc.Path;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillClock;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.plugin.api.HostedPaymentPageFormDescriptor;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.plugin.api.PluginCallContext;
import org.killbill.billing.plugin.core.resources.PluginHealthcheck;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.util.callcontext.CallContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

@Singleton
// Handle /plugins/killbill-stripe/checkout
@Path("/checkout")
public class StripeCheckoutServlet extends PluginHealthcheck {

    private final OSGIKillbillClock clock;
    private final StripePaymentPluginApi stripePaymentPluginApi;

    @Inject
    public StripeCheckoutServlet(final OSGIKillbillClock clock,
                                 final StripePaymentPluginApi stripePaymentPluginApi) {
        this.clock = clock;
        this.stripePaymentPluginApi = stripePaymentPluginApi;
    }

    @POST
    public Result createSession(@Named("kbAccountId") final UUID kbAccountId,
                                @Named("successUrl") final Optional<String> successUrl,
                                @Named("cancelUrl") final Optional<String> cancelUrl,
                                @Named("kbInvoiceId") final Optional<String> kbInvoiceId,
                                @Named("paymentMethodTypes") final Optional<List<String>> paymentMethodTypes,
                                @Local @Named("killbill_tenant") final Tenant tenant,
                                @Named("billingAddressCollection") final Optional<String> billingAddressCollection) throws JsonProcessingException, PaymentPluginApiException {
        final CallContext context = new PluginCallContext(StripeActivator.PLUGIN_NAME, clock.getClock().getUTCNow(), kbAccountId, tenant.getId());
        final ImmutableList<PluginProperty> customFields = ImmutableList.of(
                new PluginProperty("kb_account_id", kbAccountId.toString(), false),
                new PluginProperty("kb_invoice_id", kbInvoiceId.orElse(null), false),
                new PluginProperty("success_url", successUrl.orElse("https://example.com/success?sessionId={CHECKOUT_SESSION_ID}"), false),
                new PluginProperty("cancel_url", cancelUrl.orElse("https://example.com/cancel"), false),
                new PluginProperty("payment_method_types", paymentMethodTypes.orElse(null), false),
                new PluginProperty("billing_address_collection", billingAddressCollection.orElse("auto"), false));
        final HostedPaymentPageFormDescriptor hostedPaymentPageFormDescriptor = stripePaymentPluginApi.buildFormDescriptor(kbAccountId,
                                                                                                                           customFields,
                                                                                                                           ImmutableList.of(),
                                                                                                                           context);
        return Results.with(hostedPaymentPageFormDescriptor, Status.CREATED)
                      .type(MediaType.json);
    }
}
