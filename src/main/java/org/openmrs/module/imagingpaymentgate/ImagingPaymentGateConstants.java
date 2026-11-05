/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 */
package org.openmrs.module.imagingpaymentgate;

/** Global property and privilege names. The values are site configuration, not code. */
public final class ImagingPaymentGateConstants {

	public static final String GP_ENABLED = "imagingpaymentgate.enabled";

	public static final String GP_CONCEPT_SET_UUIDS = "imagingpaymentgate.conceptSetUuids";

	public static final String GP_CONCEPT_UUIDS = "imagingpaymentgate.conceptUuids";

	public static final String GP_ORDER_TYPE_UUIDS = "imagingpaymentgate.orderTypeUuids";

	/** Lets a user save results on an unpaid imaging order. Superusers hold every privilege, so they have it too. */
	public static final String PRIV_BYPASS = "Bypass Imaging Payment Gate";

	private ImagingPaymentGateConstants() {
	}
}
