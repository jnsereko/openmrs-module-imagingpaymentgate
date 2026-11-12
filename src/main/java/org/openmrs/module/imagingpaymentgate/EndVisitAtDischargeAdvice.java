/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 */
package org.openmrs.module.imagingpaymentgate;

import java.lang.reflect.Method;

import org.openmrs.Encounter;
import org.springframework.aop.AfterReturningAdvice;

/**
 * Runs {@link DischargedVisits#endVisitIfDischarged(Encounter)} after
 * {@code EncounterService.saveEncounter} (see config.xml), the call behind the ward app's « Confirmer la
 * sortie » and the REST encounter resource. It runs inside the save's transaction, so the visit's stop
 * time is written with the Discharge encounter or not at all. The visit is saved through core's
 * {@code VisitDAO}, as the nightly close does: no nested service call can mark the transaction
 * rollback-only.
 */
public class EndVisitAtDischargeAdvice implements AfterReturningAdvice {

	private final DischargedVisits dischargedVisits;

	/** Used by OpenMRS, which instantiates advice from config.xml. */
	public EndVisitAtDischargeAdvice() {
		this(null);
	}

	EndVisitAtDischargeAdvice(DischargedVisits dischargedVisits) {
		this.dischargedVisits = dischargedVisits;
	}

	@Override
	public void afterReturning(Object returnValue, Method method, Object[] args, Object target) {
		if (!"saveEncounter".equals(method.getName()) || args == null || args.length == 0
		        || !(args[0] instanceof Encounter)) {
			return;
		}
		Encounter encounter = returnValue instanceof Encounter ? (Encounter) returnValue : (Encounter) args[0];
		DischargedVisits visits = dischargedVisits != null ? dischargedVisits
		        : new DischargedVisits(CoreVisitCloseStore.onCurrentSession());
		visits.endVisitIfDischarged(encounter);
	}
}
