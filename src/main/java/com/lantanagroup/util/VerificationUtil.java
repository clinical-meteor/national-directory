package com.lantanagroup.util;

import java.util.HashMap;
import java.util.Set;

import org.elasticsearch.core.List;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.VerificationResult;
import org.hl7.fhir.r4.model.VerificationResult.Status;
import org.hl7.fhir.r4.model.VerificationResult.VerificationResultAttestationComponent;
import org.joda.time.Instant;

public class VerificationUtil {

  private static final String VERIFICATION_EXTENSION_URL = "http://hl7.org/fhir/us/ndh/StructureDefinition/base-ext-verification-status";
  private static final String VERIFICATION_CODESYSTEM = "http://hl7.org/fhir/us/ndh/CodeSystem/NdhVerificationStatusCS";

  private static final HashMap<String, String> verificationCodingMap = new HashMap<String, String>() {
    {
      put("incomplete", "Incomplete");
      put("complete", "Complete");
      put("not-required", "Not Required");
    }
  };

  /**
  * Returns the verification status Coding for the given resource if it exists, otherwise null is returned
  * @param resource The resource to get the verification status for
  * @return The verification status Coding for the given resource if it exists, otherwise null
  */
  public static Coding getVerificationStatus(DomainResource resource) {

    Coding verificationCoding = null;

    if (resource != null && resource.getExtension() != null) {

      for (var ext : resource.getExtension()) {

        if (ext.getUrl().equals(VERIFICATION_EXTENSION_URL)) {
          if (ext.getValue() instanceof CodeableConcept) {
            CodeableConcept cc = (CodeableConcept) ext.getValue();
            if (cc.getCoding().size() > 0) {
              Coding coding = cc.getCoding().get(0);
              if (coding.getSystem().equals(VERIFICATION_CODESYSTEM)) {
                verificationCoding = coding;
                break;
              } 
            }
          }
        }

      }

    }

    return verificationCoding;
  }

  
  /**
   * Returns true if the given resource has a verification status with the given value
   * @param resource The resource to check
   * @param value The value to check for
   * @return True if the resource has a verification status with the given value, otherwise false
   */
  public static boolean hasVerificationValue(DomainResource resource, String value) {
    
    Coding coding = getVerificationStatus(resource);

    if (coding != null && coding.getCode() != null && coding.getCode().equals(value)) {
      return true;
    }

    return false;

  }


  /**
   * Returns an Extension with the verification status set to the given value
   * @param value The value to set the verification status to
   * @return The Extension with the verification status set to the given value
   */
  public static Extension getVerificationStatusWithValue(String value) {

    if (!verificationCodingMap.containsKey(value)) {
      throw new IllegalArgumentException("Invalid verification status value: " + value);
    }

    Extension ext = new Extension();
    ext.setUrl(VERIFICATION_EXTENSION_URL);

    CodeableConcept cc = new CodeableConcept();
    cc.addCoding(new Coding(VERIFICATION_CODESYSTEM, value, verificationCodingMap.get(value)));
    ext.setValue(cc);

    return ext;

  }


  /**
   * Sets the verification status of the given resource to the given value
   * @param resource The resource to set the verification status for
   * @param value The value to set the verification status to
   * @return The resource with the verification status set to the given value
   */
  public static DomainResource setVerificationStatus(DomainResource resource, String value) {
    Extension extension = resource.getExtensionByUrl(VERIFICATION_EXTENSION_URL);

    if (extension != null) {
      resource.getExtension().remove(extension);
    }

    resource.addExtension(getVerificationStatusWithValue(value));
    return resource;
  }


  /**
   * Processes the given resource to add verification status if it is missing.  This does not update the resource in the database.
   * @param resource The resource to process
   * @return The resource with verification status added
   */
  public static DomainResource initializeVerification(DomainResource resource) {
    return initializeVerification(resource, false);
  }

  /**
   * Processes the given resource to add verification status if it is missing.  This does not update the resource in the database.
   * @param resource The resource to process
   * @param skipAttestationCheck If true, the resource will be processed even if it is unattested
   * @return The resource with verification status added
   */
  public static DomainResource initializeVerification(DomainResource resource, Boolean skipAttestationCheck) {

    // If the resource is unattested, we don't need to check verification status
    if (!skipAttestationCheck && AttestationUtil.isUnattested(resource)) {
      return resource;
    }

    // If the resource has any verification status set, there is nothing to do
    if (getVerificationStatus(resource) != null) {
      return resource;
    }    

    // Set default verification status based on resource type
    Set<String> relationalTypes = Set.of("OrganizationAffiliation", "PractitionerRole");

    if (relationalTypes.contains(resource.getResourceType().toString())) {
      setVerificationStatus(resource, "complete");
    }
    else {
      setVerificationStatus(resource, "incomplete");
    }


    return resource;

  }


  public static VerificationResult getDefaultVerificationResult(DomainResource resource, ParametersParameterComponent parameters) {
    VerificationResult vr = new VerificationResult();
    vr.getMeta().addProfile("http://hl7.org/fhir/us/ndh/StructureDefinition/ndh-Verification");
    vr.setTarget(List.of(new Reference(resource)));
    vr.setNeed(new CodeableConcept().addCoding(new Coding("http://terminology.hl7.org/CodeSystem/need", "none", "None")));
    vr.setStatus(Status.ATTESTED);
    vr.setStatusDate(Instant.now().toDate());
    vr.setValidationType(new CodeableConcept().addCoding(new Coding("http://terminology.hl7.org/CodeSystem/verificationresult-validation-type", "nothing", "Nothing")));
    vr.setValidationProcess(List.of(new CodeableConcept().addCoding(new Coding("http://hl7.org/fhir/us/ndh/CodeSystem/NdhVerificationProcessCS", "manual", "Manual"))));
    vr.setFailureAction(new CodeableConcept().addCoding(new Coding("http://terminology.hl7.org/CodeSystem/failure-action", "none", "None")));

    Reference attestor = (Reference)parameters.getPart().stream().filter(p -> p.getName().equals("attestor")).findFirst().get().getValue();
    
    var vrac = new VerificationResultAttestationComponent();
    vrac.setWho(attestor);
    vrac.setCommunicationMethod(new CodeableConcept().addCoding(new Coding("http://terminology.hl7.org/CodeSystem/verificationresult-communication-method", "manual", "Manual")));
    vrac.setDate(Instant.now().toDate());
    vr.setAttestation(vrac);
    
    return vr;
  }
  
  
}
