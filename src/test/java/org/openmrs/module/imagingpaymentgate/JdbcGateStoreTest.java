/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 */
package org.openmrs.module.imagingpaymentgate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Runs the store's real SQL against the columns FHIR2 and core use (only the ones it reads), so the
 * payment query is proven to be scoped to one order.
 */
public class JdbcGateStoreTest {

	private static final String PAID_ORDER = "11111111-1111-1111-1111-111111111111";

	private static final String UNPAID_ORDER = "22222222-2222-2222-2222-222222222222";

	private static final String REJECTED_ORDER = "33333333-3333-3333-3333-333333333333";

	private static final String NO_TASK_ORDER = "44444444-4444-4444-4444-444444444444";

	private Connection connection;

	private JdbcGateStore store;

	@Before
	public void setUp() throws SQLException {
		connection = DriverManager.getConnection("jdbc:h2:mem:gate;MODE=MySQL", "sa", "");
		exec("CREATE TABLE fhir_task (task_id INT PRIMARY KEY, status VARCHAR(255), retired TINYINT)",
		    "CREATE TABLE fhir_reference (reference_id INT PRIMARY KEY, target_type VARCHAR(255),"
		            + " reference VARCHAR(255), target_uuid CHAR(38))",
		    "CREATE TABLE fhir_task_based_on_reference (task_id INT, reference_id INT PRIMARY KEY)",
		    "CREATE TABLE global_property (property VARCHAR(255) PRIMARY KEY, property_value TEXT)",
		    "CREATE TABLE concept (concept_id INT PRIMARY KEY, uuid CHAR(38))",
		    "CREATE TABLE concept_set (concept_id INT, concept_set INT)");
		task(1, "ACCEPTED", 0, PAID_ORDER);
		task(2, "REQUESTED", 0, UNPAID_ORDER);
		task(3, "REJECTED", 0, REJECTED_ORDER);
		// A paid Task for another order: the case that once authorised every order.
		task(4, "ACCEPTED", 0, "55555555-5555-5555-5555-555555555555");
		// A retired accepted Task does not count.
		task(5, "ACCEPTED", 1, UNPAID_ORDER);

		store = new JdbcGateStore(new JdbcGateStore.JdbcRunner() {

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
	public void isPaid_onlyForThisOrdersAcceptedTask() {
		assertTrue(store.isPaid(PAID_ORDER));
		assertFalse(store.isPaid(UNPAID_ORDER));
		assertFalse(store.isPaid(REJECTED_ORDER));
		assertFalse(store.isPaid(NO_TASK_ORDER));
	}

	@Test
	public void loadConfig_resolvesConceptSetMembersAndLists() throws SQLException {
		exec("INSERT INTO concept VALUES (1, 'set-uuid'), (2, 'xray-uuid'), (3, 'echo-uuid'), (4, 'other-uuid')",
		    "INSERT INTO concept_set VALUES (2, 1), (3, 1)",
		    "INSERT INTO global_property VALUES ('imagingpaymentgate.enabled', 'true'),"
		            + " ('imagingpaymentgate.conceptSetUuids', 'set-uuid'),"
		            + " ('imagingpaymentgate.conceptUuids', 'other-uuid'),"
		            + " ('imagingpaymentgate.orderTypeUuids', 'radiology-type')");

		GateConfig config = store.loadConfig();

		assertTrue(config.isEnabled());
		assertEquals(new HashSet<String>(Arrays.asList("other-uuid", "xray-uuid", "echo-uuid")),
		    config.getConceptUuids());
		assertEquals(new HashSet<String>(Arrays.asList("radiology-type")), config.getOrderTypeUuids());
	}

	@Test
	public void loadConfig_withNoPropertiesIsEnabledAndEmpty() {
		GateConfig config = store.loadConfig();

		assertTrue(config.isEnabled());
		assertTrue(config.isEmpty());
	}

	private void task(int id, String status, int retired, String orderUuid) throws SQLException {
		exec("INSERT INTO fhir_task VALUES (" + id + ", '" + status + "', " + retired + ")",
		    "INSERT INTO fhir_reference VALUES (" + id + ", 'ServiceRequest', 'ServiceRequest/" + orderUuid + "', '"
		            + orderUuid + "')",
		    "INSERT INTO fhir_task_based_on_reference VALUES (" + id + ", " + id + ")");
	}

	private void exec(String... statements) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			for (String sql : statements) {
				statement.execute(sql);
			}
		}
	}
}
