/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 */
package org.openmrs.module.imagingpaymentgate;

import org.openmrs.module.BaseModuleActivator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Logs, at start-up, what the gate will hold, so a mis-set property shows in the first log lines. */
public class ImagingPaymentGateActivator extends BaseModuleActivator {

	private static final Logger log = LoggerFactory.getLogger(ImagingPaymentGateActivator.class);

	@Override
	public void started() {
		try {
			ImagingPaymentGate gate = ImagingPaymentGate.get();
			gate.invalidate();
			log.info("Imaging Payment Gate started: {}", gate.config());
		}
		catch (RuntimeException e) {
			log.error("Imaging Payment Gate started but could not read its configuration yet", e);
		}
	}
}
