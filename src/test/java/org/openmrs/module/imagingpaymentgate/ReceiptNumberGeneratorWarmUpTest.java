/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 */
package org.openmrs.module.imagingpaymentgate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.billing.api.ReceiptNumberGeneratorFactory;

public class ReceiptNumberGeneratorWarmUpTest {
	
	private final ClassLoader withBilling = getClass().getClassLoader();
	
	@Before
	public void setUp() {
		ReceiptNumberGeneratorFactory.reset();
	}
	
	@Test
	public void warm_shouldCallTheFactoryOnceAndReportALoadedGenerator() {
		ReceiptNumberGeneratorFactory.next = new ReceiptNumberGeneratorFactory.Generator(true);
		
		assertTrue(ReceiptNumberGeneratorWarmUp.warm(withBilling));
		assertEquals(1, ReceiptNumberGeneratorFactory.calls);
	}
	
	@Test
	public void warm_shouldReportAGeneratorThatIsStillNotLoaded() {
		ReceiptNumberGeneratorFactory.next = new ReceiptNumberGeneratorFactory.Generator(false);
		
		assertFalse(ReceiptNumberGeneratorWarmUp.warm(withBilling));
	}
	
	@Test
	public void warm_shouldDoNothingWhenNoGeneratorIsConfigured() {
		ReceiptNumberGeneratorFactory.next = null;
		
		assertFalse(ReceiptNumberGeneratorWarmUp.warm(withBilling));
		assertEquals(1, ReceiptNumberGeneratorFactory.calls);
	}
	
	@Test
	public void warm_shouldNotThrowWhenTheFactoryFails() {
		ReceiptNumberGeneratorFactory.failure = new IllegalStateException("no database yet");
		
		assertFalse(ReceiptNumberGeneratorWarmUp.warm(withBilling));
	}
	
	@Test
	public void warm_shouldDoNothingWhenTheClassLoaderHasNoBilling() {
		// A loader with no parent cannot see the factory: billing absent.
		assertFalse(ReceiptNumberGeneratorWarmUp.warm(new ClassLoader(null) {}));
		assertEquals(0, ReceiptNumberGeneratorFactory.calls);
	}
	
	@Test
	public void run_shouldDoNothingAndNotThrowWhenBillingIsNotStarted() {
		// No module has been started in a unit test, so billing is "not started".
		ReceiptNumberGeneratorWarmUp.run();
		assertEquals(0, ReceiptNumberGeneratorFactory.calls);
	}
}
