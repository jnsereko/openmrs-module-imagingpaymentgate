/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 */
package org.openmrs.module.imagingpaymentgate;

import java.io.Serializable;

import org.hibernate.EmptyInterceptor;
import org.hibernate.type.Type;
import org.openmrs.Obs;

/**
 * The backstop: checks every Obs Hibernate is about to insert, whichever code path saves it.
 * <p>
 * {@link ImagingPaymentGateAdvice} covers the services the O3 frontend and the REST API go through.
 * This covers writes that never call them: FHIR2 writes an Observation, with basedOn set to the
 * ServiceRequest, straight through its own DAO. Only inserts are checked; an obs is never edited in
 * place in OpenMRS (an edit voids it and inserts a copy, which comes through here).
 */
public class ImagingPaymentGateInterceptor extends EmptyInterceptor {

	private static final long serialVersionUID = 1L;

	private final transient ImagingPaymentGate gate;

	public ImagingPaymentGateInterceptor() {
		this(null);
	}

	ImagingPaymentGateInterceptor(ImagingPaymentGate gate) {
		this.gate = gate;
	}

	@Override
	public boolean onSave(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
		if (entity instanceof Obs) {
			(gate != null ? gate : ImagingPaymentGate.get()).checkNewObs((Obs) entity);
		}
		return false;
	}
}
