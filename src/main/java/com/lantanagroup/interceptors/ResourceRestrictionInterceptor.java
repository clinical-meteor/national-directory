package com.lantanagroup.interceptors;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Expression;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lantanagroup.util.AttestationUtil;

import ca.uhn.fhir.jpa.patch.FhirPatch;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.interceptor.consent.ConsentOutcome;
import ca.uhn.fhir.rest.server.interceptor.consent.IConsentContextServices;
import ca.uhn.fhir.rest.server.interceptor.consent.IConsentService;

public class ResourceRestrictionInterceptor implements IConsentService {

  private static final Logger logger = LoggerFactory.getLogger(ResourceRestrictionInterceptor.class);

  private final String RESTRICTION_EXTENSION_BASE = "http://hl7.org/fhir/us/ndh/StructureDefinition/base-ext-usage-restriction";
  private final String RESTRICTION_EXTENSION_FHIRPATH = "http://hl7.org/fhir/us/ndh/StructureDefinition/base-ext-restrictFhirPath";

  @Override
  public ConsentOutcome startOperation(RequestDetails thRequestDetails, IConsentContextServices theContextServices) {

    // bypass consent check with special header
    if (!thRequestDetails.getHeaders("X-Consent-Skip").isEmpty()) {
      return ConsentOutcome.AUTHORIZED;
    }

    return ConsentOutcome.PROCEED;
  }


  @Override
  public ConsentOutcome canSeeResource(RequestDetails theRequestDetails, IBaseResource theResource, IConsentContextServices theContextServices) {

    // logger.info("canSeeResource for resource: " + ((DomainResource)theResource).getId());


    //
    // Attestation check -- hide the resource if it is unattested and the request does not have the special header
    // 

    String purpose = theRequestDetails.getHeader("X-Purpose");

    // header not present or not equal to attestation so we need to check if the resource is attested
    if (purpose == null || !purpose.equals("attestation")) {

      if (AttestationUtil.isUnattested(theResource)) {
        return ConsentOutcome.REJECT;
      }
    }

    

    //
    // Resource restriction check
    // Consent check based on http://build.fhir.org/ig/HL7/fhir-us-ndh/StructureDefinition-ndh-Restriction.html
    //


    // need to be able to cast the resource to a DomainResource to access the contained property
    if (!DomainResource.class.isInstance(theResource)) {
      return ConsentOutcome.AUTHORIZED;
    }

    DomainResource resource = (DomainResource) theResource;

    // check if the resource has the restriction extension
    Optional<Extension> extension = resource.getExtension().stream()
        .filter(ext -> ext.getUrl().equals(RESTRICTION_EXTENSION_BASE))
        .findFirst();

    // if the extension is not present, there is no consent to check
    if (!extension.isPresent()) {
      return ConsentOutcome.AUTHORIZED;
    }

    // extension should contain a reference to a Consent resource
    if (!Reference.class.isInstance(extension.get().getValue())) {
      return ConsentOutcome.AUTHORIZED;
    }

    // if contained is empty, there is no consent to check
    if (resource.getContained().isEmpty()) {
      return ConsentOutcome.REJECT;
    }

    Reference consentReference = (Reference) extension.get().getValue();    
    
    // get the consent resource from contained
    Optional<Resource> consentResource = resource.getContained().stream()
        .filter(res -> res.getIdElement().getIdPart().equals(consentReference.getReference()))
        .findFirst();

    // incorrect reference
    if (!consentResource.isPresent()) {
      logger.error("Consent resource " + consentReference.getReference() + " not found in contained");
      return ConsentOutcome.REJECT;
    }

    // resource should be a Consent resource
    if (!Consent.class.isInstance(consentResource.get())) {
      logger.error("Consent resource " + consentReference.getReference() + " is not a Consent resource");
      return ConsentOutcome.REJECT;
    }

    // check if the resource has fhirPath restrictions or if we should restrict the entire resource
    Boolean restrictPathPresent = resource.getContained().stream()
        .anyMatch(res -> res instanceof DomainResource && ((DomainResource)res).hasExtension(RESTRICTION_EXTENSION_FHIRPATH));

    // check if the user is authorized to view the resource
    if (!isAuthorized(theRequestDetails, (Consent)consentResource.get())) {

      // logger.info("User is not authorized to view resource.  restrictPathPresent: " + restrictPathPresent);

      // user is not authorized
      // if the resource has path restrictions, then we have to proceed to the willSeeResource step to remove specific properties
      if (restrictPathPresent) {
        return ConsentOutcome.PROCEED;
      }

      // otherwise, this resource is entirely restricted
      return ConsentOutcome.REJECT;
    }
    
    // user is authorized to see the entire resource
    return ConsentOutcome.AUTHORIZED;
  }


  @Override
  public ConsentOutcome willSeeResource(RequestDetails theRequestDetails, IBaseResource theResource, IConsentContextServices theContextServices) {
    
    // need to check again if the resource has the restriction extension
    // because this method gets called for contained resources as well
    // so we cannot assume this is the main resource

    if (!DomainResource.class.isInstance(theResource)) {
      return ConsentOutcome.AUTHORIZED;
    }

    Optional<Extension> extension = ((DomainResource) theResource)
        .getExtension().stream()
        .filter(ext -> ext.getUrl().equals(RESTRICTION_EXTENSION_BASE))
        .findFirst();

    if (!extension.isPresent()) {
      return ConsentOutcome.AUTHORIZED;
    }
    
    Reference consentReference = (Reference)extension.get().getValue();

    Consent consent = (Consent) ((DomainResource)theResource).getContained().stream()
        .filter(res -> res.getIdElement().getIdPart().equals(consentReference.getReference()))
        .findFirst().get();


    // get the paths to remove from the response
    List<Extension> paths = consent.getExtension().stream()
        .filter(
            ext -> ext.getUrl().equals(RESTRICTION_EXTENSION_FHIRPATH) && Expression.class.isInstance(ext.getValue())
                && ((Expression) ext.getValue()).getLanguage().equals("text/fhirpath"))
        .collect(Collectors.toList());

    if (paths.isEmpty()) {
      return ConsentOutcome.AUTHORIZED;
    }

    var ctx = theRequestDetails.getFhirContext();

    paths.forEach(path -> {
      String pathExpression = ((Expression) path.getValue()).getExpression();

      // create parameters for a FHIRPath patch operation to remove the path from the resource
      Parameters parameters = new Parameters();
      var opParam = parameters.addParameter().setName(FhirPatch.PARAMETER_OPERATION);
      opParam.addPart().setName(FhirPatch.PARAMETER_TYPE).setValue(new StringType(FhirPatch.OPERATION_DELETE));
      opParam.addPart().setName(FhirPatch.PARAMETER_PATH).setValue(new StringType(pathExpression));

      // apply the patch operation to the resource
      new FhirPatch(ctx).apply(theResource, parameters);
    });

    return ConsentOutcome.AUTHORIZED;
  }


  /**
   * Check if the user is authorized to view a resource based on the given Consent
   * resource
   * 
   * @param theRequestDetails The server request details
   * @param theConsent        The Consent resource to check the user against
   * @return True if the user is authorized, false otherwise
   */
  protected Boolean isAuthorized(RequestDetails theRequestDetails, Consent theConsent) {

    // TODO: actually check if the user is authorized
    // for now... check for test header in the request to authorize this user

    return !theRequestDetails.getHeaders("X-Consent-Policy").isEmpty();

  }
  
}
