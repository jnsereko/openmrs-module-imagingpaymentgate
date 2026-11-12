/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 */
package org.openmrs.module.imagingpaymentgate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.api.VisitService;

public class AdmittedVisitsTest {

	private static final String ADMISSION = UUID.randomUUID().toString();

	private static final String DISCHARGE = UUID.randomUUID().toString();

	private static final String TRANSFER = UUID.randomUUID().toString();

	private static final String CONSULTATION = UUID.randomUUID().toString();

	private static final long HOUR = 3600000L;

	private final Date t0 = new Date(1762300800000L);

	private FakeStore store;

	@Before
	public void setUp() {
		store = new FakeStore();
		store.types.add(new VisitType("General", null));
		store.admission.add(ADMISSION);
		store.discharge.add(DISCHARGE);
	}

	@Test
	public void visitWithoutAdmission_isClosedAsCoreDoes() {
		Visit outpatient = visit(1, encounter(10, CONSULTATION, 0));
		store.open.add(outpatient);

		new AdmittedVisits(store).stopVisits(new Date());

		assertNotNull(outpatient.getStopDatetime());
		assertEquals(Collections.singletonList(outpatient), store.saved);
	}

	@Test
	public void admittedVisit_isLeftOpen() {
		Visit admitted = visit(1, encounter(10, CONSULTATION, 0), encounter(11, ADMISSION, 1));
		store.open.add(admitted);

		new AdmittedVisits(store).stopVisits(new Date());

		assertNull(admitted.getStopDatetime());
		assertTrue(store.saved.isEmpty());
	}

	@Test
	public void transferAfterAdmission_isStillAdmitted() {
		Visit transferred = visit(1, encounter(11, ADMISSION, 1), encounter(12, TRANSFER, 5));
		store.open.add(transferred);

		new AdmittedVisits(store).stopVisits(new Date());

		assertNull(transferred.getStopDatetime());
	}

	@Test
	public void dischargedVisit_isClosed() {
		Visit discharged = visit(1, encounter(11, ADMISSION, 1), encounter(13, DISCHARGE, 30));
		store.open.add(discharged);

		new AdmittedVisits(store).stopVisits(new Date());

		assertNotNull(discharged.getStopDatetime());
	}

	@Test
	public void readmissionAfterDischarge_isAdmitted() {
		Visit readmitted = visit(1, encounter(11, ADMISSION, 1), encounter(13, DISCHARGE, 30),
		    encounter(14, ADMISSION, 40));

		assertTrue(AdmittedVisits.isAdmitted(readmitted, store.admission, store.discharge));
	}

	@Test
	public void voidedAdmission_doesNotCount() {
		Encounter admission = encounter(11, ADMISSION, 1);
		admission.setVoided(true);

		assertFalse(AdmittedVisits.isAdmitted(visit(1, admission), store.admission, store.discharge));
	}

	@Test
	public void voidedDischarge_leavesThePatientAdmitted() {
		Encounter discharge = encounter(13, DISCHARGE, 30);
		discharge.setVoided(true);

		assertTrue(AdmittedVisits.isAdmitted(visit(1, encounter(11, ADMISSION, 1), discharge), store.admission,
		    store.discharge));
	}

	@Test
	public void admissionAndDischargeAtTheSameTime_theOneSavedLastWins() {
		assertFalse(AdmittedVisits.isAdmitted(visit(1, encounter(11, ADMISSION, 1), encounter(12, DISCHARGE, 1)),
		    store.admission, store.discharge));
		assertTrue(AdmittedVisits.isAdmitted(visit(1, encounter(12, DISCHARGE, 1), encounter(13, ADMISSION, 1)),
		    store.admission, store.discharge));
	}

	@Test
	public void mixedRun_closesOnlyTheNonAdmittedVisits_atOneStopTime() {
		Visit a = visit(1, encounter(10, CONSULTATION, 0));
		Visit b = visit(2, encounter(20, ADMISSION, 0));
		Visit c = visit(3);
		store.open.add(a);
		store.open.add(b);
		store.open.add(c);

		new AdmittedVisits(store).stopVisits(new Date());

		assertNotNull(a.getStopDatetime());
		assertNull(b.getStopDatetime());
		assertSame(a.getStopDatetime(), c.getStopDatetime());
		assertEquals(2, store.saved.size());
	}

	@Test
	public void noMappedAdmissionType_closesEverythingAsCoreDoes() {
		store.admission.clear();
		Visit admitted = visit(1, encounter(11, ADMISSION, 1));
		store.open.add(admitted);

		new AdmittedVisits(store).stopVisits(new Date());

		assertNotNull(admitted.getStopDatetime());
	}

	@Test
	public void noAutoCloseVisitType_closesNothing() {
		store.types.clear();
		Visit outpatient = visit(1);
		store.open.add(outpatient);

		new AdmittedVisits(store).stopVisits(new Date());

		assertNull(outpatient.getStopDatetime());
	}

	@Test
	public void advice_replacesStopVisits() throws Throwable {
		Visit outpatient = visit(1);
		Visit admitted = visit(2, encounter(20, ADMISSION, 0));
		store.open.add(outpatient);
		store.open.add(admitted);
		MethodInvocation invocation = invocation("stopVisits", new Object[] { new Date() });

		new KeepAdmittedVisitsOpenAdvice(new AdmittedVisits(store)).invoke(invocation);

		verify(invocation, never()).proceed();
		assertNotNull(outpatient.getStopDatetime());
		assertNull(admitted.getStopDatetime());
	}

	@Test
	public void advice_disabled_runsCore() throws Throwable {
		store.enabled = false;
		Visit admitted = visit(2, encounter(20, ADMISSION, 0));
		store.open.add(admitted);
		MethodInvocation invocation = invocation("stopVisits", new Object[] { new Date() });

		new KeepAdmittedVisitsOpenAdvice(new AdmittedVisits(store)).invoke(invocation);

		verify(invocation).proceed();
		assertTrue(store.saved.isEmpty());
	}

	@Test
	public void advice_otherMethods_passThrough() throws Throwable {
		MethodInvocation invocation = invocation("getVisitByUuid", new Object[] { "x" });

		new KeepAdmittedVisitsOpenAdvice(new AdmittedVisits(store)).invoke(invocation);

		verify(invocation).proceed();
	}

	private MethodInvocation invocation(String name, Object[] args) throws NoSuchMethodException {
		Method method = null;
		for (Method candidate : VisitService.class.getMethods()) {
			if (candidate.getName().equals(name) && candidate.getParameterCount() == args.length) {
				method = candidate;
			}
		}
		MethodInvocation invocation = mock(MethodInvocation.class);
		when(invocation.getMethod()).thenReturn(method);
		when(invocation.getArguments()).thenReturn(args);
		return invocation;
	}

	private Visit visit(int id, Encounter... encounters) {
		Visit visit = new Visit(id);
		visit.setStartDatetime(t0);
		visit.setVisitType(store.types.isEmpty() ? null : store.types.get(0));
		visit.setEncounters(new LinkedHashSet<Encounter>());
		for (Encounter encounter : encounters) {
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

	/** Core's getNextVisit over an in-memory list: the next open visit by id. */
	static class FakeStore implements VisitCloseStore {

		boolean enabled = true;

		boolean endAtDischarge = true;

		final List<VisitType> types = new ArrayList<VisitType>();

		final List<Visit> open = new ArrayList<Visit>();

		final List<Visit> saved = new ArrayList<Visit>();

		final Set<String> admission = new HashSet<String>();

		final Set<String> discharge = new HashSet<String>();

		@Override
		public boolean isKeepAdmittedVisitsOpen() {
			return enabled;
		}

		@Override
		public boolean isEndVisitAtDischarge() {
			return endAtDischarge;
		}

		@Override
		public List<VisitType> getVisitTypesToStop() {
			return types;
		}

		@Override
		public Visit getNextVisit(Visit previousVisit, Collection<VisitType> visitTypes, Date maximumStartDate) {
			int after = previousVisit == null ? 0 : previousVisit.getVisitId();
			for (Visit visit : open) {
				if (visit.getVisitId() > after && visit.getStopDatetime() == null
				        && !visit.getStartDatetime().after(maximumStartDate)) {
					return visit;
				}
			}
			return null;
		}

		@Override
		public void saveVisit(Visit visit) {
			saved.add(visit);
		}

		@Override
		public void flushAndClear() {
		}

		@Override
		public Set<String> getAdmissionEncounterTypeUuids() {
			return admission;
		}

		@Override
		public Set<String> getDischargeEncounterTypeUuids() {
			return discharge;
		}
	}
}
