/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 */
package org.openmrs.module.imagingpaymentgate;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

import org.openmrs.Encounter;
import org.openmrs.Visit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ends a visit when its patient is discharged (UVL-EMR#608).
 * <p>
 * The ward app discharges a patient by saving a Discharge encounter on the visit; nothing ended the
 * visit, and the Inpatient visit type is not in {@code visits.autoCloseVisitType} (adding it would close
 * admitted patients' visits every night). The discharged patient therefore kept an active visit, and
 * every later order, at the outpatient department included, went on the old inpatient visit and its
 * quotation.
 * <p>
 * {@link #endVisitIfDischarged(Encounter)} runs after an encounter is saved. When the encounter is a
 * non-voided Discharge (emrapi's {@code emr.exitFromInpatientEncounterType}) on an open visit, and no
 * later Admission makes the patient admitted again, the visit's stop time is set to the discharge time,
 * or to the visit's latest encounter if one is later, so the visit stays valid. Any visit type: an
 * admission made on a General or Outpatient visit ends at discharge too. The stay billing counts nights
 * up to the Discharge encounter, so its count does not change.
 */
public class DischargedVisits {

	private static final Logger log = LoggerFactory.getLogger(DischargedVisits.class);

	private final VisitCloseStore store;

	public DischargedVisits(VisitCloseStore store) {
		this.store = store;
	}

	/** Whether the encounter ended its visit. */
	public boolean endVisitIfDischarged(Encounter encounter) {
		if (encounter == null || Boolean.TRUE.equals(encounter.getVoided()) || encounter.getEncounterType() == null
		        || encounter.getEncounterDatetime() == null) {
			return false;
		}
		Visit visit = encounter.getVisit();
		if (visit == null || visit.getStopDatetime() != null || Boolean.TRUE.equals(visit.getVoided())) {
			return false;
		}
		Set<String> dischargeTypes = store.getDischargeEncounterTypeUuids();
		if (!dischargeTypes.contains(encounter.getEncounterType().getUuid())) {
			return false;
		}
		if (!store.isEndVisitAtDischarge()) {
			return false;
		}
		// The visit's collection may not hold the encounter just saved yet.
		Set<Encounter> encounters = new LinkedHashSet<Encounter>();
		if (visit.getEncounters() != null) {
			encounters.addAll(visit.getEncounters());
		}
		encounters.add(encounter);
		if (AdmittedVisits.isAdmitted(encounters, store.getAdmissionEncounterTypeUuids(), dischargeTypes)) {
			log.info("Visit {} left open: an Admission follows the Discharge {}", visit.getUuid(),
			    encounter.getUuid());
			return false;
		}

		Date stop = encounter.getEncounterDatetime();
		for (Encounter other : encounters) {
			if (!Boolean.TRUE.equals(other.getVoided()) && other.getEncounterDatetime() != null
			        && other.getEncounterDatetime().after(stop)) {
				stop = other.getEncounterDatetime();
			}
		}
		visit.setStopDatetime(stop);
		store.saveVisit(visit);
		log.info("Visit {} ended at {}: the patient was discharged (encounter {})", visit.getUuid(), stop,
		    encounter.getUuid());
		return true;
	}
}
