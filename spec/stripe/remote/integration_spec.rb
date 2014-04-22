require 'spec_helper'

ActiveMerchant::Billing::Base.mode = :test

describe Killbill::Stripe::PaymentPlugin do

  include ::Killbill::Plugin::ActiveMerchant::RSpec

  before(:each) do
    @plugin = Killbill::Stripe::PaymentPlugin.new

    @account_api    = ::Killbill::Plugin::ActiveMerchant::RSpec::FakeJavaUserAccountApi.new
    svcs            = {:account_user_api => @account_api}
    @plugin.kb_apis = Killbill::Plugin::KillbillApi.new('stripe', svcs)

    @plugin.logger       = Logger.new(STDOUT)
    @plugin.logger.level = Logger::INFO
    @plugin.conf_dir     = File.expand_path(File.dirname(__FILE__) + '../../../../')
    @plugin.start_plugin
  end

  after(:each) do
    @plugin.stop_plugin
  end

  it 'should be able to create and retrieve payment methods' do
    pm = create_payment_method(::Killbill::Stripe::StripePaymentMethod)

    pms = @plugin.get_payment_methods(pm.kb_account_id, false, nil)
    pms.size.should == 1
    pms[0].external_payment_method_id.should == pm.token

    pm_details = @plugin.get_payment_method_detail(pm.kb_account_id, pm.kb_payment_method_id, nil)
    pm_details.external_payment_method_id.should == pm.token

    pms_found = @plugin.search_payment_methods pm.cc_last_4, 0, 10, nil
    pms_found = pms_found.iterator.to_a
    pms_found.size.should == 1
    pms_found.first.external_payment_method_id.should == pm_details.external_payment_method_id

    @plugin.delete_payment_method(pm.kb_account_id, pm.kb_payment_method_id, nil)

    @plugin.get_payment_methods(pm.kb_account_id, false, nil).size.should == 0
    lambda { @plugin.get_payment_method_detail(pm.kb_account_id, pm.kb_payment_method_id, nil) }.should raise_error RuntimeError

    # Verify we can add multiple payment methods
    pm1 = create_payment_method(::Killbill::Stripe::StripePaymentMethod, pm.kb_account_id)
    pm2 = create_payment_method(::Killbill::Stripe::StripePaymentMethod, pm.kb_account_id)

    pms = @plugin.get_payment_methods(pm.kb_account_id, false, nil)
    pms.size.should == 2
    pms[0].external_payment_method_id.should == pm1.token
    pms[1].external_payment_method_id.should == pm2.token
  end

  it 'should be able to charge and refund' do
    pm            = create_payment_method(::Killbill::Stripe::StripePaymentMethod)
    amount        = BigDecimal.new("100")
    currency      = 'USD'
    kb_payment_id = SecureRandom.uuid

    payment_response = @plugin.process_payment pm.kb_account_id, kb_payment_id, pm.kb_payment_method_id, amount, currency, nil
    payment_response.amount.should == amount
    payment_response.status.should == :PROCESSED

    # Verify our table directly
    responses = Killbill::Stripe::StripeResponse.where('api_call = ? AND kb_payment_id = ?', :charge, kb_payment_id)
    responses.size.should == 1
    response = responses.first
    response.test.should be_true
    response.success.should be_true
    response.message.should == 'Transaction approved'

    payment_response = @plugin.get_payment_info pm.kb_account_id, kb_payment_id, nil
    payment_response.amount.should == amount
    payment_response.status.should == :PROCESSED

    # Check we cannot refund an amount greater than the original charge
    lambda { @plugin.process_refund pm.kb_account_id, kb_payment_id, amount + 1, currency, nil }.should raise_error RuntimeError

    refund_response = @plugin.process_refund pm.kb_account_id, kb_payment_id, amount, currency, nil
    refund_response.amount.should == amount
    refund_response.status.should == :PROCESSED

    # Verify our table directly
    responses = Killbill::Stripe::StripeResponse.where('api_call = ? AND kb_payment_id = ?', :refund, kb_payment_id)
    responses.size.should == 1
    response = responses.first
    response.test.should be_true
    response.success.should be_true

    # Check we can retrieve the refund
    refund_responses = @plugin.get_refund_info pm.kb_account_id, kb_payment_id, nil
    refund_responses.size.should == 1
    # Apparently, Stripe returns positive amounts for refunds
    refund_responses[0].amount.should == amount
    refund_responses[0].status.should == :PROCESSED

    # Make sure we can charge again the same payment method
    second_amount        = BigDecimal.new("294.71")
    second_kb_payment_id = SecureRandom.uuid

    payment_response = @plugin.process_payment pm.kb_account_id, second_kb_payment_id, pm.kb_payment_method_id, second_amount, currency, nil
    payment_response.amount.should == second_amount
    payment_response.status.should == :PROCESSED
  end
end
