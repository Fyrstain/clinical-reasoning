package org.opencds.cqf.fhir.cr.questionnaireresponse.extract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.SerializationUtils;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseCoding;
import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.opencds.cqf.fhir.utility.BundleHelper;
import org.opencds.cqf.fhir.utility.Constants;
import org.opencds.cqf.fhir.utility.adapter.IItemComponentAdapter;
import org.opencds.cqf.fhir.utility.adapter.IQuestionnaireResponseItemComponentAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtractProcessor implements IExtractProcessor {
    protected static final Logger logger = LoggerFactory.getLogger(ExtractProcessor.class);
    protected final ProcessItem itemProcessor;
    protected final ProcessComponentItem componentItemProcessor;
    protected final ProcessDefinitionItem definitionItemProcessor;

    public ExtractProcessor() {
        this(new ProcessItem(), new ProcessComponentItem(), new ProcessDefinitionItem());
    }

    private ExtractProcessor(ProcessItem processItem, ProcessComponentItem componentItemProcessor, ProcessDefinitionItem processDefinitionItem) {
        this.itemProcessor = processItem;
        this.componentItemProcessor = componentItemProcessor;
        this.definitionItemProcessor = processDefinitionItem;
    }

    @Override
    public IBaseBundle extract(ExtractRequest request) {
        var resources = processItems(request);
        return createBundle(request, resources);
    }

    @Override
    public List<IBaseResource> processItems(ExtractRequest request) {
        var resources = new ArrayList<IBaseResource>();
        var subject = (IBaseReference) request.resolvePath(request.getQuestionnaireResponse(), "subject");
        var extractionExt = request.getDefinitionExtract();
        if (extractionExt != null) {
            processDefinitionItem(request, new ItemPair(null, null), resources, subject);
        } else {
            var questionnaireCodeMap = CodeMap.create(request);
            request.getQuestionnaireResponseAdapter().getItem().forEach(item -> {
                var itemPair = new ItemPair(request.getQuestionnaireItem(item), item);
                if (!item.getItem().isEmpty()) {
                    processGroupItem(request, itemPair, questionnaireCodeMap, resources, subject);
                } else {
                    processItem(request, itemPair, questionnaireCodeMap, resources, subject);
                }
            });
        }

        return resources;
    }

    protected IBaseBundle createBundle(ExtractRequest request, List<IBaseResource> resources) {
        var bundle = BundleHelper.newBundle(request.getFhirVersion(), request.getExtractId(), "transaction");
        resources.forEach(r -> {
            var entry = BundleHelper.newEntryWithResource(r);
            var fullUrl = generateFullUrl(r);
            request.getModelResolver().setValue(entry, "fullUrl", canonicalTypeForFullUrl(request, fullUrl));

            var req = BundleHelper.newRequest(request.getFhirVersion(), "PUT");
            var idPart = r.getIdElement() != null ? r.getIdElement().getIdPart() : null;
            var url = r.fhirType() + "/" + (idPart != null ? idPart : "");
            request.getModelResolver().setValue(req, "url", canonicalTypeForFullUrl(request, url));

            BundleHelper.setEntryRequest(request.getFhirVersion(), entry, req);
            BundleHelper.addEntry(bundle, entry);
        });
        return bundle;
    }

    private static String generateFullUrl(IBaseResource resource) {
        var idPart = resource.getIdElement() != null ? resource.getIdElement().getIdPart() : null;
        if (idPart != null) {
            return "http://example.org/fhir/" + resource.fhirType() + "/" + idPart;
        }
        return java.util.UUID.randomUUID().toString();
    }

    private static Object canonicalTypeForFullUrl(ExtractRequest request, String value) {
        switch (request.getFhirVersion()) {
            case R4:
                return new org.hl7.fhir.r4.model.UriType(value);
            case R5:
                return new org.hl7.fhir.r5.model.UriType(value);
            default:
                return value;
        }
    }

    protected void processGroupItem(
        ExtractRequest request,
        ItemPair item,
        Map<String, List<IBaseCoding>> questionnaireCodeMap,
        List<IBaseResource> resources,
        IBaseReference subject) {
        var subjectItems = item.getResponseItem().getItem().stream()
            .filter(child -> child.getExtension().stream()
                .anyMatch(e -> e.getUrl().equals(Constants.SDC_QUESTIONNAIRE_RESPONSE_IS_SUBJECT)))
            .map(IQuestionnaireResponseItemComponentAdapter.class::cast)
            .toList();
        var groupSubject = !subjectItems.isEmpty()
            ? (IBaseReference) subjectItems.get(0).getAnswer().get(0).getValue()
            : SerializationUtils.clone(subject);
        if (request.isDefinitionItem(item)) {
            processDefinitionItem(request, item, resources, groupSubject);
        }
        else if (request.isObservationItem(item)) {
            processObservationItem(request, item, questionnaireCodeMap, resources, groupSubject);
            final var questionnaireChildItems =
                (item.getItem() != null && item.getItem().hasItem())
                    ? item.getItem().getItem()
                    : java.util.Collections.<IItemComponentAdapter>emptyList();

            // Process children with a parent observation ?
            item.getResponseItem().getItem().forEach(childResponseItem -> {
                if (childResponseItem.getExtension().stream()
                    .noneMatch(
                        e -> e.getUrl().equals(Constants.SDC_QUESTIONNAIRE_RESPONSE_IS_SUBJECT))) {
                    var childItem = new ItemPair(
                        request.getQuestionnaireItem(childResponseItem, questionnaireChildItems),
                        childResponseItem
                    );
                    if (childResponseItem.hasItem()) {
                        processGroupItem(request, childItem, questionnaireCodeMap, resources,
                            groupSubject);
                    } else if (request.hasAnswer(item)) {
                        processItem(request, childItem, questionnaireCodeMap, resources,
                            groupSubject);
                    } else {
                        processComponentItem(request, childItem, questionnaireCodeMap, resources,
                            groupSubject);
                    }
                }
            });
        }
        else {
            final var questionnaireChildItems =
                (item.getItem() != null && item.getItem().hasItem())
                    ? item.getItem().getItem()
                    : java.util.Collections.<IItemComponentAdapter>emptyList();
            item.getResponseItem().getItem().forEach(childResponseItem -> {
                if (childResponseItem.getExtension().stream()
                    .noneMatch(e -> e.getUrl().equals(Constants.SDC_QUESTIONNAIRE_RESPONSE_IS_SUBJECT))) {
                    var childPair = new ItemPair(
                        request.getQuestionnaireItem(childResponseItem, questionnaireChildItems),
                        childResponseItem
                    );
                    if (childResponseItem.hasItem()) {
                        processGroupItem(request, childPair, questionnaireCodeMap, resources, groupSubject);
                    } else {
                        processObservationItem(request, childPair, questionnaireCodeMap, resources, groupSubject);
                    }
                }
            });
        }
    }

    protected void processItem(
        ExtractRequest request,
        ItemPair item,
        Map<String, List<IBaseCoding>> questionnaireCodeMap,
        List<IBaseResource> resources,
        IBaseReference subject) {
        if (request.isDefinitionItem(item)) {
            processDefinitionItem(request, item, resources, subject);
        } else {
            processObservationItem(request, item, questionnaireCodeMap, resources, subject);
        }
    }

    protected void processObservationItem(
        ExtractRequest request,
        ItemPair item,
        Map<String, List<IBaseCoding>> questionnaireCodeMap,
        List<IBaseResource> resources,
        IBaseReference subject) {
        try {
            itemProcessor.processItem(request, item, questionnaireCodeMap, resources, subject);
        } catch (Exception e) {
            request.logException(e.getMessage());
            throw e;
        }
    }

    protected void processComponentItem(
        ExtractRequest request,
        ItemPair item,
        Map<String, List<IBaseCoding>> questionnaireCodeMap,
        List<IBaseResource> resources,
        IBaseReference subject) {
        try {
            componentItemProcessor.processItem(
                request, item.getResponseItem(), item.getItem(), questionnaireCodeMap, resources, subject);
        } catch (Exception e) {
            request.logException(e.getMessage());
            throw e;
        }
    }

    protected void processDefinitionItem(ExtractRequest request, ItemPair item, List<IBaseResource> resources, IBaseReference subject) {
        try {
            var resource = definitionItemProcessor.processDefinitionItem(request, item, subject);
            if (resource != null) {
                resources.add(resource);
            }
        } catch (Exception e) {
            request.logException(e.getMessage());
            throw e;
        }
    }
}