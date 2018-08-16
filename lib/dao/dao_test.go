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
	u "../utils"
	"testing"
	"database/sql"
	"github.com/stripe/stripe-go"
)

func TestPaymentMethod(t *testing.T) {
	kbAccountId := u.RandomUUID()
	kbTenantId := u.RandomUUID()

	request := pb.PaymentRequest{
		KbAccountId:       kbAccountId,
		KbPaymentMethodId: u.RandomUUID(),
		Amount:            "10",
		Currency:          "USD",
		Properties:        []*pb.PluginProperty{},
		Context: &pb.CallContext{
			AccountId: kbAccountId,
			TenantId:  kbTenantId,
		},
	}

	db, err := sql.Open("mysql", "root:root@tcp(127.0.0.1:3306)/killbill_go")
	if err != nil {
		panic(err)
	}
	defer db.Close()

	stripeId := u.RandomUUID()
	id, err := saveStripeId(db, &request, stripeId)
	if err != nil {
		t.Errorf("Error should be nil: %s", err)
	}
	if id <= 0 {
		t.Errorf("auto_increment should be positive: %d", id)
	}

	foundToken, err := getStripeId(db, &request)
	if err != nil {
		t.Errorf("Error should be nil: %s", err)
	}
	if foundToken != stripeId {
		t.Errorf("Wrong stripeId found: %s", foundToken)
	}
}

func TestTransaction(t *testing.T) {
	kbAccountId := u.RandomUUID()
	kbPaymentId := u.RandomUUID()
	kbTenantId := u.RandomUUID()

	request := pb.PaymentRequest{
		KbAccountId:       kbAccountId,
		KbPaymentId:       kbPaymentId,
		KbTransactionId:   u.RandomUUID(),
		KbPaymentMethodId: u.RandomUUID(),
		Amount:            "10",
		Currency:          "USD",
		Properties:        []*pb.PluginProperty{},
		Context: &pb.CallContext{
			AccountId: kbAccountId,
			TenantId:  kbTenantId,
		},
	}

	db, err := sql.Open("mysql", "root:root@tcp(127.0.0.1:3306)/killbill_go")
	if err != nil {
		panic(err)
	}
	defer db.Close()

	id, err := saveTransaction(*db, request, pb.PaymentTransactionInfoPlugin_AUTHORIZE, stripe.Charge{
		ID: u.RandomUUID(),
		Amount: 1000,
		Currency: "USD",
		Status: "succeeded",
	}, stripe.Error{})
	if err != nil {
		t.Errorf("Error should be nil: %s", err)
	}
	if id <= 0 {
		t.Errorf("auto_increment should be positive: %d", id)
	}

	payment, err := getTransactions(*db, request)
	if err != nil {
		t.Errorf("Error should be nil: %s", err)
	}
	if len(payment) != 1 {
		t.Errorf("Wrong number of tx found")
	}
	if payment[0].GetStatus != pb.PaymentTransactionInfoPlugin_PROCESSED {
		t.Errorf("Wrong status: %s", payment[0].GetStatus)
	}

	request.KbTransactionId = u.RandomUUID()
	id, err = saveTransaction(*db, request, pb.PaymentTransactionInfoPlugin_CAPTURE, stripe.Charge{
		ID: u.RandomUUID(),
		Amount: 1000,
		Currency: "USD",
		Status: "failed",
	}, stripe.Error{})
	if err != nil {
		t.Errorf("Error should be nil: %s", err)
	}
	if id <= 0 {
		t.Errorf("auto_increment should be positive: %d", id)
	}

	payment, err = getTransactions(*db, request)
	if err != nil {
		t.Errorf("Error should be nil: %s", err)
	}
	if len(payment) != 2 {
		t.Errorf("Wrong number of tx found")
	}
	if payment[1].GetStatus != pb.PaymentTransactionInfoPlugin_ERROR {
		t.Errorf("Wrong status: %s", payment[1].GetStatus)
	}
}
