package fr.insee.rmes.bauhaus_services.rdfUtils;

import java.util.Arrays;
import java.util.List;

import javax.ws.rs.core.Response;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openrdf.OpenRDFException;
import org.openrdf.model.Model;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.vocabulary.DCTERMS;
import org.openrdf.model.vocabulary.SKOS;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;

import fr.insee.rmes.bauhaus_services.Constants;
import fr.insee.rmes.config.Config;
import fr.insee.rmes.exceptions.RmesException;

public class RepositoryPublication extends RepositoryUtils{

	private static final String CONNECTION_TO = "Connection to ";

	private static final String FAILED = " failed";

	static final Logger logger = LogManager.getLogger(RepositoryPublication.class);

	public static final Repository REPOSITORY_PUBLICATION = initRepository(Config.SESAME_SERVER_PUBLICATION, Config.REPOSITORY_ID_PUBLICATION);

	/**
	 * Method which aims to produce response from a sparql query
	 * 
	 * @param query
	 * @return String
	 * @throws RmesException 
	 */
	public static String getResponse(String query) throws RmesException {
		return getResponse(query, REPOSITORY_PUBLICATION);
	}

	/**
	 * Method which aims to produce response from a sparql query
	 * 
	 * @param query
	 * @return JSONArray
	 * @throws RmesException 
	 */
	public static JSONArray getResponseAsArray(String query) throws RmesException {
		return getResponseAsArray(query, REPOSITORY_PUBLICATION);
	}

	/**
	 * Method which aims to produce response from a sparql query
	 * 
	 * @param query
	 * @return JSONObject
	 * @throws RmesException 
	 */
	public static JSONObject getResponseAsObject(String query) throws RmesException {
		return getResponseAsObject(query, REPOSITORY_PUBLICATION);
	}

	/**
	 * Method which aims to produce response from a sparql ASK query
	 * 
	 * @param query
	 * @return String
	 * @throws RmesException 
	 * @throws JSONException 
	 */
	public static Boolean getResponseAsBoolean(String query) throws  RmesException {
		return getResponseAsBoolean(query, REPOSITORY_PUBLICATION);
	}
	
	/**
	 * Method which aims to execute sparql update
	 * 
	 * @param updateQuery
	 * @return String
	 * @throws RmesException 
	 */
	public static Response.Status executeUpdate(String updateQuery) throws RmesException {
		return executeUpdate(updateQuery, REPOSITORY_PUBLICATION);
	}

	public static void publishConcept(Resource concept, Model model, List<Resource> noteToClear,
			List<Resource> topConceptOfToDelete) throws RmesException {
		try {
			RepositoryConnection conn = REPOSITORY_PUBLICATION.getConnection();
			// notes to delete
			for (Resource note : noteToClear) {
				conn.remove(note, null, null);
			}
			// top concepts of to delete
			for (Resource c : topConceptOfToDelete) {
				conn.remove(c, SKOS.TOP_CONCEPT_OF, null);
			}
			// links to delete
			clearConceptLinks(concept, conn);

			conn.remove(concept, null, null);
			conn.add(model);
			conn.close();
			logger.info("Publication of concept : {}", concept);
		} catch (OpenRDFException e) {
			logger.error("Publication of concept : {} {} {}", concept, FAILED,  e.getMessage());
			logger.error("{} {} {}", CONNECTION_TO , Config.SESAME_SERVER_PUBLICATION, FAILED);
			throw new RmesException(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage(), CONNECTION_TO + Config.SESAME_SERVER_PUBLICATION + FAILED);
		}
	}

	public static void publishResource(Resource resource, Model model, String type) throws RmesException {
		try {
			RepositoryConnection conn = REPOSITORY_PUBLICATION.getConnection();
			conn.remove(resource, null, null);
			conn.add(model);
			conn.close();
			logger.info("Publication of {} : {}" ,type, resource);
		} catch (OpenRDFException e) {
			logger.error("Publication of {} : {} {}" ,type, resource, FAILED);
			logger.error("{} {} {}", CONNECTION_TO, Config.SESAME_SERVER_PUBLICATION, FAILED);
			throw new RmesException(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage(), CONNECTION_TO + Config.SESAME_SERVER_PUBLICATION + FAILED);

		}
	}


	
	
	public static void clearConceptLinks(Resource concept, RepositoryConnection conn) throws RmesException {
		List<URI> typeOfLink = Arrays.asList(SKOS.BROADER, SKOS.NARROWER, SKOS.MEMBER, DCTERMS.REFERENCES,
				DCTERMS.REPLACES, SKOS.RELATED);

		for (URI predicat : typeOfLink) {
			RepositoryResult<Statement> statements = null;
			try {
				statements = conn.getStatements(null, predicat, concept, false);
			} catch (RepositoryException e) {
				throw new RmesException(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage(), Constants.REPOSITORY_EXCEPTION);
			}
			try {
				conn.remove(statements);
			} catch (RepositoryException e) {
				throw new RmesException(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage(), Constants.REPOSITORY_EXCEPTION);
			}
		}
	}

	
	private RepositoryPublication() {
	    throw new IllegalStateException("Utility class");
	}

}