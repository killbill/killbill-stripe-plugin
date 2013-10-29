module Killbill::Stripe
  class Properties
    def initialize(file = 'stripe.yml')
      @config_file = Pathname.new(file).expand_path
    end

    def parse!
      raise "#{@config_file} is not a valid file" unless @config_file.file?
      @config = YAML.load_file(@config_file.to_s)
      validate!
    end

    def [](key)
      @config[key]
    end

    private

    def validate!
      raise "Bad configuration for Stripe plugin. Config is #{@config.inspect}" if @config.blank? || !@config[:stripe] || !@config[:stripe][:api_secret_key]
    end
  end
end
