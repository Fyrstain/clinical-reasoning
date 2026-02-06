package org.opencds.cqf.fhir.cr.questionnaireresponse.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.opencds.cqf.fhir.cr.helpers.RequestHelpers.newExtractRequestForVersion;
import static org.opencds.cqf.fhir.cr.questionnaireresponse.TestQuestionnaireResponse.open;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.repository.IRepository;
import java.util.List;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemType;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemComponent;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opencds.cqf.fhir.cql.LibraryEngine;
import org.opencds.cqf.fhir.cr.common.ExpressionProcessor;
import org.opencds.cqf.fhir.utility.Constants;
import org.opencds.cqf.fhir.utility.Ids;

@SuppressWarnings("UnstableApiUsage")
@ExtendWith(MockitoExtension.class)
class ProcessDefinitionItemTests {

    private final FhirContext fhirContextR4 = FhirContext.forR4Cached();
    @Mock
    ExpressionProcessor expressionProcessor;
    @Mock
    private IRepository repository;
    @Mock
    private LibraryEngine libraryEngine;

    private ProcessDefinitionItem fixture;

    @BeforeEach
    void setup() {
        doReturn(fhirContextR4).when(repository).fhirContext();
        doReturn(repository).when(libraryEngine).getRepository();
        fixture = new ProcessDefinitionItem(expressionProcessor);
    }

    @Test
    void testItemWithNoDefinitionThrows() {
        var fhirVersion = FhirVersionEnum.R4;
        var item = new QuestionnaireItemComponent();
        var responseItem = new QuestionnaireResponseItemComponent();
        var itemPair = new ItemPair(fhirVersion, item, responseItem);
        var questionnaire = new Questionnaire();
        var response = new QuestionnaireResponse();
        var request = newExtractRequestForVersion(fhirVersion, libraryEngine, response,
            questionnaire);
        var subjectId = Ids.newId(fhirVersion, "Patient", "patient1");
        var subjectReference = new Reference(subjectId);
        assertThrows(IllegalArgumentException.class,
            () -> fixture.processDefinitionItem(request, itemPair, subjectReference));
    }

    @Test
    void testItemWithInvalidDefinitionThrows() {
        var fhirVersion = FhirVersionEnum.R4;
        var item = new QuestionnaireItemComponent();
        item.setDefinition("http://hl7.org/fhir/StructureDefinition/RelatedPerson.name.text");
        var responseItem = new QuestionnaireResponseItemComponent();
        var itemPair = new ItemPair(fhirVersion, item, responseItem);
        var questionnaire = new Questionnaire();
        var response = new QuestionnaireResponse();
        var request = newExtractRequestForVersion(fhirVersion, libraryEngine, response,
            questionnaire);
        var subjectId = Ids.newId(fhirVersion, "Patient", "patient1");
        assertThrows(IllegalArgumentException.class,
            () -> fixture.processDefinitionItem(request, itemPair, new Reference(subjectId)));
    }

    @Test
    void testItemWithContextExtensionWithType() {
        var fhirVersion = FhirVersionEnum.R4;
        var item = new QuestionnaireItemComponent().setLinkId("1");
        item.addExtension(Constants.SDC_QUESTIONNAIRE_ITEM_EXTRACTION_CONTEXT,
            new CodeType("Condition"));
        var responseItem = new QuestionnaireResponseItemComponent().setLinkId("1");
        var itemPair = new ItemPair(fhirVersion, item, responseItem);
        var questionnaire = new Questionnaire();
        var response = new QuestionnaireResponse();
        var request = newExtractRequestForVersion(fhirVersion, libraryEngine, response,
            questionnaire);
        var subjectId = Ids.newId(fhirVersion, "Patient", "patient1");
        var subjectReference = new Reference(subjectId);
        var actual = fixture.processDefinitionItem(request, itemPair, subjectReference);
        assertInstanceOf(Condition.class, actual);
    }

    @Test
    void testItemWithContextExtensionWithProfile() {
        var fhirVersion = FhirVersionEnum.R4;
        var profile = "http://hl7.org/fhir/Patient";
        var item = new QuestionnaireItemComponent().setLinkId("1");
        var extension = new Extension(Constants.SDC_QUESTIONNAIRE_ITEM_EXTRACTION_CONTEXT)
            .setValue(new CanonicalType().setValue(profile));
        item.addExtension(extension);
        var responseItem = new QuestionnaireResponseItemComponent().setLinkId("1");
        var itemPair = new ItemPair(fhirVersion, item, responseItem);
        var questionnaire = new Questionnaire();
        var response = new QuestionnaireResponse();
        var request = newExtractRequestForVersion(fhirVersion, libraryEngine, response,
            questionnaire);
        var subjectId = Ids.newId(fhirVersion, "Patient", "patient1");
        var actual = fixture.processDefinitionItem(request, itemPair, new Reference(subjectId));
        assertNotNull(actual);
    }

    @Test
    void testItemWithContextExtensionWithMultipleAnswers() {
        var fhirVersion = FhirVersionEnum.R4;
        var item = new QuestionnaireItemComponent().setLinkId("1")
            .setType(QuestionnaireItemType.STRING);
        item.setDefinition("http://hl7.org/fhir/Patient#Patient.name.given");
        var responseItem = new QuestionnaireResponseItemComponent().setLinkId("1");
        responseItem.addAnswer(
            new QuestionnaireResponseItemAnswerComponent().setValue(new StringType("test1")));
        responseItem.addAnswer(
            new QuestionnaireResponseItemAnswerComponent().setValue(new StringType("test2")));
        var questionnaire = new Questionnaire().setItem(List.of(item));
        var extension =
            new Extension(Constants.SDC_QUESTIONNAIRE_ITEM_EXTRACTION_CONTEXT).setValue(
                new CodeType("Patient"));
        questionnaire.addExtension(extension);
        var response = new QuestionnaireResponse().setItem(List.of(responseItem));
        var request = newExtractRequestForVersion(fhirVersion, libraryEngine, response,
            questionnaire);
        var itemPair = new ItemPair(null, null);
        var subjectId = Ids.newId(fhirVersion, "Patient", "patient1");
        var actual = fixture.processDefinitionItem(request, itemPair, new Reference(subjectId));
        assertInstanceOf(Patient.class, actual);
        var patient = (Patient) actual;
        assertEquals("test1", patient.getNameFirstRep().getGiven().get(0).asStringValue());
        assertEquals("test2", patient.getNameFirstRep().getGiven().get(1).asStringValue());
    }

    @Test
    void testRepeatingItemWithContextExtensionAndNestedPath() {
        var fhirVersion = FhirVersionEnum.R4;
        var parser = fhirContextR4.newJsonParser();
        var questionnaire = (Questionnaire)
            parser.parseResource(
                open("r4/input/resources/Questionnaire-extract-defn-walkthrough-4.json"));
        var response = (QuestionnaireResponse)
            parser.parseResource(
                open("r4/input/tests/QuestionnaireResponse-extract-defn-walkthrough-4.json"));
        var request = newExtractRequestForVersion(fhirVersion, libraryEngine, response,
            questionnaire);
        var itemPair = new ItemPair(null, null);
        var subjectId = Ids.newId(fhirVersion, "Patient", "patient1");
        var actual = fixture.processDefinitionItem(request, itemPair, new Reference(subjectId));
        assertInstanceOf(Patient.class, actual);
        var names = ((Patient) actual).getName();
        assertEquals(2, names.size());
        assertEquals("test1", names.get(0).getGiven().get(0).getValue());
        assertEquals("test2", names.get(0).getGiven().get(1).getValue());
        assertEquals("official", names.get(0).getUse().toCode());
        assertEquals("test3", names.get(1).getGiven().get(0).getValue());
        assertEquals("old", names.get(1).getUse().toCode());
    }

    @Test
    void testItemWithContextExtensionAndRepeatingNestedPath() {
        var fhirVersion = FhirVersionEnum.R4;
        var item = new QuestionnaireItemComponent().setLinkId("1");
        item.setDefinition("http://hl7.org/fhir/Patient#Patient.name[+].text");
        item.setRepeats(true);
        var responseItem = new QuestionnaireResponseItemComponent().setLinkId("1");
        responseItem.addAnswer(
            new QuestionnaireResponseItemAnswerComponent().setValue(new StringType("test1")));
        responseItem.addAnswer(
            new QuestionnaireResponseItemAnswerComponent().setValue(new StringType("test2")));
        var questionnaire = new Questionnaire().setItem(List.of(item));
        var extension =
            new Extension(Constants.SDC_QUESTIONNAIRE_ITEM_EXTRACTION_CONTEXT).setValue(
                new CodeType("Patient"));
        questionnaire.addExtension(extension);
        var response = new QuestionnaireResponse().setItem(List.of(responseItem));
        var request = newExtractRequestForVersion(fhirVersion, libraryEngine, response,
            questionnaire);
        var itemPair = new ItemPair(null, null);
        var subjectId = Ids.newId(fhirVersion, "Patient", "patient1");
        var actual = fixture.processDefinitionItem(request, itemPair, new Reference(subjectId));
        assertInstanceOf(Patient.class, actual);
        var names = ((Patient) actual).getName();
        assertEquals(2, names.size());
        assertEquals("test1", names.get(0).getText());
        assertEquals("test2", names.get(1).getText());
    }

    @Test
    void testDefinitionExtraction_patient_withPlusAndEqualsTelecom_createsTwoTelecomEntries() {
        var fhirVersion = FhirVersionEnum.R4;

        // Questionnaire : group Patient
        var qGroup = new QuestionnaireItemComponent()
            .setLinkId("group1")
            .setDefinition("http://hl7.org/fhir/StructureDefinition/Patient#Patient");

        // Name
        var qName = new QuestionnaireItemComponent()
            .setLinkId("name")
            .setDefinition("http://hl7.org/fhir/StructureDefinition/Patient#Patient.name.text")
            .setType(QuestionnaireItemType.STRING);

        // Birthdate
        var qBirth = new QuestionnaireItemComponent()
            .setLinkId("birthdate")
            .setDefinition("http://hl7.org/fhir/StructureDefinition/Patient#Patient.birthDate")
            .setType(QuestionnaireItemType.DATE);

        // telecom[+].system + telecom[=].value (phone)
        var qPhoneSystem = new QuestionnaireItemComponent()
            .setLinkId("phoneSystem")
            .setDefinition(
                "http://hl7.org/fhir/StructureDefinition/Patient#Patient.telecom[+].system")
            .setType(QuestionnaireItemType.CHOICE);

        var qPhoneValue = new QuestionnaireItemComponent()
            .setLinkId("phoneNumber")
            .setDefinition(
                "http://hl7.org/fhir/StructureDefinition/Patient#Patient.telecom[=].value")
            .setType(QuestionnaireItemType.STRING);

        // telecom[+].system + telecom[=].value (email)
        var qEmailSystem = new QuestionnaireItemComponent()
            .setLinkId("emailSystem")
            .setDefinition(
                "http://hl7.org/fhir/StructureDefinition/Patient#Patient.telecom[+].system")
            .setType(QuestionnaireItemType.CHOICE);

        var qEmailValue = new QuestionnaireItemComponent()
            .setLinkId("email")
            .setDefinition(
                "http://hl7.org/fhir/StructureDefinition/Patient#Patient.telecom[=].value")
            .setType(QuestionnaireItemType.STRING);

        // address.text
        var qAddress = new QuestionnaireItemComponent()
            .setLinkId("address")
            .setDefinition("http://hl7.org/fhir/StructureDefinition/Patient#Patient.address.text")
            .setType(QuestionnaireItemType.STRING);

        qGroup.setItem(
            List.of(qName, qBirth, qPhoneSystem, qPhoneValue, qEmailSystem, qEmailValue, qAddress));

        qGroup.addExtension(Constants.SDC_QUESTIONNAIRE_ITEM_EXTRACTION_CONTEXT,
            new CodeType("Patient"));

        var questionnaire = new Questionnaire().setItem(List.of(qGroup));
        var response = new QuestionnaireResponse()
            .setStatus(QuestionnaireResponse.QuestionnaireResponseStatus.INPROGRESS)
            .setSubject(new Reference("Patient/123"))
            .setAuthoredElement(new DateTimeType("2026-02-05T14:50:05+01:00"));

        // QuestionnaireResponse group
        var rGroup = new QuestionnaireResponseItemComponent().setLinkId("group1");
        // name
        rGroup.addItem(new QuestionnaireResponseItemComponent()
            .setLinkId("name")
            .addAnswer(new QuestionnaireResponseItemAnswerComponent().setValue(
                new StringType("John Doe"))));
        // birthdate
        rGroup.addItem(new QuestionnaireResponseItemComponent()
            .setLinkId("birthdate")
            .addAnswer(new QuestionnaireResponseItemAnswerComponent().setValue(
                new DateType("1980-01-01"))));
        // phoneSystem
        rGroup.addItem(new QuestionnaireResponseItemComponent()
            .setLinkId("phoneSystem")
            .addAnswer(new QuestionnaireResponseItemAnswerComponent().setValue(
                new Coding("http://hl7.org/fhir/contact-point-system", "phone", null)
            )));
        // phoneNumber
        rGroup.addItem(new QuestionnaireResponseItemComponent()
            .setLinkId("phoneNumber")
            .addAnswer(new QuestionnaireResponseItemAnswerComponent().setValue(
                new StringType("+351999000111"))));
        // emailSystem
        rGroup.addItem(new QuestionnaireResponseItemComponent()
            .setLinkId("emailSystem")
            .addAnswer(new QuestionnaireResponseItemAnswerComponent().setValue(
                new Coding("http://hl7.org/fhir/contact-point-system", "email", null)
            )));
        // email
        rGroup.addItem(new QuestionnaireResponseItemComponent()
            .setLinkId("email")
            .addAnswer(new QuestionnaireResponseItemAnswerComponent().setValue(
                new StringType("john@example.org"))));
        // address
        rGroup.addItem(new QuestionnaireResponseItemComponent()
            .setLinkId("address")
            .addAnswer(new QuestionnaireResponseItemAnswerComponent().setValue(
                new StringType("1 Rue Exemple, Lisbon"))));

        response.setItem(List.of(rGroup));

        var request = newExtractRequestForVersion(fhirVersion, libraryEngine, response,
            questionnaire);

        var itemPair = new ItemPair(fhirVersion, qGroup, rGroup);
        var actual = fixture.processDefinitionItem(request, itemPair, new Reference("Patient/123"));

        assertInstanceOf(Patient.class, actual);
        var patient = (Patient) actual;

        assertEquals("John Doe", patient.getNameFirstRep().getText());
        assertEquals("1980-01-01", patient.getBirthDateElement().asStringValue());
        assertEquals("1 Rue Exemple, Lisbon", patient.getAddressFirstRep().getText());
        assertEquals(2, patient.getTelecom().size());

        var t0 = patient.getTelecom().get(0);
        assertEquals(ContactPointSystem.PHONE.toCode(), t0.getSystem().toCode());
        assertEquals("+351999000111", t0.getValue());

        var t1 = patient.getTelecom().get(1);
        assertEquals(ContactPointSystem.EMAIL.toCode(), t1.getSystem().toCode());
        assertEquals("john@example.org", t1.getValue());
    }

    @Test
    void testDefinitionExtraction_observation_setsSubjectAndAuthoredDates_andNestedCodeCoding() {
        var fhirVersion = FhirVersionEnum.R4;

        // Questionnaire : group Observation
        var qGroup = new QuestionnaireItemComponent()
            .setLinkId("group2")
            .setDefinition("http://hl7.org/fhir/StructureDefinition/Observation#Observation");

        // status
        var qStatus = new QuestionnaireItemComponent()
            .setLinkId("observationStatus")
            .setDefinition("http://hl7.org/fhir/StructureDefinition/Observation#Observation.status")
            .setType(QuestionnaireItemType.CHOICE);

        // code.coding
        var qCodeCoding = new QuestionnaireItemComponent()
            .setLinkId("observationCode")
            .setDefinition(
                "http://hl7.org/fhir/StructureDefinition/Observation#Observation.code.coding")
            .setType(QuestionnaireItemType.CHOICE);

        // value[x]
        var qValue = new QuestionnaireItemComponent()
            .setLinkId("allergy")
            .setDefinition(
                "http://hl7.org/fhir/StructureDefinition/Observation#Observation.value")
            .setType(QuestionnaireItemType.STRING);

        qGroup.setItem(List.of(qValue, qStatus, qCodeCoding));
        qGroup.addExtension(Constants.SDC_QUESTIONNAIRE_ITEM_EXTRACTION_CONTEXT,
            new CodeType("Observation"));

        var questionnaire = new Questionnaire().setItem(List.of(qGroup));

        var response = new QuestionnaireResponse()
            .setStatus(QuestionnaireResponse.QuestionnaireResponseStatus.INPROGRESS)
            .setSubject(new Reference("Patient/123"))
            .setAuthoredElement(new DateTimeType("2026-02-05T14:50:05+01:00"));

        var rGroup = new QuestionnaireResponseItemComponent().setLinkId("group2");

        // allergy value
        rGroup.addItem(new QuestionnaireResponseItemComponent()
            .setLinkId("allergy")
            .addAnswer(new QuestionnaireResponseItemAnswerComponent().setValue(
                new StringType("peanuts"))));

        // status = final
        rGroup.addItem(new QuestionnaireResponseItemComponent()
            .setLinkId("observationStatus")
            .addAnswer(new QuestionnaireResponseItemAnswerComponent().setValue(
                new Coding("http://hl7.org/fhir/observation-status", "final", null)
            )));

        // code.coding = SNOMED code
        rGroup.addItem(new QuestionnaireResponseItemComponent()
            .setLinkId("observationCode")
            .addAnswer(new QuestionnaireResponseItemAnswerComponent().setValue(
                new Coding("http://snomed.info/sct", "408439002", null)
            )));

        response.setItem(List.of(rGroup));

        var request = newExtractRequestForVersion(fhirVersion, libraryEngine, response,
            questionnaire);

        var itemPair = new ItemPair(fhirVersion, qGroup, rGroup);
        var actual = fixture.processDefinitionItem(request, itemPair, new Reference("Patient/123"));

        assertInstanceOf(Observation.class, actual);
        var obs = (Observation) actual;

        assertNotNull(obs.getSubject());
        assertEquals("Patient/123", obs.getSubject().getReference());

        // resolveAuthored(): effectiveDateTime + issued
        assertInstanceOf(DateTimeType.class, obs.getEffective());
        assertEquals("2026-02-05T14:50:05+01:00",
            ((DateTimeType) obs.getEffective()).getValueAsString());

        assertNotNull(obs.getIssuedElement());
        assertInstanceOf(InstantType.class, obs.getIssuedElement());

        // status
        assertEquals(ObservationStatus.FINAL, obs.getStatus());

        // code.coding (nested path)
        assertNotNull(obs.getCode());
        assertFalse(obs.getCode().getCoding().isEmpty());
        var coding = obs.getCode().getCodingFirstRep();
        assertEquals("http://snomed.info/sct", coding.getSystem());
        assertEquals("408439002", coding.getCode());

        // value[x]
        assertNotNull(obs.getValue());
        assertEquals("peanuts", obs.getValue().primitiveValue());
    }
}
