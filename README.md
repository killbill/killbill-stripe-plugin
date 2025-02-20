# killbill-stripe-plugin
![Maven Central](https://img.shields.io/maven-central/v/org.kill-bill.billing.plugin.java/stripe-plugin?color=blue&label=Maven%20Central)

Plugin to use [Stripe](https://stripe.com/) as a gateway.

A full end-to-end integration demo is available [here](https://github.com/killbill/killbill-stripe-demo).

## Kill Bill compatibility

| Plugin version | Kill Bill version | Stripe version                                            |
|---------------:|------------------:| --------------------------------------------------------: |
|          1.x.y |            0.14.z | [2015-02-18](https://stripe.com/docs/upgrades#2015-02-18) |
|          3.x.y |            0.16.z | [2015-02-18](https://stripe.com/docs/upgrades#2015-02-18) |
|          4.x.y |            0.18.z | [2015-02-18](https://stripe.com/docs/upgrades#2015-02-18) |
|          5.x.y |            0.19.z | [2015-02-18](https://stripe.com/docs/upgrades#2015-02-18) |
|          6.x.y |            0.20.z | [2015-02-18](https://stripe.com/docs/upgrades#2015-02-18) |
|          7.0.y |            0.22.z | [2019-12-03](https://stripe.com/docs/upgrades#2019-12-03) |
|          7.1.y |            0.22.z | [2019-12-03](https://stripe.com/docs/upgrades#2019-12-03) |
|          7.2.y |            0.22.z | [2020-08-27](https://stripe.com/docs/upgrades#2020-08-27) |
|          7.3.y |            0.22.z | [2020-08-27](https://stripe.com/docs/upgrades#2020-08-27) |
|          8.0.y |            0.24.z | [2020-08-27](https://stripe.com/docs/upgrades#2020-08-27) |

### Release notes

* Starting with 7.3.0, the Setup Intents API is used to validate and store payment instruments, instead of the Payment Intents API.
* We've upgraded numerous dependencies in 7.1.x (required for Java 11 support).

## Requirements

The plugin needs a database. The latest version of the schema can be found [here](https://github.com/killbill/killbill-stripe-plugin/blob/master/src/main/resources/ddl.sql).

## Installation

Locally:

```
kpm install_java_plugin stripe --from-source-file target/stripe-plugin-*-SNAPSHOT.jar --destination /var/tmp/bundles
```

## Configuration

Go to https://dashboard.stripe.com/test/apikeys and copy your `Secret key`.

Then, go to the Kaui plugin configuration page (`/admin_tenants/1?active_tab=PluginConfig`), and configure the `stripe` plugin with your key:

```java
org.killbill.billing.plugin.stripe.apiKey=sk_test_XXX
```

Alternatively, you can upload the configuration directly:

```bash
curl -v \
     -X POST \
     -u admin:password \
     -H 'X-Killbill-ApiKey: bob' \
     -H 'X-Killbill-ApiSecret: lazar' \
     -H 'X-Killbill-CreatedBy: admin' \
     -H 'Content-Type: text/plain' \
     -d 'org.killbill.billing.plugin.stripe.apiKey=sk_test_XXX
org.killbill.billing.plugin.stripe.chargeDescription=YYY
org.killbill.billing.plugin.stripe.chargeStatementDescriptor=ZZZ' \
     http://127.0.0.1:8080/1.0/kb/tenants/uploadPluginConfig/killbill-stripe
```

## Payment Method flow

To charge a payment instrument (card, bank account, etc.), you first need to collect the payment instrument details in Stripe and create an associated payment method in Kill Bill.

### Using Stripe Checkout

_Use this method if you don't want to generate your own form to tokenize cards._

To save credit cards using [Stripe Checkout](https://stripe.com/docs/payments/checkout):

1. Create a Kill Bill account
2. Call `/plugins/killbill-stripe/checkout` to generate a Session:
```bash
curl -v \
     -X POST \
     -u admin:password \
     -H "X-Killbill-ApiKey: bob" \
     -H "X-Killbill-ApiSecret: lazar" \
     -H "Content-Type: application/json" \
     -H "Accept: application/json" \
     -H "X-Killbill-CreatedBy: demo" \
     -H "X-Killbill-Reason: demo" \
     -H "X-Killbill-Comment: demo" \
     "http://127.0.0.1:8080/plugins/killbill-stripe/checkout?kbAccountId=<KB_ACCOUNT_ID>"
```

The default is to only allow credit cards. If you want to enable sepa direct debit payments, you need to include the `paymentMethodTypes` option, i.e. change the URL of your POST request
to `http://127.0.0.1:8080/plugins/killbill-stripe/checkout?kbAccountId=<KB_ACCOUNT_ID>&paymentMethodTypes=card&paymentMethodTypes=sepa_debit`.

3. Redirect the user to the Stripe checkout page. The `sessionId` is returned as part of the `formFields` (`id` key):
```javascript
stripe.redirectToCheckout({ sessionId: 'cs_test_XXX' });
```
**_NOTE:_** Adding payment information, such as credit card details, is unsafe, not recommended, and disabled by default in Stripe API. Therefore, adding a credit card directly via Kaui or the Stripe API is not possible. Instead, use the secure checkout process by creating a session with `mode=setup` see [Link](https://docs.stripe.com/api/checkout/sessions/create) for more details.

4. After entering the credit card or bank account details, the payment method will be available in Stripe. Call `addPaymentMethod` to store the payment method in Kill Bill:
```bash
curl -v \
     -X POST \
     -u admin:password \
     -H "X-Killbill-ApiKey: bob" \
     -H "X-Killbill-ApiSecret: lazar" \
     -H "Content-Type: application/json" \
     -H "Accept: application/json" \
     -H "X-Killbill-CreatedBy: demo" \
     -H "X-Killbill-Reason: demo" \
     -H "X-Killbill-Comment: demo" \
     -d "{ \"pluginName\": \"killbill-stripe\"}" \
     "http://127.0.0.1:8080/1.0/kb/accounts/<KB_ACCOUNT_ID>/paymentMethods?pluginProperty=sessionId=cs_test_XXX"
```

**_NOTE:_** If you encounter issues with step 4, try to refresh the PaymentMethods in Kaui or API (/1.0/kb/accounts/{accountId}/paymentMethods/refresh). This will load the added PaymentMethods from Stripe.

### Using tokens and sources

If you have a [token](https://stripe.com/docs/api/tokens) or [sources](https://stripe.com/docs/api/sources), you can pass it directly to `addPaymentMethod` in the plugin properties:

##### Token

```bash
curl -v \
     -X POST \
     -u admin:password \
     -H "X-Killbill-ApiKey: bob" \
     -H "X-Killbill-ApiSecret: lazar" \
     -H "Content-Type: application/json" \
     -H "Accept: application/json" \
     -H "X-Killbill-CreatedBy: demo" \
     -H "X-Killbill-Reason: demo" \
     -H "X-Killbill-Comment: demo" \
     -d "{ \"pluginName\": \"killbill-stripe\"}" \
     "http://127.0.0.1:8080/1.0/kb/accounts/<KB_ACCOUNT_ID>/paymentMethods?pluginProperty=token=tok_XXX"
```

##### Source

```bash
curl -v \
     -X POST \
     -u admin:password \
     -H "X-Killbill-ApiKey: bob" \
     -H "X-Killbill-ApiSecret: lazar" \
     -H "Content-Type: application/json" \
     -H "Accept: application/json" \
     -H "X-Killbill-CreatedBy: demo" \
     -H "X-Killbill-Reason: demo" \
     -H "X-Killbill-Comment: demo" \
     -d "{ \"pluginName\": \"killbill-stripe\"}" \
     "http://127.0.0.1:8080/1.0/kb/accounts/<KB_ACCOUNT_ID>/paymentMethods?pluginProperty=source=src_XXX"
```

Take a look at [kbcmd](https://github.com/killbill/kbcli/blob/master/docs/kbcmd/kbcmd-walkthrough.md) for a step-by-step walkthrough.

**_NOTE:_** If the token/source is already attached to a customer in Stripe, make sure to first set the `STRIPE_CUSTOMER_ID` custom field to the account in Kill Bill (see below) before calling `addPaymentMethod` (in this case, the token will be stored as-is and assumed to be re-usable if you intent to do subsequent payments). Otherwise, the plugin assumes it is a one-time token and will automatically create an associated customer in Stripe attached to this token/source to be able to re-use it (if needed, you can bypass this logic by specifying the `createStripeCustomer=false` plugin property in the `addPaymentMethod` call).

### Other methods

If you are using [Stripe Elements](https://stripe.com/docs/stripe-js/elements/quickstart) or storing payment methods in Stripe via any other way (or if you want to migrate from another billing system and already have customers in Stripe), the flow to setup Kill Bill accounts is as follows:

1. Create a Kill Bill account
2. Attach the custom field `STRIPE_CUSTOMER_ID` to the Kill Bill account. The custom field value should be the Stripe customer id
```bash
curl -v \
     -X POST \
     -u admin:password \
     -H "X-Killbill-ApiKey: bob" \
     -H "X-Killbill-ApiSecret: lazar" \
     -H "Content-Type: application/json" \
     -H "Accept: application/json" \
     -H "X-Killbill-CreatedBy: demo" \
     -H "X-Killbill-Reason: demo" \
     -H "X-Killbill-Comment: demo" \
     -d "[ { \"objectType\": \"ACCOUNT\", \"name\": \"STRIPE_CUSTOMER_ID\", \"value\": \"cus_XXXXX\" }]" \
     "http://127.0.0.1:8080/1.0/kb/accounts/<ACCOUNT_ID>/customFields"
```
3. Sync the payment methods from Stripe to Kill Bill:
```bash
curl -v \
     -X PUT \
     -u admin:password \
     -H "X-Killbill-ApiKey: bob" \
     -H "X-Killbill-ApiSecret: lazar" \
     -H "Content-Type: application/json" \
     -H "Accept: application/json" \
     -H "X-Killbill-CreatedBy: demo" \
     -H "X-Killbill-Reason: demo" \
     -H "X-Killbill-Comment: demo" \
     "http://127.0.0.1:8080/1.0/kb/accounts/<ACCOUNT_ID>/paymentMethods/refresh"
```
## Development

For testing you need to add your Stripe public and private key to `src/test/resources/stripe.properties`:

```
org.killbill.billing.plugin.stripe.apiKey=sk_test_XXX
org.killbill.billing.plugin.stripe.publicKey=pk_test_XXX
```

## About

Kill Bill is the leading Open-Source Subscription Billing & Payments Platform. For more information about the project, go to https://killbill.io/.
