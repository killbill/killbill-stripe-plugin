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

import java.util.Hashtable;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;

import org.killbill.billing.osgi.api.Healthcheck;
import org.killbill.billing.osgi.api.OSGIPluginProperties;
import org.killbill.billing.osgi.libs.killbill.KillbillActivatorBase;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.plugin.api.notification.PluginConfigurationEventHandler;
import org.killbill.billing.plugin.core.config.PluginEnvironmentConfig;
import org.killbill.billing.plugin.core.resources.jooby.PluginApp;
import org.killbill.billing.plugin.core.resources.jooby.PluginAppBuilder;
import org.killbill.billing.plugin.stripe.dao.StripeDao;
import org.osgi.framework.BundleContext;

import com.stripe.Stripe;

public class StripeActivator extends KillbillActivatorBase {

    public static final String PLUGIN_NAME = "killbill-stripe";

    private StripeConfigPropertiesConfigurationHandler stripeConfigPropertiesConfigurationHandler;

    @Override
    public void start(final BundleContext context) throws Exception {
        super.start(context);

        final StripeDao stripeDao = new StripeDao(dataSource.getDataSource());

        final String region = PluginEnvironmentConfig.getRegion(configProperties.getProperties());
        stripeConfigPropertiesConfigurationHandler = new StripeConfigPropertiesConfigurationHandler(PLUGIN_NAME,
                                                                                                    killbillAPI,
                                                                                                    region);

        final StripeConfigProperties stripeConfigProperties = stripeConfigPropertiesConfigurationHandler.createConfigurable(
                configProperties.getProperties());
        stripeConfigPropertiesConfigurationHandler.setDefaultConfigurable(stripeConfigProperties);

        // Expose the healthcheck, so other plugins can check on the Stripe status
        final StripeHealthcheck stripeHealthcheck = new StripeHealthcheck(stripeConfigPropertiesConfigurationHandler);
        registerHealthcheck(context, stripeHealthcheck);

        // Register the payment plugin
        Stripe.setAppInfo("Kill Bill", "7.1.0", "https://killbill.io");
        final StripePaymentPluginApi pluginApi = new StripePaymentPluginApi(stripeConfigPropertiesConfigurationHandler,
                                                                            killbillAPI,
                                                                            configProperties,
                                                                            clock.getClock(),
                                                                            stripeDao
        );
        registerPaymentPluginApi(context, pluginApi);

        // Register the servlet
        final PluginApp pluginApp = new PluginAppBuilder(PLUGIN_NAME,
                                                         killbillAPI,
                                                         dataSource,
                                                         super.clock,
                                                         configProperties).withRouteClass(StripeHealthcheckServlet.class)
                                                                          .withRouteClass(StripeCheckoutServlet.class)
                                                                          .withService(stripeHealthcheck)
                                                                          .withService(pluginApi)
                                                                          .withService(clock)
                                                                          .build();
        final HttpServlet stripeServlet = PluginApp.createServlet(pluginApp);
        registerServlet(context, stripeServlet);

        registerHandlers();
    }

    public void registerHandlers() {
        final PluginConfigurationEventHandler handler = new PluginConfigurationEventHandler(stripeConfigPropertiesConfigurationHandler);
        dispatcher.registerEventHandlers(handler);
    }

    private void registerServlet(final BundleContext context, final HttpServlet servlet) {
        final Hashtable<String, String> props = new Hashtable<String, String>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, Servlet.class, servlet, props);
    }

    private void registerPaymentPluginApi(final BundleContext context, final PaymentPluginApi api) {
        final Hashtable<String, String> props = new Hashtable<String, String>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, PaymentPluginApi.class, api, props);
    }

    private void registerHealthcheck(final BundleContext context, final StripeHealthcheck healthcheck) {
        final Hashtable<String, String> props = new Hashtable<String, String>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, Healthcheck.class, healthcheck, props);
    }
}
