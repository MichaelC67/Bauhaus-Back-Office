package fr.insee.rmes.webservice.operations;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.insee.rmes.bauhaus_services.Constants;
import fr.insee.rmes.config.auth.roles.Roles;
import fr.insee.rmes.config.swagger.model.IdLabel;
import fr.insee.rmes.exceptions.RmesException;
import fr.insee.rmes.model.operations.Family;
import fr.insee.rmes.model.operations.Operation;
import fr.insee.rmes.webservice.OperationsCommonResources;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/***************************************************************************************************
 * FAMILY
 ******************************************************************************************************/

@Qualifier("Family")
@RestController
@RequestMapping("/operations")
public class FamilyResources extends OperationsCommonResources {

	@GetMapping("/families")
	@Produces(MediaType.APPLICATION_JSON)
	@io.swagger.v3.oas.annotations.Operation(operationId = "getFamilies", summary = "List of families", 
	responses = {@ApiResponse(content=@Content(array=@ArraySchema(schema=@Schema(implementation=IdLabel.class))))})
	public Response getFamilies() throws RmesException {
		String jsonResultat = operationsService.getFamilies();
		return Response.status(HttpStatus.SC_OK).entity(jsonResultat).build();
	}

	@GetMapping("/families/advanced-search")
	@Produces(MediaType.APPLICATION_JSON)
	@io.swagger.v3.oas.annotations.Operation(operationId = "getFamiliesForSearch", summary = "List of families for search",
	responses = {@ApiResponse(content=@Content(array=@ArraySchema(schema=@Schema(implementation=Family.class))))})
	public Response getFamiliesForSearch() throws RmesException {
		String jsonResultat = operationsService.getFamiliesForSearch();
		return Response.status(HttpStatus.SC_OK).entity(jsonResultat).build();
	}

	@GetMapping("/families/{id}/seriesWithReport")
	@Produces(MediaType.APPLICATION_JSON)
	@io.swagger.v3.oas.annotations.Operation(operationId = "getSeriesWithReport", summary = "Series with metadataReport",  responses = {@ApiResponse(content=@Content(schema=@Schema(type="array",implementation= Operation.class)))})
	public Response getSeriesWithReport(@PathVariable(Constants.ID) String id) {
		String jsonResultat;
		try {
			jsonResultat = operationsService.getSeriesWithReport(id);
		} catch (RmesException e) {
			return Response.status(e.getStatus()).entity(e.getDetails()).type(MediaType.TEXT_PLAIN).build();
		}
		return Response.status(HttpStatus.SC_OK).entity(jsonResultat).build();
	}

	@GetMapping("/family/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	@io.swagger.v3.oas.annotations.Operation(operationId = "getFamilyByID", summary = "Get a family", 
	responses = { @ApiResponse(content = @Content(mediaType = "application/json", schema = @Schema(implementation = Family.class)))}
			)
	public Response getFamilyByID(@PathVariable(Constants.ID) String id) throws RmesException {
		String jsonResultat = operationsService.getFamilyByID(id);
		return Response.status(HttpStatus.SC_OK).entity(jsonResultat).build();
	}

	/**
	 * UPDATE
	 * @param id, body
	 * @return response
	 */

	@Secured({ Roles.SPRING_ADMIN })
	@PutMapping("/family/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@io.swagger.v3.oas.annotations.Operation(operationId = "setFamilyById", summary = "Update family" )
	public Response setFamilyById(
			@PathVariable(Constants.ID) String id, 
			@RequestBody(description = "Family to update", required = true,
			content = @Content(schema = @Schema(implementation = Family.class))) String body) {
		try {
			operationsService.setFamily(id, body);
		} catch (RmesException e) {
			return Response.status(e.getStatus()).entity(e.getDetails()).type(MediaType.TEXT_PLAIN).build();
		}
		return Response.status(Status.NO_CONTENT).build();
	}


	/**
	 * CREATE
	 * @param body
	 * @return response
	 */
	@Secured({ Roles.SPRING_ADMIN })
	@PostMapping("/family")
	@Consumes(MediaType.APPLICATION_JSON)
	@io.swagger.v3.oas.annotations.Operation(operationId = "createFamily", summary = "Create family")
	public Response createFamily(
			@RequestBody(description = "Family to create", required = true, 
			content = @Content(schema = @Schema(implementation = Family.class))) String body) {
		String id = null;
		try {
			id = operationsService.createFamily(body);
		} catch (RmesException e) {
			return Response.status(e.getStatus()).entity(e.getDetails()).type(MediaType.TEXT_PLAIN).build();
		}
		return Response.status(HttpStatus.SC_OK).entity(id).build();
	}

	@Secured({ Roles.SPRING_ADMIN })
	@PutMapping("/family/validate/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@io.swagger.v3.oas.annotations.Operation(operationId = "setFamilyValidation", summary = "Family validation")
	public Response setFamilyValidation(
			@PathVariable(Constants.ID) String id) throws RmesException {
		try {
			operationsService.setFamilyValidation(id);
		} catch (RmesException e) {
			return returnRmesException(e);
		}
		return Response.status(HttpStatus.SC_OK).entity(id).build();
	}


}
