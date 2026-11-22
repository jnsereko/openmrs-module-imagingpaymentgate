/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 */
package org.openmrs.module.billing.api;

/**
 * Test stand-in for billing's factory under its own name: ReceiptNumberGeneratorWarmUp reaches the
 * real one by reflection, so a class with the same name and {@code getGenerator()} is all it needs.
 * Not packaged: it is in src/test.
 */
public class ReceiptNumberGeneratorFactory {
	
	public static int calls;
	
	/** What the next call does: a generator, null, or a failure. */
	public static Object next;
	
	public static RuntimeException failure;
	
	public static Object getGenerator() {
		calls++;
		if (failure != null) {
			throw failure;
		}
		return next;
	}
	
	public static void reset() {
		calls = 0;
		next = null;
		failure = null;
	}
	
	/** Shaped like IReceiptNumberGenerator: loaded or not. */
	public static class Generator {
		
		private final boolean loaded;
		
		public Generator(boolean loaded) {
			this.loaded = loaded;
		}
		
		public boolean isLoaded() {
			return loaded;
		}
	}
}
