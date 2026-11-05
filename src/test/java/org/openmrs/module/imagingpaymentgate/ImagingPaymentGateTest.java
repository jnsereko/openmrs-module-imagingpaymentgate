/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 */
package org.openmrs.module.imagingpaymentgate;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public class ImagingPaymentGateTest {

	private static final String XRAY_CONCEPT = UUID.randomUUID().toString();

	private static final String LAB_CONCEPT = UUID.randomUUID().toString();

	private static final String PROCEDURE_ORDER_TYPE = UUID.randomUUID().toString();

	private static final String RADIOLOGY_ORDER_TYPE = UUID.randomUUID().toString();

	private GateStore store;

	private Exemption exemption;

	private ImagingPaymentGate gate;

	@Before
	public void setUp() {
		store = mock(GateStore.class);
		exemption = mock(Exemption.class);
		when(exemption.currentUser()).thenReturn("test-user");
		when(store.loadConfig()).thenReturn(new GateConfig(true, Collections.singleton(XRAY_CONCEPT),
		        Collections.singleton(RADIOLOGY_ORDER_TYPE)));
		gate = new ImagingPaymentGate(store, exemption);
	}

	@Test
	public void paidImagingOrder_resultIsAllowed() {
		Order xray = order(XRAY_CONCEPT, PROCEDURE_ORDER_TYPE, "ORD-1");
		when(store.isPaid(xray.getUuid())).thenReturn(true);

		gate.checkObs(result(xray));
		gate.checkFulfillerStatus(xray, Order.FulfillerStatus.COMPLETED);
	}

	@Test
	public void unpaidImagingOrder_resultIsRefusedInFrench() {
		Order xray = order(XRAY_CONCEPT, PROCEDURE_ORDER_TYPE, "ORD-2");
		when(store.isPaid(xray.getUuid())).thenReturn(false);

		ImagingPaymentRequiredException refused = refusal(new Runnable() {

			@Override
			public void run() {
				gate.checkObs(result(xray));
			}
		});
		assertThat(refused.getMessage(), containsString("Résultats refusés"));
		assertThat(refused.getMessage(), containsString("ORD-2"));
		assertThat(refused.getMessage(), containsString("n'est pas confirmé"));
	}

	@Test
	public void refusal_isAnHttp402NotAServerError() {
		ResponseStatus status = ImagingPaymentRequiredException.class.getAnnotation(ResponseStatus.class);
		assertNotNull(status);
		assertEquals(HttpStatus.PAYMENT_REQUIRED, status.value());
		assertEquals("a reason would replace the French message", "", status.reason());
	}

	@Test
	public void paymentIsLookedUpForThisOrderOnly() {
		// The original defect: one paid invoice authorised every imaging order. The gate must ask
		// about the order being reported, never about the patient or "any" order.
		Order paid = order(XRAY_CONCEPT, PROCEDURE_ORDER_TYPE, "ORD-3");
		final Order unpaid = order(XRAY_CONCEPT, PROCEDURE_ORDER_TYPE, "ORD-4");
		when(store.isPaid(paid.getUuid())).thenReturn(true);
		when(store.isPaid(unpaid.getUuid())).thenReturn(false);

		gate.checkObs(result(paid));
		refusal(new Runnable() {

			@Override
			public void run() {
				gate.checkObs(result(unpaid));
			}
		});
	}

	@Test
	public void unpaidImagingOrder_cannotBeMarkedCompleted() {
		final Order xray = order(XRAY_CONCEPT, PROCEDURE_ORDER_TYPE, "ORD-5");
		when(store.isPaid(xray.getUuid())).thenReturn(false);

		refusal(new Runnable() {

			@Override
			public void run() {
				gate.checkFulfillerStatus(xray, Order.FulfillerStatus.COMPLETED);
			}
		});
		gate.checkFulfillerStatus(xray, Order.FulfillerStatus.IN_PROGRESS);
		gate.checkFulfillerStatus(xray, Order.FulfillerStatus.RECEIVED);
	}

	@Test
	public void unpaidImagingOrder_refusedThroughAnEncounterAndInsideAGroup() {
		Order xray = order(XRAY_CONCEPT, PROCEDURE_ORDER_TYPE, "ORD-6");
		when(store.isPaid(xray.getUuid())).thenReturn(false);
		final Encounter encounter = new Encounter();
		Obs group = new Obs();
		group.addGroupMember(result(xray));
		encounter.addObs(group);

		refusal(new Runnable() {

			@Override
			public void run() {
				gate.checkEncounter(encounter);
			}
		});
	}

	@Test
	public void orderTypeAlone_isEnoughToGate() {
		final Order radiology = order(LAB_CONCEPT, RADIOLOGY_ORDER_TYPE, "ORD-7");
		when(store.isPaid(radiology.getUuid())).thenReturn(false);

		refusal(new Runnable() {

			@Override
			public void run() {
				gate.checkObs(result(radiology));
			}
		});
	}

	@Test
	public void nonImagingOrder_isUnaffectedAndNeverLookedUp() {
		Order lab = order(LAB_CONCEPT, PROCEDURE_ORDER_TYPE, "ORD-8");

		gate.checkObs(result(lab));
		gate.checkFulfillerStatus(lab, Order.FulfillerStatus.COMPLETED);
		gate.checkObs(new Obs());

		verify(store, never()).isPaid(anyString());
	}

	@Test
	public void exemptUser_isAllowedOnAnUnpaidOrder() {
		Order xray = order(XRAY_CONCEPT, PROCEDURE_ORDER_TYPE, "ORD-9");
		when(store.isPaid(xray.getUuid())).thenReturn(false);
		when(exemption.isExempt()).thenReturn(true);

		gate.checkObs(result(xray));
		gate.checkFulfillerStatus(xray, Order.FulfillerStatus.COMPLETED);
	}

	@Test
	public void exemptionIsOnlyAskedAboutWhenTheOrderIsUnpaid() {
		Order xray = order(XRAY_CONCEPT, PROCEDURE_ORDER_TYPE, "ORD-10");
		when(store.isPaid(xray.getUuid())).thenReturn(true);

		gate.checkObs(result(xray));

		verify(exemption, never()).isExempt();
	}

	@Test
	public void disabledGate_allowsUnpaid() {
		when(store.loadConfig()).thenReturn(new GateConfig(false, Collections.singleton(XRAY_CONCEPT),
		        Collections.<String> emptySet()));
		Order xray = order(XRAY_CONCEPT, PROCEDURE_ORDER_TYPE, "ORD-11");

		gate.checkObs(result(xray));

		verify(store, never()).isPaid(anyString());
	}

	@Test
	public void failedPaymentLookup_refuses() {
		final Order xray = order(XRAY_CONCEPT, PROCEDURE_ORDER_TYPE, "ORD-12");
		when(store.isPaid(xray.getUuid())).thenThrow(new IllegalStateException("database unavailable"));

		ImagingPaymentRequiredException refused = refusal(new Runnable() {

			@Override
			public void run() {
				gate.checkObs(result(xray));
			}
		});
		assertThat(refused.getMessage(), containsString("n'a pas pu être vérifié"));
	}

	@Test
	public void voidedObs_isNotChecked() {
		Order xray = order(XRAY_CONCEPT, PROCEDURE_ORDER_TYPE, "ORD-13");
		Obs voided = result(xray);
		voided.setVoided(true);

		gate.checkObs(voided);
		gate.checkNewObs(voided);

		verify(store, never()).isPaid(anyString());
	}

	@Test
	public void interceptor_refusesAnUnpaidObsWrittenStraightThroughHibernate() {
		final Order xray = order(XRAY_CONCEPT, PROCEDURE_ORDER_TYPE, "ORD-14");
		when(store.isPaid(xray.getUuid())).thenReturn(false);
		final ImagingPaymentGateInterceptor interceptor = new ImagingPaymentGateInterceptor(gate);

		refusal(new Runnable() {

			@Override
			public void run() {
				interceptor.onSave(result(xray), null, null, null, null);
			}
		});
		interceptor.onSave(new Encounter(), null, null, null, null);
	}

	@Test
	public void unreadableConfiguration_leavesTheGateOffRatherThanRefusingEveryResult() {
		when(store.loadConfig()).thenThrow(new IllegalStateException("no session"));
		Order xray = order(XRAY_CONCEPT, PROCEDURE_ORDER_TYPE, "ORD-15");

		gate.checkObs(result(xray));

		verify(store, never()).isPaid(anyString());
	}

	@Test
	public void configuration_isParsedLeniently() {
		assertEquals(2, GateConfig.parseList(" a , ,b,").size());
		assertEquals(0, GateConfig.parseList(null).size());
		assertEquals(true, GateConfig.parseEnabled(null));
		assertEquals(true, GateConfig.parseEnabled(""));
		assertEquals(true, GateConfig.parseEnabled("TRUE"));
		assertEquals(false, GateConfig.parseEnabled(" False "));
	}

	private static ImagingPaymentRequiredException refusal(Runnable action) {
		try {
			action.run();
		}
		catch (ImagingPaymentRequiredException e) {
			return e;
		}
		fail("expected the result to be refused");
		return null;
	}

	private static Obs result(Order order) {
		Obs obs = new Obs();
		obs.setOrder(order);
		obs.setValueText("report");
		return obs;
	}

	static Order order(String conceptUuid, String orderTypeUuid, String orderNumber) {
		Concept concept = new Concept();
		concept.setUuid(conceptUuid);
		OrderType orderType = new OrderType();
		orderType.setUuid(orderTypeUuid);
		Order order = new Order();
		order.setUuid(UUID.randomUUID().toString());
		order.setConcept(concept);
		order.setOrderType(orderType);
		try {
			Field field = Order.class.getDeclaredField("orderNumber");
			field.setAccessible(true);
			field.set(order, orderNumber);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return order;
	}
}
