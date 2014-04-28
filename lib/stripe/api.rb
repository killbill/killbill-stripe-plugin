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

      def authorize_payment(kb_account_id, kb_payment_id, kb_payment_method_id, amount, currency, properties, context)
        # Pass extra parameters for the gateway here
        options = {}
        properties = merge_properties(properties, options)
        super(kb_account_id, kb_payment_id, kb_payment_method_id, amount, currency, properties, context)
      end

      def capture_payment(kb_account_id, kb_payment_id, kb_payment_method_id, amount, currency, properties, context)
        # Pass extra parameters for the gateway here
        options = {}
        properties = merge_properties(properties, options)
        super(kb_account_id, kb_payment_id, kb_payment_method_id, amount, currency, properties, context)
      end

      def void_payment(kb_account_id, kb_payment_id, kb_payment_method_id, properties, context)
        # Pass extra parameters for the gateway here
        options = {}
        properties = merge_properties(properties, options)
        super(kb_account_id, kb_payment_id, kb_payment_method_id, properties, context)
      end

      def process_payment(kb_account_id, kb_payment_id, kb_payment_method_id, amount, currency, properties, context)
        pm = @payment_method_model.from_kb_payment_method_id(kb_payment_method_id, context.tenant_id)

        options = {
            :customer => pm.stripe_customer_id
        }
        properties = merge_properties(properties, options)
        super(kb_account_id, kb_payment_id, kb_payment_method_id, amount, currency, properties, context)
      end

      def get_payment_info(kb_account_id, kb_payment_id, properties, context)
        super
      end

      def search_payments(search_key, offset, limit, properties, context)
        super
      end

      def process_refund(kb_account_id, kb_payment_id, refund_amount, currency, properties, context)
        # Pass extra parameters for the gateway here
        options = {}
        properties = merge_properties(properties, options)
        super(kb_account_id, kb_payment_id, refund_amount, currency, properties, context)
      end

      def get_refund_info(kb_account_id, kb_payment_id, properties, context)
        super
      end

      def search_refunds(search_key, offset, limit, properties, context)
        super
      end

      def add_payment_method(kb_account_id, kb_payment_method_id, payment_method_props, set_default, properties, context)
        # Do we have a customer for that account already?
        stripe_customer_id = StripePaymentMethod.stripe_customer_id_from_kb_account_id(kb_account_id, context.tenant_id)

        options = {
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

        options = {
            :customer_id => pm.stripe_customer_id
        }
        properties = merge_properties(properties, options)
        super(kb_account_id, kb_payment_method_id, properties, context)
      end

      def get_payment_method_detail(kb_account_id, kb_payment_method_id, properties, context)
        super
      end

      def set_default_payment_method(kb_account_id, kb_payment_method_id, properties, context)
        pm                    = StripePaymentMethod.from_kb_payment_method_id(kb_payment_method_id, context.tenant_id)

        # Update the default payment method on the customer object
        stripe_response       = gateway.update_customer(pm.stripe_customer_id, :default_card => pm.token)
        response, transaction = save_response_and_transaction stripe_response, :set_default_payment_method, kb_account_id, context.tenant_id

        if response.success
          # TODO Update our records
        else
          raise response.message
        end
      end

      def get_payment_methods(kb_account_id, refresh_from_gateway, properties, context)
        super
      end

      def search_payment_methods(search_key, offset, limit, properties, context)
        super
      end

      def reset_payment_methods(kb_account_id, payment_methods, properties, context)
        super
      end

      def build_form_descriptor(kb_account_id, custom_fields, properties, context)
        super
      end

      def process_notification(notification, properties, context)
        super
      end
    end
  end
end
