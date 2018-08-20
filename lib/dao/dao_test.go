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
	pbc "github.com/killbill/killbill-rpc/go/api/common"
	pbp "github.com/killbill/killbill-rpc/go/api/plugin/payment"
	kb "github.com/killbill/killbill-plugin-framework-go"

	"testing"
	"database/sql"
	"time"
)

func buildStripeDB(t *testing.T) StripeDB {
	db, err := sql.Open("mysql", "root:root@tcp(127.0.0.1:3306)/killbill_go")
	kb.AssertOk(t, err)

	return StripeDB{
		DB: db,
	}
}

func buildPaymentRequest() pbp.PaymentRequest {
	context := &pbc.CallContext{
		AccountId: kb.RandomUUID(),
		TenantId:  kb.RandomUUID(),
	}
	request := pbp.PaymentRequest{
		KbAccountId:       context.GetAccountId(),
		KbPaymentId:       kb.RandomUUID(),
		KbTransactionId:   kb.RandomUUID(),
		KbPaymentMethodId: kb.RandomUUID(),
		Amount:            "10",
		Currency:          "USD",
		Properties:        []*pbp.PluginProperty{},
		Context:           context,
	}
	return request
}

func TestPaymentMethod(t *testing.T) {
	db := buildStripeDB(t)
	defer db.Close()

	request := buildPaymentRequest()

	stripeSourceInput := StripeSource{
		StripeObject: StripeObject{
			CreatedAt:   time.Now().In(time.UTC),
			KBAccountId: request.GetKbAccountId(),
			KBTenantId:  request.GetContext().GetTenantId(),
		},
		KbPaymentMethodId: request.GetKbPaymentMethodId(),
		StripeId:          kb.RandomUUID(),
		StripeCustomerId:  kb.RandomUUID(),
	}
	err := db.SaveStripeSource(&stripeSourceInput)
	kb.AssertOk(t, err)

	stripeSource, err := db.GetStripeSource(request)
	kb.AssertOk(t, err)
	kb.AssertEquals(t, stripeSourceInput.StripeId, stripeSource.StripeId)
	kb.AssertEquals(t, stripeSourceInput.StripeCustomerId, stripeSource.StripeCustomerId)
}

func TestTransaction(t *testing.T) {
	db := buildStripeDB(t)
	defer db.Close()

	request := buildPaymentRequest()

	stripeTransactionInput := StripeTransaction{
		StripeObject: StripeObject{
			KBAccountId: request.GetKbAccountId(),
			KBTenantId:  request.GetContext().GetTenantId(),
		},
		KbPaymentId:            request.GetKbPaymentId(),
		KbPaymentTransactionId: request.GetKbTransactionId(),
		KbTransactionType:      "AUTHORIZE",
		StripeId:               kb.RandomUUID(),
		StripeAmount:           1000,
		StripeCurrency:         "USD",
		StripeStatus:           "succeeded",
	}
	err := db.SaveTransaction(&stripeTransactionInput)
	kb.AssertOk(t, err)

	payment, err := db.GetTransactions(request)
	kb.AssertOk(t, err)
	kb.Assert(t, len(payment) == 1, "Wrong number of tx found")
	kb.AssertEquals(t, "succeeded", payment[0].StripeStatus)
	kb.AssertEquals(t, request.GetKbTransactionId(), payment[0].KbPaymentTransactionId)

	request.KbTransactionId = kb.RandomUUID()
	stripeTransactionInput.KbPaymentTransactionId = request.KbTransactionId
	stripeTransactionInput.StripeStatus = "failed"
	stripeTransactionInput.StripeId = kb.RandomUUID()
	err = db.SaveTransaction(&stripeTransactionInput)
	kb.AssertOk(t, err)

	payment, err = db.GetTransactions(request)
	kb.AssertOk(t, err)
	kb.Assert(t, len(payment) == 2, "Wrong number of tx found")
	kb.AssertEquals(t, "failed", payment[1].StripeStatus)
	kb.AssertEquals(t, request.GetKbTransactionId(), payment[1].KbPaymentTransactionId)
	kb.Assert(t, payment[1].KbPaymentTransactionId != payment[0].KbPaymentTransactionId, "KbPaymentTransactionId should be different")
}
