include Killbill::Plugin::ActiveMerchant
module Killbill #:nodoc:
  module Stripe #:nodoc:
    class PaymentPlugin < ::Killbill::Plugin::ActiveMerchant::PaymentPlugin
      def initialize
        gateway_builder = Proc.new do |config|
          ::ActiveMerchant::Billing::StripeGateway.new :login => config[:api_secret_key]
        end

        super(gateway_builder,
              :stripe,
              ::Killbill::Stripe::StripePaymentMethod,
              ::Killbill::Stripe::StripeTransaction,
              ::Killbill::Stripe::StripeResponse)
      end

      def on_event(event)
        # Require to deal with per tenant configuration invalidation
        super(event)
        #
        # Custom event logic could be added below...
        #
      end

      def authorize_payment(kb_account_id, kb_payment_id, kb_payment_transaction_id, kb_payment_method_id, amount, currency, properties, context)
        pm = @payment_method_model.from_kb_payment_method_id(kb_payment_method_id, context.tenant_id)

        options = {}
        populate_defaults(pm, amount, properties, context, options)

        properties = merge_properties(properties, options)
        super(kb_account_id, kb_payment_id, kb_payment_transaction_id, kb_payment_method_id, amount, currency, properties, context)
      end

      def capture_payment(kb_account_id, kb_payment_id, kb_payment_transaction_id, kb_payment_method_id, amount, currency, properties, context)
        # Pass extra parameters for the gateway here
        options = {}

        properties = merge_properties(properties, options)
        super(kb_account_id, kb_payment_id, kb_payment_transaction_id, kb_payment_method_id, amount, currency, properties, context)
      end

      def purchase_payment(kb_account_id, kb_payment_id, kb_payment_transaction_id, kb_payment_method_id, amount, currency, properties, context)
        pm = @payment_method_model.from_kb_payment_method_id(kb_payment_method_id, context.tenant_id)

        options = {}
        populate_defaults(pm, amount, properties, context, options)

        properties = merge_properties(properties, options)
        super(kb_account_id, kb_payment_id, kb_payment_transaction_id, kb_payment_method_id, amount, currency, properties, context)
      end

      def void_payment(kb_account_id, kb_payment_id, kb_payment_transaction_id, kb_payment_method_id, properties, context)
        # Pass extra parameters for the gateway here
        options = {}

        properties = merge_properties(properties, options)
        super(kb_account_id, kb_payment_id, kb_payment_transaction_id, kb_payment_method_id, properties, context)
      end

      def credit_payment(kb_account_id, kb_payment_id, kb_payment_transaction_id, kb_payment_method_id, amount, currency, properties, context)
        # Pass extra parameters for the gateway here
        options = {}

        properties = merge_properties(properties, options)
        super(kb_account_id, kb_payment_id, kb_payment_transaction_id, kb_payment_method_id, amount, currency, properties, context)
      end

      def refund_payment(kb_account_id, kb_payment_id, kb_payment_transaction_id, kb_payment_method_id, amount, currency, properties, context)
        # Pass extra parameters for the gateway here
        options = {}

        reverse_transfer = find_value_from_properties(properties, :reverse_transfer)
        options[:reverse_transfer] = ::Killbill::Plugin::ActiveMerchant::Utils.normalize(reverse_transfer)

        refund_application_fee = find_value_from_properties(properties, :refund_application_fee)
        options[:refund_application_fee] = ::Killbill::Plugin::ActiveMerchant::Utils.normalize(refund_application_fee)

        properties = merge_properties(properties, options)
        super(kb_account_id, kb_payment_id, kb_payment_transaction_id, kb_payment_method_id, amount, currency, properties, context)
      end

      def get_payment_info(kb_account_id, kb_payment_id, properties, context)
        # Pass extra parameters for the gateway here
        options = {}

        properties = merge_properties(properties, options)
        super(kb_account_id, kb_payment_id, properties, context)
      end

      def search_payments(search_key, offset, limit, properties, context)
        # Pass extra parameters for the gateway here
        options = {}

        properties = merge_properties(properties, options)
        super(search_key, offset, limit, properties, context)
      end

      def add_payment_method(kb_account_id, kb_payment_method_id, payment_method_props, set_default, properties, context)
        # Do we have a customer for that account already?
        stripe_customer_id = find_value_from_properties(payment_method_props.properties, :customer) || StripePaymentMethod.stripe_customer_id_from_kb_account_id(kb_account_id, context.tenant_id)
        email = find_value_from_properties(payment_method_props.properties, :email) || @kb_apis.account_user_api.get_account_by_id(kb_account_id, @kb_apis.create_context(context.tenant_id)).email

        # Pass extra parameters for the gateway here
        options = {
            :email => email,
            # This will either update the current customer if present, or create a new one
            :customer => stripe_customer_id,
            # Magic field, see also private_api.rb (works only when creating an account)
            :description => kb_account_id
        }

        properties = merge_properties(properties, options)
        super(kb_account_id, kb_payment_method_id, payment_method_props, set_default, properties, context)
      end

      def delete_payment_method(kb_account_id, kb_payment_method_id, properties, context)
        pm = StripePaymentMethod.from_kb_payment_method_id(kb_payment_method_id, context.tenant_id)

        # Pass extra parameters for the gateway here
        options = {
            :customer_id => pm.stripe_customer_id
        }

        properties = merge_properties(properties, options)
        super(kb_account_id, kb_payment_method_id, properties, context)
      end

      def get_payment_method_detail(kb_account_id, kb_payment_method_id, properties, context)
        # Pass extra parameters for the gateway here
        options = {}

        properties = merge_properties(properties, options)
        super(kb_account_id, kb_payment_method_id, properties, context)
      end

      def set_default_payment_method(kb_account_id, kb_payment_method_id, properties, context)
        pm                           = StripePaymentMethod.from_kb_payment_method_id(kb_payment_method_id, context.tenant_id)

        # Update the default payment method on the customer object
        options                      = properties_to_hash(properties)
        payment_processor_account_id = options[:payment_processor_account_id] || :default
        gateway                      = lookup_gateway(payment_processor_account_id, context.tenant_id)
        stripe_response              = gateway.update_customer(pm.stripe_customer_id, :default_card => pm.token)
        response, transaction        = save_response_and_transaction(stripe_response, :set_default_payment_method, kb_account_id, context.tenant_id, payment_processor_account_id)

        if response.success
          # TODO Update our records
        else
          raise response.message
        end
      end

      def get_payment_methods(kb_account_id, refresh_from_gateway, properties, context)
        # Pass extra parameters for the gateway here
        options = {}

        properties = merge_properties(properties, options)
        super(kb_account_id, refresh_from_gateway, properties, context)
      end

      def search_payment_methods(search_key, offset, limit, properties, context)
        # Pass extra parameters for the gateway here
        options = {}

        properties = merge_properties(properties, options)
        super(search_key, offset, limit, properties, context)
      end

      def reset_payment_methods(kb_account_id, payment_methods, properties, context)
        super
      end

      def build_form_descriptor(kb_account_id, descriptor_fields, properties, context)
        # Pass extra parameters for the gateway here
        options = {}
        properties = merge_properties(properties, options)

        # Add your custom static hidden tags here
        options = {
            #:token => config[:stripe][:token]
        }
        descriptor_fields = merge_properties(descriptor_fields, options)

        super(kb_account_id, descriptor_fields, properties, context)
      end

      def process_notification(notification_json, properties, context)
        notification = JSON.parse(notification_json)
        gw_response = ::ActiveMerchant::Billing::Response.new(true,
                                                              nil,
                                                              notification,
                                                              :test => !notification['livemode'],
                                                              :authorization => notification['request'],
                                                              :avs_result => nil,
                                                              :cvv_result => nil,
                                                              :emv_authorization => nil,
                                                              :error_code => nil)
        save_response_and_transaction(gw_response, "webhook.#{notification['type']}".to_sym, nil, context.tenant_id, nil)

        gw_notification = ::Killbill::Plugin::Model::GatewayNotification.new
        gw_notification.kb_payment_id = nil
        gw_notification.status = 200
        gw_notification.headers = {}
        gw_notification.properties = []
        gw_notification
      end

      def verify_bank_account(stripe_customer_id, stripe_bank_account_id, amounts, kb_tenant_id)
        gateway = lookup_gateway(:default, kb_tenant_id)
        url = "customers/#{CGI.escape(stripe_customer_id)}/sources/#{CGI.escape(stripe_bank_account_id)}/verify?#{amounts_to_uri(amounts)}"
        gateway.api_request(:post, url)
      end

      private

      def amounts_to_uri(amounts)
        amounts.map {|v| "amounts[]=#{v.to_s}" }.join("&")
      end

      def before_gateways(kb_transaction, last_transaction, payment_source, amount_in_cents, currency, options, context)
        super(kb_transaction, last_transaction, payment_source, amount_in_cents, currency, options, context)
        options[:idempotency_key] ||= kb_transaction.external_key
      end

      def get_payment_source(kb_payment_method_id, properties, options, context)
        return nil if options[:customer_id]
        # check if bank account
        if is_bank_account?(properties)
          BankAccount.new({
            :bank_name => find_value_from_properties(properties, :bank_name),
            :routing_number => find_value_from_properties(properties, :routing_number),
            :account_number => find_value_from_properties(properties, :account_number),
            :type => find_value_from_properties(properties, :type) || "personal",
          })
        else
          super(kb_payment_method_id, properties, options, context)
        end
      end

      def populate_defaults(pm, amount, properties, context, options)
        options[:customer] ||= pm.stripe_customer_id
        options[:destination] ||= get_destination(properties, context)
        options[:application_fee] ||= get_application_fee(amount, properties) unless options[:destination].nil?
      end

      def get_destination(properties, context)
        stripe_account_id = find_value_from_properties(properties, :destination)
        if stripe_account_id.nil?
          config(context.tenant_id)[:stripe][:stripe_destination]
        elsif stripe_account_id =~ /[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}/
          # Stripe doesn't use UUIDs - assume the destination is a Kill Bill account id
          ::Killbill::Stripe::StripeResponse.managed_stripe_account_id_from_kb_account_id(stripe_account_id, context.tenant_id)
        else
          stripe_account_id
        end
      end

      def get_application_fee(amount, properties)
        fees_amount = find_value_from_properties(properties, :fees_amount)
        return fees_amount unless fees_amount.nil?

        fees_percent = find_value_from_properties(properties, :fees_percent)
        return (fees_percent * amount * 100).to_i unless fees_percent.nil?

        config(context.tenant_id)[:stripe][:fees_amount] || (config(context.tenant_id)[:stripe][:fees_percent].to_f * amount * 100)
      end

      def is_bank_account?(properties)
        find_value_from_properties(properties, :routing_number) &&
          find_value_from_properties(properties, :account_number)
      end
    end

    class BankAccount
      attr_accessor :bank_name, :account_number, :routing_number, :type

      def initialize(args)
        args.each do |k,v|
          instance_variable_set("@#{k}", v) unless v.nil?
        end
      end
    end
  end
end
