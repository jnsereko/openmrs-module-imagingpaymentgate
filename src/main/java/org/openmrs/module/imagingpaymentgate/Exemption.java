/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 */
package org.openmrs.module.imagingpaymentgate;

import org.openmrs.User;
import org.openmrs.api.context.Context;

/** Who may save results on an unpaid imaging order, and how to name them in the log. */
public interface Exemption {

	boolean isExempt();

	String currentUser();

	/** Holders of {@link ImagingPaymentGateConstants#PRIV_BYPASS}, which includes every superuser. */
	Exemption BY_PRIVILEGE = new Exemption() {

		@Override
		public boolean isExempt() {
			return Context.hasPrivilege(ImagingPaymentGateConstants.PRIV_BYPASS);
		}

		@Override
		public String currentUser() {
			User user = Context.getAuthenticatedUser();
			return user == null ? "(no authenticated user)" : user.getUsername() + " (" + user.getUuid() + ")";
		}
	};
}
