module ActiveMerchant
  module Billing

    KB_PLUGIN_VERSION = Gem.loaded_specs['killbill-stripe'].version.version rescue nil

    class StripeGateway

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
    end
  end
end
