require 'logger'

module Killbill::Stripe
  mattr_reader :logger
  mattr_reader :config
  mattr_reader :gateway
  mattr_reader :kb_apis
  mattr_reader :stripe_payment_description
  mattr_reader :initialized
  mattr_reader :test

  def self.initialize!(logger=Logger.new(STDOUT), conf_dir=File.expand_path('../../../', File.dirname(__FILE__)), kb_apis = nil)
    @@logger = logger
    @@kb_apis = kb_apis

    config_file = "#{conf_dir}/stripe.yml"
    @@config = Properties.new(config_file)
    @@config.parse!
    @@test = @@config[:stripe][:test]

    @@logger.log_level = Logger::DEBUG if (@@config[:logger] || {})[:debug]

    @@stripe_payment_description = @@config[:stripe][:payment_description]

    @@gateway = Killbill::Stripe::Gateway.from_config(@@config[:stripe])

    if defined?(JRUBY_VERSION)
      # See https://github.com/jruby/activerecord-jdbc-adapter/issues/302
      require 'jdbc/mysql'
      Jdbc::MySQL.load_driver(:require) if Jdbc::MySQL.respond_to?(:load_driver)
    end

    ActiveRecord::Base.establish_connection(@@config[:database])
    ActiveRecord::Base.logger = @@logger

    @@initialized = true
  end
end
