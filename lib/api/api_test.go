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
	"google.golang.org/grpc"
	"time"
)

type MockPaymentPluginApi_GetPaymentInfoServer struct {
	tx []pbp.PaymentTransactionInfoPlugin
	grpc.ServerStream
}

func (m *MockPaymentPluginApi_GetPaymentInfoServer) Send(res *pbp.PaymentTransactionInfoPlugin) error {
	m.tx = append(m.tx, *res)
	return nil
}

func TestPurchase(t *testing.T) {
	stripe.LogLevel = 3
	stripe.Key = os.Getenv("STRIPE_SECRET_KEY")
	stripeToken := os.Getenv("STRIPE_TOKEN")
	stripeCustomerId := os.Getenv("STRIPE_CUSTOMER_ID")

	context := &pbc.CallContext{
		CreatedDate: time.Now().In(time.UTC).Format(time.RFC3339),
		AccountId:   kb.RandomUUID(),
		TenantId:    kb.RandomUUID(),
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

	paymentTransactionInfoPlugin, err := server.PurchasePayment(nil, &request)
	kb.AssertOk(t, err)
	kb.AssertEquals(t, pbp.PaymentTransactionInfoPlugin_PURCHASE, paymentTransactionInfoPlugin.GetTransactionType())
	kb.AssertEquals(t, pbp.PaymentTransactionInfoPlugin_PROCESSED, paymentTransactionInfoPlugin.GetStatus)
	kb.AssertEquals(t, request.GetKbPaymentId(), paymentTransactionInfoPlugin.KbPaymentId)
	kb.AssertEquals(t, request.GetKbTransactionId(), paymentTransactionInfoPlugin.KbTransactionPaymentId)

	mockServer := &MockPaymentPluginApi_GetPaymentInfoServer{}
	err = server.GetPaymentInfo(&request, mockServer)
	kb.AssertOk(t, err)
	kb.Assert(t, len(mockServer.tx) == 1, "Wrong number of tx")
	for _, e := range mockServer.tx {
		kb.AssertEquals(t, request.GetKbPaymentId(), e.KbPaymentId)
		kb.AssertEquals(t, request.GetKbTransactionId(), e.KbTransactionPaymentId)
	}
}

func TestAuthCaptureRefund(t *testing.T) {
	stripe.LogLevel = 3
	stripe.Key = os.Getenv("STRIPE_SECRET_KEY")
	stripeToken := os.Getenv("STRIPE_TOKEN")
	stripeCustomerId := os.Getenv("STRIPE_CUSTOMER_ID")

	context := &pbc.CallContext{
		CreatedDate: time.Now().In(time.UTC).Format(time.RFC3339),
		AccountId:   kb.RandomUUID(),
		TenantId:    kb.RandomUUID(),
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

	authPaymentTransactionInfoPlugin, err := server.AuthorizePayment(nil, &request)
	kb.AssertOk(t, err)
	kb.AssertEquals(t, pbp.PaymentTransactionInfoPlugin_AUTHORIZE, authPaymentTransactionInfoPlugin.GetTransactionType())
	kb.AssertEquals(t, pbp.PaymentTransactionInfoPlugin_PROCESSED, authPaymentTransactionInfoPlugin.GetStatus)
	kb.AssertEquals(t, request.GetKbPaymentId(), authPaymentTransactionInfoPlugin.KbPaymentId)
	kb.AssertEquals(t, request.GetKbTransactionId(), authPaymentTransactionInfoPlugin.KbTransactionPaymentId)

	mockServer := &MockPaymentPluginApi_GetPaymentInfoServer{}
	err = server.GetPaymentInfo(&request, mockServer)
	kb.AssertOk(t, err)
	kb.Assert(t, len(mockServer.tx) == 1, "Wrong number of tx")
	kb.AssertEquals(t, authPaymentTransactionInfoPlugin.GetKbPaymentId(), mockServer.tx[0].KbPaymentId)
	kb.AssertEquals(t, authPaymentTransactionInfoPlugin.GetKbTransactionPaymentId(), mockServer.tx[0].KbTransactionPaymentId)

	request.KbTransactionId = kb.RandomUUID()
	capturePaymentTransactionInfoPlugin, err := server.CapturePayment(nil, &request)
	kb.AssertOk(t, err)
	kb.AssertEquals(t, pbp.PaymentTransactionInfoPlugin_CAPTURE, capturePaymentTransactionInfoPlugin.GetTransactionType())
	kb.AssertEquals(t, pbp.PaymentTransactionInfoPlugin_PROCESSED, capturePaymentTransactionInfoPlugin.GetStatus)
	kb.AssertEquals(t, request.GetKbPaymentId(), capturePaymentTransactionInfoPlugin.KbPaymentId)
	kb.AssertEquals(t, request.GetKbTransactionId(), capturePaymentTransactionInfoPlugin.KbTransactionPaymentId)

	mockServer = &MockPaymentPluginApi_GetPaymentInfoServer{}
	err = server.GetPaymentInfo(&request, mockServer)
	kb.AssertOk(t, err)
	kb.Assert(t, len(mockServer.tx) == 2, "Wrong number of tx")
	kb.AssertEquals(t, authPaymentTransactionInfoPlugin.GetKbPaymentId(), mockServer.tx[0].KbPaymentId)
	kb.AssertEquals(t, authPaymentTransactionInfoPlugin.GetKbTransactionPaymentId(), mockServer.tx[0].KbTransactionPaymentId)
	kb.AssertEquals(t, capturePaymentTransactionInfoPlugin.GetKbPaymentId(), mockServer.tx[1].KbPaymentId)
	kb.AssertEquals(t, capturePaymentTransactionInfoPlugin.GetKbTransactionPaymentId(), mockServer.tx[1].KbTransactionPaymentId)

	request.KbTransactionId = kb.RandomUUID()
	refundPaymentTransactionInfoPlugin, err := server.RefundPayment(nil, &request)
	kb.AssertOk(t, err)
	kb.AssertEquals(t, pbp.PaymentTransactionInfoPlugin_REFUND, refundPaymentTransactionInfoPlugin.GetTransactionType())
	kb.AssertEquals(t, pbp.PaymentTransactionInfoPlugin_PROCESSED, refundPaymentTransactionInfoPlugin.GetStatus)
	kb.AssertEquals(t, request.GetKbPaymentId(), refundPaymentTransactionInfoPlugin.KbPaymentId)
	kb.AssertEquals(t, request.GetKbTransactionId(), refundPaymentTransactionInfoPlugin.KbTransactionPaymentId)

	mockServer = &MockPaymentPluginApi_GetPaymentInfoServer{}
	err = server.GetPaymentInfo(&request, mockServer)
	kb.AssertOk(t, err)
	kb.Assert(t, len(mockServer.tx) == 3, "Wrong number of tx")
	kb.AssertEquals(t, authPaymentTransactionInfoPlugin.GetKbPaymentId(), mockServer.tx[0].KbPaymentId)
	kb.AssertEquals(t, authPaymentTransactionInfoPlugin.GetKbTransactionPaymentId(), mockServer.tx[0].KbTransactionPaymentId)
	kb.AssertEquals(t, capturePaymentTransactionInfoPlugin.GetKbPaymentId(), mockServer.tx[1].KbPaymentId)
	kb.AssertEquals(t, capturePaymentTransactionInfoPlugin.GetKbTransactionPaymentId(), mockServer.tx[1].KbTransactionPaymentId)
	kb.AssertEquals(t, refundPaymentTransactionInfoPlugin.GetKbPaymentId(), mockServer.tx[2].KbPaymentId)
	kb.AssertEquals(t, refundPaymentTransactionInfoPlugin.GetKbTransactionPaymentId(), mockServer.tx[2].KbTransactionPaymentId)
}
