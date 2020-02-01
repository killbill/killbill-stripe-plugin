# killbill-stripe-plugin

Plugin to use [Stripe](https://stripe.com/) as a gateway.

Release builds are available on [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.kill-bill.billing.plugin.java%22%20AND%20a%3A%22stripe-plugin%22) with coordinates `org.kill-bill.billing.plugin.java:stripe-plugin`.

A full end-to-end integration demo is available [here](https://github.com/killbill/killbill-stripe-demo).

## Kill Bill compatibility

| Plugin version | Kill Bill version  | Stripe version                                            |
| -------------: | -----------------: | --------------------------------------------------------: |
| 1.x.y          | 0.14.z             | [2015-02-18](https://stripe.com/docs/upgrades#2015-02-18) |
| 3.x.y          | 0.16.z             | [2015-02-18](https://stripe.com/docs/upgrades#2015-02-18) |
| 4.x.y          | 0.18.z             | [2015-02-18](https://stripe.com/docs/upgrades#2015-02-18) |
| 5.x.y          | 0.19.z             | [2015-02-18](https://stripe.com/docs/upgrades#2015-02-18) |
| 6.x.y          | 0.20.z             | [2015-02-18](https://stripe.com/docs/upgrades#2015-02-18) |
| 7.x.y          | 0.22.z             | [2019-12-03](https://stripe.com/docs/upgrades#2019-12-03) |

**Note**: upgrading from 6.x.y to 7.x.y is currently not documented and therefore not recommended. Users running 6.x.y in production and wishing to upgrade should contact the core team on the support forum for guidance.

## Requirements

The plugin needs a database. The latest version of the schema can be found [here](https://github.com/killbill/killbill-stripe-plugin/blob/master/src/main/resources/ddl.sql).

## Installation

Locally:

```
kpm install_java_plugin stripe --from-source-file=target/stripe-plugin-7.0.0-SNAPSHOT.jar --destination=/var/tmp/bundles
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
     -d 'org.killbill.billing.plugin.stripe.apiKey=sk_test_XXX' \
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
3. Redirect the user to the Stripe checkout page. The `sessionId` is returned as part of the `formFields` (`id` key):
```javascript
stripe.redirectToCheckout({ sessionId: 'cs_test_XXX' });
```
4. After entering the credit card, a $1 authorization will be triggered. Call `addPaymentMethod` to create the Stripe payment method and pass the `sessionId` in the plugin properties. This will void the authorization (if successful) and store the payment method in Kill Bill:
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

### Using tokens

If you have a [token](https://stripe.com/docs/api/tokens), you can pass it directly to `addPaymentMethod` in the plugin properties:

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

Take a look at [kbcmd](https://github.com/killbill/kbcli/blob/master/docs/kbcmd/kbcmd-walkthrough.md) for a step-by-step walkthrough.

Note: if the token is already attached to a customer in Stripe, make sure to first set the `STRIPE_CUSTOMER_ID` custom field to the account in Kill Bill (see below) before calling `addPaymentMethod` (in this case, the token will be stored as-is and assumed to be re-usable if you intent to do subsequent payments). Otherwise, the plugin assumes it is a one-time token and will automatically create an associated customer in Stripe attached to this token to be able to re-use it (if needed, you can bypass this logic by specifying the `createStripeCustomer=false` plugin property in the `addPaymentMethod` call).

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
