package com.lantanagroup.models;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

public class UserProfile {
  
  @Getter @Setter
  private String email;

  @Getter @Setter
  private String firstName;

  @Getter @Setter
  private String lastName;

  @Getter @Setter
  private List<String> roles;

  @Getter @Setter
  private String practitioner;

  @Getter @Setter
  private List<String> organizations;

  @Getter @Setter
  private String description;

}
