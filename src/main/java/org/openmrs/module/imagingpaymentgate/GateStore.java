/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 */
package org.openmrs.module.imagingpaymentgate;

/** Where the gate reads its configuration and the payment state from. */
public interface GateStore {

	GateConfig loadConfig();

	/**
	 * Whether payment is confirmed for this one order: an {@code accepted} FHIR Task whose basedOn is
	 * this order's ServiceRequest. A payment for another order, of this patient or any other, must not
	 * count.
	 */
	boolean isPaid(String orderUuid);
}
