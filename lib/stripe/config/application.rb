configure do
  # Usage: rackup -Ilib -E test
  if development? or test?
    Killbill::Stripe.initialize! unless Killbill::Stripe.initialized
  end
end

helpers do
  def plugin
    Killbill::Stripe::PrivatePaymentPlugin.instance
  end

  def required_parameter!(parameter_name, parameter_value, message='must be specified!')
    halt 400, "#{parameter_name} #{message}" if parameter_value.blank?
  end
end

after do
  # return DB connections to the Pool if required
  ActiveRecord::Base.connection.close
end

# http://127.0.0.1:9292/plugins/killbill-stripe
get '/plugins/killbill-stripe' do
  kb_account_id = request.GET['kb_account_id']
  required_parameter! :kb_account_id, kb_account_id

  # URL to Stripe.js
  stripejs_url = Killbill::Stripe.config[:stripe][:stripejs_url] || 'https://js.stripe.com/v2/'
  required_parameter! :stripejs_url, stripejs_url, 'is not configured'

  # Public API key
  publishable_key = Killbill::Stripe.config[:stripe][:api_publishable_key]
  required_parameter! :publishable_key, publishable_key, 'is not configured'

  # Redirect
  success_page = params[:successPage] || '/plugins/killbill-stripe'
  required_parameter! :success_page, success_page, 'is not specified'

  locals = {
      :stripejs_url => stripejs_url,
      :publishable_key => publishable_key,
      :kb_account_id => kb_account_id,
      :success_page => success_page
  }
  erb :stripejs, :views => File.expand_path(File.dirname(__FILE__) + '/../views'), :locals => locals
end

# This is mainly for testing. Your application should redirect from the Stripe.js checkout above
# to a custom endpoint where you call the Kill Bill add payment method JAX-RS API.
# If you really want to use this endpoint, you'll have to call the Kill Bill refresh payment methods API
# to get a Kill Bill payment method id assigned.
post '/plugins/killbill-stripe' do
  pm = plugin.add_payment_method params

  status 201
  redirect '/plugins/killbill-stripe/1.0/pms/' + pm.id.to_s
end

# curl -v http://127.0.0.1:9292/plugins/killbill-stripe/1.0/pms/1
get '/plugins/killbill-stripe/1.0/pms/:id', :provides => 'json' do
  if pm = Killbill::Stripe::StripePaymentMethod.find_by_id(params[:id].to_i)
    pm.to_json
  else
    status 404
  end
end

# curl -v http://127.0.0.1:9292/plugins/killbill-stripe/1.0/transactions/1
get '/plugins/killbill-stripe/1.0/transactions/:id', :provides => 'json' do
  if transaction = Killbill::Stripe::StripeTransaction.find_by_id(params[:id].to_i)
    transaction.to_json
  else
    status 404
  end
end
