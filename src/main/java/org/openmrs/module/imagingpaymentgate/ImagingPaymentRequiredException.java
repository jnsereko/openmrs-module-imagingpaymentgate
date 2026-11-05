/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 */
package org.openmrs.module.imagingpaymentgate;

import org.openmrs.api.APIException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Results were refused because the imaging order is not paid.
 * <p>
 * The REST module's generic exception handler reads {@link ResponseStatus} from the exception class,
 * so a refusal reaches the client as 402 with this message in {@code error.message}, instead of a 500
 * that looks like a server fault. No reason is set on the annotation, because a reason would replace
 * the message.
 */
@ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
public class ImagingPaymentRequiredException extends APIException {

	private static final long serialVersionUID = 1L;

	public ImagingPaymentRequiredException(String message) {
		super(message);
	}

	public ImagingPaymentRequiredException(String message, Throwable cause) {
		super(message, cause);
	}
}
