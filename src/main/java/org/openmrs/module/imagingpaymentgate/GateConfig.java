/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 */
package org.openmrs.module.imagingpaymentgate;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** What the gate applies to, as read from the global properties. Immutable. */
public final class GateConfig {

	public static final GateConfig DISABLED = new GateConfig(false, Collections.<String> emptySet(),
	        Collections.<String> emptySet());

	private final boolean enabled;

	private final Set<String> conceptUuids;

	private final Set<String> orderTypeUuids;

	/**
	 * @param conceptUuids the gated order concepts: the members of the configured sets plus the
	 *            individually configured concepts
	 */
	public GateConfig(boolean enabled, Set<String> conceptUuids, Set<String> orderTypeUuids) {
		this.enabled = enabled;
		this.conceptUuids = Collections.unmodifiableSet(new LinkedHashSet<String>(conceptUuids));
		this.orderTypeUuids = Collections.unmodifiableSet(new LinkedHashSet<String>(orderTypeUuids));
	}

	public boolean isEnabled() {
		return enabled;
	}

	public Set<String> getConceptUuids() {
		return conceptUuids;
	}

	public Set<String> getOrderTypeUuids() {
		return orderTypeUuids;
	}

	/** Enabled with nothing to gate: worth a warning, because it looks on and does nothing. */
	public boolean isEmpty() {
		return conceptUuids.isEmpty() && orderTypeUuids.isEmpty();
	}

	/** Comma-separated value to a set, dropping blanks. Null or blank gives an empty set. */
	static Set<String> parseList(String value) {
		Set<String> parsed = new LinkedHashSet<String>();
		if (value == null) {
			return parsed;
		}
		for (String entry : value.split(",")) {
			String trimmed = entry.trim();
			if (!trimmed.isEmpty()) {
				parsed.add(trimmed);
			}
		}
		return parsed;
	}

	/** Missing or blank means enabled: switching the gate off has to be explicit. */
	static boolean parseEnabled(String value) {
		return value == null || value.trim().isEmpty() || !"false".equalsIgnoreCase(value.trim());
	}

	@Override
	public String toString() {
		return "enabled=" + enabled + ", " + conceptUuids.size() + " gated concept(s), " + orderTypeUuids.size()
		        + " gated order type(s)";
	}
}
