module Killbill::Stripe
  # Closest from a streaming API as we can get with ActiveRecord
  class StreamyResultSet
    include Enumerable

    def initialize(limit, batch_size = 100, &delegate)
      @limit = limit
      @batch = [batch_size, limit].min
      @delegate = delegate
    end

    def each(&block)
      (0..(@limit - @batch)).step(@batch) do |i|
        result = @delegate.call(i, @batch)
        block.call(result)
        # Optimization: bail out if no more results
        break if result.nil? || result.empty?
      end if @batch > 0
      # Make sure to return DB connections to the Pool
      ActiveRecord::Base.connection.close
    end

    def to_a
      super.to_a.flatten
    end
  end
end
