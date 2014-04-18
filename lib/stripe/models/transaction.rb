module Killbill #:nodoc:
  module Stripe #:nodoc:
    class StripeTransaction < ::Killbill::Plugin::ActiveMerchant::ActiveRecord::Transaction

      self.table_name = 'stripe_transactions'

      belongs_to :stripe_response

    end
  end
end
