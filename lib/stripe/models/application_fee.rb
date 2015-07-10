module Killbill #:nodoc:
  module Stripe #:nodoc:
    require 'active_record'
    require 'active_merchant'
    require 'money'
    require 'time'
    require 'killbill/helpers/active_merchant/active_record/models/helpers'
    class StripeApplicationFee  < ::ActiveRecord::Base

       extend ::Killbill::Plugin::ActiveMerchant::Helpers

       self.table_name = 'stripe_application_fees'

    end
  end
end