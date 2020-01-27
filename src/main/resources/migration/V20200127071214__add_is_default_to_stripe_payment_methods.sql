alter table stripe_payment_methods add column is_default smallint not null default 0 after stripe_id;
