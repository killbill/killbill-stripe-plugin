require 'spec_helper'

ActiveMerchant::Billing::Base.mode = :test

class FakeJavaUserAccountApi
  attr_accessor :accounts

  def initialize
    @accounts = []
  end

  def get_account_by_id(id, context)
    @accounts.find { |account| account.id == id.to_s }
  end

  def get_account_by_key(external_key, context)
    @accounts.find { |account| account.external_key == external_key.to_s }
  end
end

describe Killbill::Stripe::PaymentPlugin do
  before(:each) do
    @plugin = Killbill::Stripe::PaymentPlugin.new

    @account_api = FakeJavaUserAccountApi.new
    svcs = {:account_user_api => @account_api}
    @plugin.kb_apis = Killbill::Plugin::KillbillApi.new('stripe', svcs)

    @plugin.logger = Logger.new(STDOUT)
    @plugin.logger.level = Logger::INFO
    @plugin.conf_dir = File.expand_path(File.dirname(__FILE__) + '../../../../')
    @plugin.start_plugin
  end

  after(:each) do
    @plugin.stop_plugin
  end

  it 'should be able to create and retrieve payment methods' do
    pm = create_payment_method

    pms = @plugin.get_payment_methods(pm.kb_account_id)
    pms.size.should == 1
    pms[0].external_payment_method_id.should == pm.stripe_card_id_or_token

    pm_details = @plugin.get_payment_method_detail(pm.kb_account_id, pm.kb_payment_method_id)
    pm_details.external_payment_method_id.should == pm.stripe_card_id_or_token

    pms_found = @plugin.search_payment_methods pm.cc_last_4
    pms_found = pms_found.iterator.to_a
    pms_found.size.should == 1
    pms_found.first.external_payment_method_id.should == pm_details.external_payment_method_id

    @plugin.delete_payment_method(pm.kb_account_id, pm.kb_payment_method_id)

    @plugin.get_payment_methods(pm.kb_account_id).size.should == 0
    lambda { @plugin.get_payment_method_detail(pm.kb_account_id, pm.kb_payment_method_id) }.should raise_error RuntimeError

    # Verify we can add multiple payment methods
    pm1 = create_payment_method(pm.kb_account_id)
    pm2 = create_payment_method(pm.kb_account_id)

    pms = @plugin.get_payment_methods(pm.kb_account_id)
    pms.size.should == 2
    pms[0].external_payment_method_id.should == pm1.stripe_card_id_or_token
    pms[1].external_payment_method_id.should == pm2.stripe_card_id_or_token
  end

  it 'should be able to charge and refund' do
    pm = create_payment_method
    amount = BigDecimal.new("100")
    currency = 'USD'
    kb_payment_id = SecureRandom.uuid

    payment_response = @plugin.process_payment pm.kb_account_id, kb_payment_id, pm.kb_payment_method_id, amount, currency
    payment_response.amount.should == amount
    payment_response.status.should == :PROCESSED

    # Verify our table directly
    response = Killbill::Stripe::StripeResponse.find_by_api_call_and_kb_payment_id :charge, kb_payment_id
    response.test.should be_true
    response.success.should be_true
    response.message.should == 'Transaction approved'

    payment_response = @plugin.get_payment_info pm.kb_account_id, kb_payment_id
    payment_response.amount.should == amount
    payment_response.status.should == :PROCESSED

    # Check we cannot refund an amount greater than the original charge
    lambda { @plugin.process_refund pm.kb_account_id, kb_payment_id, amount + 1, currency }.should raise_error RuntimeError

    refund_response = @plugin.process_refund pm.kb_account_id, kb_payment_id, amount, currency
    refund_response.amount.should == amount
    refund_response.status.should == :PROCESSED

    # Verify our table directly
    response = Killbill::Stripe::StripeResponse.find_by_api_call_and_kb_payment_id :refund, kb_payment_id
    response.test.should be_true
    response.success.should be_true

    # Check we can retrieve the refund
    refund_responses = @plugin.get_refund_info pm.kb_account_id, kb_payment_id
    refund_responses.size.should == 1
    # Apparently, Stripe returns positive amounts for refunds
    refund_responses[0].amount.should == amount
    refund_responses[0].status.should == :PROCESSED

    # Make sure we can charge again the same payment method
    second_amount = BigDecimal.new("294.71")
    second_kb_payment_id = SecureRandom.uuid

    payment_response = @plugin.process_payment pm.kb_account_id, second_kb_payment_id, pm.kb_payment_method_id, second_amount, currency
    payment_response.amount.should == second_amount
    payment_response.status.should == :PROCESSED
  end

  private

  def create_payment_method(kb_account_id=nil)
    kb_payment_method_id = SecureRandom.uuid

    if kb_account_id.nil?
      kb_account_id = SecureRandom.uuid

      # Create a new account
      create_kb_account kb_account_id
    end

    # Generate a token in Stripe
    cc_number = '4242424242424242'
    cc_first_name = 'John'
    cc_last_name = 'Doe'
    cc_type = 'Visa'
    cc_exp_month = 12
    cc_exp_year = 2015
    cc_last_4 = 4242
    address1 = '5, oakriu road'
    address2 = 'apt. 298'
    city = 'Gdio Foia'
    state = 'FL'
    zip = 49302
    country = 'IFP'
    cc_verification_value = 1234

    properties = []
    properties << create_pm_kv_info('ccNumber', cc_number)
    properties << create_pm_kv_info('ccFirstName', cc_first_name)
    properties << create_pm_kv_info('ccLastName', cc_last_name)
    properties << create_pm_kv_info('ccType', cc_type)
    properties << create_pm_kv_info('ccExpirationMonth', cc_exp_month)
    properties << create_pm_kv_info('ccExpirationYear', cc_exp_year)
    properties << create_pm_kv_info('ccLast4', cc_last_4)
    properties << create_pm_kv_info('address1', address1)
    properties << create_pm_kv_info('address2', address2)
    properties << create_pm_kv_info('city', city)
    properties << create_pm_kv_info('state', state)
    properties << create_pm_kv_info('zip', zip)
    properties << create_pm_kv_info('country', country)
    properties << create_pm_kv_info('ccVerificationValue', cc_verification_value)

    info = Killbill::Plugin::Model::PaymentMethodPlugin.new
    info.properties = properties
    payment_method = @plugin.add_payment_method(kb_account_id, kb_payment_method_id, info, true)

    pm = Killbill::Stripe::StripePaymentMethod.from_kb_payment_method_id kb_payment_method_id
    pm.should == payment_method
    pm.kb_account_id.should == kb_account_id
    pm.kb_payment_method_id.should == kb_payment_method_id
    pm.stripe_card_id_or_token.should_not be_nil
    pm.cc_first_name.should == cc_first_name + ' ' + cc_last_name
    pm.cc_last_name.should be_nil
    pm.cc_type.should == cc_type
    pm.cc_exp_month.should == cc_exp_month
    pm.cc_exp_year.should == cc_exp_year
    pm.cc_last_4.should == cc_last_4
    pm.address1.should == address1
    pm.address2.should == address2
    pm.city.should == city
    pm.state.should == state
    pm.zip.should == zip.to_s
    pm.country.should == country

    pm
  end

  def create_kb_account(kb_account_id)
    external_key = Time.now.to_i.to_s + '-test'
    email = external_key + '@tester.com'

    account = Killbill::Plugin::Model::Account.new
    account.id = kb_account_id
    account.external_key = external_key
    account.email = email
    account.name = 'Integration spec'
    account.currency = :USD

    @account_api.accounts << account

    return external_key, kb_account_id
  end

  def create_pm_kv_info(key, value)
    prop = Killbill::Plugin::Model::PaymentMethodKVInfo.new
    prop.key = key
    prop.value = value
    prop
  end
end
