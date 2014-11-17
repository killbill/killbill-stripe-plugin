module Killbill #:nodoc:
  module Stripe #:nodoc:
    class StripeResponse < ::Killbill::Plugin::ActiveMerchant::ActiveRecord::Response

      self.table_name = 'stripe_responses'

      has_one :stripe_transaction

      def self.from_response(api_call, kb_account_id, kb_payment_id, kb_payment_transaction_id, transaction_type, payment_processor_account_id, kb_tenant_id, response, extra_params = {}, model = ::Killbill::Stripe::StripeResponse)
        super(api_call,
              kb_account_id,
              kb_payment_id,
              kb_payment_transaction_id,
              transaction_type,
              payment_processor_account_id,
              kb_tenant_id,
              response,
              {
                  :params_id                       => extract(response, 'id'),
                  :params_object                   => extract(response, 'object'),
                  :params_created                  => extract(response, 'created'),
                  :params_livemode                 => extract(response, 'livemode'),
                  :params_paid                     => extract(response, 'paid'),
                  :params_amount                   => extract(response, 'amount'),
                  :params_currency                 => extract(response, 'currency'),
                  :params_refunded                 => extract(response, 'refunded'),
                  :params_card_id                  => extract(response, 'card', 'id'),
                  :params_card_object              => extract(response, 'card', 'object'),
                  :params_card_last4               => extract(response, 'card', 'last4'),
                  :params_card_type                => extract(response, 'card', 'type'),
                  :params_card_exp_month           => extract(response, 'card', 'exp_month'),
                  :params_card_exp_year            => extract(response, 'card', 'exp_year'),
                  :params_card_fingerprint         => extract(response, 'card', 'fingerprint'),
                  :params_card_customer            => extract(response, 'card', 'customer'),
                  :params_card_country             => extract(response, 'card', 'country'),
                  :params_card_name                => extract(response, 'card', 'name'),
                  :params_card_address_line1       => extract(response, 'card', 'address_line1'),
                  :params_card_address_line2       => extract(response, 'card', 'address_line2'),
                  :params_card_address_city        => extract(response, 'card', 'address_city'),
                  :params_card_address_state       => extract(response, 'card', 'address_state'),
                  :params_card_address_zip         => extract(response, 'card', 'address_zip'),
                  :params_card_address_country     => extract(response, 'card', 'address_country'),
                  :params_card_cvc_check           => extract(response, 'card', 'cvc_check'),
                  :params_card_address_line1_check => extract(response, 'card', 'address_line1_check'),
                  :params_card_address_zip_check   => extract(response, 'card', 'address_zip_check'),
                  :params_captured                 => extract(response, 'captured'),
                  :params_refunds                  => extract(response, 'refunds'),
                  :params_balance_transaction      => extract(response, 'balance_transaction'),
                  :params_failure_message          => extract(response, 'failure_message'),
                  :params_failure_code             => extract(response, 'failure_code'),
                  :params_amount_refunded          => extract(response, 'amount_refunded'),
                  :params_customer                 => extract(response, 'customer'),
                  :params_email                    => extract(response, 'email'),
                  :params_delinquent               => extract(response, 'delinquent'),
                  :params_subscription             => extract(response, 'subscription'),
                  :params_discount                 => extract(response, 'discount'),
                  :params_account_balance          => extract(response, 'account_balance'),
                  :params_cards                    => extract(response, 'cards'),
                  :params_invoice                  => extract(response, 'invoice'),
                  :params_description              => extract(response, 'description'),
                  :params_dispute                  => extract(response, 'dispute'),
                  :params_metadata                 => extract(response, 'metadata'),
                  :params_error_type               => extract(response, 'error', 'type'),
                  :params_error_message            => extract(response, 'error', 'message')
              }.merge!(extra_params),
              model)
      end

      def self.search_where_clause(t, search_key)
        where_clause = t[:params_id].eq(search_key)
                   .or(t[:params_card_id].eq(search_key))

        # Only search successful payments and refunds
        where_clause = where_clause.and(t[:success].eq(true))

        super.or(where_clause)
      end
    end
  end
end
