killbill-stripe-plugin
======================

Plugin to use [Stripe](https://stripe.com/) as a gateway.

Release builds are available on [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.kill-bill.billing.plugin.ruby%22%20AND%20a%3A%22stripe-plugin%22) with coordinates `org.kill-bill.billing.plugin.ruby:stripe-plugin`.

A full end-to-end integration demo is available [here](https://github.com/killbill/killbill-stripe-demo).

Kill Bill compatibility
-----------------------

| Plugin version | Kill Bill version  | Stripe version                                            |
| -------------: | -----------------: | --------------------------------------------------------: |
| 1.x.y          | 0.14.z             | [2015-02-18](https://stripe.com/docs/upgrades#2015-02-18) |
| 3.x.y          | 0.16.z             | [2015-02-18](https://stripe.com/docs/upgrades#2015-02-18) |
| 4.x.y          | 0.18.z             | [2015-02-18](https://stripe.com/docs/upgrades#2015-02-18) |
| 5.x.y          | 0.19.z             | [2015-02-18](https://stripe.com/docs/upgrades#2015-02-18) |
| 6.x.y          | 0.20.z             | [2015-02-18](https://stripe.com/docs/upgrades#2015-02-18) |

Requirements
------------

The plugin needs a database. The latest version of the schema can be found [here](https://github.com/killbill/killbill-stripe-plugin/blob/master/db/ddl.sql).

Configuration
-------------

```
curl -v \
     -X POST \
     -u admin:password \
     -H 'X-Killbill-ApiKey: bob' \
     -H 'X-Killbill-ApiSecret: lazar' \
     -H 'X-Killbill-CreatedBy: admin' \
     -H 'Content-Type: text/plain' \
     -d ':stripe:
  :api_secret_key: "your-secret-key"
  :api_publishable_key: "your-publishable-key"
  :fees_amount: "default-fees-amount-for-connect"
  :fees_percent: "default-fees-percent-for-connect"
  :dynamic_descriptor: "dynamic-descriptor-template"' \
     http://127.0.0.1:8080/1.0/kb/tenants/uploadPluginConfig/killbill-stripe
```

To get your credentials:

1. Go to [stripe.com](http://stripe.com/) and create an account. This account will be used as a sandbox environment for testing.
2. In your Stripe account, click on **Your Account** (top right), then click on **Account Settings** and then on the **API Keys** tab. Write down your keys.

For Connect, you can configure a default fees amount (`fees_amount`) or percentage (`fees_percent`, such as .3 for 30%). These can be modified on a per request basis by passing the plugin property `fees_amount` or `fees_percent`.
You'll also need to add a row to the `stripe_application_fees` table and add a percent (such as .3 for 30%) to the `application_fee` field.

For dynamic descriptors, you can configure a dynamic descriptor template (`dynamic_descriptor`) with Ruby's string interpolation syntax. For example `"KILLBILL %{kb_payment_transaction_id}"`. Available variables are `kb_account_id`, `kb_payment_id`, `kb_payment_transaction_id`, `kb_payment_method_id`. Stripe limits to 22 characters for the charge descriptor so the first 22 characters of the description will be used.

To go to production, create a `stripe.yml` configuration file under `/var/tmp/bundles/plugins/ruby/killbill-stripe/x.y.z/` containing the following:

```
:stripe:
  :test: false
```

Usage
-----

You would typically implement [Stripe.js](https://stripe.com/docs/stripe.js) to tokenize credit cards. 

After receiving the token from Stripe, create a Kill Bill payment method associated with it as such:

```
curl -v \
     -X POST \
     -u admin:password \
     -H 'X-Killbill-ApiKey: bob' \
     -H 'X-Killbill-ApiSecret: lazar' \
     -H 'X-Killbill-CreatedBy: admin' \
     -H 'Content-Type: application/json' \
     -d '{
       "pluginName": "killbill-stripe",
       "pluginInfo": {
         "properties": [{
           "key": "token",
           "value": "tok_20G53990M6953444J"
         }]
       }
     }' \
     "http://127.0.0.1:8080/1.0/kb/accounts/<KB_ACCOUNT_ID>/paymentMethods?isDefault=true"
```

An example implementation is exposed at:

```
http://127.0.0.1:8080/plugins/killbill-stripe?kb_account_id=<KB_ACCOUNT_ID>&kb_tenant_id=<KB_TENANT_ID>
```

After entering you credit card, this demo page will:

* Tokenize it in Stripe (JS call)
* Call Kill Bill during the redirect to create a payment method for that token
* Output the result of the tokenization call

### Connect

Managed accounts must first have their own account in Kill Bill. Then, create them in Stripe using `POST /plugins/killbill-stripe/accounts`:

```
curl -v -X POST \
     -d '{
       "legal_entity": {
         "address": {
           "city": "San Francisco",
           "country": "US"
         },
         "dob": {
           "day": 31,
           "month": 12,
           "year": 1969
         },
         "first_name": "Jane",
         "last_name": "Doe"",
         "type": "individual"
     }' \
     http://127.0.0.1:8080/plugins/killbill-stripe/accounts?kb_account_id=<KB_ACCOUNT_ID>&kb_tenant_id=<KB_TENANT_ID>
```

When charging customers, you can now pass the Kill Bill account id of the managed account as the `destination` plugin property.

See the [Stripe documentation](https://stripe.com/docs/connect/managed-accounts#creating-a-managed-account) for more details.

### ACH payments

You can also add, verify and charge bank accounts directly. Like credit cards, you would typically first use [Stripe.js](https://stripe.com/docs/stripe.js) to tokenize a bank account. Unlike credit cards bank accounts need to be verified by confirming the amounts of 2 microdeposits sent to the account. After receiving the amounts, you can verify the bank account as such:

```
curl -v -X POST \
     -d '[<VERIFICATION AMOUNT 1>, <VERIFICATION AMOUNT 2>]' \
     http://127.0.0.1:8080/plugins/killbill-stripe/verify
```

See the [Stripe documentation](https://stripe.com/docs/ach) for more details.


Plugin properties
-----------------

| Key                          | Description                                                       |
| ---------------------------: | ----------------------------------------------------------------- |
| skip_gw                      | If true, skip the call to Stripe                                  |
| payment_processor_account_id | Config entry name of the merchant account to use                  |
| external_key_as_order_id     | If true, set the payment external key as the Stripe order id      |
| customer                     | Stripe customer id                                                |
| token                        | Stripe token                                                      |
| cc_first_name                | Credit card holder first name                                     |
| cc_last_name                 | Credit card holder last name                                      |
| cc_type                      | Credit card brand                                                 |
| cc_expiration_month          | Credit card expiration month                                      |
| cc_expiration_year           | Credit card expiration year                                       |
| cc_verification_value        | CVC/CVV/CVN                                                       |
| email                        | Purchaser email                                                   |
| address1                     | Billing address first line                                        |
| address2                     | Billing address second line                                       |
| city                         | Billing address city                                              |
| zip                          | Billing address zip code                                          |
| state                        | Billing address state                                             |
| country                      | Billing address country                                           |
| eci                          | Network tokenization attribute                                    |
| payment_cryptogram           | Network tokenization attribute                                    |
| transaction_id               | Network tokenization attribute                                    |
| payment_instrument_name      | ApplePay tokenization attribute                                   |
| payment_network              | ApplePay tokenization attribute                                   |
| transaction_identifier       | ApplePay tokenization attribute                                   |
| destination                  | [Connect] KB account id of the receiving account                  |
| fees_amount                  | [Connect] Amount in cents of fees to collect                      |
| fees_percent                 | [Connect] Percentage amount of fees to collect                    |
| reverse_transfer             | [Connect] True if the transfer should be reversed when refunding  |
| refund_application_fee       | [Connect] True if fees should be refunded when refunding          |
| source_type                  | Credit card or bank account                                       |
| bank_name                    | Bank name                                                         |
| bank_routing_number          | Bank account routing number                                       |
| bank_account_first_name      | Bank account holder first name                                    |
| bank_account_last_name       | Bank account holder last name                                     |
