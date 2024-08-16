package com.lantanagroup.util;

import org.hl7.fhir.instance.model.api.IBaseCoding;
import org.hl7.fhir.instance.model.api.IBaseResource;

public class AttestationUtil {

  public static boolean isAttested(IBaseResource resource) {
    return !isUnattested(resource);
  }

  public static boolean isUnattested(IBaseResource resource) {
    boolean isResourceUnattested = false;

    if (resource == null || resource.getMeta() == null || resource.getMeta().getSecurity() == null) {
      return isResourceUnattested;
    }

    for (IBaseCoding coding : resource.getMeta().getSecurity()) {
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
