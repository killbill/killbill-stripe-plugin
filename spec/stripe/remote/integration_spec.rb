require 'spec_helper'

ActiveMerchant::Billing::Base.mode = :test

describe Killbill::Stripe::PaymentPlugin do

  include ::Killbill::Plugin::ActiveMerchant::RSpec

  before(:each) do
    @plugin = Killbill::Stripe::PaymentPlugin.new

    @account_api    = ::Killbill::Plugin::ActiveMerchant::RSpec::FakeJavaUserAccountApi.new
    svcs            = {:account_user_api => @account_api}
    @plugin.kb_apis = Killbill::Plugin::KillbillApi.new('stripe', svcs)

    @call_context           = Killbill::Plugin::Model::CallContext.new
    @call_context.tenant_id = '00000011-0022-0033-0044-000000000055'
    @call_context           = @call_context.to_ruby(@call_context)

    @plugin.logger       = Logger.new(STDOUT)
    @plugin.logger.level = Logger::INFO
    @plugin.conf_dir     = File.expand_path(File.dirname(__FILE__) + '../../../../')
    @plugin.start_plugin
  end

  after(:each) do
    @plugin.stop_plugin
  end

  it 'should be able to create and retrieve payment methods' do
    pm = create_payment_method(::Killbill::Stripe::StripePaymentMethod, nil, @call_context.tenant_id)

    pms = @plugin.get_payment_methods(pm.kb_account_id, false, [], @call_context)
    pms.size.should == 1
    pms[0].external_payment_method_id.should == pm.token

    pm_details = @plugin.get_payment_method_detail(pm.kb_account_id, pm.kb_payment_method_id, [], @call_context)
    pm_details.external_payment_method_id.should == pm.token

    pms_found = @plugin.search_payment_methods pm.cc_last_4, 0, 10, [], @call_context
    pms_found = pms_found.iterator.to_a
    pms_found.size.should == 1
    pms_found.first.external_payment_method_id.should == pm_details.external_payment_method_id

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

  it 'should be able to charge and refund' do
    pm            = create_payment_method(::Killbill::Stripe::StripePaymentMethod, nil, @call_context.tenant_id)
    amount        = BigDecimal.new("100")
    currency      = 'USD'
    kb_payment_id = SecureRandom.uuid
    kb_payment_transaction_id = SecureRandom.uuid

    payment_response = @plugin.purchase_payment pm.kb_account_id, kb_payment_id, kb_payment_transaction_id, pm.kb_payment_method_id, amount, currency, [], @call_context
    payment_response.amount.should == amount
    payment_response.status.should == :PROCESSED
    payment_response.transaction_type.should == :PURCHASE

    # Verify our table directly
    responses = Killbill::Stripe::StripeResponse.where('api_call = ? AND kb_payment_id = ?', :purchase, kb_payment_id)
    responses.size.should == 1
    response = responses.first
    response.test.should be_true
    response.success.should be_true
    response.message.should == 'Transaction approved'

    payment_response = @plugin.get_payment_info pm.kb_account_id, kb_payment_id, [], @call_context
    payment_response.size.should == 1
    payment_response[0].amount.should == amount
    payment_response[0].status.should == :PROCESSED
    payment_response[0].transaction_type.should == :PURCHASE

    # Check we cannot refund an amount greater than the original charge
    lambda { @plugin.refund_payment pm.kb_account_id, kb_payment_id, SecureRandom.uuid, pm.kb_payment_method_id, amount + 1, currency, [], @call_context }.should raise_error RuntimeError

    refund_response = @plugin.refund_payment pm.kb_account_id, kb_payment_id, SecureRandom.uuid, pm.kb_payment_method_id, amount, currency, [], @call_context
    refund_response.amount.should == amount
    refund_response.status.should == :PROCESSED
    refund_response.transaction_type.should == :REFUND

    # Verify our table directly
    responses = Killbill::Stripe::StripeResponse.where('api_call = ? AND kb_payment_id = ?', :refund, kb_payment_id)
    responses.size.should == 1
    response = responses.first
    response.test.should be_true
    response.success.should be_true

    # Check we can retrieve the refund
    payment_response = @plugin.get_payment_info pm.kb_account_id, kb_payment_id, [], @call_context
    payment_response.size.should == 2
    payment_response[0].amount.should == amount
    payment_response[0].status.should == :PROCESSED
    payment_response[0].transaction_type.should == :PURCHASE
    # Apparently, Stripe returns positive amounts for refunds
    payment_response[1].amount.should == amount
    payment_response[1].status.should == :PROCESSED
    payment_response[1].transaction_type.should == :REFUND

    # Make sure we can charge again the same payment method
    second_amount        = BigDecimal.new("294.71")
    second_kb_payment_id = SecureRandom.uuid

    payment_response = @plugin.purchase_payment pm.kb_account_id, second_kb_payment_id, SecureRandom.uuid, pm.kb_payment_method_id, second_amount, currency, [], @call_context
    payment_response.amount.should == second_amount
    payment_response.status.should == :PROCESSED
    payment_response.transaction_type.should == :PURCHASE
  end
end
