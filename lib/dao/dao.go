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
	"time"
	"strconv"
)

func SaveStripeSourceId(db sql.DB, req pb.PaymentRequest, stripeId string) (*pb.PaymentMethodPlugin, error) {
	now := time.Now().In(time.UTC).Format("2006-01-02T15:04:05") // TODO KB Clock

	_, err := db.Exec("insert into stripe_payment_methods (kb_payment_method_id, stripe_id, kb_account_id, kb_tenant_id, created_at, updated_at) values (?,?,?,?,?,?)", req.GetKbPaymentMethodId(), stripeId, req.GetKbAccountId(), req.GetContext().GetTenantId(), now, now)
	if err != nil {
		return nil, err
	}

	return &pb.PaymentMethodPlugin{
		KbPaymentMethodId:       req.KbPaymentMethodId,
		ExternalPaymentMethodId: stripeId,
		IsDefaultPaymentMethod:  false,
		Properties:              nil,
	}, nil
}

func GetStripeSourceId(db sql.DB, req pb.PaymentRequest) (*pb.PaymentMethodPlugin, error) {
	var stripeId string

	getStripeIdStatement, err := db.Prepare("select stripe_id from stripe_payment_methods where !is_deleted and kb_payment_method_id = ? and kb_account_id = ? and kb_tenant_id = ? limit 1")
	if err != nil {
		return "", err
	}
	defer getStripeIdStatement.Close()

	err = getStripeIdStatement.QueryRow(req.GetKbPaymentMethodId(), req.GetKbAccountId(), req.GetContext().GetTenantId()).Scan(&stripeId)
	if err != nil {
		return nil, err
	}

	return &pb.PaymentMethodPlugin{
		KbPaymentMethodId:       req.KbPaymentMethodId,
		ExternalPaymentMethodId: stripeId,
		IsDefaultPaymentMethod:  false,
		Properties:              nil,
	}, nil
}

type StripeResponse struct {
	ID       string
	Amount   int64
	Currency string
	Status   string
}

func SaveTransaction(db sql.DB, req pb.PaymentRequest, txType pb.PaymentTransactionInfoPlugin_TransactionType, resp StripeResponse, chErr error) (*pb.PaymentTransactionInfoPlugin, error) {
	now := time.Now().In(time.UTC).Format("2006-01-02T15:04:05") // TODO KB Clock

	stripeId := resp.ID
	stripeAmount := resp.Amount
	stripeCurrency := resp.Currency
	stripeStatus := resp.Status
	stripeError := chErr.Error()
	_, err := db.Exec("insert into stripe_transactions (kb_payment_id, kb_payment_transaction_id, kb_transaction_type, stripe_id, stripe_amount, stripe_currency, stripe_status, stripe_error, kb_account_id, kb_tenant_id, created_at) values (?,?,?,?,?,?,?,?,?,?,?)", req.GetKbPaymentId(), req.GetKbTransactionId(), txType.String(), stripeId, stripeAmount, stripeCurrency, stripeStatus, stripeError, req.GetKbAccountId(), req.GetContext().GetTenantId(), now)
	if err != nil {
		return nil, err
	}

	return &pb.PaymentTransactionInfoPlugin{
		KbPaymentId:             req.GetKbPaymentId(),
		KbTransactionPaymentId:  req.GetKbTransactionId(),
		TransactionType:         txType,
		Amount:                  strconv.FormatInt(stripeAmount*100, 10), // TODO Joda-Money?
		Currency:                stripeCurrency,
		CreatedDate:             now,
		EffectiveDate:           now,
		GetStatus:               toKbPaymentPluginStatus(stripeStatus),
		GatewayError:            string(stripeError),
		GatewayErrorCode:        "",
		FirstPaymentReferenceId: stripeId,
	}, nil
}

func GetTransactions(db sql.DB, req pb.PaymentRequest) ([]pb.PaymentTransactionInfoPlugin, error) {
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

		kbStatus := toKbPaymentPluginStatus(stripeStatus)

		tx = append(tx, pb.PaymentTransactionInfoPlugin{
			KbPaymentId:             req.GetKbPaymentId(),
			KbTransactionPaymentId:  kbPaymentTransactionId,
			TransactionType:         toKbTransactionType(kbTransactionType),
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

func toKbPaymentPluginStatus(stripeStatus string) pb.PaymentTransactionInfoPlugin_PaymentPluginStatus {
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
	return kbStatus
}

// TODO Move to base framework
func toKbTransactionType(kbTransactionType string) pb.PaymentTransactionInfoPlugin_TransactionType {
	kbTransactionTypeInt, ok := pb.PaymentTransactionInfoPlugin_TransactionType_value[kbTransactionType]
	if !ok {
		return -1 // TODO
	}
	return pb.PaymentTransactionInfoPlugin_TransactionType(kbTransactionTypeInt)
}
