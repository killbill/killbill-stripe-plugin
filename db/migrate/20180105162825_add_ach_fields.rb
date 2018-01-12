class AddAchFields < ActiveRecord::Migration

  def change
    add_column :stripe_payment_methods, :source_type, :string
    add_column :stripe_payment_methods, :bank_name, :string
    add_column :stripe_payment_methods, :bank_routing_number, :string
    add_column :stripe_payment_methods, :bank_account_first_name, :string
    add_column :stripe_payment_methods, :bank_account_last_name, :string
  end
end
