module ActiveMerchant
  module Billing

    KB_PLUGIN_VERSION = Gem.loaded_specs['killbill-stripe'].version.version rescue nil

    class StripeGateway
      BANK_ACCOUNT_HOLDER_TYPE_MAPPING = {
        "personal" => "individual",
        "business" => "company",
      }

      def get_balance(options = {})
        commit(:get, 'balance', nil, options)
      end

      def create_managed_account(account = {}, options = {})
        post = account.dup
        post[:country] ||= 'US'
        post[:managed] = true

        commit(:post, 'accounts', post, options)
      end

      def user_agent
        @@ua ||= JSON.dump({
                               :bindings_version => KB_PLUGIN_VERSION,
                               :lang => 'ruby',
                               :lang_version => "#{RUBY_VERSION} p#{RUBY_PATCHLEVEL} (#{RUBY_RELEASE_DATE})",
                               :platform => RUBY_PLATFORM,
                               :publisher => 'killbill'
                           })
      end

      alias_method :old_headers, :headers

      def headers(options = {})
        headers = old_headers(options)

        stripe_account = options.delete(:stripe_account)
        headers['Stripe-Account'] = stripe_account unless stripe_account.nil?

        headers
      end

      # Ignore the call to localized_amount upstream (money is already cents here)
      def add_amount(post, money, options, include_currency = false)
        currency = options[:currency] || currency(money)
        post[:amount] = money
        post[:currency] = currency.downcase if include_currency
      end

      # To create a charge on a card or a token, call
      #
      #   purchase(money, card_hash_or_token, { ... })
      #
      # To create a charge on a customer, call
      #
      #   purchase(money, nil, { :customer => id, ... })
      def purchase(money, payment, options = {})
        responses = MultiResponse.run do |r|
          if payment.is_a?(ApplePayPaymentToken)
            r.process { tokenize_apple_pay_token(payment) }
            payment = StripePaymentToken.new(r.params["token"]) if r.success?
          end
          r.process do
            post = create_post_for_auth_or_purchase(money, payment, options)
            commit(:post, 'charges', post, options)
          end
        end.responses.last
      end

      def store(payment, options = {})
        params = {}
        post = {}

        if payment.is_a?(ApplePayPaymentToken)
          token_exchange_response = tokenize_apple_pay_token(payment)
          params = { :card => token_exchange_response.params["token"]["id"] } if token_exchange_response.success?
        elsif payment.is_a?(BankAccount)
          post.merge!(bank_account_params(payment, options))
        else
          add_creditcard(params, payment, options)
        end

        unless payment.is_a?(BankAccount)
          post[:validate] = options[:validate] unless options[:validate].nil?
          post[:description] = options[:description] if options[:description]
          post[:email] = options[:email] if options[:email]
        end

        if post[:bank_account]
          customer_url = "customers"
          if options[:customer]
            customer_url += "/#{CGI.escape(options[:customer])}/sources"
          end
          responses = MultiResponse.run do |r|
            # get token and associate it with the customer
            r.process { commit(:post, "tokens?#{post_data(post)}", nil, { bank_account: true }) }

            if r.success?
              r.process { commit(:post, customer_url, { source: r.params["id"] } ) }
            end
          end.responses
          if options[:customer]
            return responses.first
          else
            return responses.last
          end
        elsif options[:account]
          add_external_account(post, params, payment)
          commit(:post, "accounts/#{CGI.escape(options[:account])}/external_accounts", post, options)
        elsif options[:customer]
          MultiResponse.run(:first) do |r|
            # The /cards endpoint does not update other customer parameters.
            r.process { commit(:post, "customers/#{CGI.escape(options[:customer])}/cards", params, options) }

            if options[:set_default] and r.success? and !r.params['id'].blank?
              post[:default_card] = r.params['id']
            end

            if post.count > 0
              r.process { update_customer(options[:customer], post) }
            end
          end
        else
          commit(:post, 'customers', post.merge(params), options)
        end
      end

      def commit(method, url, parameters = nil, options = {})
        if options[:bank_account]
          response = api_request(:post, url)
          success = response["error"].nil?

          Response.new(success, nil, response)
        else
          add_expand_parameters(parameters, options) if parameters
          response = api_request(method, url, parameters, options)

          success = !response.key?("error")

          card = card_from_response(response)
          avs_code = AVS_CODE_TRANSLATOR["line1: #{card["address_line1_check"]}, zip: #{card["address_zip_check"]}"]
          cvc_code = CVC_CODE_TRANSLATOR[card["cvc_check"]]
          Response.new(success,
                       success ? "Transaction approved" : response["error"]["message"],
                       response,
                       :test => response.has_key?("livemode") ? !response["livemode"] : false,
                       :authorization => authorization_from(success, url, method, response),
                       :avs_result => { :code => avs_code },
                       :cvv_result => cvc_code,
                       :emv_authorization => emv_authorization_from_response(response),
                       :error_code => success ? nil : error_code_from(response)
                      )
        end
      end

      def bank_account_params(bank_account, options = {})
        account_holder_type = BANK_ACCOUNT_HOLDER_TYPE_MAPPING[bank_account.type]

        post = {
          bank_account: {
            account_number: bank_account.account_number,
            country: 'US',
            currency: 'usd',
            routing_number: bank_account.routing_number,
            name: bank_account.bank_name,
            account_holder_type: account_holder_type,
          }
        }
      end

      class BankAccount
        attr_accessor :bank_name, :account_number, :routing_number, :type

        def initialize(args)
          args.each do |k,v|
            instance_variable_set("@#{k}", v) unless v.nil?
          end
        end
      end
    end
  end
end
