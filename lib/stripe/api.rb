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

      def process_payment(kb_account_id, kb_payment_id, kb_payment_method_id, amount, currency, context)
        pm = @payment_method_model.from_kb_payment_method_id(kb_payment_method_id)

        options = {
            :customer => pm.stripe_customer_id
        }
        super(kb_account_id, kb_payment_id, kb_payment_method_id, amount, currency, context, options)
      end

      def get_payment_info(kb_account_id, kb_payment_id, context)
        super
      end

      def search_payments(search_key, offset, limit, context)
        super
      end

      def process_refund(kb_account_id, kb_payment_id, refund_amount, currency, context)
        super
      end

      def get_refund_info(kb_account_id, kb_payment_id, context)
        super
      end

      def search_refunds(search_key, offset, limit, context)
        super
      end

      def add_payment_method(kb_account_id, kb_payment_method_id, payment_method_props, set_default, context)
        # Do we have a customer for that account already?
        stripe_customer_id = StripePaymentMethod.stripe_customer_id_from_kb_account_id(kb_account_id)

        options               = {}

        # This will either update the current customer if present, or create a new one
        options[:customer]    ||= stripe_customer_id
        options[:set_default] ||= set_default

        # Magic field, see also private_api.rb (works only when creating an account)
        if options[:customer].blank?
          options[:description] ||= kb_account_id
        else
          options[:description] = nil
        end

        # Registering a card or a token from Stripe.js?
        cc_or_token = find_value_from_payment_method_props(payment_method_props, 'token') || find_value_from_payment_method_props(payment_method_props, 'cardId')
        if cc_or_token.blank?
          # Nope - real credit card
          cc_or_token = ActiveMerchant::Billing::CreditCard.new(
              :number             => find_value_from_payment_method_props(payment_method_props, 'ccNumber'),
              :month              => find_value_from_payment_method_props(payment_method_props, 'ccExpirationMonth'),
              :year               => find_value_from_payment_method_props(payment_method_props, 'ccExpirationYear'),
              :verification_value => find_value_from_payment_method_props(payment_method_props, 'ccVerificationValue'),
              :first_name         => find_value_from_payment_method_props(payment_method_props, 'ccFirstName'),
              :last_name          => find_value_from_payment_method_props(payment_method_props, 'ccLastName')
          )
        end

        options[:billing_address] ||= {
            :address1 => find_value_from_payment_method_props(payment_method_props, 'address1'),
            :address2 => find_value_from_payment_method_props(payment_method_props, 'address2'),
            :city     => find_value_from_payment_method_props(payment_method_props, 'city'),
            :zip      => find_value_from_payment_method_props(payment_method_props, 'zip'),
            :state    => find_value_from_payment_method_props(payment_method_props, 'state'),
            :country  => find_value_from_payment_method_props(payment_method_props, 'country')
        }

        # Go to Stripe
        stripe_response           = gateway.store cc_or_token, options
        response, transaction     = save_response_and_transaction stripe_response, :add_payment_method

        if response.success
          unless stripe_customer_id.blank?
            card_response     = stripe_response.responses.first.params
            customer_response = stripe_response.responses.last.params
          else
            card_response     = stripe_response.params['cards']['data'][0]
            customer_response = stripe_response.params
          end

          StripePaymentMethod.create :kb_account_id        => kb_account_id,
                                     :kb_payment_method_id => kb_payment_method_id,
                                     :stripe_customer_id   => customer_response['id'],
                                     :stripe_token         => card_response['id'],
                                     :cc_first_name        => card_response['name'],
                                     :cc_last_name         => nil,
                                     :cc_type              => card_response['type'],
                                     :cc_exp_month         => card_response['exp_month'],
                                     :cc_exp_year          => card_response['exp_year'],
                                     :cc_last_4            => card_response['last4'],
                                     :address1             => card_response['address_line1'],
                                     :address2             => card_response['address_line2'],
                                     :city                 => card_response['address_city'],
                                     :state                => card_response['address_state'],
                                     :zip                  => card_response['address_zip'],
                                     :country              => card_response['address_country']
        else
          raise response.message
        end
      end

      def delete_payment_method(kb_account_id, kb_payment_method_id, context)
        pm                    = StripePaymentMethod.from_kb_payment_method_id(kb_payment_method_id)

        # Delete the card on the customer object
        stripe_response       = gateway.unstore(pm.stripe_customer_id, pm.stripe_token)
        response, transaction = save_response_and_transaction stripe_response, :delete_payment_method

        if response.success
          StripePaymentMethod.mark_as_deleted! kb_payment_method_id
        else
          raise response.message
        end
      end

      def get_payment_method_detail(kb_account_id, kb_payment_method_id, context)
        super
      end

      def set_default_payment_method(kb_account_id, kb_payment_method_id, context)
        pm                    = StripePaymentMethod.from_kb_payment_method_id(kb_payment_method_id)

        # Update the default payment method on the customer object
        stripe_response       = gateway.update_customer(pm.stripe_customer_id, :default_card => pm.stripe_token)
        response, transaction = save_response_and_transaction stripe_response, :set_default_payment_method

        if response.success
          # TODO Update our records
        else
          raise response.message
        end
      end

      def get_payment_methods(kb_account_id, refresh_from_gateway, context)
        super
      end

      def search_payment_methods(search_key, offset, limit, context)
        super
      end

      def reset_payment_methods(kb_account_id, payment_methods)
        super
      end
    end
  end
end
