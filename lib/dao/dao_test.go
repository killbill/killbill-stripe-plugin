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

func TestPaymentMethod(t *testing.T) {
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

	db, err := sql.Open("mysql", "root:root@tcp(127.0.0.1:3306)/killbill_go")
	kb.AssertOk(t, err)
	defer db.Close()

	stripeSourceInput := StripeSource{
		ID:         1,
		StripeId:   kb.RandomUUID(),
		CustomerId: kb.RandomUUID(),
		CreatedAt:  time.Now().In(time.UTC),
	}
	err = SaveStripeSource(*db, request, &stripeSourceInput)
	kb.AssertOk(t, err)

	stripeSource, err := GetStripeSource(*db, request)
	kb.AssertOk(t, err)
	kb.AssertEquals(t, stripeSourceInput.StripeId, stripeSource.StripeId)
	kb.AssertEquals(t, stripeSourceInput.CustomerId, stripeSource.CustomerId)
}

func TestTransaction(t *testing.T) {
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

	db, err := sql.Open("mysql", "root:root@tcp(127.0.0.1:3306)/killbill_go")
	kb.AssertOk(t, err)
	defer db.Close()

	err = SaveTransaction(*db, request, pbp.PaymentTransactionInfoPlugin_AUTHORIZE, &StripeResponse{
		StripeId: kb.RandomUUID(),
		Amount:   1000,
		Currency: "USD",
		Status:   "succeeded",
	}, nil)
	kb.AssertOk(t, err)

	payment, err := GetTransactions(*db, request)
	kb.AssertOk(t, err)
	kb.Assert(t, len(payment) == 1, "Wrong number of tx found")
	kb.AssertEquals(t, pbp.PaymentTransactionInfoPlugin_PROCESSED, payment[0].GetStatus)

	request.KbTransactionId = kb.RandomUUID()
	err = SaveTransaction(*db, request, pbp.PaymentTransactionInfoPlugin_CAPTURE, &StripeResponse{
		StripeId: kb.RandomUUID(),
		Amount:   1000,
		Currency: "USD",
		Status:   "failed",
	}, nil)
	kb.AssertOk(t, err)

	payment, err = GetTransactions(*db, request)
	kb.AssertOk(t, err)
	kb.Assert(t, len(payment) == 2, "Wrong number of tx found")
	kb.AssertEquals(t, pbp.PaymentTransactionInfoPlugin_ERROR, payment[1].GetStatus)
}
