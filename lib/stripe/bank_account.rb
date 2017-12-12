module Killbill
  module Stripe
    class BankAccount
      attr_accessor :bank_name, :account_number, :routing_number, :type

      def initialize(args)
        args.each do |k,v|
          instance_variable_set("@#{k}", v) unless v.nil?
        end
      end
    end
  end
end
