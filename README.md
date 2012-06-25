killbill-stripe-plugin
======================

Killbill payment plugin for Stripe.

Getting started
---------------

1. Make sure you can build the project by running the smoke tests: `mvn clean test`
2. Go to [stripe.com](http://stripe.com/) and create an account. This account will be used as a sandbox environment for testing.
3. In your Stripe account, click on **Your Account** (top right), then click on **Account Settings** and then on the **API Keys** tab. Write down your Test Secret Key.
4. Verify the setup by running the killbill-stripe-plugin integration tests (make sure to update your API Key): `mvn clean test -Pintegration -Dkillbill.payment.stripe.apiKey=1234567689abcdef`
5. Go to your Stripe account, you should see some data (e.g. account created).
6. Congrats! You're all set!

Build
-----

To build the project, use maven:

    mvn clean install

Note: you may need to install the killbill-oss-parent artifact first, to use SNAPSHOT dependencies. You can find it [here](https://github.com/killbilling/killbill-oss-parent).