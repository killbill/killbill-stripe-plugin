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
	pb "../payment"

	"../dao"
	"golang.org/x/net/context"
	"github.com/stripe/stripe-go"
	"github.com/stripe/stripe-go/charge"
	"github.com/golang/protobuf/proto"
	"time"
	"strconv"
	"database/sql"
	"github.com/pkg/errors"
	"github.com/stripe/stripe-go/refund"
)

var (
	db *sql.DB
)

func init() {
	var err error

	db, err = sql.Open("mysql", "root:root@tcp(127.0.0.1:3306)/killbill_go")
	if err != nil {
		panic(err)
	}
	// TODO destructor?
	//defer db.Close()
}

type PaymentPluginApiServer struct{}

func (m *PaymentPluginApiServer) AuthorizePayment(ctx context.Context, req *pb.PaymentRequest) (*pb.PaymentTransactionInfoPlugin, error) {
	return stripeCharge(req, pb.PaymentTransactionInfoPlugin_AUTHORIZE)
}

func (m *PaymentPluginApiServer) PurchasePayment(ctx context.Context, req *pb.PaymentRequest) (*pb.PaymentTransactionInfoPlugin, error) {
	return stripeCharge(req, pb.PaymentTransactionInfoPlugin_PURCHASE)
}

func stripeCharge(req *pb.PaymentRequest, transactionType pb.PaymentTransactionInfoPlugin_TransactionType) (*pb.PaymentTransactionInfoPlugin, error) {
	var ch *stripe.Charge
	var err error

	sourceId, err := dao.GetStripeSourceId(*db, *req)
	if err != nil {
		ch = &stripe.Charge{
			Status: "canceled",
		}
	} else {
		capture := transactionType == pb.PaymentTransactionInfoPlugin_PURCHASE
		i, _ := strconv.ParseInt(req.Amount, 10, 64) // TODO Joda-Money?
		chargeParams := &stripe.ChargeParams{
			Capture:  &capture,
			Amount:   &i,
			Currency: &req.Currency,
		}
		chargeParams.SetSource(sourceId)

		ch, err = charge.New(chargeParams)
	}

	return dao.SaveTransaction(*db, *req, transactionType, dao.StripeResponse{
		ID:       ch.ID,
		Amount:   ch.Amount,
		Currency: string(ch.Currency),
		Status:   string(ch.Status),
	}, err)
}

func (m *PaymentPluginApiServer) CapturePayment(ctx context.Context, req *pb.PaymentRequest) (*pb.PaymentTransactionInfoPlugin, error) {
	var ch *stripe.Charge
	var err error

	tx, err := dao.GetTransactions(*db, *req)
	if err != nil {
		ch = &stripe.Charge{
			Status: "canceled",
		}
	} else {
		stripeId := tx[len(tx)-1].FirstPaymentReferenceId // TODO Should we do any validation here?
		ch, err = charge.Capture(stripeId, nil)
	}

	return dao.SaveTransaction(*db, *req, pb.PaymentTransactionInfoPlugin_CAPTURE, dao.StripeResponse{
		ID:       ch.ID,
		Amount:   ch.Amount,
		Currency: string(ch.Currency),
		Status:   string(ch.Status),
	}, err)
}

func (m *PaymentPluginApiServer) RefundPayment(ctx context.Context, req *pb.PaymentRequest) (*pb.PaymentTransactionInfoPlugin, error) {
	var ref *stripe.Refund
	var err error

	tx, err := dao.GetTransactions(*db, *req)
	if err != nil {
		ref = &stripe.Refund{
			Status: "canceled",
		}
	} else {
		stripeId := tx[len(tx)-1].FirstPaymentReferenceId // TODO Should we do any validation here?
		ref, err = refund.New(&stripe.RefundParams{
			Charge: &stripeId,
		})
	}

	return dao.SaveTransaction(*db, *req, pb.PaymentTransactionInfoPlugin_REFUND, dao.StripeResponse{
		ID:       ref.ID,
		Amount:   ref.Amount,
		Currency: string(ref.Currency),
		Status:   string(ref.Status),
	}, err)
}

func (m *PaymentPluginApiServer) VoidPayment(ctx context.Context, req *pb.PaymentRequest) (*pb.PaymentTransactionInfoPlugin, error) {
	return unsupportedOperation(req, pb.PaymentTransactionInfoPlugin_VOID)
}

func (m *PaymentPluginApiServer) CreditPayment(ctx context.Context, req *pb.PaymentRequest) (*pb.PaymentTransactionInfoPlugin, error) {
	return unsupportedOperation(req, pb.PaymentTransactionInfoPlugin_CREDIT)
}

func unsupportedOperation(req *pb.PaymentRequest, transactionType pb.PaymentTransactionInfoPlugin_TransactionType) (*pb.PaymentTransactionInfoPlugin, error) {
	err := errors.New("Unsupported Stripe operation")
	return dao.SaveTransaction(*db, *req, transactionType, dao.StripeResponse{
		Status: "canceled",
	}, err)
}

func (m *PaymentPluginApiServer) GetPaymentInfo(ctx context.Context, req *pb.PaymentRequest) (*pb.PaymentTransactionInfoPlugin, error) {
	return dao.GetTransactions(*db, req)
}

func (m *PaymentPluginApiServer) AddPaymentMethod(ctx context.Context, req *pb.PaymentRequest) (*pb.PaymentMethodPlugin, error) {
	stripeId := findPluginProperty(req.GetProperties(), "stripeId")

	_, err := dao.SaveStripeSourceId(*db, *req, stripeId)
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

// TODO Move to base framework
func findPluginProperty(properties []*pb.PluginProperty, key string) string {
	for _, prop := range properties {
		if key == prop.Key {
			return prop.GetValue()
		}
	}
	return ""
}

func (m *PaymentPluginApiServer) GetPaymentMethodDetail(ctx context.Context, req *pb.PaymentRequest) (*pb.PaymentMethodPlugin, error) {
	return dao.GetStripeSourceId(*db, *req)
}
