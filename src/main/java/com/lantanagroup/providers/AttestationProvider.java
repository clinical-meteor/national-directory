package com.lantanagroup.providers;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lantanagroup.util.VerificationUtil;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.RequestDetails;

public class AttestationProvider {

  private final static Logger logger = LoggerFactory.getLogger(AttestationProvider.class);

  private DaoRegistry daoRegistry;


  public AttestationProvider(DaoRegistry daoRegistry) {
    this.daoRegistry = daoRegistry;
  }


  @Operation(name = "$attest")
  public OperationOutcome attest(
    @OperationParam(name = "resource", min = 1, max = OperationParam.MAX_UNLIMITED) List<ParametersParameterComponent> resourceParameters,
    RequestDetails theRequestDetails
  ) {

    List<DomainResource> resourceList = new ArrayList<>();
    String errorMesage = null;

    OperationOutcome outcome = new OperationOutcome();

    for (ParametersParameterComponent parameter : resourceParameters) {

      // valueReference parameter
      if (parameter.hasValue() && parameter.getValue() instanceof Reference) {

        // get the resource from the reference
        Reference reference = (Reference) parameter.getValue();
        String[] ref = reference.getReference().split("/");
        IBaseResource resource = daoRegistry.getResourceDao(ref[0]).read(new IdDt(ref[1]), theRequestDetails);

        if (resource instanceof DomainResource) {
          resourceList.add((DomainResource)resource);
        } else {
          errorMesage = "Resource not found for reference: " + reference.getReference();
          break;
        }
      }

      // unsupported parameter
      else {
        errorMesage = "Only resource parameters with a local valueReference are currently supported.";
        break;
      }
    }

    if (errorMesage != null) {
      outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR).setCode(OperationOutcome.IssueType.INVALID).setDiagnostics(errorMesage);
      return outcome;
    }


    // meta for the meta-delete operation
    Meta meta = new Meta();
    meta.addSecurity().setSystem("http://terminology.hl7.org/CodeSystem/v3-Confidentiality").setCode("V");

    // mark resources we've collected from the request as attested and update their verification status
    for (DomainResource resource : resourceList) {
      var dao = daoRegistry.getResourceDao(resource);

      // update verification status of the resource
      VerificationUtil.initializeVerification(resource, true);
      dao.update(resource, theRequestDetails);

      // invoke meta-delete operation to remove the V security tag
      dao.metaDeleteOperation(resource.getIdElement(), meta, theRequestDetails);

      logger.info("Resource attested: " + resource.getIdElement().getValueAsString());
    }


    outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.INFORMATION).setCode(OperationOutcome.IssueType.INFORMATIONAL).setDiagnostics("Attestation successful");

    return outcome;
    
  }
  
}
