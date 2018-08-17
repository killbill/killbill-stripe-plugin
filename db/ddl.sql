CREATE TABLE stripe_payment_methods (
  id serial unique,
  kb_payment_method_id varchar(255) NOT NULL,
  stripe_id varchar(255) DEFAULT NULL,
  stripe_customer_id varchar(255) DEFAULT NULL,
  is_deleted boolean NOT NULL DEFAULT '0',
  created_at datetime NOT NULL,
  updated_at datetime NOT NULL,
  kb_account_id varchar(255) DEFAULT NULL,
  kb_tenant_id varchar(255) DEFAULT NULL,
  PRIMARY KEY (id)
) /*! ENGINE=InnoDB CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX index_stripe_payment_methods_kb_account_id ON stripe_payment_methods(kb_account_id);
CREATE INDEX index_stripe_payment_methods_kb_payment_method_id ON stripe_payment_methods(kb_payment_method_id);

CREATE TABLE stripe_transactions (
  id serial unique,
  kb_payment_id varchar(255) DEFAULT NULL,
  kb_payment_transaction_id varchar(255) DEFAULT NULL,
  kb_transaction_type varchar(255) DEFAULT NULL,
  stripe_id varchar(255) DEFAULT NULL,
  stripe_amount varchar(255) DEFAULT NULL,
  stripe_currency varchar(255) DEFAULT NULL,
  stripe_status varchar(255) DEFAULT NULL,
  stripe_error text DEFAULT NULL,
  created_at datetime NOT NULL,
  kb_account_id varchar(255) DEFAULT NULL,
  kb_tenant_id varchar(255) DEFAULT NULL,
  PRIMARY KEY (id)
) /*! ENGINE=InnoDB CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX index_stripe_transactions_kb_payment_id ON stripe_transactions(kb_payment_id);
