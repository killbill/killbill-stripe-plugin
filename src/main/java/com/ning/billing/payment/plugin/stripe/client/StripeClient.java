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

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Card;
import com.stripe.model.Customer;
import com.stripe.model.DeletedCustomer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class StripeClient {
    private static final Logger log = LoggerFactory.getLogger(StripeClient.class);

    public StripeClient(final String key) {
        Stripe.apiKey = key;
    }

    /**
     * Creating a New Customer
     * <p/>
     * Returns a customer object if the call succeeded. The returned object will have information about subscriptions,
     * discount, and cards, if that information has been provided. If a non-free plan is specified and a card is not
     * provided (unless the plan has a trial period), the call will return an error. If a non-existent plan or a
     * non-existent or expired coupon is provided, the call will return an error.
     * <p/>
     * If a card has been attached to the customer, the returned customer object will have an active_card attribute
     * containing the card's details.
     *
     * @param customerData customer data
     * @return customer object on success, null otherwise
     */
    public Customer createCustomer(final Map<String, Object> customerData) {
        try {
            return Customer.create(customerData);
        } catch (StripeException e) {
            log.warn("Unable to create customer", e);
            return null;
        }
    }

    /**
     * Retrieving a Customer
     * <p/>
     * Retrieves the details of an existing customer. You need only supply the unique customer identifier that was
     * returned upon customer creation.
     *
     * @param customerId customer id
     * @return customer object on success, null otherwise
     */
    public Customer getCustomer(final String customerId) {
        try {
            return Customer.retrieve(customerId);
        } catch (StripeException e) {
            log.warn("Unable to retrieve customer", e);
            return null;
        }
    }

    /**
     * Updating a Customer
     * <p/>
     * Updates the specified customer by setting the values of the parameters passed. Any parameters not provided will
     * be left unchanged. For example, if you pass the card parameter, that becomes the customer's active card which
     * will be used for all charges in future.
     * <p/>
     * This request accepts mostly the same arguments as the customer creation call. However, subscription-related
     * arguments (plan and trial_end) are not accepted. To change those, one must update the customer's subscription directly.
     *
     * @param customerId   customer id
     * @param customerData updated customer data
     * @return updated customer object on success, null otherwise
     */
    public Customer updateCustomer(final String customerId, final Map<String, Object> customerData) {
        try {
            final Customer customer = Customer.retrieve(customerId);
            return customer.update(customerData);
        } catch (StripeException e) {
            log.warn("Unable to update customer", e);
            return null;
        }
    }

    /**
     * Deleting a Customer
     * <p/>
     * Permanently deletes a customer. It cannot be undone.
     * <p/>
     * Unlike other objects, deleted customers can still be retrieved through the API, in order to be able to track the
     * history of customers while still removing their credit card details and preventing any further operations to be
     * performed (such as adding a new subscription).
     *
     * @param customerId customer id
     * @return deleted customer object
     */
    public DeletedCustomer deleteCustomer(final String customerId) {
        try {
            final Customer customer = Customer.retrieve(customerId);
            return customer.delete();
        } catch (StripeException e) {
            log.warn("Unable to delete customer", e);
            return null;
        }
    }

    public Card getActiveCard(final String customerId) {
        try {
            final Customer customer = Customer.retrieve(customerId);
            return customer.getActiveCard();
        } catch (StripeException e) {
            log.warn("Unable to retrieve card info", e);
            return null;
        }
    }

    public Card createOrUpdateCard(final String customerId, final Map<String, Object> customerData) {
        try {
            final Customer customer = Customer.retrieve(customerId);
            return customer.update(customerData).getActiveCard();
        } catch (StripeException e) {
            log.warn("Unable to create or update card info", e);
            return null;
        }
    }
}
