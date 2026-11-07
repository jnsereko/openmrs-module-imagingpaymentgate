/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 */
package org.openmrs.module.imagingpaymentgate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Runs the store's real SQL on the metadatamapping columns emrapi writes. */
public class CoreVisitCloseStoreTest {

	private static final String ADMISSION = "e22e39fd-0000-0000-0000-000000000001";

	private static final String DISCHARGE = "181820aa-0000-0000-0000-000000000002";

	private Connection connection;

	private CoreVisitCloseStore store;

	@Before
	public void setUp() throws SQLException {
		connection = DriverManager.getConnection("jdbc:h2:mem:visits;MODE=MySQL", "sa", "");
		exec("CREATE TABLE metadatamapping_metadata_term_mapping (metadata_term_mapping_id INT PRIMARY KEY,"
		        + " code VARCHAR(255), metadata_uuid VARCHAR(38), retired TINYINT)",
		    "INSERT INTO metadatamapping_metadata_term_mapping VALUES (1, 'emr.admissionEncounterType', '"
		            + ADMISSION + "', 0)",
		    "INSERT INTO metadatamapping_metadata_term_mapping VALUES (2, 'emr.exitFromInpatientEncounterType', '"
		            + DISCHARGE + "', 0)",
		    // emrapi creates its terms unmapped; an unmapped or retired term counts for nothing.
		    "INSERT INTO metadatamapping_metadata_term_mapping VALUES (3, 'emr.admissionEncounterType', NULL, 0)",
		    "INSERT INTO metadatamapping_metadata_term_mapping VALUES (4, 'emr.exitFromInpatientEncounterType',"
		            + " 'retired-0000', 1)");
		store = new CoreVisitCloseStore(new JdbcGateStore.JdbcRunner() {

			@Override
			public <T> T run(JdbcGateStore.ConnectionWork<T> work) {
				try {
					return work.execute(connection);
				}
				catch (SQLException e) {
					throw new IllegalStateException(e);
				}
			}
		});
	}

	@After
	public void tearDown() throws SQLException {
		exec("DROP ALL OBJECTS");
		connection.close();
	}

	@Test
	public void readsTheAdtEncounterTypesEmrapiMaps() {
		assertEquals(Collections.singleton(ADMISSION), store.getAdmissionEncounterTypeUuids());
		assertEquals(Collections.singleton(DISCHARGE), store.getDischargeEncounterTypeUuids());
	}

	@Test
	public void missingTable_readsAsNothingMapped() throws SQLException {
		exec("DROP TABLE metadatamapping_metadata_term_mapping");

		assertTrue(store.getAdmissionEncounterTypeUuids().isEmpty());
	}

	private void exec(String... statements) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			for (String sql : statements) {
				statement.execute(sql);
			}
		}
	}
}
