package org.opencds.cqf.fhir.cr.questionnaireresponse.extract;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ca.uhn.fhir.context.FhirVersionEnum;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseBackboneElement;
import org.hl7.fhir.instance.model.api.IBaseCoding;
import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.opencds.cqf.fhir.utility.adapter.IQuestionnaireItemComponentAdapter;
import org.opencds.cqf.fhir.utility.adapter.IQuestionnaireResponseItemComponentAdapter;

class ProcessComponentItemTests {

    private final ProcessComponentItem sut = new ProcessComponentItem();

    @Test
    void processItem_throwsWhenCodeMapNullOrEmpty() {
        var request = mock(ExtractRequest.class, RETURNS_DEEP_STUBS);

        var answerItem = mock(IQuestionnaireResponseItemComponentAdapter.class);
        var questionnaireItem = mock(IQuestionnaireItemComponentAdapter.class);
        var resources = new ArrayList<IBaseResource>();

        // null map
        assertThrows(
            IllegalArgumentException.class,
            () -> sut.processItem(request, answerItem, questionnaireItem, null, resources, mock(IBaseReference.class)));

        // empty map
        assertThrows(
            IllegalArgumentException.class,
            () -> sut.processItem(request, answerItem, questionnaireItem, Map.of(), resources, mock(IBaseReference.class)));
    }

    @Test
    void processItem_doesNothingWhenNoAnswers() {
        var request = mock(ExtractRequest.class, RETURNS_DEEP_STUBS);
        when(request.getFhirVersion()).thenReturn(FhirVersionEnum.R4);

        var qItem = new org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent();
        var questionnaireItem = mock(IQuestionnaireItemComponentAdapter.class);
        when(questionnaireItem.get()).thenReturn(qItem);

        var qrItem = new org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemComponent();
        qrItem.setLinkId("x");

        var answerItem = mock(IQuestionnaireResponseItemComponentAdapter.class);
        when(answerItem.get()).thenReturn(qrItem);

        when(request.resolvePathList(eq((IBase) qrItem), eq("answer"), eq(IBaseBackboneElement.class)))
            .thenReturn(List.of());

        Map<String, List<IBaseCoding>> codeMap = new HashMap<>();
        codeMap.put("x", List.of(mock(IBaseCoding.class)));

        var observation = new org.hl7.fhir.r4.model.Observation();
        var resources = new ArrayList<IBaseResource>();
        resources.add(observation);

        sut.processItem(request, answerItem, questionnaireItem, codeMap, resources, mock(IBaseReference.class));

        assertEquals(0, observation.getComponent().size(), "No answers -> no components added");
    }

    @Test
    void processItem_r4_addsComponent_andWrapsCodingValueIntoCodeableConcept() {
        var request = mock(ExtractRequest.class, RETURNS_DEEP_STUBS);
        when(request.getFhirVersion()).thenReturn(FhirVersionEnum.R4);

        var qItem = new org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent();
        qItem.addCode(new org.hl7.fhir.r4.model.Coding("http://loinc.org", "1234-5", null));
        var questionnaireItem = mock(IQuestionnaireItemComponentAdapter.class);
        when(questionnaireItem.get()).thenReturn(qItem);

        var qrItem = new org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemComponent();
        qrItem.setLinkId("link1");

        var answer = new org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent();
        answer.setValue(new org.hl7.fhir.r4.model.Coding("http://snomed.info/sct", "999", null));

        var answerItem = mock(IQuestionnaireResponseItemComponentAdapter.class);
        when(answerItem.get()).thenReturn(qrItem);

        when(request.resolvePathList(eq((IBase) qrItem), eq("answer"), eq(IBaseBackboneElement.class)))
            .thenReturn(List.of(answer));
        when(request.resolvePathList(eq((IBase) answer), eq("item"), eq(IBaseBackboneElement.class)))
            .thenReturn(List.of()); // no nested items
        when(request.resolvePathString(eq((IBase) qrItem), eq("linkId"))).thenReturn("link1");

        Map<String, List<IBaseCoding>> codeMap = new HashMap<>();
        codeMap.put("link1", List.of(mock(IBaseCoding.class)));

        var observation = new org.hl7.fhir.r4.model.Observation();
        var resources = new ArrayList<IBaseResource>();
        resources.add(observation);

        sut.processItem(request, answerItem, questionnaireItem, codeMap, resources, mock(IBaseReference.class));

        assertEquals(1, observation.getComponent().size());
        var component = observation.getComponentFirstRep();

        // Code copied from questionnaire item codes
        assertEquals(1, component.getCode().getCoding().size());
        assertEquals("1234-5", component.getCode().getCodingFirstRep().getCode());

        // valueCoding wrapped into CodeableConcept
        assertTrue(component.getValue() instanceof org.hl7.fhir.r4.model.CodeableConcept);
        var cc = (org.hl7.fhir.r4.model.CodeableConcept) component.getValue();
        assertEquals("999", cc.getCodingFirstRep().getCode());
    }

    @Test
    void processItem_r4_addsComponent_andSetsNonCodingValueDirectly() {
        var request = mock(ExtractRequest.class, RETURNS_DEEP_STUBS);
        when(request.getFhirVersion()).thenReturn(FhirVersionEnum.R4);

        var qItem = new org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent();
        qItem.addCode(new org.hl7.fhir.r4.model.Coding("http://loinc.org", "2222-2", null));
        var questionnaireItem = mock(IQuestionnaireItemComponentAdapter.class);
        when(questionnaireItem.get()).thenReturn(qItem);

        var qrItem = new org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemComponent();
        qrItem.setLinkId("link2");

        var answer = new org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent();
        answer.setValue(new org.hl7.fhir.r4.model.StringType("hello"));

        var answerItem = mock(IQuestionnaireResponseItemComponentAdapter.class);
        when(answerItem.get()).thenReturn(qrItem);

        when(request.resolvePathList(eq((IBase) qrItem), eq("answer"), eq(IBaseBackboneElement.class)))
            .thenReturn(List.of(answer));
        when(request.resolvePathList(eq((IBase) answer), eq("item"), eq(IBaseBackboneElement.class)))
            .thenReturn(List.of()); // no nested
        when(request.resolvePathString(eq((IBase) qrItem), eq("linkId"))).thenReturn("link2");

        Map<String, List<IBaseCoding>> codeMap = new HashMap<>();
        codeMap.put("link2", List.of(mock(IBaseCoding.class)));

        var observation = new org.hl7.fhir.r4.model.Observation();
        var resources = new ArrayList<IBaseResource>();
        resources.add(observation);

        sut.processItem(request, answerItem, questionnaireItem, codeMap, resources, mock(IBaseReference.class));

        assertEquals(1, observation.getComponent().size());
        var component = observation.getComponentFirstRep();

        assertTrue(component.getValue() instanceof org.hl7.fhir.r4.model.StringType);
        assertEquals("hello", ((org.hl7.fhir.r4.model.StringType) component.getValue()).getValue());
    }

    @Test
    void processItem_r4_nestedAnswerItems_recursesAndAddsComponentOnNested() {
        var request = mock(ExtractRequest.class, RETURNS_DEEP_STUBS);
        when(request.getFhirVersion()).thenReturn(FhirVersionEnum.R4);

        // Questionnaire item provides the component code
        var qItem = new org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent();
        qItem.addCode(new org.hl7.fhir.r4.model.Coding("http://loinc.org", "3333-3", null));
        var questionnaireItem = mock(IQuestionnaireItemComponentAdapter.class);
        when(questionnaireItem.get()).thenReturn(qItem);

        // Outer QR item (not in map, so should not add at this level)
        var outerQrItem = new org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemComponent();
        outerQrItem.setLinkId("outer");

        // Outer answer contains nested item
        var outerAnswer = new org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent();

        // Nested QR item (this one is in the map)
        var nestedQrItem = new org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemComponent();
        nestedQrItem.setLinkId("nested");

        // Nested answer with primitive value
        var nestedAnswer = new org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent();
        nestedAnswer.setValue(new org.hl7.fhir.r4.model.StringType("nested-value"));

        var outerAdapter = mock(IQuestionnaireResponseItemComponentAdapter.class);
        when(outerAdapter.get()).thenReturn(outerQrItem);

        var nestedAdapter = mock(IQuestionnaireResponseItemComponentAdapter.class);
        when(nestedAdapter.get()).thenReturn(nestedQrItem);

        // Factory should wrap the nested QR item into an adapter
        when(request.getAdapterFactory().createQuestionnaireResponseItem(eq((IBaseBackboneElement) nestedQrItem)))
            .thenReturn(nestedAdapter);

        // resolvePathList behavior depending on the base + path
        Answer<List<IBaseBackboneElement>> resolveAnswer = inv -> {
            IBase base = inv.getArgument(0);
            String path = inv.getArgument(1);

            if ("answer".equals(path) && base == outerQrItem) {
                return List.of(outerAnswer);
            }
            if ("answer".equals(path) && base == nestedQrItem) {
                return List.of(nestedAnswer);
            }
            if ("item".equals(path) && base == outerAnswer) {
                return List.of(nestedQrItem);
            }
            if ("item".equals(path) && base == nestedAnswer) {
                return List.of();
            }
            return List.of();
        };

        when(request.resolvePathList(any(IBase.class), anyString(), eq(IBaseBackboneElement.class))).thenAnswer(resolveAnswer);

        when(request.resolvePathString(eq((IBase) outerQrItem), eq("linkId"))).thenReturn("outer");
        when(request.resolvePathString(eq((IBase) nestedQrItem), eq("linkId"))).thenReturn("nested");

        Map<String, List<IBaseCoding>> codeMap = new HashMap<>();
        codeMap.put("nested", List.of(mock(IBaseCoding.class)));

        var observation = new org.hl7.fhir.r4.model.Observation();
        var resources = new ArrayList<IBaseResource>();
        resources.add(observation);

        sut.processItem(request, outerAdapter, questionnaireItem, codeMap, resources, mock(IBaseReference.class));

        assertEquals(1, observation.getComponent().size(), "Nested item should add exactly one component");
        assertEquals("nested-value", ((org.hl7.fhir.r4.model.StringType) observation.getComponentFirstRep().getValue()).getValue());
    }

    @Test
    void processItem_r5_addsComponent_andWrapsCodingValueIntoCodeableConcept() {
        var request = mock(ExtractRequest.class, RETURNS_DEEP_STUBS);
        when(request.getFhirVersion()).thenReturn(FhirVersionEnum.R5);

        var qItem = new org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemComponent();
        qItem.addCode(new org.hl7.fhir.r5.model.Coding("http://loinc.org", "7777-7", null));
        var questionnaireItem = mock(IQuestionnaireItemComponentAdapter.class);
        when(questionnaireItem.get()).thenReturn(qItem);

        var qrItem = new org.hl7.fhir.r5.model.QuestionnaireResponse.QuestionnaireResponseItemComponent();
        qrItem.setLinkId("r5");

        var answer = new org.hl7.fhir.r5.model.QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent();
        answer.setValue(new org.hl7.fhir.r5.model.Coding("http://hl7.org/fhir/observation-status", "final", null));

        var answerItem = mock(IQuestionnaireResponseItemComponentAdapter.class);
        when(answerItem.get()).thenReturn(qrItem);

        when(request.resolvePathList(eq((IBase) qrItem), eq("answer"), eq(IBaseBackboneElement.class)))
            .thenReturn(List.of(answer));
        when(request.resolvePathList(eq((IBase) answer), eq("item"), eq(IBaseBackboneElement.class)))
            .thenReturn(List.of());
        when(request.resolvePathString(eq((IBase) qrItem), eq("linkId"))).thenReturn("r5");

        Map<String, List<IBaseCoding>> codeMap = new HashMap<>();
        codeMap.put("r5", List.of(mock(IBaseCoding.class)));

        var observation = new org.hl7.fhir.r5.model.Observation();
        var resources = new ArrayList<IBaseResource>();
        resources.add(observation);

        sut.processItem(request, answerItem, questionnaireItem, codeMap, resources, mock(IBaseReference.class));

        assertEquals(1, observation.getComponent().size());
        var component = observation.getComponentFirstRep();

        assertEquals(1, component.getCode().getCoding().size());
        assertEquals("7777-7", component.getCode().getCodingFirstRep().getCode());

        assertTrue(component.getValue() instanceof org.hl7.fhir.r5.model.CodeableConcept);
        var cc = (org.hl7.fhir.r5.model.CodeableConcept) component.getValue();
        assertEquals("final", cc.getCodingFirstRep().getCode());
    }
}
