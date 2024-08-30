package com.lantanagroup.interceptors;

import java.util.List;
import java.util.stream.Collectors;

import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.patch.FhirPatch;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.jpa.topic.SubscriptionTopicDispatcher;
import ca.uhn.fhir.jpa.topic.SubscriptionTopicPayloadBuilder;
import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.StringParam;

@Interceptor
public class SubscriptionInterceptor {

  private final static Logger logger = LoggerFactory.getLogger(SubscriptionInterceptor.class);

  SubscriptionTopicDispatcher subscriptionTopicDispatcher;

  SubscriptionTopicPayloadBuilder subscriptionTopicPayloadBuilder;

  DaoRegistry daoRegistry;

  public SubscriptionInterceptor(
    DaoRegistry daoRegistry,
    SubscriptionTopicDispatcher subscriptionTopicDispatcher
  ) {
    this.daoRegistry = daoRegistry;
    this.subscriptionTopicDispatcher = subscriptionTopicDispatcher;
  }
  

  @Hook(Pointcut.STORAGE_PRECOMMIT_RESOURCE_CREATED)
  public void created(IBaseResource theResource, RequestDetails theRequestDetails) {
    process(theResource, null, RestOperationTypeEnum.CREATE, theRequestDetails);
  }

  @Hook(Pointcut.STORAGE_PRECOMMIT_RESOURCE_UPDATED)
  public void updated(IBaseResource theOldResource, IBaseResource theResource, RequestDetails theRequestDetails) {
    process(theResource, theOldResource, RestOperationTypeEnum.UPDATE, theRequestDetails);
  }

  @Hook(Pointcut.STORAGE_PRECOMMIT_RESOURCE_DELETED)
  public void deleted(IBaseResource theResource, RequestDetails theRequestDetails) {
    process(theResource, null, RestOperationTypeEnum.DELETE, theRequestDetails);
  }


  protected void process(IBaseResource resource, IBaseResource oldResource, RestOperationTypeEnum operationType, RequestDetails theRequestDetails) {
    
    // currently only Practitioner and Oraganization resources have topics involving updates
    String type = resource.fhirType();
    List<String> updateTypes = List.of("Practitioner", "Organization");
    if (operationType == RestOperationTypeEnum.UPDATE && !updateTypes.contains(type)) {
      return;
    }

    // Which related resources will be included in the notification bundle depends on the resource type and operation type.
    // For update and delete operations we will use includes and revincludes since the resource has already been committed to the database 
    // and that means we can run a standard search using the existing resource ID.
    // For create operations we will specify FHIRPaths to include existing referenced resources since the resource has not been 
    // committed to the database yet and would return no results yet for a search using _id as a search parameter.


    //
    // Endpoint
    //
    if (type.equals("Endpoint")) {

      // return Endpoint, CareTeam, HealthcareService, InsurancePlan, Location, Network, Organization, OrganizationAffiliation, Practitioner, PracticionerRole

      Bundle bundle = operationType == RestOperationTypeEnum.CREATE
          ? getResultsWithFhirPaths(resource, List.of("Endpoint.managingOrganization"),
              theRequestDetails)
          : getResultsWithIncludes(resource, List.of("Endpoint:organization"),
              List.of("CareTeam:endpoint", "HealthcareService:endpoint", "InsurancePlan:endpoint", "Location:endpoint",
                  "Organization:endpoint", "OrganizationAffiliation:endpoint", "Practitioner:endpoint",
                  "PractitionerRole:endpoint", "VerificationResult:target"),
              theRequestDetails);

      dispatch("http://ndh.org/topic/endpoint-create-or-delete", bundle, operationType);
    }

    //
    // HealthcareService
    //
    else if (type.equals("HealthcareService")) {

      // return HealthcareService, CareTeam, Location, PractionerRole, Organization, OrganizationAffiliation

      Bundle bundle = operationType == RestOperationTypeEnum.CREATE
          ? getResultsWithFhirPaths(resource,
              List.of("HealthcareService.coverageArea", "HealthcareService.location", "HealthcareService.providedBy",
                  "HealthcareService.extension.where(url='http://hl7.org/fhir/us/ndh/StructureDefinition/base-ext-newpatients').extension.where(url='fromNetwork').value.ofType(Reference)"),
              theRequestDetails)
          : getResultsWithIncludes(resource,
              List.of("HealthcareService:coverage-area", "HealthcareService:location", "HealthcareService:organization"),
              List.of("CareTeam:service", "OrganizationAffiliation:service", "PractitionerRole:service"),
              theRequestDetails);

      dispatch("http://ndh.org/topic/healthcareservice-create-or-delete", bundle, operationType);

    }

    //
    // InsurancePlan
    //
    else if (type.equals("InsurancePlan")) {

      // return InsurancePlan, Network, Organization, Location

      Bundle bundle = operationType == RestOperationTypeEnum.CREATE
          ? getResultsWithFhirPaths(resource,
              List.of("InsurancePlan.administeredBy", "InsurancePlan.ownedBy", "InsurancePlan.coverageArea",
                  "InsurancePlan.coverage.network", "InsurancePlan.plan.network", "InsurancePlan.network"),
              theRequestDetails)
          : getResultsWithIncludes(resource,
              List.of("InsurancePlan:administered-by", "InsurancePlan:owned-by", "InsurancePlan:coverage-area",
                  "InsurancePlan:coverage-network", "InsurancePlan:plan-network", "InsurancePlan:network"),
              null, theRequestDetails);

      dispatch("http://ndh.org/topic/insuranceplan-create-or-delete", bundle, operationType);

    }

    //
    // Location
    //
    else if (type.equals("Location")) {

      // return Location, CareTeam, HealthcareService, InsurancePlan, Organization, OrganizationAffiliation

      Bundle bundle = operationType == RestOperationTypeEnum.CREATE
          ? getResultsWithFhirPaths(resource,
              List.of("Location.managingOrganization", "Location.partOf",
                  "Location.extension.where(url='http://hl7.org/fhir/us/ndh/StructureDefinition/base-ext-newpatients').extension.where(url='fromNetwork').value.ofType(Reference)"),
              theRequestDetails)
          : getResultsWithIncludes(resource,
              List.of("Location:organization", "Location:partof", "Location:new-patient-from-network"),
              List.of("CareTeam:location", "HealthcareService:coverage-area", "HealthcareService:location",
                  "InsurancePlan:coverage-area",
                  "OrganizationAffiliation:location"),
              theRequestDetails);

      dispatch("http://ndh.org/topic/location-create-or-delete", bundle, operationType);

    }

    //
    // Organization (also includes NDH "Network" type which is based on Organization)
    //
    else if (type.equals("Organization")) {

      Organization organization = (Organization) resource;

      // NDH "Network" type denoted by the code "ntwk"
      if (organization.getType().stream()
          .anyMatch(cc -> cc.hasCoding("http://hl7.org/fhir/us/ndh/CodeSystem/OrgTypeCS", "ntwk"))) {

        // Network, InsurancePlan, Organization, OrganizationAffiliation, PractitionerRole

        Bundle bundle = operationType == RestOperationTypeEnum.CREATE
            ? getResultsWithFhirPaths(organization,
                List.of("Organization.partOf",
                    "Organization.extension.where(url='http://hl7.org/fhir/us/ndh/StructureDefinition/base-ext-location-reference').value.ofType(Reference)"),
                theRequestDetails)
            : getResultsWithIncludes(organization,
                List.of("Organization:partof", "Organization:coverage-area"),
                List.of("InsurancePlan:coverage-network", "InsurancePlan:plan-network", "InsurancePlan:network",
                    "OrganizationAffiliation:network", "PractitionerRole:network",
                    "PractitionerRole:new-patient-from-network"),
                theRequestDetails);

        dispatch("http://ndh.org/topic/network-create-or-delete", bundle, operationType);

      }

      // regular Organization
      else {

        // Organization, CareTeam, Endpoint, HealthcareService, InsurancePlan, Location, Network, OrganizationAffiliation, PractitionerRole

        Bundle bundle = operationType == RestOperationTypeEnum.CREATE
            ? getResultsWithFhirPaths(organization,
                List.of("Organization.endpoint", "Organization.partOf"),
                theRequestDetails)
            : getResultsWithIncludes(organization,
                List.of("Organization:endpoint", "Organization:partof"),
                List.of("CareTeam:organization", "Endpoint:organization", "HealthcareService:organization",
                    "InsurancePlan:administered-by", "InsurancePlan:owned-by", "Location:organization",
                    "OrganizationAffiliation:participating-organization",
                    "OrganizationAffiliation:primary-organization", "PractitionerRole:organization"),
                theRequestDetails);

        dispatch("http://ndh.org/topic/organization-create-or-delete", bundle, operationType);


        String qualificationExtensionUrl = "http://hl7.org/fhir/us/ndh/StructureDefinition/base-ext-qualification";

        // extra check for Organization qualification changes
        if (oldResource != null) {

          // check for extension in old and new resource
          Extension newExt = organization.getExtensionByUrl(qualificationExtensionUrl);
          Extension oldExt = ((Organization) oldResource).getExtensionByUrl(qualificationExtensionUrl);

          // extension has been added or removed
          if ((newExt != null && oldExt == null) || (newExt == null && oldExt != null)) {
            dispatch("http://ndh.org/topic/organization-qualification-create-modified-or-delete", bundle, operationType);
          }

          // extension has been modified
          else if (newExt != null && oldExt != null) {
            var parser = theRequestDetails.getFhirContext().newJsonParser();
            if (!parser.encodeToString(newExt).equals(parser.encodeToString(oldExt))) {
              dispatch("http://ndh.org/topic/organization-qualification-create-modified-or-delete", bundle, operationType);
            }
          }
          
        }
        // qualification extension being present on a new resource means a qualification was created
        else if (operationType == RestOperationTypeEnum.CREATE && !organization.getExtensionsByUrl(qualificationExtensionUrl).isEmpty()) {
          dispatch("http://ndh.org/topic/organization-qualification-create-modified-or-delete", bundle, operationType);
        }

      }

    }

    //
    // Practitioner
    //
    else if (type.equals("Practitioner")) {

      Bundle bundle = operationType == RestOperationTypeEnum.CREATE
          ? getResultsWithFhirPaths(resource, null, theRequestDetails)
          : getResultsWithIncludes(resource, null,
              List.of("PractitionerRole:practitioner"), theRequestDetails);

      if (operationType == RestOperationTypeEnum.CREATE || operationType == RestOperationTypeEnum.DELETE) {
        dispatch("http://ndh.org/topic/practitioner-create-or-delete", bundle, operationType);
      }

      // extra check for Practitioner qualification changes
      // check for a change in the qualification list if an old resource is present
      if (oldResource != null) {
        if (pathHasChanged(oldResource, resource, "Practitioner.qualification", theRequestDetails)) {
          dispatch("http://ndh.org/topic/practitioner-qualification-create-modified-or-delete", bundle, operationType);
        }
      }
      // otherwise if this is a create operation, check if the new resource has qualifications
      else if (operationType == RestOperationTypeEnum.CREATE && ((Practitioner)resource).hasQualification()) {
        dispatch("http://ndh.org/topic/practitioner-qualification-create-modified-or-delete", bundle, operationType);
      }
    }

  }


  protected void dispatch(String topic, Bundle bundle, RestOperationTypeEnum operationType) {
    subscriptionTopicDispatcher.dispatch(topic, 
      bundle.getEntry().stream().map(e -> e.getResource()).collect(Collectors.toList()), 
      operationType);
  }


  protected Bundle getResultsWithFhirPaths(IBaseResource resource, List<String> fhirPaths, RequestDetails theRequestDetails) {
    Bundle bundle = new Bundle();
    bundle.addEntry().setResource((Resource)resource);

    if (fhirPaths == null) {
      return bundle;
    }

    for (String path : fhirPaths) {

      IFhirPath fhirPath = theRequestDetails.getFhirContext().newFhirPath();
      List<IBase> references = fhirPath.evaluate(resource, path, IBase.class);

      if (!references.isEmpty()) {
        references.forEach(r -> {
          if (!Reference.class.isInstance(r)) {
            return;
          }

          Reference ref = (Reference)r;

          var dao = daoRegistry.getResourceDaoOrNull(ref.getReferenceElement().getResourceType());
          if (dao != null) {
            var result = dao.read(new IdType(ref.getReferenceElement().getIdPart()), theRequestDetails);
            if (result != null && result instanceof Resource) {
              bundle.addEntry().setResource((Resource)result);
            }
          }
        });
      }
    }

    return bundle;
  }


  protected Bundle getResultsWithIncludes(IBaseResource resource, List<String> includes, List<String> revIncludes, RequestDetails theRequestDetails) {

    SearchParameterMap searchMap = new SearchParameterMap();
    searchMap.add("_id", new StringParam(resource.getIdElement().getIdPart()));

    if (includes != null) {
      for (String include : includes) {
        searchMap.addInclude(new Include(include));
      }
    }

    if (revIncludes != null) {
      for (String revInclude : revIncludes) {
        searchMap.addRevInclude(new Include(revInclude));
      }
    }

    Bundle bundle = new Bundle();
    bundle.addEntry().setResource((Resource)resource);

    IBundleProvider res = daoRegistry.getResourceDao(resource.fhirType()).search(searchMap, theRequestDetails);
    if (res != null) {
      res.getAllResources().forEach(r -> {
        if (r instanceof Resource && !r.getIdElement().equals(resource.getIdElement())) {
          bundle.addEntry().setResource((Resource)r);
        }
      });
    }


    return bundle;

  }


  protected Boolean pathHasChanged(IBaseResource oldResource, IBaseResource newResource, String path, RequestDetails theRequestDetails) {
    
    FhirPatch patch = new FhirPatch(theRequestDetails.getFhirContext());
    var diff = patch.diff(oldResource, newResource);
    
    Boolean hasChanged = false;
    for (var param : ((Parameters) diff).getParameter()) {
      if (param.getName().equals("operation")) {
        hasChanged = param.getPart().stream().anyMatch(part -> 
            part.getName().equals("path")
            && part.getValue() instanceof StringType
            && ((StringType) part.getValue()).getValue().startsWith(path));
      }
      if (hasChanged) {
        break;
      }
    }

    return hasChanged;

  }
  

  
}
