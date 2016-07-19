killbill-stripe-plugin
======================

Plugin to use [Stripe Connect](https://stripe.com/docs/connect) as a gateway.


Kill Bill compatibility
-----------------------

| Plugin version | Kill Bill version  | Stripe version                                            |
| -------------: | -----------------: | --------------------------------------------------------: |
| 1.0.y          | 0.14.z             | [2015-02-18](https://stripe.com/docs/upgrades#2015-02-18) |
| 2.0.y          | 0.15.z             | [2015-02-18](https://stripe.com/docs/upgrades#2015-02-18) |
| 3.0.y          | 0.16.z             | [2015-02-18](https://stripe.com/docs/upgrades#2015-02-18) |
| 4.0.y          | 0.17.z             | [2015-02-18](https://stripe.com/docs/upgrades#2015-02-18) |

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
  :destination: "default-destination-for-connect"
  :fees_amount: "default-fees-amount-for-connect"
  :fees_percent: "default-fees-percent-for-connect"' \
     http://127.0.0.1:8080/1.0/kb/tenants/uploadPluginConfig/killbill-stripe
```

To get your credentials:

1. Go to [stripe.com](http://stripe.com/) and create an account. This account will be used as a sandbox environment for testing.
2. In your Stripe account, click on **Your Account** (top right), then click on **Account Settings** and then on the **API Keys** tab. Write down your keys.

For Connect, you can configure a default fees amount (`fees_amount`) or percentage (`fees_percent`, such as .3 for 30%). These can be modified on a per request basis by passing the plugin property `fees_amount` or `fees_percent`.
You'll also need to add a row to the `stripe_application_fees` table and add a percent (such as .3 for 30%) to the `application_fee` field.

To go to production, create a `stripe.yml` configuration file under `/var/tmp/bundles/plugins/ruby/killbill-stripe/x.y.z/` containing the following:

```
:stripe:
  :test: false
```

Usage
-----

You would typically implement [Stripe.js](https://stripe.com/docs/stripe.js) to tokenize credit cards. 

After receiving the token from Stripe, call:

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
     "http://127.0.0.1:8080/1.0/kb/accounts/2a55045a-ce1d-4344-942d-b825536328f9/paymentMethods?isDefault=true"
```

An example implementation is exposed at:

```
http://127.0.0.1:8080/plugins/killbill-stripe?kb_account_id=2a55045a-ce1d-4344-942d-b825536328f9&kb_tenant_id=a86d9fd1-718d-4178-a9eb-46c61aa2548f
```

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
