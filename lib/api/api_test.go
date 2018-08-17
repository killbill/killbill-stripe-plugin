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

package api

import (
	pbc "github.com/killbill/killbill-rpc/go/api/common"
	pbp "github.com/killbill/killbill-rpc/go/api/plugin/payment"
	kb "github.com/killbill/killbill-plugin-framework-go"

	"testing"
	"os"
	"github.com/stripe/stripe-go"
)

func TestAuthCaptureRefund(t *testing.T) {
	stripe.LogLevel = 3
	stripe.Key = os.Getenv("STRIPE_SECRET_KEY")
	stripeToken := os.Getenv("STRIPE_TOKEN")
	stripeCustomerId := os.Getenv("STRIPE_CUSTOMER_ID")

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
		Properties: []*pbp.PluginProperty{
			{
				Key:   "stripeToken",
				Value: stripeToken,
			},
			{
				Key:   "stripeCustomerId",
				Value: stripeCustomerId,
			}},
		Context: context,
	}

	server := &PaymentPluginApiServer{}

	paymentMethodPlugin, err := server.AddPaymentMethod(nil, &request)
	kb.AssertOk(t, err)
	kb.AssertEquals(t, request.GetKbPaymentMethodId(), paymentMethodPlugin.GetKbPaymentMethodId())

	paymentTransactionInfoPlugin, err := server.AuthorizePayment(nil, &request)
	kb.AssertOk(t, err)
	kb.AssertEquals(t, pbp.PaymentTransactionInfoPlugin_AUTHORIZE, paymentTransactionInfoPlugin.GetTransactionType())
	kb.AssertEquals(t, pbp.PaymentTransactionInfoPlugin_PROCESSED, paymentTransactionInfoPlugin.GetStatus)

	paymentInfoPlugin, err := server.GetPaymentInfo(nil, &request)
	kb.Assert(t, len(paymentInfoPlugin) == 1, "Wrong number of tx")

	request.KbTransactionId = kb.RandomUUID()
	paymentTransactionInfoPlugin, err = server.CapturePayment(nil, &request)
	kb.AssertOk(t, err)
	kb.AssertEquals(t, pbp.PaymentTransactionInfoPlugin_CAPTURE, paymentTransactionInfoPlugin.GetTransactionType())
	kb.AssertEquals(t, pbp.PaymentTransactionInfoPlugin_PROCESSED, paymentTransactionInfoPlugin.GetStatus)

	paymentInfoPlugin, err = server.GetPaymentInfo(nil, &request)
	kb.Assert(t, len(paymentInfoPlugin) == 2, "Wrong number of tx")

	request.KbTransactionId = kb.RandomUUID()
	paymentTransactionInfoPlugin, err = server.RefundPayment(nil, &request)
	kb.AssertOk(t, err)
	kb.AssertEquals(t, pbp.PaymentTransactionInfoPlugin_REFUND, paymentTransactionInfoPlugin.GetTransactionType())
	kb.AssertEquals(t, pbp.PaymentTransactionInfoPlugin_PROCESSED, paymentTransactionInfoPlugin.GetStatus)

	paymentInfoPlugin, err = server.GetPaymentInfo(nil, &request)
	kb.Assert(t, len(paymentInfoPlugin) == 3, "Wrong number of tx")
}
