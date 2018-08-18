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

package main

import (
	pbr "github.com/killbill/killbill-rpc/go/api/killbill/registration"
	pbp "github.com/killbill/killbill-rpc/go/api/plugin/payment"

	"./api"

	"golang.org/x/net/context"

	"net"
	"flag"

	"github.com/stripe/stripe-go"
	"github.com/golang/glog"
	"time"
	"google.golang.org/grpc"
)

func startRPCServer(network, address string, serverStarted, exit chan interface{}) {
	var signal interface{}
	defer func() {
		exit <- signal
	}()

	lis, err := net.Listen(network, address)
	if err != nil {
		glog.Fatalf("failed to listen: %v", err)
	}

	var opts []grpc.ServerOption
	grpcServer := grpc.NewServer(opts...)
	pbp.RegisterPaymentPluginApiServer(grpcServer, api.PaymentPluginApiServer{})

	// Best we can do but technically there is a small race condition
	serverStarted <- signal

	// Blocking call
	grpcServer.Serve(lis)
}

func registerPlugin(network, address, killbillAddr string, serverStarted chan interface{}) {
	<-serverStarted

	var opts []grpc.DialOption

	opts = append(opts, grpc.WithInsecure())
	conn, err := grpc.Dial(killbillAddr, opts...)
	if err != nil {
		glog.Fatalf("fail to dial: %v", err)
	}
	defer conn.Close()
	client := pbr.NewPluginRegistrationApiClient(conn)

	var pluginTypes = []pbr.RegistrationRequest_PluginType{pbr.RegistrationRequest_NOTIFICATION, pbr.RegistrationRequest_PAYMENT}
	in := &pbr.RegistrationRequest{
		Key:      "killbill-stripe",
		Type:     pluginTypes,
		Version:  "7.0.0",
		Language: "GO",
		Endpoint: address,
	}

	glog.Infof("Plugin registration key=%s, type=%s, version=%s, language=%s)", in.Key, in.Type, in.Version, in.Language)
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	response, err := client.Register(ctx, in)
	if err != nil {
		glog.Fatalf("%v.Register(_) = _, %v: ", client, err)
	}
	if response.Success {
		glog.Info("Plugin registration successful")
	} else {
		glog.Warningf("Plugin registration unsuccessful: %s", response.Err)
	}
}

func main() {
	networkPtr := flag.String("network", "tcp4", "Network to use for server: tcp, tcp4, tcp6, unix or unixpacket")
	addressPtr := flag.String("address", "localhost:50051", "Address to bind to")
	killbillAddr := flag.String("kb_address", "localhost:21345", "Kill Bill RPC address")
	apiSecretKeyPtr := flag.String("api_secret_key", "", "Stripe API secret key")
	flag.Parse()

	if *apiSecretKeyPtr == "" {
		glog.Fatalf("--api_secret_key needs to be set")
	} else {
		stripe.Key = *apiSecretKeyPtr
	}

	serverStarted := make(chan interface{})
	exit := make(chan interface{})

	// Start RPC server prior we register and make sure it is running prior we start the registration
	go startRPCServer(*networkPtr, *addressPtr, serverStarted, exit)

	// Registration protocol
	registerPlugin(*networkPtr, *addressPtr, *killbillAddr, serverStarted)

	// Block until gRPC server is stopped
	<-exit
}
