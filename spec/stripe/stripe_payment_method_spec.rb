require 'spec_helper'

describe Killbill::Stripe::StripePaymentMethod do
  before :all do
    Killbill::Stripe::StripePaymentMethod.delete_all
  end

  it 'should search all fields' do
    kb_tenant_id = '77-88-99-00'

    do_search('foo', kb_tenant_id).size.should == 0

    pm = Killbill::Stripe::StripePaymentMethod.create :kb_account_id        => '11-22-33-44',
                                                      :kb_payment_method_id => '55-66-77-88',
                                                      :kb_tenant_id          => kb_tenant_id,
                                                      :stripe_customer_id   => '123xka',
                                                      :token                => 38102343,
                                                      :cc_first_name        => 'ccFirstName',
                                                      :cc_last_name         => 'ccLastName',
                                                      :cc_type              => 'ccType',
                                                      :cc_exp_month         => 10,
                                                      :cc_exp_year          => 11,
                                                      :cc_last_4            => 1234,
                                                      :address1             => 'address1',
                                                      :address2             => 'address2',
                                                      :city                 => 'city',
                                                      :state                => 'state',
                                                      :zip                  => 'zip',
                                                      :country              => 'country'

    do_search('foo', kb_tenant_id).size.should == 0
    do_search(pm.token, kb_tenant_id).size.should == 1
    do_search('ccType', kb_tenant_id).size.should == 1
    # Exact match only for cc_last_4
    do_search('123', kb_tenant_id).size.should == 0
    do_search('1234', kb_tenant_id).size.should == 1
    # Test partial match
    do_search('address', kb_tenant_id).size.should == 1
    do_search('Name', kb_tenant_id).size.should == 1

    pm2 = Killbill::Stripe::StripePaymentMethod.create :kb_account_id        => '22-33-44-55',
                                                       :kb_payment_method_id => '66-77-88-99',
                                                       :kb_tenant_id          => kb_tenant_id,
                                                       :stripe_customer_id   => '123xka',
                                                       :token                => 49384029302,
                                                       :cc_first_name        => 'ccFirstName',
                                                       :cc_last_name         => 'ccLastName',
                                                       :cc_type              => 'ccType',
                                                       :cc_exp_month         => 10,
                                                       :cc_exp_year          => 11,
                                                       :cc_last_4            => 1234,
                                                       :address1             => 'address1',
                                                       :address2             => 'address2',
                                                       :city                 => 'city',
                                                       :state                => 'state',
                                                       :zip                  => 'zip',
                                                       :country              => 'country'

    do_search('foo', kb_tenant_id).size.should == 0
    do_search(pm.token, kb_tenant_id).size.should == 1
    do_search(pm2.token, kb_tenant_id).size.should == 1
    do_search('ccType', kb_tenant_id).size.should == 2
    # Exact match only for cc_last_4
    do_search('123', kb_tenant_id).size.should == 0
    do_search('1234', kb_tenant_id).size.should == 2
    # Test partial match
    do_search('cc', kb_tenant_id).size.should == 2
    do_search('address', kb_tenant_id).size.should == 2
    do_search('Name', kb_tenant_id).size.should == 2
  end

  private

  def do_search(search_key, kb_tenant_id)
    pagination = Killbill::Stripe::StripePaymentMethod.search(search_key, kb_tenant_id)
    pagination.current_offset.should == 0
    results = pagination.iterator.to_a
    pagination.total_nb_records.should == results.size
    results
  end
end
