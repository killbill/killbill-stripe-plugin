module Killbill::Stripe
  class Gateway
    def self.from_config(config)
      if config[:test]
        ActiveMerchant::Billing::Base.mode = :test
      end

      if config[:log_file]
        ActiveMerchant::Billing::StripeGateway.wiredump_device = File.open(config[:log_file], 'w')
        ActiveMerchant::Billing::StripeGateway.wiredump_device.sync = true
      end

      Gateway.new(config[:api_secret_key])
    end

    def initialize(api_secret_key)
      @gateway = ActiveMerchant::Billing::StripeGateway.new(:login => api_secret_key)
    end

    def method_missing(m, *args, &block)
      @gateway.send(m, *args, &block)
    end
  end
end
