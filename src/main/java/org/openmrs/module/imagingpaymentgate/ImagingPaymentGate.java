/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 */
package org.openmrs.module.imagingpaymentgate;

import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Refuses results on an imaging order whose payment is not confirmed (UVL-EMR#322).
 * <p>
 * An order is gated when its concept is configured (directly or as a member of a configured concept
 * set) or its order type is. It is paid when the Odoo bridge has set its payment Task to accepted;
 * see {@link GateStore#isPaid(String)}. A gated, unpaid order refuses:
 * <ul>
 * <li>any new or changed, non-voided obs that references it, alone or inside a group;</li>
 * <li>being marked COMPLETED.</li>
 * </ul>
 * Holders of {@link ImagingPaymentGateConstants#PRIV_BYPASS} are let through, with a WARN naming
 * them and the order. Orders that are not gated, and obs with no order, are never looked at.
 * <p>
 * Payment is looked up on every check and never cached: a refusal has to clear as soon as the
 * cashier is paid. The configuration is cached for {@link #CONFIG_TTL_MILLIS}.
 */
public class ImagingPaymentGate {

	private static final Logger log = LoggerFactory.getLogger(ImagingPaymentGate.class);

	static final long CONFIG_TTL_MILLIS = 60000L;

	private static volatile ImagingPaymentGate instance;

	private final GateStore store;

	private final Exemption exemption;

	private volatile GateConfig config;

	private volatile long configLoadedAt;

	public ImagingPaymentGate(GateStore store, Exemption exemption) {
		this.store = store;
		this.exemption = exemption;
	}

	/** The gate the advice and the interceptor share, reading the OpenMRS database. */
	public static ImagingPaymentGate get() {
		ImagingPaymentGate gate = instance;
		if (gate == null) {
			synchronized (ImagingPaymentGate.class) {
				if (instance == null) {
					instance = new ImagingPaymentGate(JdbcGateStore.onCurrentSession(), Exemption.BY_PRIVILEGE);
				}
				gate = instance;
			}
		}
		return gate;
	}

	/** Checks an obs being saved through ObsService, and the members of its group. */
	public void checkObs(Obs obs) {
		if (obs == null || !config().isEnabled()) {
			return;
		}
		checkObsTree(obs);
	}

	/** Checks a single obs about to be inserted, without its members, which are inserted on their own. */
	public void checkNewObs(Obs obs) {
		if (obs == null || Boolean.TRUE.equals(obs.getVoided()) || !config().isEnabled()) {
			return;
		}
		checkResult(obs.getOrder());
	}

	/** Checks every new or changed obs of an encounter being saved. */
	public void checkEncounter(Encounter encounter) {
		if (encounter == null || !config().isEnabled()) {
			return;
		}
		for (Obs obs : encounter.getObsAtTopLevel(false)) {
			checkObsTree(obs);
		}
	}

	/** Refuses to mark an unpaid imaging order COMPLETED. Other fulfiller statuses are not gated. */
	public void checkFulfillerStatus(Order order, Order.FulfillerStatus status) {
		if (status != Order.FulfillerStatus.COMPLETED || order == null || !config().isEnabled()) {
			return;
		}
		checkResult(order);
	}

	private void checkObsTree(Obs obs) {
		if (Boolean.TRUE.equals(obs.getVoided())) {
			return;
		}
		if (obs.getId() == null || obs.isDirty()) {
			checkResult(obs.getOrder());
		}
		if (obs.hasGroupMembers(false)) {
			for (Obs member : obs.getGroupMembers(false)) {
				checkObsTree(member);
			}
		}
	}

	private void checkResult(Order order) {
		if (order == null || !isGated(order, config())) {
			return;
		}
		boolean paid;
		RuntimeException lookupFailure = null;
		try {
			paid = store.isPaid(order.getUuid());
		}
		catch (RuntimeException e) {
			// Fail closed: a gate that opens whenever its lookup breaks is not a gate.
			paid = false;
			lookupFailure = e;
		}
		if (paid) {
			return;
		}
		if (exemption.isExempt()) {
			log.warn("Imaging payment gate BYPASSED by {} for order {} ({}): payment is not confirmed",
			    exemption.currentUser(), order.getOrderNumber(), order.getUuid());
			return;
		}
		log.warn("Imaging payment gate REFUSED results by {} on order {} ({}): {}", exemption.currentUser(),
		    order.getOrderNumber(), order.getUuid(),
		    lookupFailure == null ? "payment is not confirmed" : "payment check failed: " + lookupFailure);
		throw new ImagingPaymentRequiredException(refusal(order, lookupFailure != null), lookupFailure);
	}

	static boolean isGated(Order order, GateConfig config) {
		Concept concept = order.getConcept();
		if (concept != null && config.getConceptUuids().contains(concept.getUuid())) {
			return true;
		}
		OrderType orderType = order.getOrderType();
		return orderType != null && config.getOrderTypeUuids().contains(orderType.getUuid());
	}

	static String refusal(Order order, boolean lookupFailed) {
		String number = order.getOrderNumber() != null ? order.getOrderNumber() : order.getUuid();
		if (lookupFailed) {
			return "Résultats refusés : le paiement de la commande d'imagerie " + number
			        + " n'a pas pu être vérifié. Réessayez dans un instant ; si cela persiste, prévenez l'administrateur."
			        + " (Results refused: payment for imaging order " + number
			        + " could not be checked. Try again shortly; if it persists, tell the administrator.)";
		}
		return "Résultats refusés : le paiement de la commande d'imagerie " + number
		        + " n'est pas confirmé. Faites régler la facture à la caisse, puis réessayez."
		        + " (Results refused: payment for imaging order " + number
		        + " is not confirmed. Have the invoice paid at the cashier, then try again.)";
	}

	/**
	 * The cached configuration, reloaded once it is older than {@link #CONFIG_TTL_MILLIS}. If a
	 * reload fails, the last good configuration is kept; with none, the gate stays off and says so
	 * at ERROR, rather than refusing every result that carries an order.
	 */
	GateConfig config() {
		long now = System.currentTimeMillis();
		GateConfig current = config;
		if (current != null && now - configLoadedAt < CONFIG_TTL_MILLIS) {
			return current;
		}
		synchronized (this) {
			if (config != null && now - configLoadedAt < CONFIG_TTL_MILLIS) {
				return config;
			}
			try {
				GateConfig loaded = store.loadConfig();
				if (config == null || !loaded.toString().equals(config.toString())) {
					describe(loaded);
				}
				config = loaded;
				configLoadedAt = now;
			}
			catch (RuntimeException e) {
				log.error("Imaging payment gate could not read its configuration; {}", config == null
				        ? "it is OFF until it can" : "keeping the previous one (" + config + ")", e);
				if (config == null) {
					return GateConfig.DISABLED;
				}
				configLoadedAt = now;
			}
			return config;
		}
	}

	/** Drops the cached configuration so the next check reads it again. */
	void invalidate() {
		configLoadedAt = 0L;
	}

	private static void describe(GateConfig loaded) {
		if (!loaded.isEnabled()) {
			log.warn("Imaging payment gate is DISABLED ({}=false)", ImagingPaymentGateConstants.GP_ENABLED);
		} else if (loaded.isEmpty()) {
			log.warn("Imaging payment gate is enabled but gates nothing: set {}, {} or {}",
			    ImagingPaymentGateConstants.GP_CONCEPT_SET_UUIDS, ImagingPaymentGateConstants.GP_CONCEPT_UUIDS,
			    ImagingPaymentGateConstants.GP_ORDER_TYPE_UUIDS);
		} else {
			log.info("Imaging payment gate: {}", loaded);
		}
	}
}
