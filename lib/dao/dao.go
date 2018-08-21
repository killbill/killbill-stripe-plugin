/*
 * Copyright 2011-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

// Package dao implements utility routines for database access.
package dao

import (
	pbp "github.com/killbill/killbill-rpc/go/api/plugin/payment"
	"database/sql"
	// MySQL database driver
	_ "github.com/go-sql-driver/mysql"
	"time"
	"strconv"
)

// StripeDB represents the plugin database.
type StripeDB struct {
	*sql.DB
}

// StripeObject holds common columns.
type StripeObject struct {
	ID          int64
	CreatedAt   time.Time
	KBAccountID string
	KBTenantID  string
}

// StripeSource represents a row in stripe_payment_methods.
type StripeSource struct {
	StripeObject
	KbPaymentMethodID string
	StripeID          string
	StripeCustomerID  string
}

// StripeTransaction represents a row in stripe_transactions.
type StripeTransaction struct {
	StripeObject
	KbPaymentID            string
	KbPaymentTransactionID string
	KbTransactionType      string
	StripeID               string
	StripeAmount           int64 // In cents
	StripeCurrency         string
	StripeStatus           string
	StripeError            string
}

// SaveStripeSource creates a new row in stripe_payment_methods.
func (db *StripeDB) SaveStripeSource(stripeSource *StripeSource) (error) {
	now := stripeSource.CreatedAt.Format("2006-01-02T15:04:05")

	resp, err := db.Exec("insert into stripe_payment_methods (kb_payment_method_id, stripe_id, stripe_customer_id, kb_account_id, kb_tenant_id, created_at, updated_at) values (?,?,?,?,?,?,?)",
		stripeSource.KbPaymentMethodID, stripeSource.StripeID, stripeSource.StripeCustomerID, stripeSource.KBAccountID, stripeSource.KBTenantID, now, now)
	if err != nil {
		return err
	}

	lastInsertID, err := resp.LastInsertId()
	if err != nil {
		return err
	}

	stripeSource.ID = lastInsertID

	return nil
}

// GetStripeSource retries the row matching a given payment method in stripe_payment_methods.
func (db *StripeDB) GetStripeSource(req pbp.PaymentRequest) (StripeSource, error) {
	var id int64
	var stripeID, stripeCustomerID, createdAtStr string

	getStripeIDStatement, err := db.Prepare("select id, stripe_id, stripe_customer_id, created_at from stripe_payment_methods where !is_deleted and kb_payment_method_id = ? and kb_account_id = ? and kb_tenant_id = ? limit 1")
	if err != nil {
		return StripeSource{}, err
	}
	defer getStripeIDStatement.Close()

	err = getStripeIDStatement.QueryRow(req.GetKbPaymentMethodId(), req.GetKbAccountId(), req.GetContext().GetTenantId()).Scan(&id, &stripeID, &stripeCustomerID, &createdAtStr)
	if err != nil {
		return StripeSource{}, err
	}

	createdAt, err := time.Parse("2006-01-02 15:04:05", createdAtStr)
	if err != nil {
		return StripeSource{}, err
	}

	return StripeSource{
		StripeObject: StripeObject{
			ID:          id,
			CreatedAt:   createdAt,
			KBAccountID: req.GetKbAccountId(),
			KBTenantID:  req.GetContext().GetTenantId(),
		},
		KbPaymentMethodID: req.KbPaymentMethodId,
		StripeID:          stripeID,
		StripeCustomerID:  stripeCustomerID,
	}, nil
}

// SaveTransaction creates a new row in stripe_transactions.
func (db *StripeDB) SaveTransaction(stripeTransaction *StripeTransaction) (error) {
	now := stripeTransaction.CreatedAt.Format("2006-01-02T15:04:05")

	resp, err := db.Exec("insert into stripe_transactions (kb_payment_id, kb_payment_transaction_id, kb_transaction_type, stripe_id, stripe_amount, stripe_currency, stripe_status, stripe_error, kb_account_id, kb_tenant_id, created_at) values (?,?,?,?,?,?,?,?,?,?,?)",
		stripeTransaction.KbPaymentID, stripeTransaction.KbPaymentTransactionID, stripeTransaction.KbTransactionType, stripeTransaction.StripeID, stripeTransaction.StripeAmount, stripeTransaction.StripeCurrency, stripeTransaction.StripeStatus, stripeTransaction.StripeError, stripeTransaction.KBAccountID, stripeTransaction.KBTenantID, now)
	if err != nil {
		return err
	}

	lastInsertID, err := resp.LastInsertId()
	if err != nil {
		return err
	}

	stripeTransaction.ID = lastInsertID

	return nil
}

// GetTransactions retries all rows matching a given payment in stripe_transactions.
func (db *StripeDB) GetTransactions(req pbp.PaymentRequest) ([]StripeTransaction, error) {
	rows, err := db.Query("select id, kb_payment_transaction_id, kb_transaction_type, stripe_id, stripe_amount, stripe_currency, stripe_status, stripe_error, created_at from stripe_transactions where kb_payment_id = ? and kb_account_id = ? and kb_tenant_id = ?", req.GetKbPaymentId(), req.GetKbAccountId(), req.GetContext().GetTenantId())
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var tx []StripeTransaction
	for rows.Next() {
		var id int64
		var kbPaymentTransactionID string
		var kbTransactionType string
		var stripeID string
		var stripeAmountStr string
		var stripeCurrency string
		var stripeStatus string
		var stripeError string
		var createdAtStr string
		err = rows.Scan(&id, &kbPaymentTransactionID, &kbTransactionType, &stripeID, &stripeAmountStr, &stripeCurrency, &stripeStatus, &stripeError, &createdAtStr)
		if err != nil {
			return nil, err
		}

		stripeAmount, err := strconv.ParseInt(stripeAmountStr, 10, 64)
		if err != nil {
			return nil, err
		}
		createdAt, err := time.Parse("2006-01-02 15:04:05", createdAtStr)
		if err != nil {
			return nil, err
		}
		tx = append(tx, StripeTransaction{
			StripeObject: StripeObject{
				ID:          id,
				CreatedAt:   createdAt,
				KBAccountID: req.GetKbAccountId(),
				KBTenantID:  req.GetKbAccountId(),
			},
			KbPaymentID:            req.GetKbPaymentId(),
			KbPaymentTransactionID: kbPaymentTransactionID,
			KbTransactionType:      kbTransactionType,
			StripeID:               stripeID,
			StripeAmount:           stripeAmount,
			StripeCurrency:         stripeCurrency,
			StripeStatus:           stripeStatus,
			StripeError:            stripeError,
		})
	}
	err = rows.Err()
	if err != nil {
		return nil, err
	}

	return tx, nil
}
