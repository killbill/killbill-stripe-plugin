require 'active_record'

ActiveRecord::Schema.define(:version => 20140410153635) do
  create_table "stripe_payment_methods", :force => true do |t|
    t.string   "kb_payment_method_id"      # NULL before Kill Bill knows about it
    t.string   "token"                     # stripe id
    t.string   "stripe_customer_id"
    t.string   "cc_first_name"
    t.string   "cc_last_name"
    t.string   "cc_type"
    t.integer  "cc_exp_month"
    t.integer  "cc_exp_year"
    t.integer  "cc_number"
    t.integer  "cc_last_4"
    t.integer  "cc_start_month"
    t.integer  "cc_start_year"
    t.integer  "cc_issue_number"
    t.integer  "cc_verification_value"
    t.integer  "cc_track_data"
    t.string   "address1"
    t.string   "address2"
    t.string   "city"
    t.string   "state"
    t.string   "zip"
    t.string   "country"
    t.boolean  "is_deleted",               :null => false, :default => false
    t.datetime "created_at",               :null => false
    t.datetime "updated_at",               :null => false
    t.string   "kb_account_id"
    t.string   "kb_tenant_id"
  end

  add_index(:stripe_payment_methods, :kb_account_id)
  add_index(:stripe_payment_methods, :kb_payment_method_id)

  create_table "stripe_transactions", :force => true do |t|
    t.integer  "stripe_response_id",  :null => false
    t.string   "api_call",                       :null => false
    t.string   "kb_payment_id",                  :null => false
    t.string   "kb_payment_transaction_id",      :null => false
    t.string   "transaction_type",               :null => false
    t.string   "payment_processor_account_id"
    t.string   "txn_id"                          # stripe transaction id
    # Both null for void
    t.integer  "amount_in_cents"
    t.string   "currency"
    t.datetime "created_at",                     :null => false
    t.datetime "updated_at",                     :null => false
    t.string   "kb_account_id",                  :null => false
    t.string   "kb_tenant_id",                   :null => false
  end

  add_index(:stripe_transactions, :kb_payment_id)

  create_table "stripe_responses", :force => true do |t|
    t.string   "api_call",          :null => false
    t.string   "kb_payment_id"
    t.string   "kb_payment_transaction_id"
    t.string   "transaction_type"
    t.string   "payment_processor_account_id"
    t.string   "message"
    t.string   "authorization"
    t.boolean  "fraud_review"
    t.boolean  "test"
    t.string   "params_id"
    t.string   "params_object"
    t.string   "params_created"
    t.string   "params_livemode"
    t.string   "params_paid"
    t.string   "params_amount"
    t.string   "params_currency"
    t.string   "params_refunded"
    t.string   "params_card_id"
    t.string   "params_card_object"
    t.string   "params_card_last4"
    t.string   "params_card_type"
    t.string   "params_card_exp_month"
    t.string   "params_card_exp_year"
    t.string   "params_card_fingerprint"
    t.string   "params_card_customer"
    t.string   "params_card_country"
    t.string   "params_card_name"
    t.string   "params_card_address_line1"
    t.string   "params_card_address_line2"
    t.string   "params_card_address_city"
    t.string   "params_card_address_state"
    t.string   "params_card_address_zip"
    t.string   "params_card_address_country"
    t.string   "params_card_cvc_check"
    t.string   "params_card_address_line1_check"
    t.string   "params_card_address_zip_check"
    t.string   "params_captured"
    t.string   "params_refunds"
    t.string   "params_balance_transaction"
    t.string   "params_failure_message"
    t.string   "params_failure_code"
    t.string   "params_amount_refunded"
    t.string   "params_customer"
    t.string   "params_email"
    t.string   "params_delinquent"
    t.string   "params_subscription"
    t.string   "params_discount"
    t.string   "params_account_balance"
    t.string   "params_cards"
    t.string   "params_invoice"
    t.string   "params_description"
    t.string   "params_dispute"
    t.string   "params_metadata"
    t.string   "params_error_type"
    t.string   "params_error_message"
    t.string   "avs_result_code"
    t.string   "avs_result_message"
    t.string   "avs_result_street_match"
    t.string   "avs_result_postal_match"
    t.string   "cvv_result_code"
    t.string   "cvv_result_message"
    t.boolean  "success"
    t.datetime "created_at",        :null => false
    t.datetime "updated_at",        :null => false
    t.string   "kb_account_id"
    t.string   "kb_tenant_id"
  end
end
