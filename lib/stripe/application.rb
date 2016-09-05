# -- encoding : utf-8 --

set :views, File.expand_path(File.dirname(__FILE__) + '/views')

include Killbill::Plugin::ActiveMerchant::Sinatra

configure do
  # Usage: rackup -Ilib -E test
  if development? or test?
    # Make sure the plugin is initialized
    plugin              = ::Killbill::Stripe::PaymentPlugin.new
    plugin.logger       = Logger.new(STDOUT)
    plugin.logger.level = Logger::INFO
    plugin.conf_dir     = File.dirname(File.dirname(__FILE__)) + '/..'
    plugin.start_plugin
  end
end

helpers do
  def plugin(session = {})
    ::Killbill::Stripe::PrivatePaymentPlugin.new(session)
  end
end

# http://127.0.0.1:9292/plugins/killbill-stripe
get '/plugins/killbill-stripe' do
  kb_account_id = request.GET['kb_account_id']
  required_parameter! :kb_account_id, kb_account_id

  if development? or test?
    # Just look at the global configuration
    kb_tenant_id = nil
  else
    kb_tenant_id = request.GET['kb_tenant_id']
    kb_tenant = request.env['killbill_tenant']
    kb_tenant_id ||= kb_tenant.id.to_s unless kb_tenant.nil?
  end

  stripe_config = (config(kb_tenant_id) || {})[:stripe]
  required_parameter! 'killbill-stripe', stripe_config, "is not configured for kb_tenant_id=#{kb_tenant_id}"

  # URL to Stripe.js
  stripejs_url = stripe_config[:stripejs_url] || 'https://js.stripe.com/v2/'
  required_parameter! :stripejs_url, stripejs_url, 'is not configured'

  # Public API key
  publishable_key = stripe_config[:api_publishable_key]
  required_parameter! :publishable_key, publishable_key, 'is not configured'

  # Skip redirect? Useful for testing the flow with Kill Bill
  no_redirect = request.GET['no_redirect'] == '1'

  locals = {
      :stripejs_url    => stripejs_url,
      :publishable_key => publishable_key,
      :kb_account_id   => kb_account_id,
      :kb_tenant_id    => kb_tenant_id,
      :no_redirect     => no_redirect
  }
  erb :stripejs, :locals => locals
end

# This is mainly for testing. Your application should redirect from the Stripe.js checkout above
# to a custom endpoint where you call the Kill Bill add payment method JAX-RS API.
post '/plugins/killbill-stripe', :provides => 'json' do
  return params.to_json if development? or test?

  kb_payment_method_id = plugin(session).add_payment_method(params)

  response = params.dup
  response['kb_payment_method_id'] = kb_payment_method_id
  response.to_json
end

# Create managed account
post '/plugins/killbill-stripe/accounts', :provides => 'json' do
  kb_account_id = params.delete('kb_account_id')
  required_parameter! :kb_account_id, kb_account_id

  kb_tenant_id = params.delete('kb_tenant_id')
  kb_tenant = request.env['killbill_tenant']
  kb_tenant_id ||= kb_tenant.id.to_s unless kb_tenant.nil?

  plugin(session).create_managed_account(kb_account_id, kb_tenant_id, params).params.to_json
end

# curl -v http://127.0.0.1:9292/plugins/killbill-stripe/1.0/pms/1
get '/plugins/killbill-stripe/1.0/pms/:id', :provides => 'json' do
  if pm = ::Killbill::Stripe::StripePaymentMethod.find_by_id(params[:id].to_i)
    pm.to_json
  else
    status 404
  end
end

# curl -v http://127.0.0.1:9292/plugins/killbill-stripe/1.0/transactions/1
get '/plugins/killbill-stripe/1.0/transactions/:id', :provides => 'json' do
  if transaction = ::Killbill::Stripe::StripeTransaction.find_by_id(params[:id].to_i)
    transaction.to_json
  else
    status 404
  end
end

# curl -v http://127.0.0.1:9292/plugins/killbill-stripe/1.0/responses/1
get '/plugins/killbill-stripe/1.0/responses/:id', :provides => 'json' do
  if transaction = ::Killbill::Stripe::StripeResponse.find_by_id(params[:id].to_i)
    transaction.to_json
  else
    status 404
  end
end
