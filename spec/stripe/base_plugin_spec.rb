require 'spec_helper'

describe Killbill::Stripe::PaymentPlugin do

  include ::Killbill::Plugin::ActiveMerchant::RSpec

  let(:kb_account_id) do
    SecureRandom.uuid
  end

  let(:kb_tenant_id) do
    SecureRandom.uuid
  end

  let(:context) do
    build_call_context(kb_tenant_id)
  end

  before(:each) do
    Dir.mktmpdir do |dir|
      file = File.new(File.join(dir, 'stripe.yml'), 'w+')
      file.write(<<-eos)
:stripe:
  :api_secret_key: 'j2lkb12'
# As defined by spec_helper.rb
:database:
  :adapter: 'sqlite3'
  :database: 'test.db'
      eos
      file.close

      @plugin = build_plugin(::Killbill::Stripe::PaymentPlugin, 'stripe', File.dirname(file))

      # Start the plugin here - since the config file will be deleted
      @plugin.start_plugin
    end
  end

  it 'should start and stop correctly' do
    @plugin.stop_plugin
  end

  it 'should reset payment methods' do
    @plugin.get_payment_methods(kb_account_id, false, [], context).size.should == 0
    verify_pms kb_account_id, 0, context

    # Create a pm with a kb_payment_method_id
    Killbill::Stripe::StripePaymentMethod.create(:kb_account_id        => kb_account_id,
                                                 :kb_tenant_id         => kb_tenant_id,
                                                 :kb_payment_method_id => 'kb-1',
                                                 :token                => 'stripe-1',
                                                 :created_at           => Time.now.utc,
                                                 :updated_at           => Time.now.utc)
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
    Killbill::Stripe::StripePaymentMethod.create(:kb_account_id => kb_account_id,
                                                 :kb_tenant_id  => kb_tenant_id,
                                                 :token         => 'stripe-5',
                                                 :created_at    => Time.now.utc,
                                                 :updated_at    => Time.now.utc)
    @plugin.get_payment_methods(kb_account_id, false, nil, context).size.should == 5

    # Verify we can match it
    payment_methods << create_pm_info_plugin(kb_account_id, 'kb-5', false, 'stripe-5')
    @plugin.reset_payment_methods kb_account_id, payment_methods, [], context
    verify_pms kb_account_id, 5, context

    @plugin.stop_plugin
  end

  it 'processes notifications' do
    notification =<<EOF
{
  "id": "evt_1234",
  "user_id": "acct_12QkqYGSOD4VcegJ",
  "type": "account.updated",
  "data": {
    "object": {
      "legal_entity": {
        "verification": {
          "status": "unverified"
        }
      },
      "verification": {
        "fields_needed": ["legal_entity.personal_id_number"],
        "due_by": null,
        "contacted": false
      }
    },
    "previous_attributes": {
      "legal_entity": {
        "verification": {
          "status": "pending"
        }
      },
      "verification": {
        "fields_needed": []
      }
    }
  }
}
EOF
    gw_notification = @plugin.process_notification(notification, {}, context)
    gw_notification.status.should == 200

    response = ::Killbill::Stripe::StripeResponse.last
    response.params_id.should == 'evt_1234'
    response.api_call.should == 'webhook.account.updated'
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
