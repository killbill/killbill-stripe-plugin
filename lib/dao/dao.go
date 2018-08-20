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

package dao

import (
	pbp "github.com/killbill/killbill-rpc/go/api/plugin/payment"
	"database/sql"
	_ "github.com/go-sql-driver/mysql"
	"time"
	"strconv"
)

type StripeDB struct {
	*sql.DB
}

type StripeObject struct {
	ID          int64
	CreatedAt   time.Time
	KBAccountId string
	KBTenantId  string
}

type StripeSource struct {
	StripeObject
	KbPaymentMethodId string
	StripeId          string
	StripeCustomerId  string
}

type StripeTransaction struct {
	StripeObject
	KbPaymentId            string
	KbPaymentTransactionId string
	KbTransactionType      string
	StripeId               string
	StripeAmount           int64 // In cents
	StripeCurrency         string
	StripeStatus           string
	StripeError            string
}

func (db *StripeDB) SaveStripeSource(stripeSource *StripeSource) (error) {
	now := stripeSource.CreatedAt.Format("2006-01-02T15:04:05")

	resp, err := db.Exec("insert into stripe_payment_methods (kb_payment_method_id, stripe_id, stripe_customer_id, kb_account_id, kb_tenant_id, created_at, updated_at) values (?,?,?,?,?,?,?)",
		stripeSource.KbPaymentMethodId, stripeSource.StripeId, stripeSource.StripeCustomerId, stripeSource.KBAccountId, stripeSource.KBTenantId, now, now)
	if err != nil {
		return err
	}

	lastInsertId, err := resp.LastInsertId()
	if err != nil {
		return err
	}

	stripeSource.ID = lastInsertId

	return nil
}

func (db *StripeDB) GetStripeSource(req pbp.PaymentRequest) (StripeSource, error) {
	var id int64
	var stripeId, stripeCustomerId, createdAtStr string

	getStripeIdStatement, err := db.Prepare("select id, stripe_id, stripe_customer_id, created_at from stripe_payment_methods where !is_deleted and kb_payment_method_id = ? and kb_account_id = ? and kb_tenant_id = ? limit 1")
	if err != nil {
		return StripeSource{}, err
	}
	defer getStripeIdStatement.Close()

	err = getStripeIdStatement.QueryRow(req.GetKbPaymentMethodId(), req.GetKbAccountId(), req.GetContext().GetTenantId()).Scan(&id, &stripeId, &stripeCustomerId, &createdAtStr)
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
			KBAccountId: req.GetKbAccountId(),
			KBTenantId:  req.GetContext().GetTenantId(),
		},
		KbPaymentMethodId: req.KbPaymentMethodId,
		StripeId:          stripeId,
		StripeCustomerId:  stripeCustomerId,
	}, nil
}

func (db *StripeDB) SaveTransaction(stripeTransaction *StripeTransaction) (error) {
	now := time.Now().In(time.UTC).Format("2006-01-02T15:04:05")

	resp, err := db.Exec("insert into stripe_transactions (kb_payment_id, kb_payment_transaction_id, kb_transaction_type, stripe_id, stripe_amount, stripe_currency, stripe_status, stripe_error, kb_account_id, kb_tenant_id, created_at) values (?,?,?,?,?,?,?,?,?,?,?)",
		stripeTransaction.KbPaymentId, stripeTransaction.KbPaymentTransactionId, stripeTransaction.KbTransactionType, stripeTransaction.StripeId, stripeTransaction.StripeAmount, stripeTransaction.StripeCurrency, stripeTransaction.StripeStatus, stripeTransaction.StripeError, stripeTransaction.KBAccountId, stripeTransaction.KBTenantId, now)
	if err != nil {
		return err
	}

	lastInsertId, err := resp.LastInsertId()
	if err != nil {
		return err
	}

	stripeTransaction.ID = lastInsertId

	return nil
}

func (db *StripeDB) GetTransactions(req pbp.PaymentRequest) ([]StripeTransaction, error) {
	rows, err := db.Query("select id, kb_payment_transaction_id, kb_transaction_type, stripe_id, stripe_amount, stripe_currency, stripe_status, stripe_error, created_at from stripe_transactions where kb_payment_id = ? and kb_account_id = ? and kb_tenant_id = ?", req.GetKbPaymentId(), req.GetKbAccountId(), req.GetContext().GetTenantId())
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var tx []StripeTransaction
	for rows.Next() {
		var id int64
		var kbPaymentTransactionId string
		var kbTransactionType string
		var stripeId string
		var stripeAmountStr string
		var stripeCurrency string
		var stripeStatus string
		var stripeError string
		var createdAtStr string
		err = rows.Scan(&id, &kbPaymentTransactionId, &kbTransactionType, &stripeId, &stripeAmountStr, &stripeCurrency, &stripeStatus, &stripeError, &createdAtStr)
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
				KBAccountId: req.GetKbAccountId(),
				KBTenantId:  req.GetKbAccountId(),
			},
			KbPaymentId:            req.GetKbPaymentId(),
			KbPaymentTransactionId: kbPaymentTransactionId,
			KbTransactionType:      kbTransactionType,
			StripeId:               stripeId,
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
