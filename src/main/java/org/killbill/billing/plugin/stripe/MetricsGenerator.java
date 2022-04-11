/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

import org.killbill.billing.osgi.libs.killbill.OSGIMetricRegistry;
import org.killbill.billing.osgi.libs.killbill.OSGIServiceNotAvailable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricsGenerator {

    private static final Logger logger = LoggerFactory.getLogger(MetricsGenerator.class);

    private final Thread thread;

    private volatile boolean stopMetrics;

    public MetricsGenerator(final OSGIMetricRegistry metricRegistry) {
        this.thread = new Thread(new Runnable() {
            public void run() {
                while (!stopMetrics) {
                    try {
                        Thread.sleep(1000L);
                    } catch (final InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        logger.info("MetricsGenerator shutting down...");
                        break;
                    }

                    try {
                        metricRegistry.getMetricRegistry().counter("stripe_plugin_counter").inc(1);
                    } catch (final OSGIServiceNotAvailable ignored) {
                        logger.warn("No MetricRegistry available");
                    }
                }
            }
        });
    }

    public void start() {
        thread.start();
    }

    public void stop() {
        stopMetrics = true;
    }
}
