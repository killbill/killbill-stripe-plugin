module Killbill::Stripe
  class StripePaymentMethod < ActiveRecord::Base
    attr_accessible :kb_account_id,
                    :kb_payment_method_id,
                    :stripe_customer_id,
                    :stripe_card_id_or_token,
                    :cc_first_name,
                    :cc_last_name,
                    :cc_type,
                    :cc_exp_month,
                    :cc_exp_year,
                    :cc_last_4,
                    :address1,
                    :address2,
                    :city,
                    :state,
                    :zip,
                    :country

    alias_attribute :external_payment_method_id, :stripe_card_id_or_token

    def self.from_kb_account_id(kb_account_id)
      find_all_by_kb_account_id_and_is_deleted(kb_account_id, false)
    end

    def self.stripe_customer_id_from_kb_account_id(kb_account_id)
      pms = from_kb_account_id(kb_account_id)
      return nil if pms.empty?

      stripe_customer_ids = Set.new
      pms.each { |pm| stripe_customer_ids << pm.stripe_customer_id }
      raise "No Stripe customer id found for account #{kb_account_id}" if stripe_customer_ids.empty?
      raise "Killbill account #{kb_account_id} mapping to multiple Stripe customers: #{stripe_customer_ids}" if stripe_customer_ids.size > 1
      stripe_customer_ids.first
    end

    def self.from_kb_payment_method_id(kb_payment_method_id)
      payment_methods = find_all_by_kb_payment_method_id_and_is_deleted(kb_payment_method_id, false)
      raise "No payment method found for payment method #{kb_payment_method_id}" if payment_methods.empty?
      raise "Killbill payment method mapping to multiple active Stripe tokens for payment method #{kb_payment_method_id}" if payment_methods.size > 1
      payment_methods[0]
    end

    def self.mark_as_deleted!(kb_payment_method_id)
      payment_method = from_kb_payment_method_id(kb_payment_method_id)
      payment_method.is_deleted = true
      payment_method.save!
    end

    # VisibleForTesting
    def self.search_query(search_key, offset = nil, limit = nil)
      t = self.arel_table

      # Exact match for kb_account_id, kb_payment_method_id, stripe_customer_id, stripe_card_id_or_token, cc_type, cc_exp_month,
      # cc_exp_year, cc_last_4, state and zip, partial match for the reset
      where_clause =     t[:kb_account_id].eq(search_key)
                     .or(t[:kb_payment_method_id].eq(search_key))
                     .or(t[:stripe_customer_id].eq(search_key))
                     .or(t[:stripe_card_id_or_token].eq(search_key))
                     .or(t[:cc_type].eq(search_key))
                     .or(t[:state].eq(search_key))
                     .or(t[:zip].eq(search_key))
                     .or(t[:cc_first_name].matches("%#{search_key}%"))
                     .or(t[:cc_last_name].matches("%#{search_key}%"))
                     .or(t[:address1].matches("%#{search_key}%"))
                     .or(t[:address2].matches("%#{search_key}%"))
                     .or(t[:city].matches("%#{search_key}%"))
                     .or(t[:country].matches("%#{search_key}%"))

      # Coming from Kill Bill, search_key will always be a String. Check to see if it represents a numeric for numeric-only fields
      if search_key.is_a?(Numeric) or search_key.to_s =~ /\A[-+]?[0-9]*\.?[0-9]+\Z/
        where_clause = where_clause.or(t[:cc_exp_month].eq(search_key))
                                   .or(t[:cc_exp_year].eq(search_key))
                                   .or(t[:cc_last_4].eq(search_key))
      end

      # Remove garbage payment methods (added in the plugin but not reconcilied with Kill Bill yet)
      query = t.where(where_clause)
               .where(t[:kb_payment_method_id].not_eq(nil))
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

    def self.search(search_key, offset = 0, limit = 100)
      pagination = Killbill::Plugin::Model::Pagination.new
      pagination.current_offset = offset
      pagination.total_nb_records = self.count_by_sql(self.search_query(search_key))
      pagination.max_nb_records = self.count
      pagination.next_offset = (!pagination.total_nb_records.nil? && offset + limit >= pagination.total_nb_records) ? nil : offset + limit
      # Reduce the limit if the specified value is larger than the number of records
      actual_limit = [pagination.max_nb_records, limit].min
      pagination.iterator = StreamyResultSet.new(actual_limit) do |offset,limit|
        self.find_by_sql(self.search_query(search_key, offset, limit))
            .map(&:to_payment_method_response)
      end
      pagination
    end

    def to_payment_method_response
      properties = []
      properties << create_pm_kv_info('token', external_payment_method_id)
      properties << create_pm_kv_info('ccName', cc_name)
      properties << create_pm_kv_info('ccType', cc_type)
      properties << create_pm_kv_info('ccExpirationMonth', cc_exp_month)
      properties << create_pm_kv_info('ccExpirationYear', cc_exp_year)
      properties << create_pm_kv_info('ccLast4', cc_last_4)
      properties << create_pm_kv_info('address1', address1)
      properties << create_pm_kv_info('address2', address2)
      properties << create_pm_kv_info('city', city)
      properties << create_pm_kv_info('state', state)
      properties << create_pm_kv_info('zip', zip)
      properties << create_pm_kv_info('country', country)

      pm_plugin = Killbill::Plugin::Model::PaymentMethodPlugin.new
      pm_plugin.kb_payment_method_id = kb_payment_method_id
      pm_plugin.external_payment_method_id = external_payment_method_id
      pm_plugin.is_default_payment_method = is_default
      pm_plugin.properties = properties
      pm_plugin.type = 'CreditCard'
      pm_plugin.cc_name = cc_name
      pm_plugin.cc_type = cc_type
      pm_plugin.cc_expiration_month = cc_exp_month
      pm_plugin.cc_expiration_year = cc_exp_year
      pm_plugin.cc_last4 = cc_last_4
      pm_plugin.address1 = address1
      pm_plugin.address2 = address2
      pm_plugin.city = city
      pm_plugin.state = state
      pm_plugin.zip = zip
      pm_plugin.country = country

      pm_plugin
    end

    def to_payment_method_info_response
      pm_info_plugin = Killbill::Plugin::Model::PaymentMethodInfoPlugin.new
      pm_info_plugin.account_id = kb_account_id
      pm_info_plugin.payment_method_id = kb_payment_method_id
      pm_info_plugin.is_default = is_default
      pm_info_plugin.external_payment_method_id = external_payment_method_id
      pm_info_plugin
    end

    def is_default
      # There is a concept of default credit card in Stripe but it's not exposed by the API
      # Return false to let Kill Bill knows it's authoritative on the matter
      false
    end

    def cc_name
      if cc_first_name and cc_last_name
        "#{cc_first_name} #{cc_last_name}"
      elsif cc_first_name
        cc_first_name
      elsif cc_last_name
        cc_last_name
      else
        nil
      end
    end

    private

    def create_pm_kv_info(key, value)
      prop = Killbill::Plugin::Model::PaymentMethodKVInfo.new
      prop.key = key
      prop.value = value
      prop
    end
  end
end
