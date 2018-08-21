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

// Package api implements the Kill Bill PaymentPluginApi.
package api

import (
	pba "github.com/killbill/killbill-rpc/go/api/killbill/account"
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
	"github.com/stripe/stripe-go/customer"
	"google.golang.org/grpc"
	"github.com/golang/glog"
)

var (
	stripeDb dao.StripeDB
)

func init() {
	var err error

	db, err := sql.Open("mysql", "root:root@tcp(127.0.0.1:3306)/killbill_go")
	if err != nil {
		panic(err)
	}

	stripeDb = dao.StripeDB{
		DB: db,
	}

	// TODO destructor?
	//defer db.Close()
}

// PaymentPluginAPIServer implements PaymentPluginApi.
type PaymentPluginAPIServer struct{}

// AuthorizePayment implements PaymentPluginApi#AuthorizePayment.
func (m PaymentPluginAPIServer) AuthorizePayment(ctx context.Context, req *pbp.PaymentRequest) (*pbp.PaymentTransactionInfoPlugin, error) {
	return stripeCharge(req, pbp.PaymentTransactionInfoPlugin_AUTHORIZE)
}

// PurchasePayment implements PaymentPluginApi#PurchasePayment.
func (m PaymentPluginAPIServer) PurchasePayment(ctx context.Context, req *pbp.PaymentRequest) (*pbp.PaymentTransactionInfoPlugin, error) {
	return stripeCharge(req, pbp.PaymentTransactionInfoPlugin_PURCHASE)
}

func stripeCharge(req *pbp.PaymentRequest, transactionType pbp.PaymentTransactionInfoPlugin_TransactionType) (*pbp.PaymentTransactionInfoPlugin, error) {
	var ch *stripe.Charge
	var err error

	stripeSource, err := stripeDb.GetStripeSource(*req)
	if err != nil {
		ch = &stripe.Charge{
			Status: "canceled",
		}
	} else {
		capture := transactionType == pbp.PaymentTransactionInfoPlugin_PURCHASE
		i, _ := strconv.ParseFloat(req.Amount, 32) // TODO Joda-Money?
		i = i * 100
		cents := int64(i)

		chargeParams := &stripe.ChargeParams{
			Amount:         &cents,
			ApplicationFee: nil,
			Capture:        &capture,
			Currency:       &req.Currency,
			Customer:       &stripeSource.StripeCustomerID,
			Source: &stripe.SourceParams{
				Token: &stripeSource.StripeID,
			},
		}
		ch, err = charge.New(chargeParams)
	}

	stripeError := ""
	if err != nil {
		stripeError = err.Error()
	}

	kbCreatedDate, err := time.Parse("2006-01-02T15:04:05Z", req.GetContext().GetCreatedDate())
	if err != nil {
		return nil, err
	}

	stripeResponse := dao.StripeTransaction{
		StripeObject: dao.StripeObject{
			CreatedAt:   kbCreatedDate,
			KBAccountID: req.GetKbAccountId(),
			KBTenantID:  req.GetContext().GetTenantId(),
		},
		KbPaymentID:            req.GetKbPaymentId(),
		KbPaymentTransactionID: req.GetKbTransactionId(),
		KbTransactionType:      transactionType.String(),
		StripeID:               ch.ID,
		StripeAmount:           ch.Amount,
		StripeCurrency:         string(ch.Currency),
		StripeStatus:           string(ch.Status),
		StripeError:            stripeError,
	}
	err = stripeDb.SaveTransaction(&stripeResponse)
	if err != nil {
		return nil, err
	}

	return buildPaymentTransactionInfoPlugin(stripeResponse, err), err
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

// CapturePayment implements PaymentPluginApi#CapturePayment.
func (m PaymentPluginAPIServer) CapturePayment(ctx context.Context, req *pbp.PaymentRequest) (*pbp.PaymentTransactionInfoPlugin, error) {
	var ch *stripe.Charge
	var err error

	tx, err := stripeDb.GetTransactions(*req)
	if err != nil {
		ch = &stripe.Charge{
			Status: "canceled",
		}
	} else {
		stripeID := tx[len(tx)-1].StripeID // TODO Should we do any validation here?
		ch, err = charge.Capture(stripeID, nil)
	}

	stripeError := ""
	if err != nil {
		stripeError = err.Error()
	}

	kbCreatedDate, err := time.Parse("2006-01-02T15:04:05Z", req.GetContext().GetCreatedDate())
	if err != nil {
		return nil, err
	}

	stripeResponse := dao.StripeTransaction{
		StripeObject: dao.StripeObject{
			CreatedAt:   kbCreatedDate,
			KBAccountID: req.GetKbAccountId(),
			KBTenantID:  req.GetContext().GetTenantId(),
		},
		KbPaymentID:            req.GetKbPaymentId(),
		KbPaymentTransactionID: req.GetKbTransactionId(),
		KbTransactionType:      pbp.PaymentTransactionInfoPlugin_CAPTURE.String(),
		StripeID:               ch.ID,
		StripeAmount:           ch.Amount,
		StripeCurrency:         string(ch.Currency),
		StripeStatus:           string(ch.Status),
		StripeError:            stripeError,
	}
	err = stripeDb.SaveTransaction(&stripeResponse)
	if err != nil {
		return nil, err
	}

	return buildPaymentTransactionInfoPlugin(stripeResponse, err), err
}

// RefundPayment implements PaymentPluginApi#RefundPayment.
func (m PaymentPluginAPIServer) RefundPayment(ctx context.Context, req *pbp.PaymentRequest) (*pbp.PaymentTransactionInfoPlugin, error) {
	var ref *stripe.Refund
	var err error

	tx, err := stripeDb.GetTransactions(*req)
	if err != nil {
		ref = &stripe.Refund{
			Status: "canceled",
		}
	} else {
		stripeID := tx[len(tx)-1].StripeID // TODO Should we do any validation here?
		ref, err = refund.New(&stripe.RefundParams{
			Charge: &stripeID,
		})
	}

	stripeError := ""
	if err != nil {
		stripeError = err.Error()
	}

	kbCreatedDate, err := time.Parse("2006-01-02T15:04:05Z", req.GetContext().GetCreatedDate())
	if err != nil {
		return nil, err
	}

	stripeResponse := dao.StripeTransaction{
		StripeObject: dao.StripeObject{
			CreatedAt:   kbCreatedDate,
			KBAccountID: req.GetKbAccountId(),
			KBTenantID:  req.GetContext().GetTenantId(),
		},
		KbPaymentID:            req.GetKbPaymentId(),
		KbPaymentTransactionID: req.GetKbTransactionId(),
		KbTransactionType:      pbp.PaymentTransactionInfoPlugin_REFUND.String(),
		StripeID:               ref.ID,
		StripeAmount:           ref.Amount,
		StripeCurrency:         string(ref.Currency),
		StripeStatus:           string(ref.Status),
		StripeError:            stripeError,
	}
	err = stripeDb.SaveTransaction(&stripeResponse)
	if err != nil {
		return nil, err
	}

	return buildPaymentTransactionInfoPlugin(stripeResponse, err), err
}

func buildPaymentTransactionInfoPlugin(stripeTransaction dao.StripeTransaction, chErr error) *pbp.PaymentTransactionInfoPlugin {
	return &pbp.PaymentTransactionInfoPlugin{
		KbPaymentId:             stripeTransaction.KbPaymentID,
		KbTransactionPaymentId:  stripeTransaction.KbPaymentTransactionID,
		TransactionType:         kb.ToKbTransactionType(stripeTransaction.KbTransactionType),
		Amount:                  strconv.FormatInt(stripeTransaction.StripeAmount/100, 10), // TODO Joda-Money?
		Currency:                strings.ToUpper(stripeTransaction.StripeCurrency),
		CreatedDate:             stripeTransaction.CreatedAt.Format(time.RFC3339),
		EffectiveDate:           stripeTransaction.CreatedAt.Format(time.RFC3339),
		GetStatus:               toKbPaymentPluginStatus(stripeTransaction.StripeStatus, chErr),
		GatewayError:            stripeTransaction.StripeError,
		GatewayErrorCode:        "",
		FirstPaymentReferenceId: stripeTransaction.StripeID,
	}
}

// VoidPayment implements PaymentPluginApi#VoidPayment.
func (m PaymentPluginAPIServer) VoidPayment(ctx context.Context, req *pbp.PaymentRequest) (*pbp.PaymentTransactionInfoPlugin, error) {
	return unsupportedOperation(req, pbp.PaymentTransactionInfoPlugin_VOID)
}

// CreditPayment implements PaymentPluginApi#CreditPayment.
func (m PaymentPluginAPIServer) CreditPayment(ctx context.Context, req *pbp.PaymentRequest) (*pbp.PaymentTransactionInfoPlugin, error) {
	return unsupportedOperation(req, pbp.PaymentTransactionInfoPlugin_CREDIT)
}

func unsupportedOperation(req *pbp.PaymentRequest, transactionType pbp.PaymentTransactionInfoPlugin_TransactionType) (*pbp.PaymentTransactionInfoPlugin, error) {
	paymentErr := errors.New("Unsupported Stripe operation")

	kbCreatedDate, err := time.Parse("2006-01-02T15:04:05Z", req.GetContext().GetCreatedDate())
	if err != nil {
		return nil, err
	}

	stripeResponse := dao.StripeTransaction{
		StripeObject: dao.StripeObject{
			CreatedAt:   kbCreatedDate,
			KBAccountID: req.GetKbAccountId(),
			KBTenantID:  req.GetContext().GetTenantId(),
		},
		KbPaymentID:            req.GetKbPaymentId(),
		KbPaymentTransactionID: req.GetKbTransactionId(),
		KbTransactionType:      transactionType.String(),
		StripeStatus:           "canceled",
	}

	err = stripeDb.SaveTransaction(&stripeResponse)
	if err != nil {
		return nil, err
	}

	return buildPaymentTransactionInfoPlugin(stripeResponse, paymentErr), paymentErr
}

// GetPaymentInfo implements PaymentPluginApi#GetPaymentInfo.
func (m PaymentPluginAPIServer) GetPaymentInfo(req *pbp.PaymentRequest, s pbp.PaymentPluginApi_GetPaymentInfoServer) (error) {
	res, err := stripeDb.GetTransactions(*req)
	if err != nil {
		return err
	}

	for _, e := range res {
		paymentTransactionInfoPlugin := buildPaymentTransactionInfoPlugin(e, nil)
		s.Send(paymentTransactionInfoPlugin)
	}

	return nil
}

// AddPaymentMethod implements PaymentPluginApi#AddPaymentMethod.
func (m PaymentPluginAPIServer) AddPaymentMethod(ctx context.Context, req *pbp.PaymentRequest) (*pbp.PaymentMethodPlugin, error) {
	stripeToken := kb.FindPluginProperty2(req.GetProperties(), "stripeToken")
	if stripeToken == "" {
		// Backward compatibility
		stripeToken = kb.FindPluginProperty2(req.GetProperties(), "token")
	}

	stripeCustomerID := kb.FindPluginProperty2(req.GetProperties(), "stripeCustomerID")
	if stripeCustomerID == "" {
		// TODO Retrieve it from a previous tx first

		// Retrieve the Kill Bill account external key
		kbAccount := getKillBillAccount(req, "localhost:21345")

		// Create the Stripe customer
		params := &stripe.CustomerParams{
			Description: stripe.String(kbAccount.GetExternalKey()),
		}
		cus, err := customer.New(params)
		if err != nil {
			return nil, err
		}
		stripeCustomerID = cus.ID
	}

	params := &stripe.CardParams{
		Customer: &stripeCustomerID,
		Token:    &stripeToken,
	}
	c, err := card.New(params)
	if err != nil {
		return nil, err
	}

	kbCreatedDate, err := time.Parse("2006-01-02T15:04:05Z", req.GetContext().GetCreatedDate())
	if err != nil {
		return nil, err
	}

	stripeSource := dao.StripeSource{
		StripeObject: dao.StripeObject{
			CreatedAt:   kbCreatedDate,
			KBAccountID: req.GetKbAccountId(),
			KBTenantID:  req.GetContext().GetTenantId(),
		},
		KbPaymentMethodID: req.GetKbPaymentMethodId(),
		StripeID:          c.ID,
		StripeCustomerID:  stripeCustomerID,
	}
	err = stripeDb.SaveStripeSource(&stripeSource)
	if err != nil {
		return nil, err
	}

	return buildPaymentMethodPlugin(req, stripeSource), nil
}

func getKillBillAccount(req *pbp.PaymentRequest, killbillAddr string) (*pba.Account) {
	var opts []grpc.DialOption
	opts = append(opts, grpc.WithInsecure())
	conn, err := grpc.Dial(killbillAddr, opts...)
	if err != nil {
		glog.Warning("fail to dial: %v", err)
	}
	defer conn.Close()

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	kbClient := pba.NewAccountApiClient(conn)
	accountRequest := &pba.AccountRequest{
		Account: &pba.Account{
			AccountId: req.GetContext().GetAccountId(),
		},
		Context: req.GetContext(),
	}
	response, err := kbClient.GetAccount(ctx, accountRequest)
	if err != nil {
		glog.Warning("Unable to retrieve account: %v", err)
	}
	if response.BillingException != nil {
		glog.Warning("Unable to retrieve account: %v", response.BillingException)
		return nil
	} else {
		return response.Account
	}
}

// GetPaymentMethodDetail implements PaymentPluginApi#GetPaymentMethodDetail.
func (m PaymentPluginAPIServer) GetPaymentMethodDetail(ctx context.Context, req *pbp.PaymentRequest) (*pbp.PaymentMethodPlugin, error) {
	stripeSource, err := stripeDb.GetStripeSource(*req)
	if err != nil {
		return nil, err
	}

	return buildPaymentMethodPlugin(req, stripeSource), nil
}

func buildPaymentMethodPlugin(req *pbp.PaymentRequest, stripeSource dao.StripeSource) *pbp.PaymentMethodPlugin {
	return &pbp.PaymentMethodPlugin{
		KbPaymentMethodId:       req.KbPaymentMethodId,
		ExternalPaymentMethodId: stripeSource.StripeID,
		IsDefaultPaymentMethod:  false,
		Properties: []*pbp.PluginProperty{
			{
				Key:         "stripeCustomerId",
				Value:       stripeSource.StripeCustomerID,
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
