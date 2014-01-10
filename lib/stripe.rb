require 'active_record'
require 'activemerchant'
require 'bigdecimal'
require 'money'
require 'pathname'
require 'set'
require 'sinatra'
require 'singleton'
require 'yaml'

require 'killbill'

require 'stripe/config/configuration'
require 'stripe/config/properties'

require 'stripe/api'
require 'stripe/private_api'

require 'stripe/models/stripe_payment_method'
require 'stripe/models/stripe_response'
require 'stripe/models/stripe_transaction'

require 'stripe/stripe_utils'
require 'stripe/stripe/gateway'

class Object
  def blank?
    respond_to?(:empty?) ? empty? : !self
  end
end
