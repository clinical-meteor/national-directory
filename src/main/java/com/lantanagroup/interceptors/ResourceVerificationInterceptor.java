package com.lantanagroup.interceptors;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.DomainResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lantanagroup.util.VerificationUtil;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;


/**
 * Interceptor to handle new or updated resources and process them to add verification status as needed
 */
@Interceptor
public class ResourceVerificationInterceptor {

  private final static Logger logger = LoggerFactory.getLogger(ResourceVerificationInterceptor.class);

  @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
  public void insert(IBaseResource theResource) {
    // logger.info("Checking verification for new resource of type: " + theResource.getClass().getSimpleName());
    VerificationUtil.initializeVerification((DomainResource)theResource);
  }

  @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_UPDATED)
  public void update(IBaseResource theOldResource, IBaseResource theResource) {
    // logger.info("Checking verification for existing resource of type: " + theResource.getClass().getSimpleName());
    VerificationUtil.initializeVerification((DomainResource)theResource);
  }
  

  
  
}
