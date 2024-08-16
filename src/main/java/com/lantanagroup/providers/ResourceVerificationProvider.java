package com.lantanagroup.providers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Type;
import org.hl7.fhir.r4.model.VerificationResult;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lantanagroup.util.AttestationUtil;
import com.lantanagroup.util.VerificationUtil;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.api.model.DaoMethodOutcome;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.RequestDetails;

public class ResourceVerificationProvider {

  private final Logger logger = LoggerFactory.getLogger(ResourceVerificationProvider.class);

  private DaoRegistry daoRegistry;

  public ResourceVerificationProvider(DaoRegistry daoRegistry) {
    this.daoRegistry = daoRegistry;
  }

  @Operation(name = "$verify")
  public OperationOutcome verify(
    @OperationParam(name = "verification", min = 1, max = OperationParam.MAX_UNLIMITED) List<ParametersParameterComponent> verificationParameters,
    RequestDetails theRequestDetails
  ) {

    List<DomainResource> resourceList = new ArrayList<>();
    List<ParametersParameterComponent> parameterList = new ArrayList<>();

    HashMap<String, Class<?>> requiredParts = new HashMap<String, Class<?>>() {
      {
        put("target", Reference.class);
        put("attestor", Reference.class);
      }
    };


    // validate the verification parameters before proceeding
    // the first incorrect or missing parameter will return an error outcome
    for (ParametersParameterComponent verificationParameter : verificationParameters) {

      // check that all required parts are present and have the correct type
      for (String partName : requiredParts.keySet()) {
        Optional<ParametersParameterComponent> part = verificationParameter.getPart().stream().filter(p -> p.getName().equals(partName)).findFirst();
        
        if (part.isPresent()) {
          Type value = part.get().getValue();
          if (!requiredParts.get(partName).isInstance(value)) {
            return errorOutcome("Invalid parameter type for part: " + partName + ". Expected: " + requiredParts.get(partName).getSimpleName());
          }
        }
        else {
          return errorOutcome("Required parameter part not found: " + partName);
        }
      }

      // ensure the target resource exists in the database and is a DomainResource
      String[] targetRef = ((Reference) verificationParameter.getPart().stream()
          .filter(p -> p.getName().equals("target"))
          .findFirst()
          .get()
          .getValue())
          .getReference()
          .split("/");
      IBaseResource resource = daoRegistry.getResourceDao(targetRef[0]).read(new IdDt(targetRef[1]), theRequestDetails);
      
      if (resource instanceof DomainResource) {

        if (AttestationUtil.isUnattested(resource)) {
          return errorOutcome("Resource is not attested: " + String.join("/", targetRef));
        }

        // add the resource and parameters to the corresponding lists
        resourceList.add((DomainResource)resource);
        parameterList.add(verificationParameter);
      }
      else {
        return errorOutcome("Resource not found for reference: " + String.join("/", targetRef));
      }

    }

    // all parameters are valid, proceed with verification
    OperationOutcome outcome = new OperationOutcome();

    IFhirResourceDao<VerificationResult> vrDao = daoRegistry.getResourceDao(VerificationResult.class);

    // create the VerificationResult and update the verification status for each resource 
    for (int i = 0; i < resourceList.size(); i++) {

      DomainResource resource = resourceList.get(i);

      VerificationResult vr = VerificationUtil.getDefaultVerificationResult(resource, parameterList.get(i));
      DaoMethodOutcome vrOutcome = vrDao.create(vr, theRequestDetails);

      VerificationUtil.setVerificationStatus(resource, "complete");
      var dao = daoRegistry.getResourceDao(resource);
      DaoMethodOutcome updateOutcome = dao.update(resource, theRequestDetails);

      // report if resource did not need to be updated
      if (updateOutcome.isNop()) {
        outcome.addIssue()
          .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
          .setCode(OperationOutcome.IssueType.INFORMATIONAL)
          .setDiagnostics("Resource " + resource.getResourceType() + "/" + resource.getIdPart() + " did not need to be updated");
      }

      outcome.addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
        .setCode(OperationOutcome.IssueType.INFORMATIONAL)
        .setDiagnostics("Verification successful for resource: " + resource.getResourceType() + "/" + resource.getIdPart() + " with VerificationResult: " + vrOutcome.getId().getIdPart());
    }

    outcome.addIssue(new OperationOutcome.OperationOutcomeIssueComponent()
      .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
      .setCode(OperationOutcome.IssueType.INFORMATIONAL)
      .setDiagnostics("Verification operation completed successfully"));
    
    return outcome;
  }


  /**
   * 
   * @param message
   * @return
   */
  protected OperationOutcome errorOutcome(String message) {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR).setDiagnostics(message);
    return outcome;
  }
  
  
}
