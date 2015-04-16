killbill-stripe-plugin
======================

Plugin to use [Stripe](https://stripe.com/) as a gateway.

Release builds are available on [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.kill-bill.billing.plugin.ruby%22%20AND%20a%3A%22stripe-plugin%22) with coordinates `org.kill-bill.billing.plugin.ruby:stripe-plugin`.

Kill Bill compatibility
-----------------------

| Plugin version | Kill Bill version | Stripe version                                            |
| -------------: | ----------------: | --------------------------------------------------------: |
| 1.0.y          | 0.14.z            | [2015-02-18](https://stripe.com/docs/upgrades#2015-02-18) |

Requirements
------------

The plugin needs a database. The latest version of the schema can be found [here](https://raw.github.com/killbill/killbill-stripe-plugin/master/db/ddl.sql).

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
  :api_publishable_key: "your-publishable-key"' \
     http://127.0.0.1:8080/1.0/kb/tenants/uploadPluginConfig/killbill-paypal-express
```

To get your credentials:

1. Go to [stripe.com](http://stripe.com/) and create an account. This account will be used as a sandbox environment for testing.
2. In your Stripe account, click on **Your Account** (top right), then click on **Account Settings** and then on the **API Keys** tab. Write down your keys.

To go to production, create a `stripe.yml` configuration file under `/var/tmp/bundles/plugins/ruby/killbill-stripe/x.y.z/` containing the following:

```
:stripe:
  :test: false
```
