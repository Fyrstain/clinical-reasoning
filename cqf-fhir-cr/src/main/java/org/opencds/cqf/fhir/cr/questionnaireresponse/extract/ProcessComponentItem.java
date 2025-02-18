package org.opencds.cqf.fhir.cr.questionnaireresponse.extract;

import org.hl7.fhir.instance.model.api.IBaseBackboneElement;
import org.hl7.fhir.instance.model.api.IBaseCoding;
import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationComponentComponent;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent;
import org.hl7.fhir.r4.model.Type;
import org.hl7.fhir.r5.model.DataType;
import org.hl7.fhir.r5.model.QuestionnaireResponse;
import java.util.List;
import java.util.Map;

public class ProcessComponentItem {
    public void processItem(
            ExtractRequest request,
            IBaseBackboneElement answerItem,
            IBaseBackboneElement questionnaireItem,
            Map<String, List<IBaseCoding>> questionnaireCodeMap,
            List<IBaseResource> resources,
            IBaseReference subject) {
        if (questionnaireCodeMap == null || questionnaireCodeMap.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unable to retrieve Questionnaire code map for Observation based extraction");
        }
        var answers = request.resolvePathList(answerItem, "answer", IBaseBackboneElement.class);
        if (!answers.isEmpty()) {
            answers.forEach(answer -> {
                var answerItems = request.getItems(answer);
                if (!answerItems.isEmpty()) {
                    answerItems.forEach(answerChild -> processItem(
                            request, answerChild, questionnaireItem, questionnaireCodeMap, resources, subject));
                } else {
                    var linkId = request.resolvePathString(answerItem, "linkId");
                    if (questionnaireCodeMap.get(linkId) != null
                            && !questionnaireCodeMap.get(linkId).isEmpty()) {
                        IBaseResource observation = resources.get(resources.size() - 1);
                        addComponentToObservation(
                                observation,
                                request,
                                answer,
                                questionnaireItem,
                                linkId,
                                subject,
                                questionnaireCodeMap);
                    }
                }
            });
        }
    }

    private void addComponentToObservation(
            IBaseResource observation,
            ExtractRequest request,
            IBaseBackboneElement answer,
            IBaseBackboneElement questionnaireItem,
            String linkId,
            IBaseReference subject,
            Map<String, List<IBaseCoding>> questionnaireCodeMap) {

        switch (request.getFhirVersion()) {
            case R4:
                Observation r4Observation = (Observation) observation;

                List<Coding> codes = ((QuestionnaireItemComponent) questionnaireItem).getCode();
                ObservationComponentComponent component = r4Observation.addComponent();
                codes.forEach(component.getCode()::addCoding);
                Type value = ((QuestionnaireResponseItemAnswerComponent) answer).getValue();
                if (value instanceof Coding) {
                    component.setValue(new CodeableConcept().addCoding((Coding) value));
                } else {
                    component.setValue(value);
                }
                return;
            case R5:
                org.hl7.fhir.r5.model.Observation r5Observation = (org.hl7.fhir.r5.model.Observation) observation;
                List<org.hl7.fhir.r5.model.Coding> r5codes = ((org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemComponent) questionnaireItem).getCode();
                org.hl7.fhir.r5.model.Observation.ObservationComponentComponent r5componentCodes = r5Observation.addComponent();
                r5codes.forEach(r5componentCodes.getCode()::addCoding);
                DataType valueR5 = ((QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent) answer).getValue();
                if (valueR5 instanceof org.hl7.fhir.r5.model.Coding) {
                    r5componentCodes.setValue(new org.hl7.fhir.r5.model.CodeableConcept().addCoding((org.hl7.fhir.r5.model.Coding) valueR5));
                } else {
                    r5componentCodes.setValue(valueR5);
                }
        }
    }
}
