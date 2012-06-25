/*
 * Copyright 2010-2012 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.payment.plugin.stripe.client;

import com.google.common.collect.ImmutableMap;
import com.stripe.model.Card;
import com.stripe.model.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.ning.billing.payment.plugin.stripe.TestUtils.randomString;

public class TestStripeClient {
    private static final Logger log = LoggerFactory.getLogger(TestStripeClient.class);

    private StripeClient stripeClient;

    @BeforeMethod(groups = "integration")
    public void setUp() throws Exception {
        final String apiKey = System.getProperty("killbill.payment.stripe.apiKey");
        if (apiKey == null) {
            Assert.fail("You need to set your Stripe api key to run integration tests: -Dkillbill.payment.stripe.apiKey=...");
        }

        stripeClient = new StripeClient(apiKey);
    }

    @Test(groups = "integration")
    public void testCreateAccount() throws Exception {
        final String email = randomString() + "@laposte.net";
        final Customer customer = stripeClient.createCustomer(ImmutableMap.<String, Object>of("email", email,
                                                                                              "description", "Killbill Stripe plugin integration test"));
        Assert.assertNotNull(customer);
        Assert.assertEquals(customer.getEmail(), email);
        Assert.assertNull(customer.getActiveCard());
        log.info("Created account: {}", customer.getId());

        final Customer retrievedCustomer = stripeClient.getCustomer(customer.getId());
        Assert.assertEquals(retrievedCustomer.getActiveCard(), customer.getActiveCard());
        Assert.assertEquals(retrievedCustomer.getAccountBalance(), customer.getAccountBalance());
        Assert.assertEquals(retrievedCustomer.getCreated(), customer.getCreated());
        Assert.assertEquals(retrievedCustomer.getDeleted(), customer.getDeleted());
        Assert.assertEquals(retrievedCustomer.getDelinquent(), customer.getDelinquent());
        Assert.assertEquals(retrievedCustomer.getDescription(), customer.getDescription());
        Assert.assertEquals(retrievedCustomer.getDiscount(), customer.getDiscount());
        Assert.assertEquals(retrievedCustomer.getEmail(), customer.getEmail());
        Assert.assertEquals(retrievedCustomer.getId(), customer.getId());
        Assert.assertEquals(retrievedCustomer.getLivemode(), customer.getLivemode());
        Assert.assertEquals(retrievedCustomer.getNextRecurringCharge(), customer.getNextRecurringCharge());
        Assert.assertEquals(retrievedCustomer.getPlan(), customer.getPlan());
        Assert.assertEquals(retrievedCustomer.getSubscription(), customer.getSubscription());
        Assert.assertEquals(retrievedCustomer.getTrialEnd(), customer.getTrialEnd());

        final int expMonth = 12;
        final int expYear = 2015;
        final ImmutableMap<String, Object> cardData = ImmutableMap.<String, Object>of("number", "4242424242424242",
                                                                                      "exp_month", expMonth,
                                                                                      "exp_year", expYear,
                                                                                      "name", email);
        final Card card = stripeClient.createOrUpdateCard(customer.getId(), ImmutableMap.<String, Object>of("card", cardData));
        Assert.assertNotNull(card);
        Assert.assertEquals((int) card.getExpMonth(), expMonth);
        Assert.assertEquals((int) card.getExpYear(), expYear);
        Assert.assertEquals(card.getName(), email);
        log.info("Added credit card: {}", card.getType());

        final Card retrievedCard = stripeClient.getActiveCard(customer.getId());
        Assert.assertEquals(retrievedCard.getAddressCountry(), card.getAddressCountry());
        Assert.assertEquals(retrievedCard.getAddressLine1(), card.getAddressLine1());
        Assert.assertEquals(retrievedCard.getAddressLine1Check(), card.getAddressLine1Check());
        Assert.assertEquals(retrievedCard.getAddressLine2(), card.getAddressLine2());
        Assert.assertEquals(retrievedCard.getAddressState(), card.getAddressState());
        Assert.assertEquals(retrievedCard.getAddressZip(), card.getAddressZip());
        Assert.assertEquals(retrievedCard.getAddressZipCheck(), card.getAddressZipCheck());
        Assert.assertEquals(retrievedCard.getCountry(), card.getCountry());
        Assert.assertEquals(retrievedCard.getCvcCheck(), card.getCvcCheck());
        Assert.assertEquals(retrievedCard.getExpMonth(), card.getExpMonth());
        Assert.assertEquals(retrievedCard.getExpYear(), card.getExpYear());
        Assert.assertEquals(retrievedCard.getFingerprint(), card.getFingerprint());
        Assert.assertEquals(retrievedCard.getLast4(), card.getLast4());
        Assert.assertEquals(retrievedCard.getName(), card.getName());
        Assert.assertEquals(retrievedCard.getType(), card.getType());
    }
}
