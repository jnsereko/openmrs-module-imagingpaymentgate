/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 */
package org.openmrs.module.imagingpaymentgate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.openmrs.api.context.Context;
import org.openmrs.api.db.hibernate.DbSessionFactory;

/**
 * Reads the configuration and payment state with plain SQL on the current session's connection.
 * <p>
 * Why not the services: the gate also runs inside a Hibernate save (see
 * {@link ImagingPaymentGateInterceptor}), where an HQL query or a service call would trigger an
 * auto-flush in the middle of the flush that called it. JDBC on the same connection does not flush,
 * and still sees this transaction's own writes.
 * <p>
 * Why not a FHIR search for the Task: OpenMRS's FHIR2 module silently ignores search parameters it
 * does not support, which once turned "is this order paid" into "is anything paid" (one paid invoice
 * authorised every imaging order). The query below names the order explicitly; there is nothing to
 * ignore.
 */
public class JdbcGateStore implements GateStore {

	/** Runs SQL on a connection the caller does not own and must not close. */
	public interface JdbcRunner {

		<T> T run(ConnectionWork<T> work);
	}

	public interface ConnectionWork<T> {

		T execute(Connection connection) throws SQLException;
	}

	/** The Task the Odoo bridge sets to accepted once the order's invoice line is paid. */
	static final String PAID_SQL = "SELECT COUNT(*) FROM fhir_task t"
	        + " JOIN fhir_task_based_on_reference b ON b.task_id = t.task_id"
	        + " JOIN fhir_reference r ON r.reference_id = b.reference_id"
	        + " WHERE r.target_uuid = ? AND r.target_type = 'ServiceRequest'"
	        + " AND t.status = 'ACCEPTED' AND t.retired = 0";

	static final String GLOBAL_PROPERTIES_SQL = "SELECT property, property_value FROM global_property"
	        + " WHERE property IN (?, ?, ?, ?)";

	/** Retired members stay gated: an order placed before the concept was retired can still be reported. */
	static final String SET_MEMBERS_SQL = "SELECT m.uuid FROM concept_set cs"
	        + " JOIN concept s ON s.concept_id = cs.concept_set" + " JOIN concept m ON m.concept_id = cs.concept_id"
	        + " WHERE s.uuid = ?";

	private final JdbcRunner runner;

	public JdbcGateStore(JdbcRunner runner) {
		this.runner = runner;
	}

	/** The current OpenMRS Hibernate session's connection. */
	public static JdbcGateStore onCurrentSession() {
		return new JdbcGateStore(new JdbcRunner() {

			@Override
			public <T> T run(final ConnectionWork<T> work) {
				DbSessionFactory sessionFactory = Context.getRegisteredComponent("dbSessionFactory",
				    DbSessionFactory.class);
				return sessionFactory.getCurrentSession().doReturningWork(new org.hibernate.jdbc.ReturningWork<T>() {

					@Override
					public T execute(Connection connection) throws SQLException {
						return work.execute(connection);
					}
				});
			}
		});
	}

	@Override
	public GateConfig loadConfig() {
		return runner.run(new ConnectionWork<GateConfig>() {

			@Override
			public GateConfig execute(Connection connection) throws SQLException {
				Map<String, String> values = new HashMap<String, String>();
				try (PreparedStatement statement = connection.prepareStatement(GLOBAL_PROPERTIES_SQL)) {
					statement.setString(1, ImagingPaymentGateConstants.GP_ENABLED);
					statement.setString(2, ImagingPaymentGateConstants.GP_CONCEPT_SET_UUIDS);
					statement.setString(3, ImagingPaymentGateConstants.GP_CONCEPT_UUIDS);
					statement.setString(4, ImagingPaymentGateConstants.GP_ORDER_TYPE_UUIDS);
					try (ResultSet rows = statement.executeQuery()) {
						while (rows.next()) {
							values.put(rows.getString(1), rows.getString(2));
						}
					}
				}
				Set<String> concepts = new LinkedHashSet<String>(
				        GateConfig.parseList(values.get(ImagingPaymentGateConstants.GP_CONCEPT_UUIDS)));
				for (String setUuid : GateConfig.parseList(values.get(ImagingPaymentGateConstants.GP_CONCEPT_SET_UUIDS))) {
					concepts.addAll(setMembers(connection, setUuid));
				}
				return new GateConfig(GateConfig.parseEnabled(values.get(ImagingPaymentGateConstants.GP_ENABLED)),
				        concepts, GateConfig.parseList(values.get(ImagingPaymentGateConstants.GP_ORDER_TYPE_UUIDS)));
			}
		});
	}

	@Override
	public boolean isPaid(final String orderUuid) {
		return runner.run(new ConnectionWork<Boolean>() {

			@Override
			public Boolean execute(Connection connection) throws SQLException {
				try (PreparedStatement statement = connection.prepareStatement(PAID_SQL)) {
					statement.setString(1, orderUuid);
					try (ResultSet rows = statement.executeQuery()) {
						return rows.next() && rows.getInt(1) > 0;
					}
				}
			}
		});
	}

	private static Set<String> setMembers(Connection connection, String setUuid) throws SQLException {
		Set<String> members = new LinkedHashSet<String>();
		try (PreparedStatement statement = connection.prepareStatement(SET_MEMBERS_SQL)) {
			statement.setString(1, setUuid);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					members.add(rows.getString(1));
				}
			}
		}
		return members;
	}
}
