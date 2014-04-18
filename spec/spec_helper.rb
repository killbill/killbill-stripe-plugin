require 'bundler'
require 'stripe'

require 'logger'

require 'rspec'

RSpec.configure do |config|
  config.color_enabled = true
  config.tty = true
  config.formatter = 'documentation'
end

require 'active_record'
ActiveRecord::Base.establish_connection(
    :adapter => 'sqlite3',
    :database => 'test.db'
)
# For debugging
#ActiveRecord::Base.logger = Logger.new(STDOUT)
# Create the schema
require File.expand_path(File.dirname(__FILE__) + '../../db/schema.rb')

begin
  require 'securerandom'
  SecureRandom.uuid
rescue LoadError, NoMethodError
  # See http://jira.codehaus.org/browse/JRUBY-6176
  module SecureRandom
    def self.uuid
      ary = self.random_bytes(16).unpack("NnnnnN")
      ary[2] = (ary[2] & 0x0fff) | 0x4000
      ary[3] = (ary[3] & 0x3fff) | 0x8000
      "%08x-%04x-%04x-%04x-%04x%08x" % ary
    end unless respond_to?(:uuid)
  end
end
