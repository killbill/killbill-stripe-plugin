class EnlargeMessage < ActiveRecord::Migration

  def change
    reversible do |dir|
      change_table :stripe_responses do |t|
        dir.up { t.change :message, :text }
        dir.down { t.change :message, :string }
      end
    end
  end
end
