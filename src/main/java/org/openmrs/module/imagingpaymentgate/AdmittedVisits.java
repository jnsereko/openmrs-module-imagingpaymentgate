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

import org.openmrs.Encounter;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core's nightly visit closing, minus the visits of admitted patients (UVL-EMR#246).
 * <p>
 * Core's Auto Close Visits Task calls {@code VisitService.stopVisits(Date)}, which stops every open
 * visit of a type in {@code visits.autoCloseVisitType} that started by then, whether or not the
 * patient is on a ward. An admission made on a General or Outpatient visit was therefore closed the
 * same night, with no discharge, and its stay could not be counted.
 * {@link #stopVisits(Date)} is the same loop as core's (same query, same types, one stop time for
 * the whole run, saved through core's DAO), except that a visit whose latest Admission or Discharge
 * encounter is an Admission is left open. Visits without an admission close exactly as before, at
 * the same time (#350).
 * <p>
 * Admission and Discharge are the encounter types emrapi maps to {@code emr.admissionEncounterType}
 * and {@code emr.exitFromInpatientEncounterType}, the ones the ward app writes. When neither can be
 * read, nothing counts as admitted and the run is core's, with a WARN.
 */
public class AdmittedVisits {

	private static final Logger log = LoggerFactory.getLogger(AdmittedVisits.class);

	/** Core flushes and clears the session every 50 visits; so does this. */
	static final int BATCH_SIZE = 50;

	private final VisitCloseStore store;

	public AdmittedVisits(VisitCloseStore store) {
		this.store = store;
	}

	public boolean isEnabled() {
		return store.isKeepAdmittedVisitsOpen();
	}

	/** Same contract as {@code VisitService.stopVisits(Date)}, admitted visits excepted. */
	public void stopVisits(Date maximumStartDate) {
		List<VisitType> visitTypes = store.getVisitTypesToStop();
		if (maximumStartDate == null) {
			maximumStartDate = new Date();
		}
		if (visitTypes.isEmpty()) {
			return;
		}
		Set<String> admissionTypes = store.getAdmissionEncounterTypeUuids();
		Set<String> dischargeTypes = store.getDischargeEncounterTypeUuids();
		if (admissionTypes.isEmpty()) {
			log.warn("No admission encounter type is mapped (emrapi term emr.admissionEncounterType):"
			        + " closing visits as core does, admitted patients included");
		}

		Date stopDate = new Date();
		int closed = 0;
		int kept = 0;
		int counter = 0;
		Visit visit = store.getNextVisit(null, visitTypes, maximumStartDate);
		while (visit != null) {
			if (isAdmitted(visit, admissionTypes, dischargeTypes)) {
				kept++;
				log.info("Visit {} left open: the patient is admitted and not discharged", visit.getUuid());
			} else {
				visit.setStopDatetime(stopDate);
				store.saveVisit(visit);
				closed++;
				if (counter++ > BATCH_SIZE) {
					store.flushAndClear();
					counter = 0;
				}
			}
			visit = store.getNextVisit(visit, visitTypes, maximumStartDate);
		}
		log.info("Auto close visits: {} closed, {} left open for admitted patients", closed, kept);
	}

	/**
	 * Whether the visit's latest non-voided Admission or Discharge encounter is an Admission. A
	 * Transfer moves the patient between wards and changes nothing here. Encounters at the same time
	 * are ordered by id, so the one saved last wins.
	 */
	static boolean isAdmitted(Visit visit, Set<String> admissionTypes, Set<String> dischargeTypes) {
		return isAdmitted(visit.getEncounters(), admissionTypes, dischargeTypes);
	}

	/** {@link #isAdmitted(Visit, Set, Set)} over the given encounters of one visit. */
	static boolean isAdmitted(Collection<Encounter> encounters, Set<String> admissionTypes,
	        Set<String> dischargeTypes) {
		if (admissionTypes.isEmpty() || encounters == null) {
			return false;
		}
		Encounter latest = null;
		for (Encounter encounter : encounters) {
			if (Boolean.TRUE.equals(encounter.getVoided()) || encounter.getEncounterType() == null
			        || encounter.getEncounterDatetime() == null) {
				continue;
			}
			String type = encounter.getEncounterType().getUuid();
			if (!admissionTypes.contains(type) && !dischargeTypes.contains(type)) {
				continue;
			}
			if (latest == null || isLater(encounter, latest)) {
				latest = encounter;
			}
		}
		return latest != null && admissionTypes.contains(latest.getEncounterType().getUuid());
	}

	private static boolean isLater(Encounter candidate, Encounter current) {
		int byTime = candidate.getEncounterDatetime().compareTo(current.getEncounterDatetime());
		if (byTime != 0) {
			return byTime > 0;
		}
		Integer candidateId = candidate.getEncounterId();
		Integer currentId = current.getEncounterId();
		if (candidateId == null || currentId == null) {
			return candidateId == null;
		}
		return candidateId > currentId;
	}
}
