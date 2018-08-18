killbill-stripe-plugin
======================

Plugin to use [Stripe](https://stripe.com/) as a gateway.

Run
---

```bash
go run plugin.go -logtostderr=false -stderrthreshold=INFO --api_secret_key=sk_test_XXX
```

Tests
-----

```bash
STRIPE_SECRET_KEY=sk_test_XXX STRIPE_CUSTOMER_ID=cus_YYY STRIPE_TOKEN=tok_mastercard go test -test.v ./...
```
