package com.lantanagroup.util;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DomainResource;

public class AttestationUtil {

  public static boolean isAttested(DomainResource resource) {
    return !isUnattested(resource);
  }

  public static boolean isUnattested(DomainResource resource) {
    boolean isResourceUnattested = false;

    if (resource == null || resource.getMeta() == null || resource.getMeta().getSecurity() == null) {
      return isResourceUnattested;
    }

    for (Coding coding : resource.getMeta().getSecurity()) {
      if (coding == null || coding.getCode() == null || coding.getSystem() == null) {
        break;
      }

      if (coding.getCode().equals("V")
          && coding.getSystem().equals("http://terminology.hl7.org/CodeSystem/v3-Confidentiality")) {
        isResourceUnattested = true;
        break;
      }
    }

    return isResourceUnattested;
  }

}
