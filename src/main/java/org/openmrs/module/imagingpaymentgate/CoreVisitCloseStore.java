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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.VisitDAO;
import org.openmrs.util.OpenmrsConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads and saves visits through core's own {@code VisitDAO}, as {@code VisitServiceImpl.stopVisits}
 * does, so a visit this run closes is written exactly as core would have written it. The ADT
 * encounter types are emrapi's metadata mappings, read with SQL so this module needs neither emrapi
 * nor metadatamapping on its classpath.
 */
public class CoreVisitCloseStore implements VisitCloseStore {

	private static final Logger log = LoggerFactory.getLogger(CoreVisitCloseStore.class);

	static final String ADMISSION_TERM = "emr.admissionEncounterType";

	static final String DISCHARGE_TERM = "emr.exitFromInpatientEncounterType";

	static final String TERM_SQL = "SELECT metadata_uuid FROM metadatamapping_metadata_term_mapping"
	        + " WHERE code = ? AND retired = 0 AND metadata_uuid IS NOT NULL";

	private final JdbcGateStore.JdbcRunner runner;

	public CoreVisitCloseStore(JdbcGateStore.JdbcRunner runner) {
		this.runner = runner;
	}

	public static CoreVisitCloseStore onCurrentSession() {
		return new CoreVisitCloseStore(JdbcGateStore.currentSession());
	}

	@Override
	public boolean isKeepAdmittedVisitsOpen() {
		return GateConfig.parseEnabled(Context.getAdministrationService()
		        .getGlobalProperty(ImagingPaymentGateConstants.GP_KEEP_ADMITTED_VISITS_OPEN));
	}

	/** Core's VisitServiceImpl.getVisitTypesToStop: names, trimmed and case-insensitive. */
	@Override
	public List<VisitType> getVisitTypesToStop() {
		List<VisitType> types = new ArrayList<VisitType>();
		Set<String> names = new LinkedHashSet<String>();
		for (String name : GateConfig.parseList(
		    Context.getAdministrationService().getGlobalProperty(OpenmrsConstants.GP_VISIT_TYPES_TO_AUTO_CLOSE))) {
			names.add(name.toLowerCase());
		}
		if (names.isEmpty()) {
			return types;
		}
		for (VisitType visitType : Context.getVisitService().getAllVisitTypes()) {
			if (names.contains(visitType.getName().toLowerCase())) {
				types.add(visitType);
			}
		}
		return types;
	}

	@Override
	public Visit getNextVisit(Visit previousVisit, Collection<VisitType> visitTypes, Date maximumStartDate) {
		return dao().getNextVisit(previousVisit, visitTypes, maximumStartDate);
	}

	@Override
	public void saveVisit(Visit visit) {
		dao().saveVisit(visit);
	}

	@Override
	public void flushAndClear() {
		Context.flushSession();
		Context.clearSession();
	}

	@Override
	public Set<String> getAdmissionEncounterTypeUuids() {
		return term(ADMISSION_TERM);
	}

	@Override
	public Set<String> getDischargeEncounterTypeUuids() {
		return term(DISCHARGE_TERM);
	}

	private Set<String> term(final String code) {
		try {
			return runner.run(new JdbcGateStore.ConnectionWork<Set<String>>() {

				@Override
				public Set<String> execute(Connection connection) throws SQLException {
					Set<String> uuids = new LinkedHashSet<String>();
					try (PreparedStatement statement = connection.prepareStatement(TERM_SQL)) {
						statement.setString(1, code);
						try (ResultSet rows = statement.executeQuery()) {
							while (rows.next()) {
								uuids.add(rows.getString(1));
							}
						}
					}
					return uuids;
				}
			});
		}
		catch (RuntimeException e) {
			log.warn("Could not read the emrapi mapping {}", code, e);
			return new LinkedHashSet<String>();
		}
	}

	private static VisitDAO dao() {
		return Context.getRegisteredComponent("visitDAO", VisitDAO.class);
	}
}
