module Killbill #:nodoc:
  module Stripe #:nodoc:
    class StripePaymentMethod < ::Killbill::Plugin::ActiveMerchant::ActiveRecord::PaymentMethod

      self.table_name = 'stripe_payment_methods'

      def self.from_response(kb_account_id, kb_payment_method_id, kb_tenant_id, cc_or_token, response, options, extra_params = {:source_type => "cc"}, model = ::Killbill::Stripe::StripePaymentMethod)
        stripe_customer_id = options[:customer] || self.stripe_customer_id_from_kb_account_id(kb_account_id, kb_tenant_id)
        if response.params["bank_account"]
          extra_params = {} #overwrite extra params because they will be passed with assumption of CC
          payment_response = {
            "token" => response.params["bank_account"]["id"],
            "address_country" => response.params["bank_account"]["country"],
          }
          customer_response = { "id" => stripe_customer_id }
          extra_params[:bank_name] = response.params["bank_account"]["bank_name"]
          extra_params[:bank_routing_number] = response.params["bank_account"]["routing_number"]
          extra_params[:source_type] = "bank_account"
          cc_or_token ||= response.params["bank_account"]["id"]
        elsif !stripe_customer_id.blank? && response.respond_to?(:responses)
          payment_response     = response.responses.first.params
          customer_response = response.responses.last.params
        elsif response.params['sources']
          payment_response     = response.params['sources']['data'][0]
          customer_response = response.params
        else
          payment_response = {}
          customer_response = { 'id' => stripe_customer_id }
        end

        super(kb_account_id,
              kb_payment_method_id,
              kb_tenant_id,
              cc_or_token,
              response,
              options,
              {
                  :stripe_customer_id => customer_response['id'],
                  :token              => payment_response['id'],
                  :cc_first_name      => payment_response['name'],
                  :cc_last_name       => nil,
                  :cc_type            => payment_response['brand'],
                  :cc_exp_month       => payment_response['exp_month'],
                  :cc_exp_year        => payment_response['exp_year'],
                  :cc_last_4          => payment_response['last4'],
                  :address1           => payment_response['address_line1'],
                  :address2           => payment_response['address_line2'],
                  :city               => payment_response['address_city'],
                  :state              => payment_response['address_state'],
                  :zip                => payment_response['address_zip'],
                  :country            => payment_response['address_country']
              }.merge!(extra_params.compact), # Don't override with nil values
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
        raise "Kill Bill account #{kb_account_id} mapping to multiple Stripe customers: #{stripe_customer_ids.to_a}" if stripe_customer_ids.size > 1
        stripe_customer_ids.first
      end
    end
  end
end
