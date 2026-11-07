# openmrs-module-imagingpaymentgate

Refuses results on an imaging order whose payment is not confirmed (UVL-EMR#322), whichever
client saves them: the chart's "Entrer des résultats", the Laboratory app, REST or FHIR.

## How it decides

- **Gated order:** its concept is in `imagingpaymentgate.conceptUuids` or is a member of a set in
  `imagingpaymentgate.conceptSetUuids`, or its order type is in `imagingpaymentgate.orderTypeUuids`.
  Use the same concept set the EIP bridges read (`RADIOLOGY_CONCEPT_SET_UUID`), so the gate never
  holds an order the Odoo bridge cannot mark paid.
- **Paid:** a FHIR Task with status `accepted` whose `basedOn` is this order's ServiceRequest. The
  eip-odoo-openmrs bridge sets it once the order's invoice line is paid. The lookup is SQL on
  `fhir_task` / `fhir_task_based_on_reference` / `fhir_reference`, keyed on the order uuid, not a
  FHIR search: FHIR2 silently ignores search parameters it does not support.
- **Refused:** a new or changed, non-voided obs that references a gated, unpaid order (alone or in
  a group), and marking such an order COMPLETED. The REST API answers **402** with a French and
  English message in `error.message`. A failed payment lookup also refuses.
- **Exempt:** holders of the `Bypass Imaging Payment Gate` privilege (superusers included). Each use
  is logged at WARN.
- `imagingpaymentgate.enabled=false` turns it off. Configuration is re-read every 60 s; payment is
  looked up on every check.

## Where it hooks

- AOP before-advice on `ObsService`, `EncounterService` and `OrderService` (config.xml): refuses
  before anything is written.
- A Hibernate interceptor on Obs inserts (moduleApplicationContext.xml): the backstop for writes that
  bypass the services, such as a FHIR Observation with `basedOn`.

## Admitted patients' visits stay open (UVL-EMR#246)

Core's Auto Close Visits Task calls `VisitService.stopVisits(Date)`, which closes every open visit
of a type in `visits.autoCloseVisitType`, admitted patients included. An around-advice on
`VisitService` (`KeepAdmittedVisitsOpenAdvice`) replaces that one method with the same loop (core's
`VisitDAO.getNextVisit` / `saveVisit`, one stop time per run, flush every 50) that skips a visit
whose latest non-voided Admission or Discharge encounter is an Admission. Transfers do not change
that. The encounter types are emrapi's metadata mappings `emr.admissionEncounterType` and
`emr.exitFromInpatientEncounterType`, the ones the ward app writes; with no admission type mapped the
run is core's, with a WARN. The task, its class and its start time are core's (#350 sets 21:59:59
UTC). `imagingpaymentgate.keepAdmittedVisitsOpen=false` gives core's behaviour back.

## Build

    mvn clean package   # target/imagingpaymentgate-<version>.omod

Requires OpenMRS core 2.7.6 or later.
