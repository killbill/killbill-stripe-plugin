module Killbill #:nodoc:
  module Stripe #:nodoc:
    class PrivatePaymentPlugin < ::Killbill::Plugin::ActiveMerchant::PrivatePaymentPlugin
      def initialize(session = {})
        super(:stripe,
              ::Killbill::Stripe::StripePaymentMethod,
              ::Killbill::Stripe::StripeTransaction,
              ::Killbill::Stripe::StripeResponse,
              session)
      end
    end
  end
end
