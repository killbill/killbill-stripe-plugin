require 'spec_helper'

describe Killbill::Stripe::PaymentPlugin do
  before(:each) do
    Dir.mktmpdir do |dir|
      file = File.new(File.join(dir, 'stripe.yml'), "w+")
      file.write(<<-eos)
:stripe:
  :api_secret_key: 'j2lkb12'
# As defined by spec_helper.rb
:database:
  :adapter: 'sqlite3'
  :database: 'test.db'
      eos
      file.close

      @plugin              = Killbill::Stripe::PaymentPlugin.new
      @plugin.logger       = Logger.new(STDOUT)
      @plugin.logger.level = Logger::INFO
      @plugin.conf_dir     = File.dirname(file)
      @account_api         = ::Killbill::Plugin::ActiveMerchant::RSpec::FakeJavaUserAccountApi.new
      @plugin.kb_apis      = Killbill::Plugin::KillbillApi.new('stripe', {})

      # Start the plugin here - since the config file will be deleted
      @plugin.start_plugin
    end
  end

  it 'should start and stop correctly' do
    @plugin.stop_plugin
  end

  it 'should reset payment methods' do
    kb_account_id = '129384'
    kb_tenant_id  = '092384'
    context       = @plugin.kb_apis.create_context(kb_tenant_id)

    @plugin.get_payment_methods(kb_account_id, false, [], context).size.should == 0
    verify_pms kb_account_id, 0, context

    # Create a pm with a kb_payment_method_id
    Killbill::Stripe::StripePaymentMethod.create :kb_account_id        => kb_account_id,
                                                 :kb_tenant_id         => kb_tenant_id,
                                                 :kb_payment_method_id => 'kb-1',
                                                 :token                => 'stripe-1'
    verify_pms kb_account_id, 1, context

    # Add some in KillBill and reset
    payment_methods = []
    # Random order... Shouldn't matter...
    payment_methods << create_pm_info_plugin(kb_account_id, 'kb-3', false, 'stripe-3')
    payment_methods << create_pm_info_plugin(kb_account_id, 'kb-2', false, 'stripe-2')
    payment_methods << create_pm_info_plugin(kb_account_id, 'kb-4', false, 'stripe-4')
    @plugin.reset_payment_methods kb_account_id, payment_methods, [], context
    verify_pms kb_account_id, 4, context

    # Add a payment method without a kb_payment_method_id
    Killbill::Stripe::StripePaymentMethod.create :kb_account_id => kb_account_id,
                                                 :kb_tenant_id  => kb_tenant_id,
                                                 :token         => 'stripe-5'
    @plugin.get_payment_methods(kb_account_id, false, nil, context).size.should == 5

    # Verify we can match it
    payment_methods << create_pm_info_plugin(kb_account_id, 'kb-5', false, 'stripe-5')
    @plugin.reset_payment_methods kb_account_id, payment_methods, [], context
    verify_pms kb_account_id, 5, context

    @plugin.stop_plugin
  end

  private

  def verify_pms(kb_account_id, size, context)
    pms = @plugin.get_payment_methods(kb_account_id, false, [], context)
    pms.size.should == size
    pms.each do |pm|
      pm.account_id.should == kb_account_id
      pm.is_default.should == false
      pm.external_payment_method_id.should == 'stripe-' + pm.payment_method_id.split('-')[1]
    end
  end

  def create_pm_info_plugin(kb_account_id, kb_payment_method_id, is_default, external_payment_method_id)
    pm_info_plugin                            = Killbill::Plugin::Model::PaymentMethodInfoPlugin.new
    pm_info_plugin.account_id                 = kb_account_id
    pm_info_plugin.payment_method_id          = kb_payment_method_id
    pm_info_plugin.is_default                 = is_default
    pm_info_plugin.external_payment_method_id = external_payment_method_id
    pm_info_plugin
  end
end
