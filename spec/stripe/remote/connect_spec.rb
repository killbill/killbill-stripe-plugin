require 'spec_helper'

ActiveMerchant::Billing::Base.mode = :test

describe Killbill::Stripe::PaymentPlugin do

  include ::Killbill::Plugin::ActiveMerchant::RSpec

  let(:kb_account_id_for_contractor) do
    kb_account_id = SecureRandom.uuid
    create_kb_account(kb_account_id, plugin.kb_apis.proxied_services[:account_user_api])
    kb_account_id
  end

  let(:kb_account_id_for_customer) do
    kb_account_id = SecureRandom.uuid
    create_kb_account(kb_account_id, plugin.kb_apis.proxied_services[:account_user_api])
    kb_account_id
  end

  let(:kb_tenant_id) do
    SecureRandom.uuid
  end

  let(:call_context) do
    build_call_context(kb_tenant_id)
  end

  let(:plugin) do
    plugin = build_plugin(::Killbill::Stripe::PaymentPlugin, 'stripe')
    plugin.start_plugin
    plugin
  end

  let(:private_plugin) do
    ::Killbill::Stripe::PrivatePaymentPlugin.new
  end

  before(:all) do
    # Start the plugin to initialize caches, etc.
    plugin.should_not be_nil
  end

  it 'creates a managed account and transfers money' do
    init_balance = get_balance

    #
    # Create managed Stripe account for contractor Jane Doe
    #

    account = {}
    account[:legal_entity] = {}
    account[:legal_entity][:type] = 'individual'
    account[:legal_entity][:first_name] = 'Jane'
    account[:legal_entity][:last_name] = 'Doe'
    account[:legal_entity][:address] = {}
    account[:legal_entity][:address][:city] = 'San Francisco'
    account[:legal_entity][:dob] = {}
    account[:legal_entity][:dob][:day] = 31
    account[:legal_entity][:dob][:month] = 12
    account[:legal_entity][:dob][:year] = 1969
    account[:legal_entity][:ssn_last_4] = 1234
    account[:tos_acceptance] = {}
    account[:tos_acceptance][:date] = 1468778430
    account[:tos_acceptance][:ip] = '8.8.8.8'

    stripe_account = private_plugin.create_managed_account(kb_account_id_for_contractor, kb_tenant_id, account, {}).params
    stripe_account['id'].should_not be_nil
    stripe_account['keys'].should_not be_nil
    stripe_account['managed'].should be_true

    ::Killbill::Stripe::StripeResponse.managed_stripe_account_id_from_kb_account_id(SecureRandom.uuid, kb_tenant_id).should be_nil
    ::Killbill::Stripe::StripeResponse.managed_stripe_account_id_from_kb_account_id(kb_account_id_for_contractor, nil).should be_nil
    ::Killbill::Stripe::StripeResponse.managed_stripe_account_id_from_kb_account_id(kb_account_id_for_contractor, kb_tenant_id).should == stripe_account['id']

    check_balance(kb_account_id_for_contractor)

    #
    # Tokenize card and charge customer John Doe
    #

    pm = create_payment_method(::Killbill::Stripe::StripePaymentMethod, kb_account_id_for_customer, kb_tenant_id, [], {}, true, plugin)

    props = []
    props << build_property(:destination, kb_account_id_for_contractor)
    props << build_property(:fees_amount, 200)
    payment_response = plugin.purchase_payment(kb_account_id_for_customer, SecureRandom.uuid, SecureRandom.uuid, pm.kb_payment_method_id, 10, :USD, props, call_context)

    plugin.logger.info "Useful links:
Contractor dashboard: https://dashboard.stripe.com/#{stripe_account['id']}/test/dashboard
Customer dashboard:   https://dashboard.stripe.com/test/customers/#{::Killbill::Stripe::StripePaymentMethod.stripe_customer_id_from_kb_account_id(kb_account_id_for_customer, kb_tenant_id)}
Collected fees:       https://dashboard.stripe.com/test/applications/fees"

    payment_response.status.should eq(:PROCESSED), payment_response.gateway_error
    payment_response.amount.should == 10
    payment_response.transaction_type.should == :PURCHASE

    check_balance(nil, init_balance + 141)
    check_balance(kb_account_id_for_contractor, 800)

    props = []
    props << build_property(:reverse_transfer, 'true')
    props << build_property(:refund_application_fee, 'true')
    refund_response = plugin.refund_payment(kb_account_id_for_customer, payment_response.kb_payment_id, SecureRandom.uuid, pm.kb_payment_method_id, 10, :USD, props, call_context)
    refund_response.status.should eq(:PROCESSED), refund_response.gateway_error
    refund_response.amount.should == 10
    refund_response.transaction_type.should == :REFUND

    check_balance(nil, init_balance)
    check_balance(kb_account_id_for_contractor, 0)
  end

  private

  def check_balance(kb_account_id = nil, amount = 0)
    balance = private_plugin.get_balance(kb_account_id, kb_tenant_id)
    balance.params['pending'].first['currency'].should == 'usd'
    balance.params['pending'].first['amount'].should == amount
  end

  def get_balance
    private_plugin.get_balance(nil, kb_tenant_id).params['pending'].first['amount']
  end
end
