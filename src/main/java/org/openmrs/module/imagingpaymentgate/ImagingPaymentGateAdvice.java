/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 */
package org.openmrs.module.imagingpaymentgate;

import java.lang.reflect.Method;

import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.springframework.aop.MethodBeforeAdvice;

/**
 * Runs the gate before ObsService.saveObs, EncounterService.saveEncounter and
 * OrderService.updateOrderFulfillerStatus (see config.xml), so a refusal happens before anything is
 * written. These are the calls behind the chart's "Entrer des résultats", the Laboratory app and the
 * REST obs, encounter and fulfillerdetails resources.
 */
public class ImagingPaymentGateAdvice implements MethodBeforeAdvice {

	private final ImagingPaymentGate gate;

	/** Used by OpenMRS, which instantiates advice from config.xml. */
	public ImagingPaymentGateAdvice() {
		this(null);
	}

	ImagingPaymentGateAdvice(ImagingPaymentGate gate) {
		this.gate = gate;
	}

	@Override
	public void before(Method method, Object[] args, Object target) {
		if (args == null || args.length == 0) {
			return;
		}
		String name = method.getName();
		if ("saveObs".equals(name) && args[0] instanceof Obs) {
			gate().checkObs((Obs) args[0]);
		} else if ("saveEncounter".equals(name) && args[0] instanceof Encounter) {
			gate().checkEncounter((Encounter) args[0]);
		} else if ("updateOrderFulfillerStatus".equals(name) && args.length > 1 && args[0] instanceof Order
		        && args[1] instanceof Order.FulfillerStatus) {
			gate().checkFulfillerStatus((Order) args[0], (Order.FulfillerStatus) args[1]);
		}
	}

	private ImagingPaymentGate gate() {
		return gate != null ? gate : ImagingPaymentGate.get();
	}
}
