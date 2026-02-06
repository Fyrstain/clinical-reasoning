package org.opencds.cqf.fhir.cr.questionnaireresponse.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirVersionEnum;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.hl7.fhir.instance.model.api.IBaseCoding;
import org.hl7.fhir.instance.model.api.IBaseExtension;
import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opencds.cqf.cql.engine.model.ModelResolver;
import org.opencds.cqf.fhir.utility.adapter.IItemComponentAdapter;
import org.opencds.cqf.fhir.utility.adapter.IQuestionnaireResponseAdapter;
import org.opencds.cqf.fhir.utility.adapter.IQuestionnaireResponseItemComponentAdapter;

@ExtendWith(MockitoExtension.class)
class ExtractProcessorTests {

    private static org.hl7.fhir.r4.model.Extension extWithUrl(String url) {
        var e = new org.hl7.fhir.r4.model.Extension();
        e.setUrl(url);
        return e;
    }

    @Test
    void processItems_whenDefinitionExtractPresent_callsDefinitionItemOnce() {
        var processor = new RecordingExtractProcessor();

        var request = mock(ExtractRequest.class);
        when(request.getDefinitionExtract()).thenReturn(mock(IBaseExtension.class));

        var qr = new org.hl7.fhir.r4.model.QuestionnaireResponse();
        when(request.getQuestionnaireResponse()).thenReturn(qr);
        when(request.resolvePath(eq(qr), eq("subject"))).thenReturn(new Reference("Patient/123"));

        var resources = processor.processItems(request);

        assertEquals(1, processor.definitionCalls.get(),
            "definition item processor should be called once");
        assertEquals(0, processor.groupCalls.get(),
            "no group processing when definition extract is present");
        assertEquals(0, processor.observationCalls.get(),
            "no observation items processed in definition branch");
        assertEquals(1, resources.size(), "definition branch should add one resource");
        assertEquals("Patient", resources.get(0).fhirType());
    }

    @Test
    void processItems_whenNoDefinitionExtract_dispatchesGroupVsLeaf() {
        var processor = spy(new RecordingExtractProcessor());

        var request = mock(ExtractRequest.class);
        when(request.getDefinitionExtract()).thenReturn(null);

        var qr = new org.hl7.fhir.r4.model.QuestionnaireResponse();
        when(request.getQuestionnaireResponse()).thenReturn(qr);
        when(request.resolvePath(eq(qr), eq("subject"))).thenReturn(new Reference("Patient/123"));

        var responseAdapter = mock(IQuestionnaireResponseAdapter.class);
        when(request.getQuestionnaireResponseAdapter()).thenReturn(responseAdapter);

        var groupTop = mock(IQuestionnaireResponseItemComponentAdapter.class);
        var groupTopChildren = new ArrayList<IQuestionnaireResponseItemComponentAdapter>();
        groupTopChildren.add(mock(IQuestionnaireResponseItemComponentAdapter.class));
        // IMPORTANT: doReturn avoids generic/wildcard Mockito issues
        doReturn(groupTopChildren).when(groupTop).getItem();

        var leafTop = mock(IQuestionnaireResponseItemComponentAdapter.class);
        doReturn(Collections.emptyList()).when(leafTop).getItem();

        doReturn(List.of(groupTop, leafTop)).when(responseAdapter).getItem();

        when(request.getQuestionnaireItem(any(IItemComponentAdapter.class))).thenReturn(null);

        try (MockedStatic<CodeMap> mocked = mockStatic(CodeMap.class)) {
            mocked.when(() -> CodeMap.create(any())).thenReturn(new HashMap<>());

            doNothing().when(processor).processGroupItem(any(), any(), anyMap(), anyList(), any());
            doNothing().when(processor).processItem(any(), any(), anyMap(), anyList(), any());

            var resources = processor.processItems(request);

            assertNotNull(resources);
            verify(processor, times(1)).processGroupItem(eq(request), any(ItemPair.class), anyMap(),
                anyList(), any());
            verify(processor, times(1)).processItem(eq(request), any(ItemPair.class), anyMap(),
                anyList(), any());
        }
    }

    @Test
    void createBundle_buildsTransactionBundle_andSetsFullUrlAndRequestUrl() {
        var processor = new ExtractProcessor();

        var request = mock(ExtractRequest.class);
        when(request.getFhirVersion()).thenReturn(FhirVersionEnum.R4);
        when(request.getExtractId()).thenReturn("extract-123");

        ModelResolver modelResolver = mock(ModelResolver.class);
        when(request.getModelResolver()).thenReturn(modelResolver);

        var p = new Patient();
        p.setId("Patient/p1");

        var obs = new Observation(); // no id
        List<IBaseResource> resources = List.of(p, obs);

        var bundleBase = processor.createBundle(request, resources);
        assertNotNull(bundleBase);
        assertInstanceOf(Bundle.class, bundleBase);

        var bundle = (Bundle) bundleBase;
        assertEquals(2, bundle.getEntry().size(), "bundle should have one entry per resource");
        assertEquals(Bundle.BundleType.TRANSACTION, bundle.getType(), "bundle must be transaction");

        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(modelResolver, atLeast(4)).setValue(any(), anyString(), valueCaptor.capture());

        boolean sawR4UriType = valueCaptor.getAllValues().stream()
            .anyMatch(v -> v instanceof org.hl7.fhir.r4.model.UriType);
        assertTrue(sawR4UriType, "expected R4 UriType values to be set on fullUrl/url");
    }

    @Test
    void extract_callsProcessItems_thenCreateBundle() {
        var processor = spy(new RecordingExtractProcessor());

        var request = mock(ExtractRequest.class);
        when(request.getFhirVersion()).thenReturn(FhirVersionEnum.R4);
        when(request.getExtractId()).thenReturn("extract-abc");
        when(request.getModelResolver()).thenReturn(mock(ModelResolver.class));

        when(request.getDefinitionExtract()).thenReturn(mock(IBaseExtension.class));

        var qr = new org.hl7.fhir.r4.model.QuestionnaireResponse();
        when(request.getQuestionnaireResponse()).thenReturn(qr);
        when(request.resolvePath(eq(qr), eq("subject"))).thenReturn(new Reference("Patient/123"));

        var bundle = processor.extract(request);
        assertNotNull(bundle);

        verify(processor, times(1)).processItems(eq(request));
        assertInstanceOf(Bundle.class, bundle);
    }

    static class RecordingExtractProcessor extends ExtractProcessor {

        final AtomicInteger definitionCalls = new AtomicInteger(0);
        final AtomicInteger observationCalls = new AtomicInteger(0);
        final AtomicInteger componentCalls = new AtomicInteger(0);
        final AtomicInteger groupCalls = new AtomicInteger(0);

        @Override
        protected void processGroupItem(
            ExtractRequest request,
            ItemPair item,
            Map<String, List<IBaseCoding>> questionnaireCodeMap,
            List<IBaseResource> resources,
            IBaseReference subject) {
            groupCalls.incrementAndGet();
            super.processGroupItem(request, item, questionnaireCodeMap, resources, subject);
        }

        @Override
        protected void processDefinitionItem(
            ExtractRequest request,
            ItemPair item,
            List<IBaseResource> resources,
            IBaseReference subject) {

            definitionCalls.incrementAndGet();
            var p = new Patient();
            p.setId("Patient/def-" + definitionCalls.get());
            resources.add(p);
        }

        @Override
        protected void processObservationItem(
            ExtractRequest request,
            ItemPair item,
            Map<String, List<IBaseCoding>> questionnaireCodeMap,
            List<IBaseResource> resources,
            IBaseReference subject) {

            observationCalls.incrementAndGet();
            var obs = new Observation();
            obs.setId("Observation/obs-" + observationCalls.get());
            obs.setSubject((Reference) subject);
            resources.add(obs);
        }

        @Override
        protected void processComponentItem(
            ExtractRequest request,
            ItemPair item,
            Map<String, List<IBaseCoding>> questionnaireCodeMap,
            List<IBaseResource> resources,
            IBaseReference subject) {
            componentCalls.incrementAndGet();
        }
    }
}
