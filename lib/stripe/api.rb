module Killbill::Stripe
  class PaymentPlugin < Killbill::Plugin::Payment
    def start_plugin
      Killbill::Stripe.initialize! @logger, @conf_dir, @kb_apis

      super

      @logger.info 'Killbill::Stripe::PaymentPlugin started'
    end

    # return DB connections to the Pool if required
    def after_request
      ActiveRecord::Base.connection.close
    end

    def process_payment(kb_account_id, kb_payment_id, kb_payment_method_id, amount, currency, call_context = nil, options = {})
      amount_in_cents = (amount * 100).to_i

      # If the payment was already made, just return the status
      stripe_transaction = StripeTransaction.from_kb_payment_id(kb_payment_id) rescue nil
      return stripe_transaction.stripe_response.to_payment_response unless stripe_transaction.nil?

      options[:order_id] ||= kb_payment_id
      options[:currency] ||= currency.to_s.upcase
      options[:description] ||= Killbill::Stripe.stripe_payment_description || "Kill Bill payment for #{kb_payment_id}"

      # Retrieve the Stripe payment method (charge the Stripe customer)
      options[:customer] ||= StripePaymentMethod.from_kb_payment_method_id(kb_payment_method_id).stripe_customer_id

      # Go to Stripe
      stripe_response = Killbill::Stripe.gateway.purchase amount_in_cents, nil, options
      response = save_response_and_transaction stripe_response, :charge, kb_payment_id, amount_in_cents

      response.to_payment_response
    end

    def get_payment_info(kb_account_id, kb_payment_id, tenant_context = nil, options = {})
      # We assume the payment is immutable in Stripe and only look at our tables
      stripe_transaction = StripeTransaction.from_kb_payment_id(kb_payment_id)
      stripe_transaction.stripe_response.to_payment_response
    end

    def process_refund(kb_account_id, kb_payment_id, amount, currency, call_context = nil, options = {})
      amount_in_cents = (amount * 100).to_i

      stripe_transaction = StripeTransaction.find_candidate_transaction_for_refund(kb_payment_id, amount_in_cents)

      # Go to Stripe
      stripe_response = Killbill::Stripe.gateway.refund amount_in_cents, stripe_transaction.stripe_txn_id, options
      response = save_response_and_transaction stripe_response, :refund, kb_payment_id, amount_in_cents

      response.to_refund_response
    end

    def get_refund_info(kb_account_id, kb_payment_id, tenant_context = nil, options = {})
      # We assume the refund is immutable in Stripe and only look at our tables
      stripe_transaction = StripeTransaction.refund_from_kb_payment_id(kb_payment_id)
      stripe_transaction.stripe_response.to_refund_response
    end

    def add_payment_method(kb_account_id, kb_payment_method_id, payment_method_props, set_default, call_context = nil, options = {})
      # Do we have a customer for that account already?
      stripe_customer_id = StripePaymentMethod.stripe_customer_id_from_kb_account_id(kb_account_id)

      # This will either update the current customer if present, or create a new one
      options[:customer] ||= stripe_customer_id

      # Magic field, see also private_api.rb
      options[:description] ||= kb_account_id

      # Registering a token from Stripe.js?
      cc_or_token = find_value_from_payment_method_props payment_method_props, 'token'
      if cc_or_token.blank?
        cc = ActiveMerchant::Billing.CreditCard.new(
              :number             => find_value_from_payment_method_props(payment_method_props, 'ccNumber'),
              :month              => find_value_from_payment_method_props(payment_method_props, 'ccExpirationMonth'),
              :year               => find_value_from_payment_method_props(payment_method_props, 'ccExpirationYear'),
              :verification_value => find_value_from_payment_method_props(payment_method_props, 'ccVerificationValue'),
              :first_name         => find_value_from_payment_method_props(payment_method_props, 'ccFirstName'),
              :last_name          => find_value_from_payment_method_props(payment_method_props, 'ccLstName')
        )
      end

      # Go to Stripe
      stripe_response = Killbill::Stripe.gateway.store cc_or_token, options
      response = save_response_and_transaction stripe_response, :add_payment_method

      if response.success
        # TODO Extract values from response
        StripePaymentMethod.create :kb_account_id => kb_account_id,
                                   :kb_payment_method_id => kb_payment_method_id,
                                   :stripe_customer_id => options[:customer],
                                   :stripe_token => response.stripe_token,
                                   :cc_first_name => find_value_from_payment_method_props(payment_method_props, 'ccFirstName'),
                                   :cc_last_name => find_value_from_payment_method_props(payment_method_props, 'ccLastName'),
                                   :cc_type => find_value_from_payment_method_props(payment_method_props, 'ccType'),
                                   :cc_exp_month => find_value_from_payment_method_props(payment_method_props, 'ccExpMonth'),
                                   :cc_exp_year => find_value_from_payment_method_props(payment_method_props, 'ccExpYear'),
                                   :cc_last_4 => find_value_from_payment_method_props(payment_method_props, 'ccLast4'),
                                   :address1 => find_value_from_payment_method_props(payment_method_props, 'address1'),
                                   :address2 => find_value_from_payment_method_props(payment_method_props, 'address2'),
                                   :city => find_value_from_payment_method_props(payment_method_props, 'city'),
                                   :state => find_value_from_payment_method_props(payment_method_props, 'state'),
                                   :zip => find_value_from_payment_method_props(payment_method_props, 'zip'),
                                   :country => find_value_from_payment_method_props(payment_method_props, 'country')
      else
        raise response.message
      end
    end

    def delete_payment_method(kb_account_id, kb_payment_method_id, call_context = nil, options = {})
      # TODO No ActiveMerchant API to unstore the card without deleting the customer
      StripePaymentMethod.mark_as_deleted! kb_payment_method_id
    end

    def get_payment_method_detail(kb_account_id, kb_payment_method_id, tenant_context = nil, options = {})
      StripePaymentMethod.from_kb_payment_method_id(kb_payment_method_id).to_payment_method_response
    end

    def set_default_payment_method(kb_account_id, kb_payment_method_id, call_context = nil, options = {})
      # TODO Update our records and Stripe's
      # No-op
    end

    def get_payment_methods(kb_account_id, refresh_from_gateway = false, call_context = nil, options = {})
      StripePaymentMethod.from_kb_account_id(kb_account_id).collect { |pm| pm.to_payment_method_info_response }
    end

    def reset_payment_methods(kb_account_id, payment_methods)
      return if payment_methods.nil?

      stripe_pms = StripePaymentMethod.from_kb_account_id(kb_account_id)

      payment_methods.delete_if do |payment_method_info_plugin|
        should_be_deleted = false
        stripe_pms.each do |stripe_pm|
          # Do stripe_pm and payment_method_info_plugin represent the same Stripe payment method?
          if stripe_pm.external_payment_method_id == payment_method_info_plugin.external_payment_method_id
            # Do we already have a kb_payment_method_id?
            if stripe_pm.kb_payment_method_id == payment_method_info_plugin.payment_method_id
              should_be_deleted = true
              break
            elsif stripe_pm.kb_payment_method_id.nil?
              # We didn't have the kb_payment_method_id - update it
              stripe_pm.kb_payment_method_id = payment_method_info_plugin.payment_method_id
              should_be_deleted = stripe_pm.save
              break
              # Otherwise the same card points to 2 different kb_payment_method_id. This should never happen,
              # but we cowardly will insert a second row below
            end
          end
        end

        should_be_deleted
      end

      # The remaining elements in payment_methods are not in our table (this should never happen?!)
      # TODO retrieve and create Stripe account if needed
      payment_methods.each do |payment_method_info_plugin|
        StripePaymentMethod.create :kb_account_id => payment_method_info_plugin.account_id,
                                   :kb_payment_method_id => payment_method_info_plugin.payment_method_id,
                                   :stripe_token => payment_method_info_plugin.external_payment_method_id
      end
    end

    def search_payment_methods(search_key, offset = 0, limit = 100, call_context = nil, options = {})
      StripePaymentMethod.search(search_key, offset, limit)
    end

    private

    def find_value_from_payment_method_props(payment_method_props, key)
      prop = (payment_method_props.properties.find { |kv| kv.key == key })
      prop.nil? ? nil : prop.value
    end

    def save_response_and_transaction(stripe_response, api_call, kb_payment_id=nil, amount_in_cents=0)
      @logger.warn "Unsuccessful #{api_call}: #{stripe_response.message}" unless stripe_response.success?

      # Save the response to our logs
      response = StripeResponse.from_response(api_call, kb_payment_id, stripe_response)
      response.save!

      if response.success and !kb_payment_id.blank? and !response.stripe_txn_id.blank?
        # Record the transaction
        transaction = response.create_stripe_transaction!(:amount_in_cents => amount_in_cents,
                                                          :api_call => api_call,
                                                          :kb_payment_id => kb_payment_id,
                                                          :stripe_txn_id => response.stripe_txn_id)
        @logger.debug "Recorded transaction: #{transaction.inspect}"
      end
      response
    end
  end
end
