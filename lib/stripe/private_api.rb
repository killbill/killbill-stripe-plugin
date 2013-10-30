module Killbill::Stripe
  class PrivatePaymentPlugin
    include Singleton

    def add_payment_method(params)
      stripe_customer_id = StripePaymentMethod.stripe_customer_id_from_kb_account_id(params[:kbAccountId])

      # This will either update the current customer if present, or create a new one
      stripe_response = gateway.store params[:stripeToken], { :description => params[:kbAccountId], :customer => stripe_customer_id }
      response = save_response stripe_response, :add_payment_method
      raise response.message unless response.success

      # Create the payment method (not associated to a Kill Bill payment method yet)
      pm = Killbill::Stripe::StripePaymentMethod.create! :kb_account_id => params[:kbAccountId],
                                                         :kb_payment_method_id => nil,
                                                         :stripe_customer_id => stripe_customer_id,
                                                         :stripe_token => params[:stripeToken],
                                                         :cc_first_name => params[:stripeCardName],
                                                         :cc_last_name => nil,
                                                         :cc_type => params[:stripeCardType],
                                                         :cc_exp_month => params[:stripeCardExpMonth],
                                                         :cc_exp_year => params[:stripeCardExpYear],
                                                         :cc_last_4 => params[:stripeCardLast4],
                                                         :address1 => params[:stripeCardAddressLine1],
                                                         :address2 => params[:stripeCardAddressLine2],
                                                         :city => params[:stripeCardAddressCity],
                                                         :state => params[:stripeCardAddressState],
                                                         :zip => params[:stripeCardAddressZip],
                                                         :country => params[:stripeCardAddressCountry] || params[:stripeCardCountry]
      pm
    end

    def save_response(stripe_response, api_call)
      logger.warn "Unsuccessful #{api_call}: #{stripe_response.message}" unless stripe_response.success?

      # Save the response to our logs
      response = StripeResponse.from_response(api_call, nil, stripe_response)
      response.save!
      response
    end

    def gateway
      # The gateway should have been configured when the plugin started
      Killbill::Stripe.gateway
    end

    def logger
      # The logger should have been configured when the plugin started
      Killbill::Stripe.logger
    end
  end
end
