/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 */
package org.openmrs.module.imagingpaymentgate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.openmrs.module.Module;
import org.openmrs.module.ModuleFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads the billing module's receipt number generator once, at start-up, before any order is
 * billed (UVL-EMR#846).
 * <p>
 * {@code ReceiptNumberGeneratorFactory.getGenerator()} (billing 2.3.0 to 2.5.0) creates and loads
 * the generator on first use, without a lock, and assigns the static field before {@code load()}
 * has run. Orders signed together right after a start are billed on one thread each, and the
 * first of them can be handed a generator that is not loaded yet: its bill is skipped with
 * "Cannot invoke ...getGroupingType() because this.model is null". Calling the factory here, on
 * the one thread that refreshes the context and before requests are served, creates and loads the
 * generator without any other caller.
 * <p>
 * Billing is reached by reflection through its own class loader: there is no compile or
 * {@code require_modules} dependency, so this module starts, and does nothing, where billing is
 * absent. It never throws: a failed warm-up leaves the generator to its first use, as before.
 */
public final class ReceiptNumberGeneratorWarmUp {
	
	private static final Logger log = LoggerFactory.getLogger(ReceiptNumberGeneratorWarmUp.class);
	
	static final String BILLING_MODULE_ID = "billing";
	
	static final String FACTORY_CLASS = "org.openmrs.module.billing.api.ReceiptNumberGeneratorFactory";
	
	private ReceiptNumberGeneratorWarmUp() {
	}
	
	/** Warms the generator of the started billing module, if there is one. */
	public static void run() {
		try {
			Module billing = ModuleFactory.getStartedModuleById(BILLING_MODULE_ID);
			if (billing == null) {
				log.info("Receipt number generator not warmed: the billing module is not started");
				return;
			}
			warm(ModuleFactory.getModuleClassLoader(billing));
		}
		catch (RuntimeException | LinkageError e) {
			log.warn("Receipt number generator not warmed", e);
		}
	}
	
	/**
	 * @param billingClassLoader the class loader that sees billing's factory
	 * @return true if a loaded generator was obtained
	 */
	static boolean warm(ClassLoader billingClassLoader) {
		try {
			Class<?> factory = Class.forName(FACTORY_CLASS, true, billingClassLoader);
			Object generator = factory.getMethod("getGenerator").invoke(null);
			if (generator == null) {
				log.warn("Receipt number generator not warmed: billing.systemReceiptNumberGenerator is not set");
				return false;
			}
			Method isLoaded = generator.getClass().getMethod("isLoaded");
			boolean loaded = Boolean.TRUE.equals(isLoaded.invoke(generator));
			if (loaded) {
				log.info("Receipt number generator warmed at start-up: {} loaded (UVL-EMR#846)",
				    generator.getClass().getName());
			} else {
				log.warn("Receipt number generator not warmed: {} is not loaded", generator.getClass().getName());
			}
			return loaded;
		}
		catch (ClassNotFoundException e) {
			log.info("Receipt number generator not warmed: no billing factory class ({})", e.getMessage());
		}
		catch (InvocationTargetException e) {
			log.warn("Receipt number generator not warmed: getGenerator() failed", e.getCause());
		}
		catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			log.warn("Receipt number generator not warmed", e);
		}
		return false;
	}
}
