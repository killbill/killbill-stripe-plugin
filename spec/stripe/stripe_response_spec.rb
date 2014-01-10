require 'spec_helper'

describe Killbill::Stripe::StripeResponse do
  before :all do
    Killbill::Stripe::StripeResponse.delete_all
  end

  it 'should generate the right SQL query' do
    # Check count query (search query numeric)
    expected_query = "SELECT COUNT(DISTINCT \"stripe_responses\".\"id\") FROM \"stripe_responses\"  WHERE ((\"stripe_responses\".\"authorization\" = '1234' OR \"stripe_responses\".\"params_id\" = '1234') OR \"stripe_responses\".\"params_card_id\" = '1234') AND \"stripe_responses\".\"api_call\" = 'charge' AND \"stripe_responses\".\"success\" = 't' ORDER BY \"stripe_responses\".\"id\""
    # Note that Kill Bill will pass a String, even for numeric types
    Killbill::Stripe::StripeResponse.search_query('charge', '1234').to_sql.should == expected_query

    # Check query with results (search query numeric)
    expected_query = "SELECT  DISTINCT \"stripe_responses\".* FROM \"stripe_responses\"  WHERE ((\"stripe_responses\".\"authorization\" = '1234' OR \"stripe_responses\".\"params_id\" = '1234') OR \"stripe_responses\".\"params_card_id\" = '1234') AND \"stripe_responses\".\"api_call\" = 'charge' AND \"stripe_responses\".\"success\" = 't' ORDER BY \"stripe_responses\".\"id\" LIMIT 10 OFFSET 0"
    # Note that Kill Bill will pass a String, even for numeric types
    Killbill::Stripe::StripeResponse.search_query('charge', '1234', 0, 10).to_sql.should == expected_query

    # Check count query (search query string)
    expected_query = "SELECT COUNT(DISTINCT \"stripe_responses\".\"id\") FROM \"stripe_responses\"  WHERE ((\"stripe_responses\".\"authorization\" = 'XXX' OR \"stripe_responses\".\"params_id\" = 'XXX') OR \"stripe_responses\".\"params_card_id\" = 'XXX') AND \"stripe_responses\".\"api_call\" = 'charge' AND \"stripe_responses\".\"success\" = 't' ORDER BY \"stripe_responses\".\"id\""
    Killbill::Stripe::StripeResponse.search_query('charge', 'XXX').to_sql.should == expected_query

    # Check query with results (search query string)
    expected_query = "SELECT  DISTINCT \"stripe_responses\".* FROM \"stripe_responses\"  WHERE ((\"stripe_responses\".\"authorization\" = 'XXX' OR \"stripe_responses\".\"params_id\" = 'XXX') OR \"stripe_responses\".\"params_card_id\" = 'XXX') AND \"stripe_responses\".\"api_call\" = 'charge' AND \"stripe_responses\".\"success\" = 't' ORDER BY \"stripe_responses\".\"id\" LIMIT 10 OFFSET 0"
    Killbill::Stripe::StripeResponse.search_query('charge', 'XXX', 0, 10).to_sql.should == expected_query
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
                                                  :params_id => '11-22-33-44',
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
