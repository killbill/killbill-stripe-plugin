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

import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.plugin.TestUtils;
import org.killbill.billing.plugin.stripe.dao.StripeDao;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.clock.ClockMock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

public class TestBase {

    private static final String PROPERTIES_FILE_NAME = "stripe.properties";

    public static final Currency DEFAULT_CURRENCY = Currency.USD;
    public static final String DEFAULT_COUNTRY = "US";

    protected ClockMock clock;
    protected CallContext context;
    protected Account account;
    protected StripePaymentPluginApi stripePaymentPluginApi;
    protected OSGIKillbillAPI killbillApi;
    protected CustomFieldUserApi customFieldUserApi;
    protected StripeConfigPropertiesConfigurationHandler stripeConfigPropertiesConfigurationHandler;
    protected StripeDao dao;

    @BeforeMethod(groups = {"slow", "integration"})
    public void setUp() throws Exception {
        EmbeddedDbHelper.instance().resetDB();
        dao = EmbeddedDbHelper.instance().getStripeDao();

        clock = new ClockMock();

        context = Mockito.mock(CallContext.class);
        Mockito.when(context.getTenantId()).thenReturn(UUID.randomUUID());

        account = TestUtils.buildAccount(DEFAULT_CURRENCY, DEFAULT_COUNTRY);
        killbillApi = TestUtils.buildOSGIKillbillAPI(account);
        customFieldUserApi = Mockito.mock(CustomFieldUserApi.class);
        Mockito.when(killbillApi.getCustomFieldUserApi()).thenReturn(customFieldUserApi);

        TestUtils.buildPaymentMethod(account.getId(), account.getPaymentMethodId(), StripeActivator.PLUGIN_NAME, killbillApi);

        stripeConfigPropertiesConfigurationHandler = new StripeConfigPropertiesConfigurationHandler(StripeActivator.PLUGIN_NAME, killbillApi, null);

        final OSGIConfigPropertiesService configPropertiesService = Mockito.mock(OSGIConfigPropertiesService.class);
        stripePaymentPluginApi = new StripePaymentPluginApi(stripeConfigPropertiesConfigurationHandler,
                                                            killbillApi,
                                                            configPropertiesService,
                                                            clock,
                                                            dao);

        TestUtils.updateOSGIKillbillAPI(killbillApi, stripePaymentPluginApi);

        Mockito.when(killbillApi.getPaymentApi()
                                .addPaymentMethod(Mockito.any(Account.class),
                                                  Mockito.anyString(),
                                                  Mockito.eq("killbill-stripe"),
                                                  Mockito.anyBoolean(),
                                                  Mockito.any(PaymentMethodPlugin.class),
                                                  Mockito.any(Iterable.class),
                                                  Mockito.any(CallContext.class)))
               .thenAnswer(new Answer<Object>() {
                   @Override
                   public Object answer(final InvocationOnMock invocation) throws Throwable {
                       stripePaymentPluginApi.addPaymentMethod(((Account) invocation.getArguments()[0]).getId(),
                                                               UUID.randomUUID(),
                                                               (PaymentMethodPlugin) invocation.getArguments()[4],
                                                               (Boolean) invocation.getArguments()[3],
                                                               (Iterable) invocation.getArguments()[5],
                                                               (CallContext) invocation.getArguments()[6]);
                       return null;
                   }
               });

        Mockito.doAnswer(new Answer<Object>() {
            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable {
                // A bit simplistic but good enough for now?
                Mockito.when(customFieldUserApi.getCustomFieldsForAccountType(Mockito.eq(account.getId()), Mockito.eq(ObjectType.ACCOUNT), Mockito.any(TenantContext.class)))
                       .thenReturn((List<CustomField>) invocation.getArguments()[0]);
                return null;
            }
        })
               .when(customFieldUserApi).addCustomFields(Mockito.anyList(), Mockito.any(CallContext.class));
    }

    @BeforeMethod(groups = "integration")
    public void setUpIntegration() throws Exception {
        final Properties properties = TestUtils.loadProperties(PROPERTIES_FILE_NAME);
        final StripeConfigProperties stripeConfigProperties = new StripeConfigProperties(properties, "");
        stripeConfigPropertiesConfigurationHandler.setDefaultConfigurable(stripeConfigProperties);
    }

    @BeforeSuite(groups = {"slow", "integration"})
    public void setUpBeforeSuite() throws Exception {
        EmbeddedDbHelper.instance().startDb();
    }

    @AfterSuite(groups = {"slow", "integration"})
    public void tearDownAfterSuite() throws Exception {
        EmbeddedDbHelper.instance().stopDB();
    }
}
