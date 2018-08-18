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
	pbp "github.com/killbill/killbill-rpc/go/api/plugin/payment"
	kb "github.com/killbill/killbill-plugin-framework-go"

	"../dao"
	"golang.org/x/net/context"
	"github.com/stripe/stripe-go"
	"github.com/stripe/stripe-go/charge"
	"strconv"
	"database/sql"
	"github.com/pkg/errors"
	"github.com/stripe/stripe-go/refund"
	"time"
	"github.com/stripe/stripe-go/card"
	"strings"
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

func (m PaymentPluginApiServer) AuthorizePayment(ctx context.Context, req *pbp.PaymentRequest) (*pbp.PaymentTransactionInfoPlugin, error) {
	return stripeCharge(req, pbp.PaymentTransactionInfoPlugin_AUTHORIZE)
}

func (m PaymentPluginApiServer) PurchasePayment(ctx context.Context, req *pbp.PaymentRequest) (*pbp.PaymentTransactionInfoPlugin, error) {
	return stripeCharge(req, pbp.PaymentTransactionInfoPlugin_PURCHASE)
}

func stripeCharge(req *pbp.PaymentRequest, transactionType pbp.PaymentTransactionInfoPlugin_TransactionType) (*pbp.PaymentTransactionInfoPlugin, error) {
	var ch *stripe.Charge
	var err error

	stripeSource, err := dao.GetStripeSource(*db, *req)
	if err != nil {
		ch = &stripe.Charge{
			Status: "canceled",
		}
	} else {
		capture := transactionType == pbp.PaymentTransactionInfoPlugin_PURCHASE
		i, _ := strconv.ParseInt(req.Amount, 10, 64) // TODO Joda-Money?
		i = i * 100

		chargeParams := &stripe.ChargeParams{
			Amount:         &i,
			ApplicationFee: nil,
			Capture:        &capture,
			Currency:       &req.Currency,
			Customer:       &stripeSource.CustomerId,
			Source: &stripe.SourceParams{
				Token: &stripeSource.StripeId,
			},
		}
		ch, err = charge.New(chargeParams)
	}

	stripeResponse := dao.StripeResponse{
		StripeId: ch.ID,
		Amount:   ch.Amount,
		Currency: string(ch.Currency),
		Status:   string(ch.Status),
	}
	err = dao.SaveTransaction(*db, *req, transactionType, &stripeResponse, err)
	if err != nil {
		return nil, err
	}

	return buildPaymentTransactionInfoPlugin(req, transactionType, stripeResponse, err), err
}

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

func (m PaymentPluginApiServer) CapturePayment(ctx context.Context, req *pbp.PaymentRequest) (*pbp.PaymentTransactionInfoPlugin, error) {
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

	stripeResponse := dao.StripeResponse{
		StripeId: ch.ID,
		Amount:   ch.Amount,
		Currency: string(ch.Currency),
		Status:   string(ch.Status),
	}
	err = dao.SaveTransaction(*db, *req, pbp.PaymentTransactionInfoPlugin_CAPTURE, &stripeResponse, err)
	if err != nil {
		return nil, err
	}

	return buildPaymentTransactionInfoPlugin(req, pbp.PaymentTransactionInfoPlugin_CAPTURE, stripeResponse, err), err
}

func (m PaymentPluginApiServer) RefundPayment(ctx context.Context, req *pbp.PaymentRequest) (*pbp.PaymentTransactionInfoPlugin, error) {
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

	stripeResponse := dao.StripeResponse{
		StripeId: ref.ID,
		Amount:   ref.Amount,
		Currency: string(ref.Currency),
		Status:   string(ref.Status),
	}
	err = dao.SaveTransaction(*db, *req, pbp.PaymentTransactionInfoPlugin_REFUND, &stripeResponse, err)
	if err != nil {
		return nil, err
	}

	return buildPaymentTransactionInfoPlugin(req, pbp.PaymentTransactionInfoPlugin_REFUND, stripeResponse, err), err
}

func buildPaymentTransactionInfoPlugin(req *pbp.PaymentRequest, txType pbp.PaymentTransactionInfoPlugin_TransactionType, stripeResponse dao.StripeResponse, chErr error) *pbp.PaymentTransactionInfoPlugin {
	return &pbp.PaymentTransactionInfoPlugin{
		KbPaymentId:             req.GetKbPaymentId(),
		KbTransactionPaymentId:  req.GetKbTransactionId(),
		TransactionType:         txType,
		Amount:                  strconv.FormatInt(stripeResponse.Amount*100, 10), // TODO Joda-Money?
		Currency:                strings.ToUpper(stripeResponse.Currency),
		CreatedDate:             stripeResponse.CreatedAt.Format(time.RFC3339),
		EffectiveDate:           stripeResponse.CreatedAt.Format(time.RFC3339),
		GetStatus:               toKbPaymentPluginStatus(stripeResponse.Status, chErr),
		GatewayError:            stripeResponse.Error,
		GatewayErrorCode:        "",
		FirstPaymentReferenceId: stripeResponse.StripeId,
	}
}

func (m PaymentPluginApiServer) VoidPayment(ctx context.Context, req *pbp.PaymentRequest) (*pbp.PaymentTransactionInfoPlugin, error) {
	return unsupportedOperation(req, pbp.PaymentTransactionInfoPlugin_VOID)
}

func (m PaymentPluginApiServer) CreditPayment(ctx context.Context, req *pbp.PaymentRequest) (*pbp.PaymentTransactionInfoPlugin, error) {
	return unsupportedOperation(req, pbp.PaymentTransactionInfoPlugin_CREDIT)
}

func unsupportedOperation(req *pbp.PaymentRequest, transactionType pbp.PaymentTransactionInfoPlugin_TransactionType) (*pbp.PaymentTransactionInfoPlugin, error) {
	paymentErr := errors.New("Unsupported Stripe operation")
	stripeResponse := dao.StripeResponse{
		Status: "canceled",
	}

	err := dao.SaveTransaction(*db, *req, transactionType, &stripeResponse, paymentErr)
	if err != nil {
		return nil, err
	}

	return buildPaymentTransactionInfoPlugin(req, transactionType, stripeResponse, paymentErr), paymentErr
}

func (m PaymentPluginApiServer) GetPaymentInfo(req *pbp.PaymentRequest, s pbp.PaymentPluginApi_GetPaymentInfoServer) (error) {
	res, err := dao.GetTransactions(*db, *req)
	if err != nil {
		return err
	}

	for _, e := range res {
		s.Send(&e)
	}

	return nil
}

func (m PaymentPluginApiServer) AddPaymentMethod(ctx context.Context, req *pbp.PaymentRequest) (*pbp.PaymentMethodPlugin, error) {
	stripeCustomerId := kb.FindPluginProperty2(req.GetProperties(), "stripeCustomerId")
	stripeToken := kb.FindPluginProperty2(req.GetProperties(), "stripeToken")
	params := &stripe.CardParams{
		Customer: &stripeCustomerId,
		Token:    &stripeToken,
	}
	c, err := card.New(params)
	if err != nil {
		return nil, err
	}

	stripeSource := dao.StripeSource{
		StripeId:   c.ID,
		CustomerId: stripeCustomerId,
		CreatedAt:  time.Now().In(time.UTC), // TODO update customer in stripe instead and use source id created date
	}

	err = dao.SaveStripeSource(*db, *req, &stripeSource)
	if err != nil {
		return nil, err
	}

	return buildPaymentMethodPlugin(req, stripeSource), nil
}

func (m PaymentPluginApiServer) GetPaymentMethodDetail(ctx context.Context, req *pbp.PaymentRequest) (*pbp.PaymentMethodPlugin, error) {
	stripeSource, err := dao.GetStripeSource(*db, *req)
	if err != nil {
		return nil, err
	}

	return buildPaymentMethodPlugin(req, *stripeSource), nil
}

func buildPaymentMethodPlugin(req *pbp.PaymentRequest, stripeSource dao.StripeSource) *pbp.PaymentMethodPlugin {
	return &pbp.PaymentMethodPlugin{
		KbPaymentMethodId:       req.KbPaymentMethodId,
		ExternalPaymentMethodId: stripeSource.StripeId,
		IsDefaultPaymentMethod:  false,
		Properties: []*pbp.PluginProperty{
			{
				Key:         "stripeCustomerId",
				Value:       stripeSource.CustomerId,
				IsUpdatable: false,
			},
			{
				Key:         "stripePaymentMethodsId",
				Value:       strconv.FormatInt(stripeSource.ID, 10),
				IsUpdatable: false,
			},
		},
	}
}
