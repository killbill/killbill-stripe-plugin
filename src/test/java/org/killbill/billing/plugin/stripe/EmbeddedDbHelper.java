/*
 * Copyright 2020-2020 Equinix, Inc
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

import java.io.IOException;
import java.sql.SQLException;

import org.killbill.billing.platform.test.PlatformDBTestingHelper;
import org.killbill.billing.plugin.TestUtils;
import org.killbill.commons.embeddeddb.EmbeddedDB;

import org.killbill.billing.plugin.stripe.dao.StripeDao;

public class EmbeddedDbHelper {

    private static final String DDL_FILE_NAME = "ddl.sql";

    private static final EmbeddedDbHelper INSTANCE = new EmbeddedDbHelper();
    private EmbeddedDB embeddedDB;

    public static EmbeddedDbHelper instance() {
        return INSTANCE;
    }

    public void startDb() throws Exception {
        System.setProperty("org.jooq.no-logo", "true");

        embeddedDB = PlatformDBTestingHelper.get().getInstance();
        embeddedDB.initialize();
        embeddedDB.start();

        final String databaseSpecificDDL = "ddl-" + embeddedDB.getDBEngine().name().toLowerCase() + ".sql";
        try {
            embeddedDB.executeScript(TestUtils.toString(databaseSpecificDDL));
        } catch (final IllegalArgumentException e) {
            // Ignore, no engine specific DDL
        }

        final String ddl = TestUtils.toString(DDL_FILE_NAME);
        embeddedDB.executeScript(ddl);
        embeddedDB.refreshTableNames();
    }

    public StripeDao getStripeDao() throws IOException, SQLException {
        return new StripeDao(embeddedDB.getDataSource());
    }

    public void resetDB() throws Exception {
        embeddedDB.cleanupAllTables();
    }

    public void stopDB() throws Exception {
        embeddedDB.stop();
    }
}