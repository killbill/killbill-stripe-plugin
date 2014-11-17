require 'spec_helper'

ActiveMerchant::Billing::Base.mode = :test

describe Killbill::Stripe::PaymentPlugin do

  include ::Killbill::Plugin::ActiveMerchant::RSpec

  before(:each) do
    @plugin = Killbill::Stripe::PaymentPlugin.new

    @account_api    = ::Killbill::Plugin::ActiveMerchant::RSpec::FakeJavaUserAccountApi.new
    @payment_api    = ::Killbill::Plugin::ActiveMerchant::RSpec::FakeJavaPaymentApi.new
    svcs            = {:account_user_api => @account_api, :payment_api => @payment_api}
    @plugin.kb_apis = Killbill::Plugin::KillbillApi.new('stripe', svcs)

    @call_context           = Killbill::Plugin::Model::CallContext.new
    @call_context.tenant_id = '00000011-0022-0033-0044-000000000055'
    @call_context           = @call_context.to_ruby(@call_context)

    @plugin.logger       = Logger.new(STDOUT)
    @plugin.logger.level = Logger::INFO
    @plugin.conf_dir     = File.expand_path(File.dirname(__FILE__) + '../../../../')
    @plugin.start_plugin

    @properties = []
    @pm         = create_payment_method(::Killbill::Stripe::StripePaymentMethod, nil, @call_context.tenant_id, @properties)
    @amount     = BigDecimal.new('100')
    @currency   = 'USD'

    kb_payment_id = SecureRandom.uuid
    1.upto(6) do
      @kb_payment = @payment_api.add_payment(kb_payment_id)
    end
  end

  after(:each) do
    @plugin.stop_plugin
  end

  it 'should be able to create and retrieve payment methods' do
    pm = create_payment_method(::Killbill::Stripe::StripePaymentMethod, nil, @call_context.tenant_id)

    pms = @plugin.get_payment_methods(pm.kb_account_id, false, [], @call_context)
    pms.size.should == 1
    pms.first.external_payment_method_id.should == pm.token

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
  end

  it 'should be able to charge a Credit Card directly' do
    properties = build_pm_properties

    # We created the payment methods, hence the rows
    max_response_id = Killbill::Stripe::StripeResponse.all.last.id
    Killbill::Stripe::StripeTransaction.all.size.should == 0

    payment_response = @plugin.purchase_payment(@pm.kb_account_id, @kb_payment.id, @kb_payment.transactions[0].id, @pm.kb_payment_method_id, @amount, @currency, properties, @call_context)
    payment_response.status.should eq(:PROCESSED), payment_response.gateway_error
    payment_response.amount.should == @amount
    payment_response.transaction_type.should == :PURCHASE

    responses = Killbill::Stripe::StripeResponse.all
    responses.size.should == max_response_id + 1
    responses[max_response_id].api_call.should == 'purchase'
    responses[max_response_id].message.should == 'Transaction approved'
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
