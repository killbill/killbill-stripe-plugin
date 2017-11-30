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

      # To create a charge on a card or a token, call
      #
      #   purchase(money, card_hash_or_token, { ... })
      #
      # To create a charge on a customer, call
      #
      #   purchase(money, nil, { :customer => id, ... })
      def purchase(money, payment, options = {})
        if ach?(payment)
          direct_bank_error = "Direct bank account transactions are not supported. Bank accounts must be stored and verified before use."
          return Response.new(false, direct_bank_error)
        end

        MultiResponse.run do |r|
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

        if card_brand(payment) == "check"
          bank_token_response = tokenize_bank_account(payment)
          if bank_token_response.success?
            params = { source: bank_token_response.params["token"]["id"] }
          else
            return bank_token_response
          end
        elsif payment.is_a?(ApplePayPaymentToken)
          token_exchange_response = tokenize_apple_pay_token(payment)
          params = { card: token_exchange_response.params["token"]["id"] } if token_exchange_response.success?
        else
          add_creditcard(params, payment, options)
        end

        post[:validate] = options[:validate] unless options[:validate].nil?
        post[:description] = options[:description] if options[:description]
        post[:email] = options[:email] if options[:email]

        if options[:account]
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
      def tokenize_bank_account(bank_account, options = {})
        account_holder_type = BANK_ACCOUNT_HOLDER_TYPE_MAPPING[bank_account.account_holder_type]

        post = {
          bank_account: {
            account_number: bank_account.account_number,
            country: 'US',
            currency: 'usd',
            routing_number: bank_account.routing_number,
            name: bank_account.name,
            account_holder_type: account_holder_type,
          }
        }

        token_response = api_request(:post, "tokens?#{post_data(post)}")
        success = token_response["error"].nil?

        if success && token_response["id"]
          Response.new(success, nil, token: token_response)
        else
          Response.new(success, token_response["error"]["message"])
        end
      end

      def ach?(payment_method)
        case payment_method
        when String, nil
          false
        else
          card_brand(payment_method) == "check"
        end
      end
    end
  end
end
