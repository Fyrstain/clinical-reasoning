package org.opencds.cqf.fhir.cr.questionnaireresponse.extract;

import java.util.List;
import java.util.UUID;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.opencds.cqf.fhir.utility.Ids;

public class ResponseBundle {
    public static IBaseBundle createBundleDstu3(String extractId, List<IBaseResource> resources) {
        var newBundle = new org.hl7.fhir.dstu3.model.Bundle();
        newBundle.setId(Ids.ensureIdType(extractId, "Bundle"));
        newBundle.setType(org.hl7.fhir.dstu3.model.Bundle.BundleType.TRANSACTION);
        // ensure entry array
        newBundle.getEntry();
        resources.forEach(resource -> {
            var entryRequest = new org.hl7.fhir.dstu3.model.Bundle.BundleEntryRequestComponent();
            entryRequest.setMethod(org.hl7.fhir.dstu3.model.Bundle.HTTPVerb.PUT);
            entryRequest.setUrl(
                    resource.fhirType() + "/" + resource.getIdElement().getIdPart());

            var entry = new org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent();
            entry.setResource((org.hl7.fhir.dstu3.model.Resource) resource);
            entry.setRequest(entryRequest);
            entry.setFullUrl(generateFullUrl(resource));
            newBundle.addEntry(entry);
        });

        return newBundle;
    }

    public static IBaseBundle createBundleR4(String extractId, List<IBaseResource> resources) {
        var newBundle = new org.hl7.fhir.r4.model.Bundle();
        newBundle.setId(Ids.ensureIdType(extractId, "Bundle"));
        newBundle.setType(org.hl7.fhir.r4.model.Bundle.BundleType.TRANSACTION);
        // ensure entry array
        newBundle.getEntry();
        resources.forEach(resource -> {
            var entryRequest = new org.hl7.fhir.r4.model.Bundle.BundleEntryRequestComponent();
            entryRequest.setMethod(org.hl7.fhir.r4.model.Bundle.HTTPVerb.PUT);
            entryRequest.setUrl(
                    resource.fhirType() + "/" + resource.getIdElement().getIdPart());

            var entry = new org.hl7.fhir.r4.model.Bundle.BundleEntryComponent();
            entry.setFullUrl(generateFullUrl(resource));
            entry.setResource((org.hl7.fhir.r4.model.Resource) resource);
            entry.setRequest(entryRequest);
            newBundle.addEntry(entry);
        });

        return newBundle;
    }

    public static IBaseBundle createBundleR5(String extractId, List<IBaseResource> resources) {
        var newBundle = new org.hl7.fhir.r5.model.Bundle();
        newBundle.setId(Ids.ensureIdType(extractId, "Bundle"));
        newBundle.setType(org.hl7.fhir.r5.model.Bundle.BundleType.TRANSACTION);
        // ensure entry array
        newBundle.getEntry();
        resources.forEach(resource -> {
            var entryRequest = new org.hl7.fhir.r5.model.Bundle.BundleEntryRequestComponent();
            entryRequest.setMethod(org.hl7.fhir.r5.model.Bundle.HTTPVerb.PUT);
            entryRequest.setUrl(
                    resource.fhirType() + "/" + resource.getIdElement().getIdPart());

            var entry = new org.hl7.fhir.r5.model.Bundle.BundleEntryComponent();
            entry.setFullUrl(generateFullUrl(resource));
            entry.setResource((org.hl7.fhir.r5.model.Resource) resource);
            entry.setRequest(entryRequest);
            newBundle.addEntry(entry);
        });

        return newBundle;
    }

    private static String generateFullUrl(IBaseResource resource) {
        if (resource.getIdElement().getIdPart() != null) {
            return "http://example.org/fhir/" + resource.fhirType() + "/" + resource.getIdElement().getIdPart();
        } else {
            return UUID.randomUUID().toString();
        }
    }
}
