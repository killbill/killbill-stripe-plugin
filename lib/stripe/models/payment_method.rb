module Killbill #:nodoc:
  module Stripe #:nodoc:
    class StripePaymentMethod < ::Killbill::Plugin::ActiveMerchant::ActiveRecord::PaymentMethod

      self.table_name = 'stripe_payment_methods'

      def self.from_response(kb_account_id, kb_payment_method_id, kb_tenant_id, cc_or_token, response, options, extra_params = {}, model = ::Killbill::Stripe::StripePaymentMethod)
        stripe_customer_id = self.stripe_customer_id_from_kb_account_id(kb_account_id, kb_tenant_id)
        unless stripe_customer_id.blank?
          card_response     = response.responses.first.params
          customer_response = response.responses.last.params
        else
          card_response     = response.params['cards']['data'][0]
          customer_response = response.params
        end

        super(kb_account_id,
              kb_payment_method_id,
              kb_tenant_id,
              cc_or_token,
              response,
              options,
              {
                  :stripe_customer_id => customer_response['id'],
                  :token              => card_response['id'],
                  :cc_first_name      => card_response['name'],
                  :cc_last_name       => nil,
                  :cc_type            => card_response['type'],
                  :cc_exp_month       => card_response['exp_month'],
                  :cc_exp_year        => card_response['exp_year'],
                  :cc_last_4          => card_response['last4'],
                  :address1           => card_response['address_line1'],
                  :address2           => card_response['address_line2'],
                  :city               => card_response['address_city'],
                  :state              => card_response['address_state'],
                  :zip                => card_response['address_zip'],
                  :country            => card_response['address_country']
              }.merge!(extra_params),
              model)
      end

      def self.search_where_clause(t, search_key)
        super.or(t[:stripe_customer_id].eq(search_key))
      end

      def self.stripe_customer_id_from_kb_account_id(kb_account_id, tenant_id)
        pms = from_kb_account_id(kb_account_id, tenant_id)
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
