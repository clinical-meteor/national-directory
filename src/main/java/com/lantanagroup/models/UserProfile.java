package com.lantanagroup.models;

import java.util.List;

public class UserProfile {

  private String email;
  private String firstName;
  private String lastName;
  private List<String> roles;
  private String practitioner;
  private List<String> organizations;
  private String description;

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public List<String> getRoles() {
    return roles;
  }

  public void setRoles(List<String> roles) {
    this.roles = roles;
  }

  public String getPractitioner() {
    return practitioner;
  }

  public void setPractitioner(String practitioner) {
    this.practitioner = practitioner;
  }

  public List<String> getOrganizations() {
    return organizations;
  }

  public void setOrganizations(List<String> organizations) {
    this.organizations = organizations;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

}
