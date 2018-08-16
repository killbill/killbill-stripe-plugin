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
	pb "../payment"
	"database/sql"
	_ "github.com/go-sql-driver/mysql"
	"github.com/stripe/stripe-go"
	"time"
)

func saveStripeId(db *sql.DB, req *pb.PaymentRequest, stripeId string) (int64, error) {
	now := time.Now().In(time.UTC).Format("2006-01-02T15:04:05") // TODO KB Clock

	res, err := db.Exec("insert into stripe_payment_methods (kb_payment_method_id, stripe_id, kb_account_id, kb_tenant_id, created_at, updated_at) values (?,?,?,?,?,?)", req.GetKbPaymentMethodId(), stripeId, req.GetKbAccountId(), req.GetContext().GetTenantId(), now, now)
	if err != nil {
		return 0, err
	}

	lastInsertId, _ := res.LastInsertId()
	if err != nil {
		return 0, err
	}

	return lastInsertId, nil
}

func getStripeId(db *sql.DB, req *pb.PaymentRequest) (string, error) {
	var stripeId string

	getStripeIdStatement, err := db.Prepare("select stripe_id from stripe_payment_methods where !is_deleted and kb_payment_method_id = ? and kb_account_id = ? and kb_tenant_id = ? limit 1")
	if err != nil {
		return "", err
	}
	defer getStripeIdStatement.Close()

	err = getStripeIdStatement.QueryRow(req.GetKbPaymentMethodId(), req.GetKbAccountId(), req.GetContext().GetTenantId()).Scan(&stripeId)
	if err != nil {
		return "", err
	}

	return stripeId, nil
}

func saveTransaction(db sql.DB, req pb.PaymentRequest, txType pb.PaymentTransactionInfoPlugin_TransactionType, ch stripe.Charge, chErr stripe.Error) (int64, error) {
	now := time.Now().In(time.UTC).Format("2006-01-02T15:04:05") // TODO KB Clock

	res, err := db.Exec("insert into stripe_transactions (kb_payment_id, kb_payment_transaction_id, kb_transaction_type, stripe_id, stripe_amount, stripe_currency, stripe_status, stripe_error, kb_account_id, kb_tenant_id, created_at) values (?,?,?,?,?,?,?,?,?,?,?)", req.GetKbPaymentId(), req.GetKbTransactionId(), txType.String(), ch.ID, ch.Amount, ch.Currency, ch.Status, chErr.Code, req.GetKbAccountId(), req.GetContext().GetTenantId(), now)
	if err != nil {
		return 0, err
	}

	lastInsertId, _ := res.LastInsertId()
	if err != nil {
		return 0, err
	}

	return lastInsertId, nil
}

func getTransactions(db sql.DB, req pb.PaymentRequest) ([]pb.PaymentTransactionInfoPlugin, error) {
	rows, err := db.Query("select kb_payment_transaction_id, kb_transaction_type, stripe_id, stripe_amount, stripe_currency, stripe_status, stripe_error, created_at from stripe_transactions where kb_payment_id = ? and kb_account_id = ? and kb_tenant_id = ?", req.GetKbPaymentId(), req.GetKbAccountId(), req.GetContext().GetTenantId())
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var tx []pb.PaymentTransactionInfoPlugin
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

		kbTransactionTypeInt, ok := pb.PaymentTransactionInfoPlugin_TransactionType_value[kbTransactionType]
		if !ok {
			return nil, nil // TODO
		}

		kbStatus := pb.PaymentTransactionInfoPlugin_UNDEFINED
		if stripeStatus == "succeeded" {
			kbStatus = pb.PaymentTransactionInfoPlugin_PROCESSED
		} else if stripeStatus == "pending" {
			kbStatus = pb.PaymentTransactionInfoPlugin_PENDING
		} else if stripeStatus == "failed" {
			kbStatus = pb.PaymentTransactionInfoPlugin_ERROR
		} else if stripeStatus == "canceled" {
			kbStatus = pb.PaymentTransactionInfoPlugin_CANCELED
		}

		tx = append(tx, pb.PaymentTransactionInfoPlugin{
			KbPaymentId:             req.KbPaymentId,
			KbTransactionPaymentId:  kbPaymentTransactionId,
			TransactionType:         pb.PaymentTransactionInfoPlugin_TransactionType(kbTransactionTypeInt),
			Amount:                  stripeAmount, // TODO Joda-Money?
			Currency:                stripeCurrency,
			CreatedDate:             createdAt,
			EffectiveDate:           createdAt,
			GetStatus:               kbStatus,
			GatewayError:            stripeError,
			GatewayErrorCode:        "",
			FirstPaymentReferenceId: stripeId,
		})
	}
	err = rows.Err()
	if err != nil {
		return nil, err
	}

	return tx, nil
}
