require 'spec_helper'

ActiveMerchant::Billing::Base.mode = :test

describe Killbill::Stripe::PaymentPlugin do

  include ::Killbill::Plugin::ActiveMerchant::RSpec

  before(:each) do
    @plugin = build_plugin(::Killbill::Stripe::PaymentPlugin, 'stripe')
    @plugin.start_plugin

    ::Killbill::Stripe::StripePaymentMethod.delete_all
    ::Killbill::Stripe::StripeResponse.delete_all
    ::Killbill::Stripe::StripeTransaction.delete_all

    @call_context = build_call_context

    @properties = []
    @pm         = create_payment_method(::Killbill::Stripe::StripePaymentMethod, nil, @call_context.tenant_id, @properties)
    @amount     = BigDecimal.new('100')
    @currency   = 'USD'

    kb_payment_id = SecureRandom.uuid
    1.upto(6) do
      @kb_payment = @plugin.kb_apis.proxied_services[:payment_api].add_payment(kb_payment_id)
    end
  end

  after(:each) do
    @plugin.stop_plugin
  end

  it 'should be able to create and retrieve payment methods' do
    # Override default payment method params to make sure we store the data returned by Stripe (see https://github.com/killbill/killbill-stripe-plugin/issues/8)
    pm = create_payment_method(::Killbill::Stripe::StripePaymentMethod, nil, @call_context.tenant_id, [], { :cc_type => '', :cc_last_4 => '' })

    pms = @plugin.get_payment_methods(pm.kb_account_id, false, [], @call_context)
    pms.size.should == 1
    pms.first.external_payment_method_id.should == pm.token

    pm_details = @plugin.get_payment_method_detail(pm.kb_account_id, pms.first.payment_method_id, [], @call_context)
    pm_props = properties_to_hash(pm_details.properties)
    pm_props[:ccFirstName].should == 'John'
    pm_props[:ccLastName].should == 'Doe'
    pm_props[:ccType].should == 'Visa'
    pm_props[:ccExpirationMonth].should == '12'
    pm_props[:ccExpirationYear].should == '2017'
    pm_props[:ccLast4].should == '4242'
    pm_props[:address1].should == '5, oakriu road'
    pm_props[:address2].should == 'apt. 298'
    pm_props[:city].should == 'Gdio Foia'
    pm_props[:state].should == 'FL'
    pm_props[:zip].should == '49302'
    pm_props[:country].should == 'US'

    pm_details = @plugin.get_payment_method_detail(pm.kb_account_id, pm.kb_payment_method_id, [], @call_context)
    pm_details.external_payment_method_id.should == pm.token

    pms_found = @plugin.search_payment_methods pm.cc_last_4, 0, 10, [], @call_context
    pms_found = pms_found.iterator.to_a
    pms_found.size.should == 2
    pms_found[1].external_payment_method_id.should == pm_details.external_payment_method_id

    @plugin.delete_payment_method(pm.kb_account_id, pm.kb_payment_method_id, [], @call_context)

    @plugin.get_payment_methods(pm.kb_account_id, false, [], @call_context).size.should == 0
    lambda { @plugin.get_payment_method_detail(pm.kb_account_id, pm.kb_payment_method_id, [], @call_context) }.should raise_error RuntimeError

    # Verify we can add multiple payment methods
    pm1 = create_payment_method(::Killbill::Stripe::StripePaymentMethod, pm.kb_account_id, @call_context.tenant_id)
    pm2 = create_payment_method(::Killbill::Stripe::StripePaymentMethod, pm.kb_account_id, @call_context.tenant_id)

    pms = @plugin.get_payment_methods(pm.kb_account_id, false, [], @call_context)
    pms.size.should == 2
    pms[0].external_payment_method_id.should == pm1.token
    pms[1].external_payment_method_id.should == pm2.token

    # Update the default payment method in Stripe (we cannot easily verify the result unfortunately)
    @plugin.set_default_payment_method(pm.kb_account_id, pm2.kb_payment_method_id, [], @call_context)
    response = Killbill::Stripe::StripeResponse.last
    response.api_call.should == 'set_default_payment_method'
    response.message.should == 'Transaction approved'
    response.success.should be_true
  end

  it 'should be able to charge a Credit Card directly' do
    properties = build_pm_properties

    # We created the payment methods, hence the rows
    nb_responses = Killbill::Stripe::StripeResponse.count
    Killbill::Stripe::StripeTransaction.all.size.should == 0

    payment_response = @plugin.purchase_payment(@pm.kb_account_id, @kb_payment.id, @kb_payment.transactions[0].id, @pm.kb_payment_method_id, @amount, @currency, properties, @call_context)
    payment_response.status.should eq(:PROCESSED), payment_response.gateway_error
    payment_response.amount.should == @amount
    payment_response.transaction_type.should == :PURCHASE

    responses = Killbill::Stripe::StripeResponse.all
    responses.size.should == nb_responses + 1
    responses[nb_responses].api_call.should == 'purchase'
    responses[nb_responses].message.should == 'Transaction approved'
    transactions = Killbill::Stripe::StripeTransaction.all
    transactions.size.should == 1
    transactions[0].api_call.should == 'purchase'
  end

  it 'should be able to charge and refund' do
    payment_response = @plugin.purchase_payment(@pm.kb_account_id, @kb_payment.id, @kb_payment.transactions[0].id, @pm.kb_payment_method_id, @amount, @currency, @properties, @call_context)
    payment_response.status.should eq(:PROCESSED), payment_response.gateway_error
    payment_response.amount.should == @amount
    payment_response.transaction_type.should == :PURCHASE

    # Try a full refund
    refund_response = @plugin.refund_payment(@pm.kb_account_id, @kb_payment.id, @kb_payment.transactions[1].id, @pm.kb_payment_method_id, @amount, @currency, @properties, @call_context)
    refund_response.status.should eq(:PROCESSED), refund_response.gateway_error
    refund_response.amount.should == @amount
    refund_response.transaction_type.should == :REFUND
  end

  it 'prevents double payments' do
    payment_response = @plugin.purchase_payment(@pm.kb_account_id, @kb_payment.id, @kb_payment.transactions[0].id, @pm.kb_payment_method_id, @amount, @currency, @properties, @call_context)
    payment_response.status.should eq(:PROCESSED), payment_response.gateway_error
    payment_response.amount.should == @amount
    payment_response.transaction_type.should == :PURCHASE

    payment_response = @plugin.purchase_payment(@pm.kb_account_id, @kb_payment.id, @kb_payment.transactions[0].id, @pm.kb_payment_method_id, @amount, @currency, @properties, @call_context)
    payment_response.status.should eq(:PROCESSED), payment_response.gateway_error
    payment_response.amount.should == @amount
    payment_response.transaction_type.should == :PURCHASE

    responses = Killbill::Stripe::StripeResponse.all
    responses.size.should == 2 + 1
    responses[1].params_id.should == responses[2].params_id
  end

  # It doesn't look like Stripe supports multiple partial captures
  #it 'should be able to auth, capture and refund' do
  #  payment_response = @plugin.authorize_payment(@pm.kb_account_id, @kb_payment.id, @kb_payment.transactions[0].id, @pm.kb_payment_method_id, @amount, @currency, @properties, @call_context)
  #  payment_response.status.should eq(:PROCESSED), payment_response.gateway_error
  #  payment_response.amount.should == @amount
  #  payment_response.transaction_type.should == :AUTHORIZE
  #
  #  # Try multiple partial captures
  #  partial_capture_amount = BigDecimal.new('10')
  #  1.upto(3) do |i|
  #    payment_response = @plugin.capture_payment(@pm.kb_account_id, @kb_payment.id, @kb_payment.transactions[i].id, @pm.kb_payment_method_id, partial_capture_amount, @currency, @properties, @call_context)
  #    payment_response.status.should eq(:PROCESSED), payment_response.gateway_error
  #    payment_response.amount.should == partial_capture_amount
  #    payment_response.transaction_type.should == :CAPTURE
  #  end
  #
  #  # Try a partial refund
  #  refund_response = @plugin.refund_payment(@pm.kb_account_id, @kb_payment.id, @kb_payment.transactions[4].id, @pm.kb_payment_method_id, partial_capture_amount, @currency, @properties, @call_context)
  #  refund_response.status.should eq(:PROCESSED), refund_response.gateway_error
  #  refund_response.amount.should == partial_capture_amount
  #  refund_response.transaction_type.should == :REFUND
  #
  #  # Try to capture again
  #  payment_response = @plugin.capture_payment(@pm.kb_account_id, @kb_payment.id, @kb_payment.transactions[5].id, @pm.kb_payment_method_id, partial_capture_amount, @currency, @properties, @call_context)
  #  payment_response.status.should eq(:PROCESSED), payment_response.gateway_error
  #  payment_response.amount.should == partial_capture_amount
  #  payment_response.transaction_type.should == :CAPTURE
  #end

  it 'should be able to auth and void' do
    payment_response = @plugin.authorize_payment(@pm.kb_account_id, @kb_payment.id, @kb_payment.transactions[0].id, @pm.kb_payment_method_id, @amount, @currency, @properties, @call_context)
    payment_response.status.should eq(:PROCESSED), payment_response.gateway_error
    payment_response.amount.should == @amount
    payment_response.transaction_type.should == :AUTHORIZE

    payment_response = @plugin.void_payment(@pm.kb_account_id, @kb_payment.id, @kb_payment.transactions[1].id, @pm.kb_payment_method_id, @properties, @call_context)
    payment_response.status.should eq(:PROCESSED), payment_response.gateway_error
    payment_response.transaction_type.should == :VOID
  end

  it 'should be able to auth, partial capture and void' do
    payment_response = @plugin.authorize_payment(@pm.kb_account_id, @kb_payment.id, @kb_payment.transactions[0].id, @pm.kb_payment_method_id, @amount, @currency, @properties, @call_context)
    payment_response.status.should eq(:PROCESSED), payment_response.gateway_error
    payment_response.amount.should == @amount
    payment_response.transaction_type.should == :AUTHORIZE

    partial_capture_amount = BigDecimal.new('10')
    payment_response       = @plugin.capture_payment(@pm.kb_account_id, @kb_payment.id, @kb_payment.transactions[1].id, @pm.kb_payment_method_id, partial_capture_amount, @currency, @properties, @call_context)
    payment_response.status.should eq(:PROCESSED), payment_response.gateway_error
    payment_response.amount.should == partial_capture_amount
    payment_response.transaction_type.should == :CAPTURE

    payment_response = @plugin.void_payment(@pm.kb_account_id, @kb_payment.id, @kb_payment.transactions[2].id, @pm.kb_payment_method_id, @properties, @call_context)
    payment_response.status.should eq(:PROCESSED), payment_response.gateway_error
    payment_response.transaction_type.should == :VOID
  end
end
