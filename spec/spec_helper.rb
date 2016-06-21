require 'bundler'
require 'stripe'
require 'killbill/helpers/active_merchant/killbill_spec_helper'

require 'logger'

require 'rspec'

RSpec.configure do |config|
  config.color_enabled = true
  config.tty = true
  config.formatter = 'documentation'
end

require defined?(JRUBY_VERSION) ? 'arjdbc' : 'active_record'
db_config = {
    :adapter => ENV['AR_ADAPTER'] || 'sqlite3',
    :database => ENV['AR_DATABASE'] || 'test.db',
}
db_config[:username] = ENV['AR_USERNAME'] if ENV['AR_USERNAME']
db_config[:password] = ENV['AR_PASSWORD'] if ENV['AR_PASSWORD']
ActiveRecord::Base.establish_connection(db_config)

# For debugging
#ActiveRecord::Base.logger = Logger.new(STDOUT)
# Create the schema
require File.expand_path(File.dirname(__FILE__) + '../../db/schema.rb')

