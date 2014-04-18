require 'spec_helper'

describe Killbill::Stripe::StripeResponse do
  before :all do
    Killbill::Stripe::StripeResponse.delete_all
  end

  it 'should search all fields' do
    do_search('foo').size.should == 0

    pm = Killbill::Stripe::StripeResponse.create :api_call => 'charge',
                                                 :kb_payment_id => '11-22-33-44',
                                                 :authorization => 'aa-bb-cc-dd',
                                                 :params_id => '55-66-77-88',
                                                 :params_card_id => 38102343,
                                                 :success => true

    # Wrong api_call
    ignored1 = Killbill::Stripe::StripeResponse.create :api_call => 'add_payment_method',
                                                       :kb_payment_id => pm.kb_payment_id,
                                                       :authorization => pm.authorization,
                                                       :params_id => pm.params_id,
                                                       :params_card_id => pm.params_card_id,
                                                       :success => true

    # Not successful
    ignored2 = Killbill::Stripe::StripeResponse.create :api_call => 'charge',
                                                       :kb_payment_id => pm.kb_payment_id,
                                                       :authorization => pm.authorization,
                                                       :params_id => pm.params_id,
                                                       :params_card_id => pm.params_card_id,
                                                       :success => false

    do_search('foo').size.should == 0
    do_search(pm.authorization).size.should == 1
    do_search(pm.params_id).size.should == 1
    do_search(pm.params_card_id).size.should == 1

    pm2 = Killbill::Stripe::StripeResponse.create :api_call => 'charge',
                                                  :kb_payment_id => '11-22-33-44',
                                                  :authorization => 'AA-BB-CC-DD',
                                                  :params_id => '55-66-77-88-99',
                                                  :params_card_id => pm.params_card_id,
                                                  :success => true

    do_search('foo').size.should == 0
    do_search(pm.authorization).size.should == 1
    do_search(pm.params_id).size.should == 1
    do_search(pm.params_card_id).size.should == 2
    do_search(pm2.authorization).size.should == 1
    do_search(pm2.params_id).size.should == 1
    do_search(pm2.params_card_id).size.should == 2
  end

  private

  def do_search(search_key)
    pagination = Killbill::Stripe::StripeResponse.search(search_key)
    pagination.current_offset.should == 0
    results = pagination.iterator.to_a
    pagination.total_nb_records.should == results.size
    results
  end
end
