/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 */
package org.openmrs.module.imagingpaymentgate;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.openmrs.Visit;
import org.openmrs.VisitType;

/** What {@link AdmittedVisits} reads and writes; {@link CoreVisitCloseStore} is the OpenMRS one. */
public interface VisitCloseStore {

	/** {@code imagingpaymentgate.keepAdmittedVisitsOpen}; missing or blank means true. */
	boolean isKeepAdmittedVisitsOpen();

	/** The visit types named in {@code visits.autoCloseVisitType}, matched as core matches them. */
	List<VisitType> getVisitTypesToStop();

	/** Core's {@code VisitDAO.getNextVisit}: the next open visit, by id, of these types started by then. */
	Visit getNextVisit(Visit previousVisit, Collection<VisitType> visitTypes, Date maximumStartDate);

	void saveVisit(Visit visit);

	void flushAndClear();

	Set<String> getAdmissionEncounterTypeUuids();

	Set<String> getDischargeEncounterTypeUuids();
}
