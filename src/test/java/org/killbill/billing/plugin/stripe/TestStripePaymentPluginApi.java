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
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.asynchttpclient.BoundRequestBuilder;
import org.joda.time.Period;
import org.killbill.billing.ObjectType;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.plugin.api.HostedPaymentPageFormDescriptor;
import org.killbill.billing.payment.plugin.api.PaymentMethodInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.plugin.TestUtils;
import org.killbill.billing.plugin.api.PluginProperties;
import org.killbill.billing.plugin.api.core.PluginCustomField;
import org.killbill.billing.plugin.api.payment.PluginPaymentMethodPlugin;
import org.killbill.billing.plugin.util.http.HttpClient;
import org.killbill.billing.plugin.util.http.ResponseFormat;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.stripe.exception.StripeException;
import com.stripe.model.BankAccount;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.PaymentSource;
import com.stripe.model.Token;
import com.stripe.net.RequestOptions;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestStripePaymentPluginApi extends TestBase {

    private Customer customer;
    private PaymentMethodPlugin paymentMethodPlugin;

    @Test(groups = "integration")
    public void testLegacyTokensAndChargesAPI() throws PaymentPluginApiException, StripeException, PaymentApiException {
        final UUID kbAccountId = account.getId();

        assertEquals(stripePaymentPluginApi.getPaymentMethods(kbAccountId, false, ImmutableList.<PluginProperty>of(), context).size(), 0);

        final Map<String, Object> card = new HashMap<>();
        card.put("number", "4242424242424242");
        card.put("exp_month", 1);
        card.put("exp_year", 2021);
        card.put("cvc", "314");
        final Map<String, Object> params = new HashMap<>();
        params.put("card", card);
        final RequestOptions options = stripePaymentPluginApi.buildRequestOptions(context);
        final Token token = Token.create(params, options);

        final UUID kbPaymentMethodId = UUID.randomUUID();
        stripePaymentPluginApi.addPaymentMethod(kbAccountId,
                                                kbPaymentMethodId,
                                                new PluginPaymentMethodPlugin(kbPaymentMethodId, null, false, ImmutableList.of()),
                                                true,
                                                ImmutableList.of(new PluginProperty("token", token.getId(), false)),
                                                context);

        final Payment payment1 = TestUtils.buildPayment(account.getId(), kbPaymentMethodId, account.getCurrency(), killbillApi);
        final PaymentTransaction purchaseTransaction1 = TestUtils.buildPaymentTransaction(payment1, TransactionType.PURCHASE, BigDecimal.TEN, payment1.getCurrency());
        final PaymentTransactionInfoPlugin purchaseInfoPlugin1 = stripePaymentPluginApi.purchasePayment(account.getId(),
                                                                                                        payment1.getId(),
                                                                                                        purchaseTransaction1.getId(),
                                                                                                        kbPaymentMethodId,
                                                                                                        purchaseTransaction1.getAmount(),
                                                                                                        purchaseTransaction1.getCurrency(),
                                                                                                        ImmutableList.of(),
                                                                                                        context);
        TestUtils.updatePaymentTransaction(purchaseTransaction1, purchaseInfoPlugin1);
        verifyPaymentTransactionInfoPlugin(payment1, purchaseTransaction1, purchaseInfoPlugin1, PaymentPluginStatus.PROCESSED);

        // Verify we can re-use the token
        final Payment payment2 = TestUtils.buildPayment(account.getId(), kbPaymentMethodId, account.getCurrency(), killbillApi);
        final PaymentTransaction purchaseTransaction2 = TestUtils.buildPaymentTransaction(payment2, TransactionType.PURCHASE, BigDecimal.TEN, payment2.getCurrency());
        final PaymentTransactionInfoPlugin purchaseInfoPlugin2 = stripePaymentPluginApi.purchasePayment(account.getId(),
                                                                                                        payment2.getId(),
                                                                                                        purchaseTransaction2.getId(),
                                                                                                        kbPaymentMethodId,
                                                                                                        purchaseTransaction2.getAmount(),
                                                                                                        purchaseTransaction2.getCurrency(),
                                                                                                        ImmutableList.of(),
                                                                                                        context);
        TestUtils.updatePaymentTransaction(purchaseTransaction2, purchaseInfoPlugin2);
        verifyPaymentTransactionInfoPlugin(payment2, purchaseTransaction2, purchaseInfoPlugin2, PaymentPluginStatus.PROCESSED);
    }

    @Test(groups = "integration")
    public void testLegacyTokensAndChargesAPICustomerCreatedOutsideOfKillBill() throws PaymentPluginApiException, StripeException, PaymentApiException {
        final UUID kbAccountId = account.getId();

        assertEquals(stripePaymentPluginApi.getPaymentMethods(kbAccountId, false, ImmutableList.<PluginProperty>of(), context).size(), 0);

        final Map<String, Object> card = new HashMap<>();
        card.put("number", "4242424242424242");
        card.put("exp_month", 1);
        card.put("exp_year", 2021);
        card.put("cvc", "314");
        final Map<String, Object> params = new HashMap<>();
        params.put("card", card);
        final RequestOptions options = stripePaymentPluginApi.buildRequestOptions(context);
        final Token token = Token.create(params, options);

        Map<String, Object> customerParams = new HashMap<>();
        customerParams.put("source", token.getId());
        final Customer customer = Customer.create(customerParams, options);

        assertTrue(customer.getDefaultSource().startsWith("card_"));

        // Add the magic Custom Field
        final CustomField customField = new PluginCustomField(kbAccountId,
                                                              ObjectType.ACCOUNT,
                                                              "STRIPE_CUSTOMER_ID",
                                                              customer.getId(),
                                                              clock.getUTCNow());
        Mockito.when(customFieldUserApi.getCustomFieldsForAccountType(Mockito.eq(kbAccountId), Mockito.eq(ObjectType.ACCOUNT), Mockito.any(TenantContext.class)))
               .thenReturn(ImmutableList.of(customField));

        // Sync Stripe <-> Kill Bill
        final List<PaymentMethodInfoPlugin> paymentMethods = stripePaymentPluginApi.getPaymentMethods(kbAccountId, true, ImmutableList.<PluginProperty>of(), context);

        final UUID kbPaymentMethodId = paymentMethods.get(0).getPaymentMethodId();

        final Payment payment1 = TestUtils.buildPayment(account.getId(), kbPaymentMethodId, account.getCurrency(), killbillApi);
        final PaymentTransaction purchaseTransaction1 = TestUtils.buildPaymentTransaction(payment1, TransactionType.PURCHASE, BigDecimal.TEN, payment1.getCurrency());
        final PaymentTransactionInfoPlugin purchaseInfoPlugin1 = stripePaymentPluginApi.purchasePayment(account.getId(),
                                                                                                        payment1.getId(),
                                                                                                        purchaseTransaction1.getId(),
                                                                                                        kbPaymentMethodId,
                                                                                                        purchaseTransaction1.getAmount(),
                                                                                                        purchaseTransaction1.getCurrency(),
                                                                                                        ImmutableList.of(),
                                                                                                        context);
        TestUtils.updatePaymentTransaction(purchaseTransaction1, purchaseInfoPlugin1);
        verifyPaymentTransactionInfoPlugin(payment1, purchaseTransaction1, purchaseInfoPlugin1, PaymentPluginStatus.PROCESSED);

        // Verify we can re-use the card
        final Payment payment2 = TestUtils.buildPayment(account.getId(), kbPaymentMethodId, account.getCurrency(), killbillApi);
        final PaymentTransaction purchaseTransaction2 = TestUtils.buildPaymentTransaction(payment2, TransactionType.PURCHASE, BigDecimal.TEN, payment2.getCurrency());
        final PaymentTransactionInfoPlugin purchaseInfoPlugin2 = stripePaymentPluginApi.purchasePayment(account.getId(),
                                                                                                        payment2.getId(),
                                                                                                        purchaseTransaction2.getId(),
                                                                                                        kbPaymentMethodId,
                                                                                                        purchaseTransaction2.getAmount(),
                                                                                                        purchaseTransaction2.getCurrency(),
                                                                                                        ImmutableList.of(),
                                                                                                        context);
        TestUtils.updatePaymentTransaction(purchaseTransaction2, purchaseInfoPlugin2);
        verifyPaymentTransactionInfoPlugin(payment2, purchaseTransaction2, purchaseInfoPlugin2, PaymentPluginStatus.PROCESSED);
    }

    @Test(groups = "integration")
    public void testVerifySyncOfPaymentMethods() throws PaymentPluginApiException, StripeException {
        final UUID kbAccountId = account.getId();

        assertEquals(stripePaymentPluginApi.getPaymentMethods(kbAccountId, false, ImmutableList.<PluginProperty>of(), context).size(), 0);

        createStripeCustomerWithCreditCardAndSyncPaymentMethod();

        final List<PaymentMethodInfoPlugin> paymentMethods = stripePaymentPluginApi.getPaymentMethods(kbAccountId, true, ImmutableList.<PluginProperty>of(), context);
        assertEquals(paymentMethods.size(), 1);
        assertEquals(paymentMethods.get(0).getAccountId(), kbAccountId);
        assertNotNull(paymentMethods.get(0).getExternalPaymentMethodId());
        final RequestOptions options = stripePaymentPluginApi.buildRequestOptions(context);
        final PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethods.get(0).getExternalPaymentMethodId(), options);
        assertEquals(paymentMethod.getCustomer(), customer.getId());

        // Verify update path
        PaymentMethodPlugin paymentMethodDetail = stripePaymentPluginApi.getPaymentMethodDetail(kbAccountId,
                                                                                                paymentMethods.get(0).getPaymentMethodId(),
                                                                                                ImmutableList.of(),
                                                                                                context);
        assertNull(PluginProperties.findPluginPropertyValue("metadata", paymentMethodDetail.getProperties()));

        // Update metadata in Stripe
        final Map<String, Object> metadata = new HashMap<>();
        metadata.put("testing", UUID.randomUUID().toString());
        final Map<String, Object> params = new HashMap<>();
        params.put("metadata", metadata);
        paymentMethod.update(params, options);

        stripePaymentPluginApi.getPaymentMethods(kbAccountId, true, ImmutableList.<PluginProperty>of(), context);
        paymentMethodDetail = stripePaymentPluginApi.getPaymentMethodDetail(kbAccountId,
                                                                            paymentMethods.get(0).getPaymentMethodId(),
                                                                            ImmutableList.of(),
                                                                            context);
        assertEquals(((Map) PluginProperties.toMap(paymentMethodDetail.getProperties()).get("metadata")).get("testing"), metadata.get("testing"));
    }

    @Test(groups = "integration")
    public void testDeletePaymentMethod() throws PaymentPluginApiException, StripeException {
        createStripeCustomerWithCreditCardAndSyncPaymentMethod();

        final UUID kbAccountId = account.getId();

        final List<PaymentMethodInfoPlugin> paymentMethods = stripePaymentPluginApi.getPaymentMethods(kbAccountId, true, ImmutableList.<PluginProperty>of(), context);
        assertEquals(paymentMethods.size(), 1);

        final RequestOptions options = stripePaymentPluginApi.buildRequestOptions(context);
        final PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethods.get(0).getExternalPaymentMethodId(), options);
        assertEquals(paymentMethod.getCustomer(), customer.getId());

        stripePaymentPluginApi.deletePaymentMethod(kbAccountId, paymentMethods.get(0).getPaymentMethodId(), ImmutableList.of(), context);

        // Nothing locally (before refresh)
        assertEquals(stripePaymentPluginApi.getPaymentMethods(kbAccountId, false, ImmutableList.<PluginProperty>of(), context).size(), 0);

        // Nothing locally (after refresh)
        assertEquals(stripePaymentPluginApi.getPaymentMethods(kbAccountId, true, ImmutableList.<PluginProperty>of(), context).size(), 0);

        assertNull(PaymentMethod.retrieve(paymentMethods.get(0).getExternalPaymentMethodId(), options).getCustomer());
    }

    @Test(groups = "integration")
    public void testSuccessfulAuthCapture() throws PaymentPluginApiException, StripeException, PaymentApiException {
        createStripeCustomerWithCreditCardAndSyncPaymentMethod();

        final Payment payment = TestUtils.buildPayment(account.getId(), account.getPaymentMethodId(), account.getCurrency(), killbillApi);
        final PaymentTransaction authorizationTransaction = TestUtils.buildPaymentTransaction(payment, TransactionType.AUTHORIZE, BigDecimal.TEN, payment.getCurrency());
        final PaymentTransaction captureTransaction = TestUtils.buildPaymentTransaction(payment, TransactionType.CAPTURE, BigDecimal.TEN, payment.getCurrency());

        final PaymentTransactionInfoPlugin authorizationInfoPlugin = stripePaymentPluginApi.authorizePayment(account.getId(),
                                                                                                             payment.getId(),
                                                                                                             authorizationTransaction.getId(),
                                                                                                             paymentMethodPlugin.getKbPaymentMethodId(),
                                                                                                             authorizationTransaction.getAmount(),
                                                                                                             authorizationTransaction.getCurrency(),
                                                                                                             ImmutableList.of(),
                                                                                                             context);
        TestUtils.updatePaymentTransaction(authorizationTransaction, authorizationInfoPlugin);
        verifyPaymentTransactionInfoPlugin(payment, authorizationTransaction, authorizationInfoPlugin, PaymentPluginStatus.PROCESSED);

        final PaymentTransactionInfoPlugin captureInfoPlugin = stripePaymentPluginApi.capturePayment(account.getId(),
                                                                                                     payment.getId(),
                                                                                                     captureTransaction.getId(),
                                                                                                     paymentMethodPlugin.getKbPaymentMethodId(),
                                                                                                     captureTransaction.getAmount(),
                                                                                                     captureTransaction.getCurrency(),
                                                                                                     ImmutableList.of(),
                                                                                                     context);
        TestUtils.updatePaymentTransaction(captureTransaction, captureInfoPlugin);
        verifyPaymentTransactionInfoPlugin(payment, captureTransaction, captureInfoPlugin, PaymentPluginStatus.PROCESSED);
    }

    @Test(groups = "integration")
    public void testSuccessfulAuthVoid() throws PaymentPluginApiException, StripeException, PaymentApiException {
        createStripeCustomerWithCreditCardAndSyncPaymentMethod();

        final Payment payment = TestUtils.buildPayment(account.getId(), account.getPaymentMethodId(), account.getCurrency(), killbillApi);
        final PaymentTransaction authorizationTransaction = TestUtils.buildPaymentTransaction(payment, TransactionType.AUTHORIZE, BigDecimal.TEN, payment.getCurrency());
        final PaymentTransaction voidTransaction = TestUtils.buildPaymentTransaction(payment, TransactionType.VOID, BigDecimal.TEN, payment.getCurrency());

        final PaymentTransactionInfoPlugin authorizationInfoPlugin = stripePaymentPluginApi.authorizePayment(account.getId(),
                                                                                                             payment.getId(),
                                                                                                             authorizationTransaction.getId(),
                                                                                                             paymentMethodPlugin.getKbPaymentMethodId(),
                                                                                                             authorizationTransaction.getAmount(),
                                                                                                             authorizationTransaction.getCurrency(),
                                                                                                             ImmutableList.of(),
                                                                                                             context);
        TestUtils.updatePaymentTransaction(authorizationTransaction, authorizationInfoPlugin);
        verifyPaymentTransactionInfoPlugin(payment, authorizationTransaction, authorizationInfoPlugin, PaymentPluginStatus.PROCESSED);

        final PaymentTransactionInfoPlugin voidInfoPlugin = stripePaymentPluginApi.voidPayment(account.getId(),
                                                                                               payment.getId(),
                                                                                               voidTransaction.getId(),
                                                                                               paymentMethodPlugin.getKbPaymentMethodId(),
                                                                                               ImmutableList.of(),
                                                                                               context);
        TestUtils.updatePaymentTransaction(voidTransaction, voidInfoPlugin);
        verifyPaymentTransactionInfoPlugin(payment, voidTransaction, voidInfoPlugin, PaymentPluginStatus.PROCESSED);
    }

    @Test(groups = "integration")
    public void testSuccessfulPurchaseRefund() throws PaymentPluginApiException, StripeException, PaymentApiException {
        createStripeCustomerWithCreditCardAndSyncPaymentMethod();

        final Payment payment = TestUtils.buildPayment(account.getId(), account.getPaymentMethodId(), account.getCurrency(), killbillApi);
        final PaymentTransaction purchaseTransaction = TestUtils.buildPaymentTransaction(payment, TransactionType.PURCHASE, BigDecimal.TEN, payment.getCurrency());
        final PaymentTransaction refundTransaction = TestUtils.buildPaymentTransaction(payment, TransactionType.REFUND, BigDecimal.TEN, payment.getCurrency());

        final PaymentTransactionInfoPlugin purchaseInfoPlugin = stripePaymentPluginApi.purchasePayment(account.getId(),
                                                                                                       payment.getId(),
                                                                                                       purchaseTransaction.getId(),
                                                                                                       paymentMethodPlugin.getKbPaymentMethodId(),
                                                                                                       purchaseTransaction.getAmount(),
                                                                                                       purchaseTransaction.getCurrency(),
                                                                                                       ImmutableList.of(),
                                                                                                       context);
        TestUtils.updatePaymentTransaction(purchaseTransaction, purchaseInfoPlugin);
        verifyPaymentTransactionInfoPlugin(payment, purchaseTransaction, purchaseInfoPlugin, PaymentPluginStatus.PROCESSED);

        final PaymentTransactionInfoPlugin refundInfoPlugin = stripePaymentPluginApi.refundPayment(account.getId(),
                                                                                                   payment.getId(),
                                                                                                   refundTransaction.getId(),
                                                                                                   paymentMethodPlugin.getKbPaymentMethodId(),
                                                                                                   refundTransaction.getAmount(),
                                                                                                   refundTransaction.getCurrency(),
                                                                                                   ImmutableList.of(),
                                                                                                   context);
        TestUtils.updatePaymentTransaction(refundTransaction, refundInfoPlugin);
        verifyPaymentTransactionInfoPlugin(payment, refundTransaction, refundInfoPlugin, PaymentPluginStatus.PROCESSED);
    }

    @Test(groups = "integration")
    public void testSuccessfulPurchaseMultiplePartialRefunds() throws PaymentPluginApiException, StripeException, PaymentApiException {
        createStripeCustomerWithCreditCardAndSyncPaymentMethod();

        final Payment payment = TestUtils.buildPayment(account.getId(), account.getPaymentMethodId(), account.getCurrency(), killbillApi);
        final PaymentTransaction purchaseTransaction = TestUtils.buildPaymentTransaction(payment, TransactionType.PURCHASE, BigDecimal.TEN, payment.getCurrency());
        final PaymentTransaction refundTransaction1 = TestUtils.buildPaymentTransaction(payment, TransactionType.REFUND, new BigDecimal("1"), payment.getCurrency());
        final PaymentTransaction refundTransaction2 = TestUtils.buildPaymentTransaction(payment, TransactionType.REFUND, new BigDecimal("2"), payment.getCurrency());
        final PaymentTransaction refundTransaction3 = TestUtils.buildPaymentTransaction(payment, TransactionType.REFUND, new BigDecimal("3"), payment.getCurrency());

        final PaymentTransactionInfoPlugin purchaseInfoPlugin = stripePaymentPluginApi.purchasePayment(account.getId(),
                                                                                                       payment.getId(),
                                                                                                       purchaseTransaction.getId(),
                                                                                                       paymentMethodPlugin.getKbPaymentMethodId(),
                                                                                                       purchaseTransaction.getAmount(),
                                                                                                       purchaseTransaction.getCurrency(),
                                                                                                       ImmutableList.of(),
                                                                                                       context);
        TestUtils.updatePaymentTransaction(purchaseTransaction, purchaseInfoPlugin);
        verifyPaymentTransactionInfoPlugin(payment, purchaseTransaction, purchaseInfoPlugin, PaymentPluginStatus.PROCESSED);

        final List<PaymentTransactionInfoPlugin> paymentTransactionInfoPlugin1 = stripePaymentPluginApi.getPaymentInfo(account.getId(),
                                                                                                                       payment.getId(),
                                                                                                                       ImmutableList.of(),
                                                                                                                       context);
        assertEquals(paymentTransactionInfoPlugin1.size(), 1);

        final PaymentTransactionInfoPlugin refundInfoPlugin1 = stripePaymentPluginApi.refundPayment(account.getId(),
                                                                                                    payment.getId(),
                                                                                                    refundTransaction1.getId(),
                                                                                                    paymentMethodPlugin.getKbPaymentMethodId(),
                                                                                                    refundTransaction1.getAmount(),
                                                                                                    refundTransaction1.getCurrency(),
                                                                                                    ImmutableList.of(),
                                                                                                    context);
        TestUtils.updatePaymentTransaction(refundTransaction1, refundInfoPlugin1);
        verifyPaymentTransactionInfoPlugin(payment, refundTransaction1, refundInfoPlugin1, PaymentPluginStatus.PROCESSED);

        final List<PaymentTransactionInfoPlugin> paymentTransactionInfoPlugin2 = stripePaymentPluginApi.getPaymentInfo(account.getId(),
                                                                                                                       payment.getId(),
                                                                                                                       ImmutableList.of(),
                                                                                                                       context);
        assertEquals(paymentTransactionInfoPlugin2.size(), 2);

        final PaymentTransactionInfoPlugin refundInfoPlugin2 = stripePaymentPluginApi.refundPayment(account.getId(),
                                                                                                    payment.getId(),
                                                                                                    refundTransaction2.getId(),
                                                                                                    paymentMethodPlugin.getKbPaymentMethodId(),
                                                                                                    refundTransaction2.getAmount(),
                                                                                                    refundTransaction2.getCurrency(),
                                                                                                    ImmutableList.of(),
                                                                                                    context);
        TestUtils.updatePaymentTransaction(refundTransaction2, refundInfoPlugin2);
        verifyPaymentTransactionInfoPlugin(payment, refundTransaction2, refundInfoPlugin2, PaymentPluginStatus.PROCESSED);

        final List<PaymentTransactionInfoPlugin> paymentTransactionInfoPlugin3 = stripePaymentPluginApi.getPaymentInfo(account.getId(),
                                                                                                                       payment.getId(),
                                                                                                                       ImmutableList.of(),
                                                                                                                       context);
        assertEquals(paymentTransactionInfoPlugin3.size(), 3);

        final PaymentTransactionInfoPlugin refundInfoPlugin3 = stripePaymentPluginApi.refundPayment(account.getId(),
                                                                                                    payment.getId(),
                                                                                                    refundTransaction3.getId(),
                                                                                                    paymentMethodPlugin.getKbPaymentMethodId(),
                                                                                                    refundTransaction3.getAmount(),
                                                                                                    refundTransaction3.getCurrency(),
                                                                                                    ImmutableList.of(),
                                                                                                    context);
        TestUtils.updatePaymentTransaction(refundTransaction3, refundInfoPlugin3);
        verifyPaymentTransactionInfoPlugin(payment, refundTransaction3, refundInfoPlugin3, PaymentPluginStatus.PROCESSED);

        final List<PaymentTransactionInfoPlugin> paymentTransactionInfoPlugin4 = stripePaymentPluginApi.getPaymentInfo(account.getId(),
                                                                                                                       payment.getId(),
                                                                                                                       ImmutableList.of(),
                                                                                                                       context);
        assertEquals(paymentTransactionInfoPlugin4.size(), 4);
    }

    @Test(groups = "integration")
    public void testExpired3DSPurchase() throws PaymentPluginApiException, StripeException, PaymentApiException {
        createStripeCustomerWith3DSCreditCardAndSyncPaymentMethod();

        final Payment payment = TestUtils.buildPayment(account.getId(), account.getPaymentMethodId(), account.getCurrency(), killbillApi);
        final PaymentTransaction purchaseTransaction = TestUtils.buildPaymentTransaction(payment, TransactionType.PURCHASE, BigDecimal.TEN, payment.getCurrency());

        final PaymentTransactionInfoPlugin purchaseInfoPlugin = stripePaymentPluginApi.purchasePayment(account.getId(),
                                                                                                       payment.getId(),
                                                                                                       purchaseTransaction.getId(),
                                                                                                       paymentMethodPlugin.getKbPaymentMethodId(),
                                                                                                       purchaseTransaction.getAmount(),
                                                                                                       purchaseTransaction.getCurrency(),
                                                                                                       ImmutableList.of(),
                                                                                                       context);
        TestUtils.updatePaymentTransaction(purchaseTransaction, purchaseInfoPlugin);
        verifyPaymentTransactionInfoPlugin(payment, purchaseTransaction, purchaseInfoPlugin, PaymentPluginStatus.PENDING);

        // Refresh is idempotent
        final List<PaymentTransactionInfoPlugin> paymentTransactionInfoPluginRefreshed = stripePaymentPluginApi.getPaymentInfo(account.getId(),
                                                                                                                               payment.getId(),
                                                                                                                               ImmutableList.of(),
                                                                                                                               context);
        assertEquals(paymentTransactionInfoPluginRefreshed.get(0).getStatus(), PaymentPluginStatus.PENDING);

        // See getPending3DsPaymentExpirationPeriod
        clock.addDeltaFromReality(new Period("PT3H").toStandardDuration().getMillis());

        final List<PaymentTransactionInfoPlugin> paymentTransactionInfoPluginExpired = stripePaymentPluginApi.getPaymentInfo(account.getId(),
                                                                                                                             payment.getId(),
                                                                                                                             ImmutableList.of(),
                                                                                                                             context);
        assertEquals(paymentTransactionInfoPluginExpired.get(0).getStatus(), PaymentPluginStatus.CANCELED);
    }

    @Test(groups = "integration")
    public void testSuccessfulBankAccountPurchase() throws PaymentPluginApiException, StripeException, PaymentApiException {
        createStripeCustomerWithBankAccountAndCreditCardAndSyncPaymentMethod();

        final Payment payment = TestUtils.buildPayment(account.getId(), account.getPaymentMethodId(), account.getCurrency(), killbillApi);
        final PaymentTransaction purchaseTransaction = TestUtils.buildPaymentTransaction(payment, TransactionType.PURCHASE, BigDecimal.TEN, payment.getCurrency());

        final PaymentTransactionInfoPlugin purchaseInfoPlugin = stripePaymentPluginApi.purchasePayment(account.getId(),
                                                                                                       payment.getId(),
                                                                                                       purchaseTransaction.getId(),
                                                                                                       paymentMethodPlugin.getKbPaymentMethodId(),
                                                                                                       purchaseTransaction.getAmount(),
                                                                                                       purchaseTransaction.getCurrency(),
                                                                                                       ImmutableList.of(),
                                                                                                       context);
        TestUtils.updatePaymentTransaction(purchaseTransaction, purchaseInfoPlugin);
        // Note the PROCESSED state here - I don't think the sandbox returns PENDING like it does in live mode (https://stripe.com/docs/ach#ach-payments-workflow)
        verifyPaymentTransactionInfoPlugin(payment, purchaseTransaction, purchaseInfoPlugin, PaymentPluginStatus.PROCESSED);
    }

    @Test(groups = "integration", enabled = false, description = "Manual test")
    public void testSuccessful3DSAuthCapture() throws PaymentPluginApiException, StripeException, PaymentApiException {
        createStripeCustomerWith3DSCreditCardAndSyncPaymentMethod();

        final Payment payment = TestUtils.buildPayment(account.getId(), account.getPaymentMethodId(), account.getCurrency(), killbillApi);
        final PaymentTransaction authorizationTransaction = TestUtils.buildPaymentTransaction(payment, TransactionType.AUTHORIZE, BigDecimal.TEN, payment.getCurrency());
        final PaymentTransaction captureTransaction = TestUtils.buildPaymentTransaction(payment, TransactionType.CAPTURE, BigDecimal.TEN, payment.getCurrency());

        final PaymentTransactionInfoPlugin authorizationInfoPlugin = stripePaymentPluginApi.authorizePayment(account.getId(),
                                                                                                             payment.getId(),
                                                                                                             authorizationTransaction.getId(),
                                                                                                             paymentMethodPlugin.getKbPaymentMethodId(),
                                                                                                             authorizationTransaction.getAmount(),
                                                                                                             authorizationTransaction.getCurrency(),
                                                                                                             ImmutableList.of(new PluginProperty("return_url", "https://killbill.io/", false)),
                                                                                                             context);
        TestUtils.updatePaymentTransaction(authorizationTransaction, authorizationInfoPlugin);
        verifyPaymentTransactionInfoPlugin(payment, authorizationTransaction, authorizationInfoPlugin, PaymentPluginStatus.PENDING);

        final Map nextAction = (Map) PluginProperties.findPluginProperties("next_action", authorizationInfoPlugin.getProperties()).iterator().next().getValue();
        assertNotNull(nextAction);
        assertEquals(nextAction.get("type"), "redirect_to_url");
        final String redirectUrl = (String) ((Map) nextAction.get("redirectToUrl")).get("url");
        assertNotNull(redirectUrl);
        System.out.println("Go to: " + redirectUrl);
        // Set a breakpoint here
        System.out.flush();

        final List<PaymentTransactionInfoPlugin> paymentTransactionInfoPluginRefreshed = stripePaymentPluginApi.getPaymentInfo(account.getId(),
                                                                                                                               payment.getId(),
                                                                                                                               ImmutableList.of(),
                                                                                                                               context);
        assertEquals(paymentTransactionInfoPluginRefreshed.get(0).getStatus(), PaymentPluginStatus.PROCESSED);

        final PaymentTransactionInfoPlugin captureInfoPlugin = stripePaymentPluginApi.capturePayment(account.getId(),
                                                                                                     payment.getId(),
                                                                                                     captureTransaction.getId(),
                                                                                                     paymentMethodPlugin.getKbPaymentMethodId(),
                                                                                                     captureTransaction.getAmount(),
                                                                                                     captureTransaction.getCurrency(),
                                                                                                     ImmutableList.of(),
                                                                                                     context);
        TestUtils.updatePaymentTransaction(captureTransaction, captureInfoPlugin);
        verifyPaymentTransactionInfoPlugin(payment, captureTransaction, captureInfoPlugin, PaymentPluginStatus.PROCESSED);
    }

    @Test(groups = "integration", enabled = false, description = "Manual test")
    public void testSuccessful3DSPurchase() throws PaymentPluginApiException, StripeException, PaymentApiException {
        createStripeCustomerWith3DSCreditCardAndSyncPaymentMethod();

        final Payment payment = TestUtils.buildPayment(account.getId(), account.getPaymentMethodId(), account.getCurrency(), killbillApi);
        final PaymentTransaction purchaseTransaction = TestUtils.buildPaymentTransaction(payment, TransactionType.PURCHASE, BigDecimal.TEN, payment.getCurrency());

        final PaymentTransactionInfoPlugin purchaseInfoPlugin = stripePaymentPluginApi.purchasePayment(account.getId(),
                                                                                                       payment.getId(),
                                                                                                       purchaseTransaction.getId(),
                                                                                                       paymentMethodPlugin.getKbPaymentMethodId(),
                                                                                                       purchaseTransaction.getAmount(),
                                                                                                       purchaseTransaction.getCurrency(),
                                                                                                       ImmutableList.of(new PluginProperty("return_url", "https://killbill.io/", false)),
                                                                                                       context);
        TestUtils.updatePaymentTransaction(purchaseTransaction, purchaseInfoPlugin);
        verifyPaymentTransactionInfoPlugin(payment, purchaseTransaction, purchaseInfoPlugin, PaymentPluginStatus.PENDING);

        final Map nextAction = (Map) PluginProperties.findPluginProperties("next_action", purchaseInfoPlugin.getProperties()).iterator().next().getValue();
        assertNotNull(nextAction);
        assertEquals(nextAction.get("type"), "redirect_to_url");
        final String redirectUrl = (String) ((Map) nextAction.get("redirectToUrl")).get("url");
        assertNotNull(redirectUrl);
        System.out.println("Go to: " + redirectUrl);
        // Set a breakpoint here
        System.out.flush();

        final List<PaymentTransactionInfoPlugin> paymentTransactionInfoPluginRefreshed = stripePaymentPluginApi.getPaymentInfo(account.getId(),
                                                                                                                               payment.getId(),
                                                                                                                               ImmutableList.of(),
                                                                                                                               context);
        assertEquals(paymentTransactionInfoPluginRefreshed.get(0).getStatus(), PaymentPluginStatus.PROCESSED);
    }
    
    @Test(groups = "integration", enabled = false, description = "Manual test")
    public void testAuthorizationFailureOn3DSPurchase() throws PaymentPluginApiException, StripeException, PaymentApiException {
        createStripeCustomerWith3DSCreditCardAndSyncPaymentMethod();
        final Payment payment = TestUtils.buildPayment(account.getId(), account.getPaymentMethodId(), account.getCurrency(), killbillApi);
        final PaymentTransaction purchaseTransaction = TestUtils.buildPaymentTransaction(payment, TransactionType.PURCHASE, BigDecimal.TEN, payment.getCurrency());
        final PaymentTransactionInfoPlugin purchaseInfoPlugin = stripePaymentPluginApi.purchasePayment(account.getId(),
                                                                                                       payment.getId(),
                                                                                                       purchaseTransaction.getId(),
                                                                                                       paymentMethodPlugin.getKbPaymentMethodId(),
                                                                                                       purchaseTransaction.getAmount(),
                                                                                                       purchaseTransaction.getCurrency(),
                                                                                                       ImmutableList.of(new PluginProperty("return_url", "https://killbill.io/", false)),
                                                                                                       context);
        TestUtils.updatePaymentTransaction(purchaseTransaction, purchaseInfoPlugin);
        verifyPaymentTransactionInfoPlugin(payment, purchaseTransaction, purchaseInfoPlugin, PaymentPluginStatus.PENDING);

        final Map nextAction = (Map) PluginProperties.findPluginProperties("next_action", purchaseInfoPlugin.getProperties()).iterator().next().getValue();
        assertNotNull(nextAction);
        assertEquals(nextAction.get("type"), "redirect_to_url");
        final String redirectUrl = (String) ((Map) nextAction.get("redirectToUrl")).get("url");
        assertNotNull(redirectUrl);
        System.out.println("Go to: " + redirectUrl);
        // Set a breakpoint here
        // When presented with an authorization screen fail the authorization
        System.out.flush();

        final List<PaymentTransactionInfoPlugin> paymentTransactionInfoPluginRefreshed = stripePaymentPluginApi.getPaymentInfo(account.getId(),
                                                                                                                               payment.getId(),
                                                                                                                               ImmutableList.of(),
                                                                                                                               context);
        // Pending PaymentPluginStatus change to CANCELED from ERROR
        // Related: https://github.com/killbill/killbill-stripe-plugin/pull/33
        if (super.stripeConfigPropertiesConfigurationHandler.getConfigurable(super.context.getTenantId()).isCancelOn3DSAuthorizationFailure()) {
            assertEquals(paymentTransactionInfoPluginRefreshed.get(0).getStatus(), PaymentPluginStatus.ERROR);
        } else {
            assertEquals(paymentTransactionInfoPluginRefreshed.get(0).getStatus(), PaymentPluginStatus.PENDING);
        }
    }

    @Test(groups = "integration", enabled = false, description = "Manual test")
    public void testHPP() throws PaymentPluginApiException, StripeException, PaymentApiException {
        final UUID kbAccountId = account.getId();
        final HostedPaymentPageFormDescriptor hostedPaymentPageFormDescriptor = stripePaymentPluginApi.buildFormDescriptor(kbAccountId,
                                                                                                                           ImmutableList.of(),
                                                                                                                           ImmutableList.of(),
                                                                                                                           context);
        final String sessionId = PluginProperties.findPluginPropertyValue("id", hostedPaymentPageFormDescriptor.getFormFields());
        assertNotNull(sessionId);

        System.out.println("sessionId: " + sessionId);
        // Set a breakpoint here and open the index.html test file (use card 4242424242424242)
        System.out.flush();

        // Still no payment method
        assertEquals(stripePaymentPluginApi.getPaymentMethods(kbAccountId, false, ImmutableList.<PluginProperty>of(), context).size(), 0);
        assertEquals(killbillApi.getCustomFieldUserApi().getCustomFieldsForAccountType(kbAccountId, ObjectType.ACCOUNT, context).size(), 0);

        final UUID kbPaymentMethodId = UUID.randomUUID();
        stripePaymentPluginApi.addPaymentMethod(kbAccountId,
                                                kbPaymentMethodId,
                                                new PluginPaymentMethodPlugin(kbPaymentMethodId, null, false, ImmutableList.of()),
                                                false,
                                                ImmutableList.of(new PluginProperty("sessionId", sessionId, false)),
                                                context);

        // Verify payment method was created (without refresh)
        final List<PaymentMethodInfoPlugin> paymentMethodsNoRefresh = stripePaymentPluginApi.getPaymentMethods(kbAccountId, false, ImmutableList.<PluginProperty>of(), context);
        assertEquals(paymentMethodsNoRefresh.size(), 1);
        assertEquals(paymentMethodsNoRefresh.get(0).getAccountId(), kbAccountId);
        assertNotNull(paymentMethodsNoRefresh.get(0).getExternalPaymentMethodId());

        // Verify refresh is a no-op. This will also verify that the custom field was created
        assertEquals(stripePaymentPluginApi.getPaymentMethods(kbAccountId, true, ImmutableList.<PluginProperty>of(), context), paymentMethodsNoRefresh);

        // Verify customer has no payment (voided)
        final String paymentIntentId = PluginProperties.findPluginPropertyValue("payment_intent_id", hostedPaymentPageFormDescriptor.getFormFields());
        assertEquals(PaymentIntent.retrieve(paymentIntentId, stripePaymentPluginApi.buildRequestOptions(context)).getStatus(), "canceled");

        // Verify we can charge the card
        paymentMethodPlugin = stripePaymentPluginApi.getPaymentMethodDetail(kbAccountId,
                                                                            paymentMethodsNoRefresh.get(0).getPaymentMethodId(),
                                                                            ImmutableList.of(),
                                                                            context);
        final Payment payment = TestUtils.buildPayment(account.getId(), account.getPaymentMethodId(), account.getCurrency(), killbillApi);
        final PaymentTransaction purchaseTransaction = TestUtils.buildPaymentTransaction(payment, TransactionType.AUTHORIZE, BigDecimal.TEN, payment.getCurrency());
        final PaymentTransactionInfoPlugin purchaseInfoPlugin = stripePaymentPluginApi.purchasePayment(account.getId(),
                                                                                                       payment.getId(),
                                                                                                       purchaseTransaction.getId(),
                                                                                                       paymentMethodPlugin.getKbPaymentMethodId(),
                                                                                                       purchaseTransaction.getAmount(),
                                                                                                       purchaseTransaction.getCurrency(),
                                                                                                       ImmutableList.of(),
                                                                                                       context);
        TestUtils.updatePaymentTransaction(purchaseTransaction, purchaseInfoPlugin);
        verifyPaymentTransactionInfoPlugin(payment, purchaseTransaction, purchaseInfoPlugin, PaymentPluginStatus.PROCESSED);
    }

    private void verifyPaymentTransactionInfoPlugin(final Payment payment,
                                                    final PaymentTransaction paymentTransaction,
                                                    final PaymentTransactionInfoPlugin paymentTransactionInfoPlugin,
                                                    final PaymentPluginStatus expectedPaymentPluginStatus) {
        assertEquals(paymentTransactionInfoPlugin.getKbPaymentId(), payment.getId());
        assertEquals(paymentTransactionInfoPlugin.getKbTransactionPaymentId(), paymentTransaction.getId());
        assertEquals(paymentTransactionInfoPlugin.getTransactionType(), paymentTransaction.getTransactionType());
        if (TransactionType.VOID.equals(paymentTransaction.getTransactionType())) {
            assertNull(paymentTransactionInfoPlugin.getAmount());
            assertNull(paymentTransactionInfoPlugin.getCurrency());
        } else {
            assertEquals(paymentTransactionInfoPlugin.getAmount().compareTo(paymentTransaction.getAmount()), 0);
            assertEquals(paymentTransactionInfoPlugin.getCurrency(), paymentTransaction.getCurrency());
        }
        assertNotNull(paymentTransactionInfoPlugin.getCreatedDate());
        assertNotNull(paymentTransactionInfoPlugin.getEffectiveDate());

        if ("skip_gw".equals(paymentTransactionInfoPlugin.getGatewayError()) ||
            "true".equals(PluginProperties.findPluginPropertyValue("skipGw", paymentTransactionInfoPlugin.getProperties()))) {
            assertNull(paymentTransactionInfoPlugin.getGatewayErrorCode());
            assertEquals(paymentTransactionInfoPlugin.getStatus(), PaymentPluginStatus.PROCESSED);
        } else {
            assertNull(paymentTransactionInfoPlugin.getGatewayErrorCode());
            assertEquals(paymentTransactionInfoPlugin.getStatus(), expectedPaymentPluginStatus);

            assertNull(paymentTransactionInfoPlugin.getGatewayError());
            if (expectedPaymentPluginStatus == PaymentPluginStatus.PROCESSED) {
                // No charge id for 3DS until PaymentIntent is confirmed
                assertNotNull(paymentTransactionInfoPlugin.getFirstPaymentReferenceId());
                // Not populated in the sandbox
                //assertNotNull(paymentTransactionInfoPlugin.getSecondPaymentReferenceId());
            }
        }
    }

    private void createStripeCustomerWithCreditCardAndSyncPaymentMethod() throws StripeException, PaymentPluginApiException {
        final UUID kbAccountId = account.getId();

        customer = createStripeCustomerWithCreditCard(kbAccountId);

        // Sync Stripe <-> Kill Bill
        final List<PaymentMethodInfoPlugin> paymentMethods = stripePaymentPluginApi.getPaymentMethods(kbAccountId, true, ImmutableList.<PluginProperty>of(), context);

        paymentMethodPlugin = stripePaymentPluginApi.getPaymentMethodDetail(kbAccountId,
                                                                            paymentMethods.get(0).getPaymentMethodId(),
                                                                            ImmutableList.of(),
                                                                            context);
    }

    private Customer createStripeCustomerWithCreditCard(final UUID kbAccountId) throws StripeException {
        // Create new customer with VISA card
        Map<String, Object> customerParams = new HashMap<String, Object>();
        customerParams.put("payment_method", "pm_card_visa");
        final Customer customer = Customer.create(customerParams, stripePaymentPluginApi.buildRequestOptions(context));

        // Add the magic Custom Field
        final PluginCustomField customField = new PluginCustomField(kbAccountId,
                                                                    ObjectType.ACCOUNT,
                                                                    "STRIPE_CUSTOMER_ID",
                                                                    customer.getId(),
                                                                    clock.getUTCNow());
        Mockito.when(customFieldUserApi.getCustomFieldsForAccountType(Mockito.eq(kbAccountId), Mockito.eq(ObjectType.ACCOUNT), Mockito.any(TenantContext.class)))
               .thenReturn(ImmutableList.of(customField));

        return customer;
    }

    private void createStripeCustomerWith3DSCreditCardAndSyncPaymentMethod() throws StripeException, PaymentPluginApiException {
        final UUID kbAccountId = account.getId();

        customer = createStripeCustomerWith3DSCreditCard(kbAccountId);

        // Sync Stripe <-> Kill Bill
        final List<PaymentMethodInfoPlugin> paymentMethods = stripePaymentPluginApi.getPaymentMethods(kbAccountId, true, ImmutableList.<PluginProperty>of(), context);

        paymentMethodPlugin = stripePaymentPluginApi.getPaymentMethodDetail(kbAccountId,
                                                                            paymentMethods.get(0).getPaymentMethodId(),
                                                                            ImmutableList.of(),
                                                                            context);
    }

    private Customer createStripeCustomerWith3DSCreditCard(final UUID kbAccountId) throws StripeException {
        // Create new customer with VISA card
        Map<String, Object> customerParams = new HashMap<String, Object>();
        customerParams.put("payment_method", "pm_card_threeDSecure2Required");
        final Customer customer = Customer.create(customerParams, stripePaymentPluginApi.buildRequestOptions(context));

        // Add the magic Custom Field
        final PluginCustomField customField = new PluginCustomField(kbAccountId,
                                                                    ObjectType.ACCOUNT,
                                                                    "STRIPE_CUSTOMER_ID",
                                                                    customer.getId(),
                                                                    clock.getUTCNow());
        Mockito.when(customFieldUserApi.getCustomFieldsForAccountType(Mockito.eq(kbAccountId), Mockito.eq(ObjectType.ACCOUNT), Mockito.any(TenantContext.class)))
               .thenReturn(ImmutableList.of(customField));

        return customer;
    }

    private void createStripeCustomerWithBankAccountAndCreditCardAndSyncPaymentMethod() throws StripeException, PaymentPluginApiException {
        final UUID kbAccountId = account.getId();

        customer = createStripeCustomerWithBankAccountAndCreditCard(kbAccountId);

        // Sync Stripe <-> Kill Bill
        final List<PaymentMethodInfoPlugin> paymentMethods = stripePaymentPluginApi.getPaymentMethods(kbAccountId, true, ImmutableList.<PluginProperty>of(), context);

        for (final PaymentMethodInfoPlugin paymentMethodInfoPlugin : paymentMethods) {
            if (paymentMethodInfoPlugin.getExternalPaymentMethodId().startsWith("ba_")) {
                // It's the bank account
                paymentMethodPlugin = stripePaymentPluginApi.getPaymentMethodDetail(kbAccountId,
                                                                                    paymentMethodInfoPlugin.getPaymentMethodId(),
                                                                                    ImmutableList.of(),
                                                                                    context);
                break;
            }
        }
    }

    private Customer createStripeCustomerWithBankAccountAndCreditCard(final UUID kbAccountId) throws StripeException {
        final RequestOptions options = stripePaymentPluginApi.buildRequestOptions(context);

        // Massive hack: I couldn't manage to charge an ACH source created programmatically via Source.create,
        // so I reverse-engineered the call that stripe.js makes...
        String bankAccount = null;
        try {
            bankAccount = new StripeJsClient().createBankAccount();
        } catch (Exception e) {
            fail(e.getMessage());
        }

        Map<String, Object> customerParams = new HashMap<String, Object>();
        customerParams.put("source", bankAccount);
        // Add also a card on the account, to verify we support multiple payment method types per account
        customerParams.put("payment_method", "pm_card_visa");
        final Customer customer = Customer.create(customerParams, options);

        // Verify the bank account
        final Map<String, Object> params = new HashMap<String, Object>();
        final List<Integer> amounts = new ArrayList<Integer>();
        amounts.add(32);
        amounts.add(45);
        params.put("amounts", amounts);
        for (final PaymentSource source : customer.getSources().autoPagingIterable()) {
            if (source instanceof BankAccount) {
                ((BankAccount) source).verify(params, options);
                break;
            }
        }

        // Add the magic Custom Field
        final PluginCustomField customField = new PluginCustomField(kbAccountId,
                                                                    ObjectType.ACCOUNT,
                                                                    "STRIPE_CUSTOMER_ID",
                                                                    customer.getId(),
                                                                    clock.getUTCNow());
        Mockito.when(customFieldUserApi.getCustomFieldsForAccountType(Mockito.eq(kbAccountId), Mockito.eq(ObjectType.ACCOUNT), Mockito.any(TenantContext.class)))
               .thenReturn(ImmutableList.of(customField));

        return customer;
    }

    static class StripeJsClient extends HttpClient {

        public StripeJsClient() throws GeneralSecurityException {
            super("https://api.stripe.com/v1/tokens", null, null, null, null, false, 60000, 60000);
        }

        public String createBankAccount() throws Exception {
            final String accountNumber = "000123456789";
            final String country = "US";
            final String currency = "usd";
            final String routingNumber = "110000000";
            final String name = "Jenny+Rosen";
            final String type = "individual";
            final String stripePublishableKey = "pk_test_xueTzlxxkKSa5Q47NrnLPcle";
            final String body = "bank_account[account_number]=" + accountNumber + "&bank_account[country]=" + country + "&bank_account[currency]=" + currency + "&bank_account[routing_number]=" + routingNumber + "&bank_account[account_holder_name]=" + name + "&bank_account[account_holder_type]=" + type + "&key=" + stripePublishableKey;

            final BoundRequestBuilder builder = getBuilderWithHeaderAndQuery(POST, url, ImmutableMap.<String, String>of(), ImmutableMap.<String, String>of()).setBody(body);
            final Map response = executeAndWait(builder, DEFAULT_HTTP_TIMEOUT_SEC, Map.class, ResponseFormat.JSON);

            return (String) response.get("id");
        }
    }
}
