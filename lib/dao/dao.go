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
	kb "github.com/killbill/killbill-plugin-framework-go"

	"database/sql"
	_ "github.com/go-sql-driver/mysql"
	"time"
)

type StripeSource struct {
	ID         int64
	StripeId   string
	CustomerId string
	CreatedAt  time.Time
}

func SaveStripeSource(db sql.DB, req pbp.PaymentRequest, stripeSource *StripeSource) (error) {
	now := stripeSource.CreatedAt.Format("2006-01-02T15:04:05") // TODO KB Clock

	resp, err := db.Exec("insert into stripe_payment_methods (kb_payment_method_id, stripe_id, stripe_customer_id, kb_account_id, kb_tenant_id, created_at, updated_at) values (?,?,?,?,?,?,?)", req.GetKbPaymentMethodId(), stripeSource.StripeId, stripeSource.CustomerId, req.GetKbAccountId(), req.GetContext().GetTenantId(), now, now)
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

func GetStripeSource(db sql.DB, req pbp.PaymentRequest) (*StripeSource, error) {
	var id int64
	var stripeId, stripeCustomerId, createdAtStr string

	getStripeIdStatement, err := db.Prepare("select id, stripe_id, stripe_customer_id, created_at from stripe_payment_methods where !is_deleted and kb_payment_method_id = ? and kb_account_id = ? and kb_tenant_id = ? limit 1")
	if err != nil {
		return nil, err
	}
	defer getStripeIdStatement.Close()

	err = getStripeIdStatement.QueryRow(req.GetKbPaymentMethodId(), req.GetKbAccountId(), req.GetContext().GetTenantId()).Scan(&id, &stripeId, &stripeCustomerId, &createdAtStr)
	if err != nil {
		return nil, err
	}

	createdAt, _ := time.Parse("2006-01-02T15:04:05", createdAtStr)

	return &StripeSource{
		ID:         id,
		StripeId:   stripeId,
		CustomerId: stripeCustomerId,
		CreatedAt:  createdAt,
	}, nil
}

type StripeResponse struct {
	ID        int64
	StripeId  string
	Amount    int64
	Currency  string
	Status    string
	Error     string
	CreatedAt time.Time
}

func SaveTransaction(db sql.DB, req pbp.PaymentRequest, txType pbp.PaymentTransactionInfoPlugin_TransactionType, stripeResponse *StripeResponse, chErr error) (error) {
	now := time.Now().In(time.UTC).Format("2006-01-02T15:04:05") // TODO KB Clock

	stripeId := stripeResponse.ID
	stripeAmount := stripeResponse.Amount
	stripeCurrency := stripeResponse.Currency
	stripeStatus := stripeResponse.Status
	stripeError := ""
	if chErr != nil {
		stripeError = chErr.Error()
	}

	resp, err := db.Exec("insert into stripe_transactions (kb_payment_id, kb_payment_transaction_id, kb_transaction_type, stripe_id, stripe_amount, stripe_currency, stripe_status, stripe_error, kb_account_id, kb_tenant_id, created_at) values (?,?,?,?,?,?,?,?,?,?,?)", req.GetKbPaymentId(), req.GetKbTransactionId(), txType.String(), stripeId, stripeAmount, stripeCurrency, stripeStatus, stripeError, req.GetKbAccountId(), req.GetContext().GetTenantId(), now)
	if err != nil {
		return err
	}

	lastInsertId, err := resp.LastInsertId()
	if err != nil {
		return err
	}

	stripeResponse.ID = lastInsertId

	return nil
}

func GetTransactions(db sql.DB, req pbp.PaymentRequest) ([]pbp.PaymentTransactionInfoPlugin, error) {
	rows, err := db.Query("select kb_payment_transaction_id, kb_transaction_type, stripe_id, stripe_amount, stripe_currency, stripe_status, stripe_error, created_at from stripe_transactions where kb_payment_id = ? and kb_account_id = ? and kb_tenant_id = ?", req.GetKbPaymentId(), req.GetKbAccountId(), req.GetContext().GetTenantId())
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var tx []pbp.PaymentTransactionInfoPlugin
	for rows.Next() {
		var kbPaymentTransactionId string
		var kbTransactionType string
		var stripeId string
		var stripeAmount string
		var stripeCurrency string
		var stripeStatus string
		var stripeError string
		var createdAt string
		err = rows.Scan(&kbPaymentTransactionId, &kbTransactionType, &stripeId, &stripeAmount, &stripeCurrency, &stripeStatus, &stripeError, &createdAt)
		if err != nil {
			return nil, err
		}

		kbStatus := toKbPaymentPluginStatus(stripeStatus, nil)

		tx = append(tx, buildPaymentTransactionInfoPlugin(req, kbPaymentTransactionId, kbTransactionType, stripeAmount, stripeCurrency, createdAt, kbStatus, stripeError, stripeId))
	}
	err = rows.Err()
	if err != nil {
		return nil, err
	}

	return tx, nil
}

func buildPaymentTransactionInfoPlugin(req pbp.PaymentRequest, kbPaymentTransactionId string, kbTransactionType string, stripeAmount string, stripeCurrency string, createdAt string, kbStatus pbp.PaymentTransactionInfoPlugin_PaymentPluginStatus, stripeError string, stripeId string) pbp.PaymentTransactionInfoPlugin {
	return pbp.PaymentTransactionInfoPlugin{
		KbPaymentId:             req.GetKbPaymentId(),
		KbTransactionPaymentId:  kbPaymentTransactionId,
		TransactionType:         kb.ToKbTransactionType(kbTransactionType),
		Amount:                  stripeAmount, // TODO Joda-Money?
		Currency:                stripeCurrency,
		CreatedDate:             createdAt,
		EffectiveDate:           createdAt,
		GetStatus:               kbStatus,
		GatewayError:            stripeError,
		GatewayErrorCode:        "",
		FirstPaymentReferenceId: stripeId,
	}
}

// TODO DUP
func toKbPaymentPluginStatus(stripeStatus string, chErr error) pbp.PaymentTransactionInfoPlugin_PaymentPluginStatus {
	if chErr != nil {
		return pbp.PaymentTransactionInfoPlugin_CANCELED
	}

	kbStatus := pbp.PaymentTransactionInfoPlugin_UNDEFINED
	if stripeStatus == "succeeded" {
		kbStatus = pbp.PaymentTransactionInfoPlugin_PROCESSED
	} else if stripeStatus == "pending" {
		kbStatus = pbp.PaymentTransactionInfoPlugin_PENDING
	} else if stripeStatus == "failed" {
		kbStatus = pbp.PaymentTransactionInfoPlugin_ERROR
	} else if stripeStatus == "canceled" {
		kbStatus = pbp.PaymentTransactionInfoPlugin_CANCELED
	}
	return kbStatus
}
