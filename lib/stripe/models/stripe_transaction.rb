module Killbill::Stripe
  class StripeTransaction < ActiveRecord::Base
    belongs_to :stripe_response
    attr_accessible :amount_in_cents, :currency, :api_call, :kb_payment_id, :stripe_txn_id

    def self.from_kb_payment_id(kb_payment_id)
      single_transaction_from_kb_payment_id :charge, kb_payment_id
    end

    def self.refund_from_kb_payment_id(kb_payment_id)
      single_transaction_from_kb_payment_id :refund, kb_payment_id
    end

    def self.single_transaction_from_kb_payment_id(api_call, kb_payment_id)
      stripe_transactions = find_all_by_api_call_and_kb_payment_id(api_call, kb_payment_id)
      raise "Unable to find Stripe transaction id for payment #{kb_payment_id}" if stripe_transactions.empty?
      raise "Killbill payment mapping to multiple Stripe transactions for payment #{kb_payment_id}" if stripe_transactions.size > 1
      stripe_transactions[0]
    end

    def self.find_candidate_transaction_for_refund(kb_payment_id, amount_in_cents)
      # Find one successful charge which amount is at least the amount we are trying to refund
      stripe_transactions = StripeTransaction.where("stripe_transactions.amount_in_cents >= ?", amount_in_cents)
                                             .find_all_by_api_call_and_kb_payment_id(:charge, kb_payment_id)
      raise "Unable to find Stripe transaction id for payment #{kb_payment_id}" if stripe_transactions.size == 0

      # We have candidates, but we now need to make sure we didn't refund more than for the specified amount
      amount_refunded_in_cents = Killbill::Stripe::StripeTransaction.where("api_call = ? and kb_payment_id = ?", :refund, kb_payment_id)
                                                                    .sum("amount_in_cents")

      amount_left_to_refund_in_cents = -amount_refunded_in_cents
      stripe_transactions.map { |transaction| amount_left_to_refund_in_cents += transaction.amount_in_cents }
      raise "Amount #{amount_in_cents} too large to refund for payment #{kb_payment_id}" if amount_left_to_refund_in_cents < amount_in_cents

      stripe_transactions.first
    end
  end
end
