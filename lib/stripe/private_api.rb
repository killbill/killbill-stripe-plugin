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

      def get_balance(kb_account_id = nil, kb_tenant_id = nil, options = {})
        options[:stripe_account] ||= ::Killbill::Stripe::StripeResponse.managed_stripe_account_id_from_kb_account_id(kb_account_id, kb_tenant_id)

        payment_processor_account_id = options[:payment_processor_account_id] || :default
        gateway = gateway(payment_processor_account_id, kb_tenant_id)
        stripe_response = gateway.get_balance(options)
        save_response_and_transaction(stripe_response, :get_balance, kb_account_id, kb_tenant_id, payment_processor_account_id)

        stripe_response
      end

      def create_managed_account(kb_account_id, kb_tenant_id, account = {}, options = {})
        payment_processor_account_id = options[:payment_processor_account_id] || :default
        gateway = gateway(payment_processor_account_id, kb_tenant_id)
        stripe_response = gateway.create_managed_account(account, options)
        save_response_and_transaction(stripe_response, :create_managed_account, kb_account_id, kb_tenant_id, payment_processor_account_id)

        stripe_response
      end
    end
  end
end
