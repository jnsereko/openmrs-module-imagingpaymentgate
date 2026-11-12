/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 */
package org.openmrs.module.imagingpaymentgate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Visit;
import org.openmrs.api.EncounterService;

public class DischargedVisitsTest {

	private static final String ADMISSION = UUID.randomUUID().toString();

	private static final String DISCHARGE = UUID.randomUUID().toString();

	private static final String TRANSFER = UUID.randomUUID().toString();

	private static final String CONSULTATION = UUID.randomUUID().toString();

	private static final long HOUR = 3600000L;

	private final Date t0 = new Date(1762300800000L);

	private AdmittedVisitsTest.FakeStore store;

	@Before
	public void setUp() {
		store = new AdmittedVisitsTest.FakeStore();
		store.admission.add(ADMISSION);
		store.discharge.add(DISCHARGE);
	}

	@Test
	public void discharge_endsTheVisitAtTheDischargeTime() {
		Visit visit = visit(encounter(11, ADMISSION, 1), encounter(12, TRANSFER, 20));
		Encounter discharge = encounter(13, DISCHARGE, 50);
		discharge.setVisit(visit);

		assertTrue(new DischargedVisits(store).endVisitIfDischarged(discharge));

		assertEquals(discharge.getEncounterDatetime(), visit.getStopDatetime());
		assertEquals(Collections.singletonList(visit), store.saved);
	}

	@Test
	public void discharge_alreadyInTheVisitsEncounters_endsTheVisit() {
		Encounter discharge = encounter(13, DISCHARGE, 50);
		Visit visit = visit(encounter(11, ADMISSION, 1), discharge);

		assertTrue(new DischargedVisits(store).endVisitIfDischarged(discharge));

		assertEquals(discharge.getEncounterDatetime(), visit.getStopDatetime());
	}

	@Test
	public void encounterAfterTheDischarge_isKeptInsideTheVisit() {
		Encounter note = encounter(12, CONSULTATION, 60);
		Visit visit = visit(encounter(11, ADMISSION, 1), note);
		Encounter discharge = encounter(13, DISCHARGE, 50);
		discharge.setVisit(visit);

		new DischargedVisits(store).endVisitIfDischarged(discharge);

		assertEquals(note.getEncounterDatetime(), visit.getStopDatetime());
	}

	@Test
	public void laterAdmission_leavesTheVisitOpen() {
		Visit visit = visit(encounter(11, ADMISSION, 1), encounter(14, ADMISSION, 60));
		Encounter discharge = encounter(13, DISCHARGE, 50);
		discharge.setVisit(visit);

		assertFalse(new DischargedVisits(store).endVisitIfDischarged(discharge));

		assertNull(visit.getStopDatetime());
		assertTrue(store.saved.isEmpty());
	}

	@Test
	public void otherEncounterTypes_leaveTheVisitOpen() {
		Visit visit = visit(encounter(11, ADMISSION, 1));
		for (String type : new String[] { ADMISSION, TRANSFER, CONSULTATION }) {
			Encounter encounter = encounter(20, type, 30);
			encounter.setVisit(visit);

			assertFalse(new DischargedVisits(store).endVisitIfDischarged(encounter));
		}
		assertNull(visit.getStopDatetime());
	}

	@Test
	public void voidedDischarge_leavesTheVisitOpen() {
		Visit visit = visit(encounter(11, ADMISSION, 1));
		Encounter discharge = encounter(13, DISCHARGE, 50);
		discharge.setVisit(visit);
		discharge.setVoided(true);

		assertFalse(new DischargedVisits(store).endVisitIfDischarged(discharge));
		assertNull(visit.getStopDatetime());
	}

	@Test
	public void endedVisit_isNotChanged() {
		Visit visit = visit(encounter(11, ADMISSION, 1));
		Date stop = new Date(t0.getTime() + 40 * HOUR);
		visit.setStopDatetime(stop);
		Encounter discharge = encounter(13, DISCHARGE, 40);
		discharge.setVisit(visit);

		assertFalse(new DischargedVisits(store).endVisitIfDischarged(discharge));
		assertEquals(stop, visit.getStopDatetime());
		assertTrue(store.saved.isEmpty());
	}

	@Test
	public void dischargeWithoutVisit_isIgnored() {
		assertFalse(new DischargedVisits(store).endVisitIfDischarged(encounter(13, DISCHARGE, 50)));
	}

	@Test
	public void noMappedDischargeType_leavesTheVisitOpen() {
		store.discharge.clear();
		Visit visit = visit(encounter(11, ADMISSION, 1));
		Encounter discharge = encounter(13, DISCHARGE, 50);
		discharge.setVisit(visit);

		assertFalse(new DischargedVisits(store).endVisitIfDischarged(discharge));
		assertNull(visit.getStopDatetime());
	}

	@Test
	public void switchedOff_leavesTheVisitOpen() {
		store.endAtDischarge = false;
		Visit visit = visit(encounter(11, ADMISSION, 1));
		Encounter discharge = encounter(13, DISCHARGE, 50);
		discharge.setVisit(visit);

		assertFalse(new DischargedVisits(store).endVisitIfDischarged(discharge));
		assertNull(visit.getStopDatetime());
	}

	@Test
	public void advice_endsTheVisitAfterSaveEncounter() throws Exception {
		Visit visit = visit(encounter(11, ADMISSION, 1));
		Encounter discharge = encounter(13, DISCHARGE, 50);
		discharge.setVisit(visit);
		Method save = EncounterService.class.getMethod("saveEncounter", Encounter.class);

		new EndVisitAtDischargeAdvice(new DischargedVisits(store)).afterReturning(discharge, save,
		    new Object[] { discharge }, null);

		assertEquals(discharge.getEncounterDatetime(), visit.getStopDatetime());
	}

	@Test
	public void advice_otherMethods_doNothing() throws Exception {
		Visit visit = visit(encounter(11, ADMISSION, 1));
		Encounter discharge = encounter(13, DISCHARGE, 50);
		discharge.setVisit(visit);
		Method get = EncounterService.class.getMethod("getEncounterByUuid", String.class);

		new EndVisitAtDischargeAdvice(new DischargedVisits(store)).afterReturning(discharge, get,
		    new Object[] { discharge.getUuid() }, null);

		assertNull(visit.getStopDatetime());
	}

	private Visit visit(Encounter... encounters) {
		Visit visit = new Visit(1);
		visit.setStartDatetime(t0);
		visit.setEncounters(new LinkedHashSet<Encounter>());
		for (Encounter encounter : encounters) {
			encounter.setVisit(visit);
			visit.getEncounters().add(encounter);
		}
		return visit;
	}

	private Encounter encounter(int id, String typeUuid, int hoursAfterStart) {
		Encounter encounter = new Encounter(id);
		EncounterType type = new EncounterType();
		type.setUuid(typeUuid);
		encounter.setEncounterType(type);
		encounter.setEncounterDatetime(new Date(t0.getTime() + hoursAfterStart * HOUR));
		encounter.setVoided(false);
		return encounter;
	}
}
