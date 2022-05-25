/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.plugin.TestUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.common.collect.ImmutableList;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Checks if the plugin could handle technical communication errors (strange responses, read/connect timeouts etc...) and map them to the correct PaymentPluginStatus.
 * <p/>
 * WireMock is used to create failure scenarios (toxiproxy will be used in the ruby ITs).
 * <p/>
 * Attention: If you have failing tests check first that you don't have a proxy configured (Charles, Fiddler, Burp etc...).
 */
public class TestStripePaymentPluginApiWithErrors extends TestBase {

    @BeforeMethod(groups = "slow")
    public void setupConfig() throws Exception {
        final Properties properties = new Properties();
        properties.put("org.killbill.billing.plugin.stripe.apiKey", "unused");
        properties.put("org.killbill.billing.plugin.stripe.apiBase", WireMockHelper.wireMockUri("/"));

        final StripeConfigProperties stripeConfigProperties = new StripeConfigProperties(properties, "");
        stripeConfigPropertiesConfigurationHandler.setDefaultConfigurable(stripeConfigProperties);
    }

    @Test(groups = "slow")
    public void testPurchaseConnectionException() throws Exception {
        final UUID kbPaymentMethodId = UUID.randomUUID();
        dao.addPaymentMethod(account.getId(), kbPaymentMethodId, Collections.emptyMap(), "token", clock.getUTCNow(), context.getTenantId());

        final Payment payment = TestUtils.buildPayment(account.getId(), kbPaymentMethodId, Currency.EUR, killbillApi);
        final PaymentTransaction purchaseTransaction = TestUtils.buildPaymentTransaction(payment, TransactionType.PURCHASE, BigDecimal.TEN, payment.getCurrency());
        final PaymentTransactionInfoPlugin result = stripePaymentPluginApi.purchasePayment(account.getId(),
                                                                                           payment.getId(),
                                                                                           purchaseTransaction.getId(),
                                                                                           kbPaymentMethodId,
                                                                                           purchaseTransaction.getAmount(),
                                                                                           purchaseTransaction.getCurrency(),
                                                                                           ImmutableList.of(),
                                                                                           context);

        assertEquals(result.getStatus(), PaymentPluginStatus.CANCELED);
        assertNull(result.getGatewayError());
        assertNull(result.getGatewayErrorCode());

        final List<PaymentTransactionInfoPlugin> results = stripePaymentPluginApi.getPaymentInfo(account.getId(), payment.getId(), ImmutableList.<PluginProperty>of(), context);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getStatus(), PaymentPluginStatus.CANCELED);
        assertNull(results.get(0).getGatewayError());
        assertNull(results.get(0).getGatewayErrorCode());
    }

    @Test(groups = "slow")
    public void testPurchase500Error() throws Exception {
        final UUID kbPaymentMethodId = UUID.randomUUID();
        dao.addPaymentMethod(account.getId(), kbPaymentMethodId, Collections.emptyMap(), "token", clock.getUTCNow(), context.getTenantId());

        final Payment payment = TestUtils.buildPayment(account.getId(), kbPaymentMethodId, Currency.EUR, killbillApi);
        final PaymentTransaction purchaseTransaction = TestUtils.buildPaymentTransaction(payment, TransactionType.PURCHASE, BigDecimal.TEN, payment.getCurrency());

        final PaymentTransactionInfoPlugin result = WireMockHelper.doWithWireMock(new WithWireMock<>() {
            @Override
            public PaymentTransactionInfoPlugin execute(final WireMockServer server) throws PaymentPluginApiException {
                stubFor(any(anyUrl()).willReturn(serverError().withBody("{\n" +
                                                                        "  \"error\": {\n" +
                                                                        "    \"code\": \"parameter_unknown\",\n" +
                                                                        "    \"doc_url\": \"https://stripe.com/docs/error-codes/parameter-unknown\",\n" +
                                                                        "    \"message\": \"Received unknown parameter: foo\",\n" +
                                                                        "    \"param\": \"foo\",\n" +
                                                                        "    \"type\": \"invalid_request_error\"\n" +
                                                                        "  }\n" +
                                                                        "}")));

                return stripePaymentPluginApi.purchasePayment(account.getId(),
                                                              payment.getId(),
                                                              purchaseTransaction.getId(),
                                                              kbPaymentMethodId,
                                                              purchaseTransaction.getAmount(),
                                                              purchaseTransaction.getCurrency(),
                                                              ImmutableList.of(),
                                                              context);
            }
        });

        assertEquals(result.getStatus(), PaymentPluginStatus.UNDEFINED);
        assertEquals(result.getGatewayError(), "Received unknown parameter: foo");
        assertEquals(result.getGatewayErrorCode(), "parameter_unknown");

        final List<PaymentTransactionInfoPlugin> results = stripePaymentPluginApi.getPaymentInfo(account.getId(), payment.getId(), ImmutableList.<PluginProperty>of(), context);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getStatus(), PaymentPluginStatus.UNDEFINED);
        assertEquals(results.get(0).getGatewayError(), "Received unknown parameter: foo");
        assertEquals(results.get(0).getGatewayErrorCode(), "parameter_unknown");
    }

    private interface WithWireMock<T> {

        T execute(WireMockServer server) throws Exception;
    }

    static class WireMockHelper {

        private static final WireMockHelper INSTANCE = new WireMockHelper();

        public static WireMockHelper instance() {
            return INSTANCE;
        }

        private int freePort = -1;

        private synchronized int getFreePort() throws IOException {
            if (freePort == -1) {
                freePort = findFreePort();
            }
            return freePort;
        }

        public static String wireMockUri(final String path) throws IOException {
            return "http://localhost:" + WireMockHelper.instance().getFreePort() + path;
        }

        public static <T> T doWithWireMock(final WithWireMock<T> command) throws Exception {
            final int wireMockPort = WireMockHelper.instance().getFreePort();
            final WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(wireMockPort));
            wireMockServer.start();
            WireMock.configureFor("localhost", wireMockPort);
            try {
                return command.execute(wireMockServer);
            } finally {
                wireMockServer.shutdown();
                while (wireMockServer.isRunning()) {
                    Thread.sleep(1);
                }
            }
        }
    }

    static int findFreePort() throws IOException {
        final ServerSocket serverSocket = new ServerSocket(0);
        final int freePort = serverSocket.getLocalPort();
        serverSocket.close();
        return freePort;
    }
}
