# NDH Reference Implementation

This is a FHIR server reference implementation of the [National Directory IG](https://build.fhir.org/ig/HL7/fhir-us-ndh/).  It is built on the [HAPI FHIR JPA Starter Project](https://github.com/hapifhir/hapi-fhir-jpaserver-starter) project and more detailed configuration information can be found in that repository.

## Prerequisites
Building and running the server locally requires either Docker or
- Java 17+
- Maven

## Building and Running the Server

There are multiple ways to build and run the server locally.  By default, the server's base FHIR endpoint will be available at http://localhost:8080/fhir

### Using Maven


```bash
mvn spring-boot:run
```
or
```bash
mvn -Pjetty spring-boot:run
```

### Using Docker

```bash
docker compose up -d
```

## Custom Operations

The reference implementation includes two custom operations to handle resource attestation and resource verification.  These operations are not defined in the NDH IG.

### `$attest` Operation

The `$attest` operation currently marks existing resource(s) as attested.  In this RI, resources are considered unattested if they include the following in their `meta.security` property:
```json
{
  "system": "http://terminology.hl7.org/CodeSystem/v3-Confidentiality",
  "code": "V"
}
```

The operation expects a FHIR `Parameters` body with the following structure that contains references to which resources should be marked as attested:
```
POST [fhir base]/$attest
```
```json
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "resource",
      "valueReference": {
        "reference": "Practitioner/123"
      }
    }
  ]
}
```

The output of the operation is an `OperationOutcome`.


### `$verify` Operation

The `$verify` operation will mark a resource as verified by updating its [NDH Verification Status extension](http://build.fhir.org/ig/HL7/fhir-us-ndh/StructureDefinition-base-ext-verification-status.html).

The operation expects a FHIR `Parameters` body with the following structure that contains references to which resources should be marked as verified and providing the "who" that is verifying the resource:
```
POST [fhir base]/$verify
```
```json
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "verification",
      "part": [
        {
          "name": "target",
          "valueReference": {
            "reference": "Practitioner/123"
          }
        },
        {
          "name": "attestor",
          "valueReference": {
            "reference": "Organization/Hospital1"
          }
        }
      ]
    }
  ]
}
```


## Postman Collection

A basic Postman collection that demonstrates creating a new Practitioner, marking it as attested, and verifying it is available in the ndh.postman_collection.json file.


## Questions and Contributions
Questions about the project can be asked in the [US National Directory stream on the FHIR Zulip Chat](https://chat.fhir.org/#narrow/stream/283066-united-states.2Fnational-directory).

This project welcomes Pull Requests. Any issues identified with the RI should be submitted via the [GitHub issue tracker](https://github.com/HL7-FAST/national-directory/issues).

As of October 1, 2022, The Lantana Consulting Group is responsible for the management and maintenance of this Reference Implementation.
In addition to posting on FHIR Zulip Chat channel mentioned above you can contact [Corey Spears](mailto:corey.spears@lantanagroup.com) for questions or requests.
