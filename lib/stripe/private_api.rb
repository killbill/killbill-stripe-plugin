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

      def add_payment_method(params, options = {})
        # We need to log-in manually because the call comes unauthenticated from the browser
        # (the default credentials hardcoded: good enough since this should only be used for testing)
        kb_apis.security_api.login('admin', 'password')

        kb_account_id = params['kbAccountId']
        kb_tenant_id = params['kbTenantId']

        context = kb_apis.create_context(kb_tenant_id)
        kb_account = kb_apis.account_user_api.get_account_by_id(kb_account_id, context)

        payment_method_info = ::Killbill::Plugin::Model::PaymentMethodPlugin.new
        payment_method_info.properties = []
        payment_method_info.properties << build_property('cc_first_name', params['stripeCardName'])
        payment_method_info.properties << build_property('address1', params['stripeCardAddressLine1'])
        payment_method_info.properties << build_property('address2', params['stripeCardAddressLine2'])
        payment_method_info.properties << build_property('city', params['stripeCardAddressCity'])
        payment_method_info.properties << build_property('zip', params['stripeCardAddressZip'])
        payment_method_info.properties << build_property('state', params['stripeCardAddressState'])
        payment_method_info.properties << build_property('country', params['stripeCardAddressCountry'])
        payment_method_info.properties << build_property('cc_expiration_month', params['stripeCardExpMonth'])
        payment_method_info.properties << build_property('cc_expiration_year', params['stripeCardExpYear'])
        payment_method_info.properties << build_property('token', params['stripeToken'])

        kb_apis.payment_api.add_payment_method(kb_account, params['stripeToken'], 'killbill-stripe', true, payment_method_info, [], context)
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

      private

      def build_property(key, value = nil)
        prop = ::Killbill::Plugin::Model::PluginProperty.new
        prop.key = key
        prop.value = value
        prop
      end
    end
  end
end
