/*
 * Copyright 2014-2020 The Billing Project, LLC
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

package org.killbill.billing.plugin.stripe;

import java.util.Properties;

import org.killbill.billing.osgi.api.Healthcheck;
import org.killbill.billing.plugin.TestUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestStripeHealthcheck extends TestBase {

    @Test(groups = "slow")
    public void testHealthcheckNoTenant() {
        final StripeConfigPropertiesConfigurationHandler noConfigHandler = new StripeConfigPropertiesConfigurationHandler(StripeActivator.PLUGIN_NAME, killbillApi, TestUtils.buildLogService(), null);
        noConfigHandler.setDefaultConfigurable(new StripeConfigProperties(new Properties(), ""));
        final Healthcheck healthcheck = new StripeHealthcheck(noConfigHandler);
        Assert.assertTrue(healthcheck.getHealthStatus(null, null).isHealthy());
    }

    @Test(groups = "slow")
    public void testHealthcheck() {
        final Healthcheck healthcheck = new StripeHealthcheck(stripeConfigPropertiesConfigurationHandler);
        Assert.assertTrue(healthcheck.getHealthStatus(null, null).isHealthy());
    }
}
