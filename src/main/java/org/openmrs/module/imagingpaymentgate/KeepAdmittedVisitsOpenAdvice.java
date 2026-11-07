/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 */
package org.openmrs.module.imagingpaymentgate;

import java.util.Date;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * Replaces {@code VisitService.stopVisits(Date)}, the call behind core's Auto Close Visits Task, with
 * {@link AdmittedVisits#stopVisits(Date)} (see config.xml). The task itself, its class and its
 * 21:59:59 UTC start (#350) are untouched. With {@code imagingpaymentgate.keepAdmittedVisitsOpen=false}
 * core's own method runs. Every other VisitService method passes straight through.
 */
public class KeepAdmittedVisitsOpenAdvice implements MethodInterceptor {

	private final AdmittedVisits admittedVisits;

	/** Used by OpenMRS, which instantiates advice from config.xml. */
	public KeepAdmittedVisitsOpenAdvice() {
		this(null);
	}

	KeepAdmittedVisitsOpenAdvice(AdmittedVisits admittedVisits) {
		this.admittedVisits = admittedVisits;
	}

	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		Object[] args = invocation.getArguments();
		if (!"stopVisits".equals(invocation.getMethod().getName()) || args == null || args.length != 1
		        || (args[0] != null && !(args[0] instanceof Date))) {
			return invocation.proceed();
		}
		AdmittedVisits visits = admittedVisits != null ? admittedVisits
		        : new AdmittedVisits(CoreVisitCloseStore.onCurrentSession());
		if (!visits.isEnabled()) {
			return invocation.proceed();
		}
		visits.stopVisits((Date) args[0]);
		return null;
	}
}
