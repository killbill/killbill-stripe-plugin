module Killbill::Stripe
  class StripeResponse < ActiveRecord::Base
    has_one :stripe_transaction
    attr_accessible :api_call,
                    :kb_payment_id,
                    :message,
                    :authorization,
                    :fraud_review,
                    :test,
                    :params_id,
                    :params_object,
                    :params_created,
                    :params_livemode,
                    :params_paid,
                    :params_amount,
                    :params_currency,
                    :params_refunded,
                    :params_card_id,
                    :params_card_object,
                    :params_card_last4,
                    :params_card_type,
                    :params_card_exp_month,
                    :params_card_exp_year,
                    :params_card_fingerprint,
                    :params_card_customer,
                    :params_card_country,
                    :params_card_name,
                    :params_card_address_line1,
                    :params_card_address_line2,
                    :params_card_address_city,
                    :params_card_address_state,
                    :params_card_address_zip,
                    :params_card_address_country,
                    :params_card_cvc_check,
                    :params_card_address_line1_check,
                    :params_card_address_zip_check,
                    :params_captured,
                    :params_refunds,
                    :params_balance_transaction,
                    :params_failure_message,
                    :params_failure_code,
                    :params_amount_refunded,
                    :params_customer,
                    :params_invoice,
                    :params_description,
                    :params_dispute,
                    :params_metadata,
                    :params_error_type,
                    :params_error_message,
                    :avs_result_code,
                    :avs_result_message,
                    :avs_result_street_match,
                    :avs_result_postal_match,
                    :cvv_result_code,
                    :cvv_result_message,
                    :success

    def stripe_txn_id
      params_id || authorization
    end

    def self.from_response(api_call, kb_payment_id, response)
      StripeResponse.new({
                            :api_call => api_call,
                            :kb_payment_id => kb_payment_id,
                            :message => response.message,
                            :authorization => response.authorization,
                            :fraud_review => response.fraud_review?,
                            :test => response.test?,
                            :params_id => extract(response, "id"),
                            :params_object => extract(response, "object"),
                            :params_created => extract(response, "created"),
                            :params_livemode => extract(response, "livemode"),
                            :params_paid => extract(response, "paid"),
                            :params_amount => extract(response, "amount"),
                            :params_currency => extract(response, "currency"),
                            :params_refunded => extract(response, "refunded"),
                            :params_card_id => extract(response, "card", "id"),
                            :params_card_object => extract(response, "card", "object"),
                            :params_card_last4 => extract(response, "card", "last4"),
                            :params_card_type => extract(response, "card", "type"),
                            :params_card_exp_month => extract(response, "card", "exp_month"),
                            :params_card_exp_year => extract(response, "card", "exp_year"),
                            :params_card_fingerprint => extract(response, "card", "fingerprint"),
                            :params_card_customer => extract(response, "card", "customer"),
                            :params_card_country => extract(response, "card", "country"),
                            :params_card_name => extract(response, "card", "name"),
                            :params_card_address_line1 => extract(response, "card", "address_line1"),
                            :params_card_address_line2 => extract(response, "card", "address_line2"),
                            :params_card_address_city => extract(response, "card", "address_city"),
                            :params_card_address_state => extract(response, "card", "address_state"),
                            :params_card_address_zip => extract(response, "card", "address_zip"),
                            :params_card_address_country => extract(response, "card", "address_country"),
                            :params_card_cvc_check => extract(response, "card", "cvc_check"),
                            :params_card_address_line1_check => extract(response, "card", "address_line1_check"),
                            :params_card_address_zip_check => extract(response, "card", "address_zip_check"),
                            :params_captured => extract(response, "captured"),
                            :params_refunds => extract(response, "refunds"),
                            :params_balance_transaction => extract(response, "balance_transaction"),
                            :params_failure_message => extract(response, "failure_message"),
                            :params_failure_code => extract(response, "failure_code"),
                            :params_amount_refunded => extract(response, "amount_refunded"),
                            :params_customer => extract(response, "customer"),
                            :params_invoice => extract(response, "invoice"),
                            :params_description => extract(response, "description"),
                            :params_dispute => extract(response, "dispute"),
                            :params_metadata => extract(response, "metadata"),
                            :params_error_type => extract(response, "error", "type"),
                            :params_error_message => extract(response, "error", "message"),
                            :avs_result_code => response.avs_result.kind_of?(ActiveMerchant::Billing::AVSResult) ? response.avs_result.code : response.avs_result['code'],
                            :avs_result_message => response.avs_result.kind_of?(ActiveMerchant::Billing::AVSResult) ? response.avs_result.message : response.avs_result['message'],
                            :avs_result_street_match => response.avs_result.kind_of?(ActiveMerchant::Billing::AVSResult) ? response.avs_result.street_match : response.avs_result['street_match'],
                            :avs_result_postal_match => response.avs_result.kind_of?(ActiveMerchant::Billing::AVSResult) ? response.avs_result.postal_match : response.avs_result['postal_match'],
                            :cvv_result_code => response.cvv_result.kind_of?(ActiveMerchant::Billing::CVVResult) ? response.cvv_result.code : response.cvv_result['code'],
                            :cvv_result_message => response.cvv_result.kind_of?(ActiveMerchant::Billing::CVVResult) ? response.cvv_result.message : response.cvv_result['message'],
                            :success => response.success?
                        })
    end

    def to_payment_response
      to_killbill_response :payment
    end

    def to_refund_response
      to_killbill_response :refund
    end

    # VisibleForTesting
    def self.search_query(api_call, search_key, offset = nil, limit = nil)
      t = self.arel_table

      # Exact matches only
      where_clause =     t[:authorization].eq(search_key)
                     .or(t[:params_id].eq(search_key))
                     .or(t[:params_card_id].eq(search_key))

      # Only search successful payments and refunds
      where_clause = where_clause.and(t[:api_call].eq(api_call))
                                 .and(t[:success].eq(true))

      query = t.where(where_clause)
               .order(t[:id])

      if offset.blank? and limit.blank?
        # true is for count distinct
        query.project(t[:id].count(true))
      else
        query.skip(offset) unless offset.blank?
        query.take(limit) unless limit.blank?
        query.project(t[Arel.star])
        # Not chainable
        query.distinct
      end
      query
    end

    def self.search(search_key, offset = 0, limit = 100, type = :payment)
      api_call = type == :payment ? 'charge' : 'refund'
      pagination = Killbill::Plugin::Model::Pagination.new
      pagination.current_offset = offset
      pagination.total_nb_records = self.count_by_sql(self.search_query(api_call, search_key))
      pagination.max_nb_records = self.where(:api_call => api_call, :success => true).count
      pagination.next_offset = (!pagination.total_nb_records.nil? && offset + limit >= pagination.total_nb_records) ? nil : offset + limit
      # Reduce the limit if the specified value is larger than the number of records
      actual_limit = [pagination.max_nb_records, limit].min
      pagination.iterator = StreamyResultSet.new(actual_limit) do |offset,limit|
        self.find_by_sql(self.search_query(api_call, search_key, offset, limit))
            .map { |x| type == :payment ? x.to_payment_response : x.to_refund_response }
      end
      pagination
    end

    private

    def to_killbill_response(type)
      if stripe_transaction.nil?
        amount_in_cents = nil
        currency = nil
        created_date = created_at
        first_payment_reference_id = nil
        second_payment_reference_id = nil
      else
        amount_in_cents = stripe_transaction.amount_in_cents
        currency = stripe_transaction.currency
        created_date = stripe_transaction.created_at
        first_payment_reference_id = params_balance_transaction
        second_payment_reference_id = stripe_transaction.stripe_txn_id
      end

      unless params_created.blank?
        effective_date = DateTime.strptime(params_created.to_s, "%s") rescue nil
      end
      effective_date ||= created_date
      gateway_error = message || params_error_message
      gateway_error_code = params_error_type

      if type == :payment
        p_info_plugin = Killbill::Plugin::Model::PaymentInfoPlugin.new
        p_info_plugin.kb_payment_id = kb_payment_id
        p_info_plugin.amount = Money.new(amount_in_cents, currency).to_d if currency
        p_info_plugin.currency = currency
        p_info_plugin.created_date = created_date
        p_info_plugin.effective_date = effective_date
        p_info_plugin.status = (success ? :PROCESSED : :ERROR)
        p_info_plugin.gateway_error = gateway_error
        p_info_plugin.gateway_error_code = gateway_error_code
        p_info_plugin.first_payment_reference_id = first_payment_reference_id
        p_info_plugin.second_payment_reference_id = second_payment_reference_id
        p_info_plugin
      else
        r_info_plugin = Killbill::Plugin::Model::RefundInfoPlugin.new
        r_info_plugin.amount = Money.new(amount_in_cents, currency).to_d if currency
        r_info_plugin.currency = currency
        r_info_plugin.created_date = created_date
        r_info_plugin.effective_date = effective_date
        r_info_plugin.status = (success ? :PROCESSED : :ERROR)
        r_info_plugin.gateway_error = gateway_error
        r_info_plugin.gateway_error_code = gateway_error_code
        r_info_plugin.reference_id = first_payment_reference_id
        r_info_plugin
      end
    end

    def self.extract(response, key1, key2=nil, key3=nil)
      return nil if response.nil? || response.params.nil?
      level1 = response.params[key1]

      if level1.nil? or (key2.nil? and key3.nil?)
        return level1
      end
      level2 = level1[key2]

      if level2.nil? or key3.nil?
        return level2
      else
        return level2[key3]
      end
    end
  end
end
