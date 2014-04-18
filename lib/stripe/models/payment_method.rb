module Killbill #:nodoc:
  module Stripe #:nodoc:
    class StripePaymentMethod < ::Killbill::Plugin::ActiveMerchant::ActiveRecord::PaymentMethod

      self.table_name = 'stripe_payment_methods'

      def external_payment_method_id
        stripe_token
      end

      def self.search_where_clause(t, search_key)
        where_clause = t[:stripe_token].eq(search_key)
                   .or(t[:stripe_customer_id].eq(search_key))

        super.or(where_clause)
      end

      def self.stripe_customer_id_from_kb_account_id(kb_account_id)
        pms = from_kb_account_id(kb_account_id)
        return nil if pms.empty?

        stripe_customer_ids = Set.new
        pms.each { |pm| stripe_customer_ids << pm.stripe_customer_id }
        raise "No Stripe customer id found for account #{kb_account_id}" if stripe_customer_ids.empty?
        raise "Kill Bill account #{kb_account_id} mapping to multiple Stripe customers: #{stripe_customer_ids}" if stripe_customer_ids.size > 1
        stripe_customer_ids.first
      end
    end
  end
end
