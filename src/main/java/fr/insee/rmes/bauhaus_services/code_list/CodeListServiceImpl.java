package fr.insee.rmes.bauhaus_services.code_list;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.vocabulary.DC;
import org.eclipse.rdf4j.model.vocabulary.DCTERMS;
import org.eclipse.rdf4j.model.vocabulary.OWL;
import org.eclipse.rdf4j.model.vocabulary.PROV;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.model.vocabulary.SKOS;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.insee.rmes.bauhaus_services.CodeListService;
import fr.insee.rmes.bauhaus_services.Constants;
import fr.insee.rmes.bauhaus_services.operations.famopeserind_utils.FamOpeSerIndUtils;
import fr.insee.rmes.bauhaus_services.rdf_utils.QueryUtils;
import fr.insee.rmes.bauhaus_services.rdf_utils.RdfService;
import fr.insee.rmes.bauhaus_services.rdf_utils.RdfUtils;
import fr.insee.rmes.exceptions.ErrorCodes;
import fr.insee.rmes.exceptions.RmesBadRequestException;
import fr.insee.rmes.exceptions.RmesException;
import fr.insee.rmes.exceptions.RmesUnauthorizedException;
import fr.insee.rmes.model.ValidationStatus;
import fr.insee.rmes.persistance.ontologies.INSEE;
import fr.insee.rmes.persistance.sparql_queries.code_list.CodeListQueries;
import fr.insee.rmes.utils.DateUtils;

@Service
public class CodeListServiceImpl extends RdfService implements CodeListService  {

	private static final String LAST_CLASS_URI_SEGMENT = "lastClassUriSegment";

	private static final String CODE = "code";

	private static final String POSITION = "position";

	private static final String CODES = "codes";

	private static final String LAST_LIST_URI_SEGMENT = "lastListUriSegment";

	private static final String LAST_CODE_URI_SEGMENT = "lastCodeUriSegment";

	static final Logger logger = LogManager.getLogger(CodeListServiceImpl.class);
	
	@Autowired
	FamOpeSerIndUtils famOpeSerIndUtils;


	@Override
	public String getCodeListJson(String notation) throws RmesException{
		JSONObject codeList = repoGestion.getResponseAsObject(CodeListQueries.getCodeListLabelByNotation(notation));
		codeList.put(Constants.NOTATION,notation);
		JSONArray items = repoGestion.getResponseAsArray(CodeListQueries.getCodeListItemsByNotation(notation));
		if (items.length() != 0){
			codeList.put(CODES, items);
		}
		return QueryUtils.correctEmptyGroupConcat(codeList.toString());
	}

	public CodeList buildCodeListFromJson(String codeListJson) {
		ObjectMapper mapper = new ObjectMapper();
		CodeList codeList = new CodeList();
		try {
			codeList = mapper.readValue(codeListJson, CodeList.class);
		} catch (JsonProcessingException e) {
			logger.error("Json cannot be parsed: ".concat(e.getMessage()));
		}
		return codeList;
	}
	
	@Override
	public CodeList getCodeList(String notation) throws RmesException {
		return buildCodeListFromJson(getCodeListJson(notation));	
	}

	@Override
	public String getDetailedCodesList(String notation, boolean partial) throws RmesException {
		return getDetailedCodesListJson(notation, partial).toString();
	}

	public JSONObject getDetailedCodesListJson(String notation, boolean partial) throws RmesException {
		JSONObject codeList = repoGestion.getResponseAsObject(CodeListQueries.getDetailedCodeListByNotation(notation));
		JSONArray codes = repoGestion.getResponseAsArray(CodeListQueries.getDetailedCodes(notation, partial));

		if(!partial){
			formatCodesForFullList(notation, codeList, codes);
		}
		else {
			formatCodesForPartialList(codeList, codes);
		}

		return codeList;
	}

	private void formatCodesForFullList(String notation, JSONObject codeList, JSONArray codes) throws RmesException {
		Map<String, List<String>> parents = new HashMap<>();

		if(codes.length() > 0){
			JSONObject formattedCodes = new JSONObject();
			codes.forEach(c -> {
				JSONObject tempCode = (JSONObject) c;
				String code = tempCode.getString(CODE);
				if(tempCode.has(Constants.PARENTS)){
					String parentCode = tempCode.getString(Constants.PARENTS);
					if(!parents.containsKey(parentCode)){
						parents.put(parentCode, Arrays.asList(code));
						formattedCodes.put(code, tempCode);
					} else {
						List<String> parentList = new ArrayList<>(parents.get(parentCode));
						parentList.add(code);
						parents.put(parentCode, parentList);
					}
					tempCode.remove(Constants.PARENTS);
				}
				formattedCodes.put(code, tempCode);
			});


			JSONArray seq =  repoGestion.getResponseAsArray(CodeListQueries.getCodesSeq(notation));
	
			if(seq.length() > 0){
				formatCodeListBySeq(parents, formattedCodes, seq);
			} else {
				formatCodeListWithoutSeq(parents, formattedCodes);
			}
			codeList.put(CODES, formattedCodes);
		}
	}

	private void formatCodeListWithoutSeq(Map<String, List<String>> parents, JSONObject formattedCodes) {
		// If we do not have a Seq, we have to sort alphabetically.
		parents.keySet().forEach(key -> {
			List<String> children = parents.get(key);
			children.sort((o1, o2) -> o1.compareTo(o2));

			for(int i = 0; i < children.size(); i++){
				String child = children.get(i);
				JSONObject codeObject = formattedCodes.getJSONObject(child);
				addOrUpdateParents(i + 1, key, codeObject);
				formattedCodes.put(child, codeObject);
			}
		});
		// Here will order all root codes. Codes without parents
		orderRootCodes(formattedCodes);
	}

	private void formatCodeListBySeq(Map<String, List<String>> parents, JSONObject formattedCodes, JSONArray seq) {
		for(int i = 0; i < seq.length(); i++){
			JSONObject codeAndPosition = seq.getJSONObject(i);
			String code = codeAndPosition.getString(CODE);

			if(parents.containsKey(code)){
				List<String> parentChilden = parents.get(code);
				int j = i + 1;
				int position = 1;
				while (j < seq.length() && parentChilden.contains(seq.getJSONObject(j).getString(CODE))){
					String childCode = seq.getJSONObject(j).getString(CODE);
					JSONObject child = formattedCodes.getJSONObject(childCode);
					addOrUpdateParents(position, code, child);
					formattedCodes.put(childCode, child);
					j++;
					position++;
				}
				i = j - 1;

			}
		}
	}

	private void addOrUpdateParents(int position, String code, JSONObject child) {
		if(!child.has(Constants.PARENTS)){
			child.put(Constants.PARENTS, new JSONArray());
		}
		child.getJSONArray(Constants.PARENTS).put(new JSONObject().put(CODE, code).put(POSITION, position));
	}


	/**
	 * Format the codes list for a partial code list.
	 * We just need to remove the parents property.
	 *
	 * @param codeList
	 * @param codes
	 */
	private void formatCodesForPartialList(JSONObject codeList, JSONArray codes) {
		JSONObject formattedCodes = new JSONObject();
		codes.forEach(c -> {
			JSONObject tempCode = (JSONObject) c;
			String code = tempCode.getString(CODE);
			if (tempCode.has(Constants.PARENTS)) {
				tempCode.remove(Constants.PARENTS);
			}
			formattedCodes.put(code, tempCode);
		});
		codeList.put(CODES, formattedCodes);
	}

	private void orderRootCodes(JSONObject formattedCodes) {
		List<String> rootCodes = formattedCodes.keySet().stream().filter(key ->  
		!formattedCodes.getJSONObject(key).has(Constants.PARENTS))
				.sorted(Comparator.comparing(code -> code))
				.collect(Collectors.toList()
		);
		if(!rootCodes.isEmpty()) {
			for(int i = 0; i < rootCodes.size(); i++) {
				JSONObject parent = new JSONObject().put(CODE, "").put(POSITION, i + 1);
				formattedCodes.getJSONObject(rootCodes.get(i)).put(Constants.PARENTS, new JSONArray().put(parent));
			}
		}
	}

	@Override
	public String getDetailedCodesListForSearch(boolean partial) throws RmesException {
		JSONArray lists =  repoGestion.getResponseAsArray(CodeListQueries.getCodesListsForSearch(partial));
		JSONArray codes =  repoGestion.getResponseAsArray(CodeListQueries.getCodesForSearch(partial));

		for (int i = 0 ; i < lists.length(); i++) {
			JSONObject list = lists.getJSONObject(i);
			list.put(CODES, this.getCodesForList(codes, list));
		}

		return lists.toString();
	}


	public void validateCodeList(JSONObject codeList, boolean partial) throws RmesException {
		if (!codeList.has(Constants.ID)) {
			throw new RmesBadRequestException("The id of the list should be defined");
		}
		if (!codeList.has(Constants.LABEL_LG1)) {
			throw new RmesBadRequestException("The labelLg1 of the list should be defined");
		}
		if (!codeList.has(Constants.LABEL_LG2)) {
			throw new RmesBadRequestException("The labelLg2 of the list should be defined");
		}
		if (!partial && !codeList.has(LAST_CLASS_URI_SEGMENT)) {
			throw new RmesBadRequestException("The lastClassUriSegment of the list should be defined");
		}
		if (!partial && !codeList.has(LAST_LIST_URI_SEGMENT)) {
			throw new RmesBadRequestException("The lastListUriSegment of the list should be defined");
		}
		if(!codeList.has(CODES) || codeList.getJSONObject(CODES).keySet().isEmpty()){
			throw new RmesUnauthorizedException(ErrorCodes.CODE_LIST_AT_LEAST_ONE_CODE, "A code list should contain at least one code");
		}
	}

	private IRI generateIri(JSONObject codesList, boolean partial) {
		if(partial){
			return RdfUtils.codeListIRI(codesList.getString(Constants.ID));
		} else {
			return RdfUtils.codeListIRI(codesList.getString(LAST_LIST_URI_SEGMENT));
		}
	}

	private boolean checkCodeListUnicity(boolean partial, JSONObject codeList, String iri) throws RmesException {
		String id = codeList.getString(Constants.ID);
		if(!partial) {
			IRI seeAlso = RdfUtils.codeListIRI("concept/" + codeList.getString(LAST_CLASS_URI_SEGMENT));
			return repoGestion.getResponseAsBoolean(CodeListQueries.checkCodeListUnicity(id, iri, RdfUtils.toString(seeAlso), partial));
		}
		return repoGestion.getResponseAsBoolean(CodeListQueries.checkCodeListUnicity(id, iri, "", partial));
	}

	@Override
	public String setCodesList(String body, boolean partial) throws RmesException {
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(
				DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		JSONObject codesList = new JSONObject(body);

		this.validateCodeList(codesList, partial);

		IRI codeListIri = this.generateIri(codesList, partial);

		if(this.checkCodeListUnicity(partial, codesList, RdfUtils.toString(codeListIri))){
			throw new RmesUnauthorizedException(ErrorCodes.CODE_LIST_UNICITY,
					"The identifier, IRI and OWL class should be unique", "");
		}

		repoGestion.clearStructureNodeAndComponents(codeListIri);
		Model model = new LinkedHashModel();
		Resource graph = RdfUtils.codesListGraph();
		RdfUtils.addTripleDateTime(codeListIri, DCTERMS.CREATED, DateUtils.getCurrentDate(), model, graph);
		RdfUtils.addTripleDateTime(codeListIri, DCTERMS.MODIFIED, DateUtils.getCurrentDate(), model, graph);
		return this.createOrUpdateCodeList(model, graph, codesList, codeListIri, partial);
	}

	@Override
	public String setCodesList(String id, String body, boolean partial) throws RmesException {
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(
				DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		JSONObject codesList = new JSONObject(body);

		this.validateCodeList(codesList, partial);

		IRI codeListIri = this.generateIri(codesList, partial);
		repoGestion.clearStructureNodeAndComponents(codeListIri);
		Model model = new LinkedHashModel();
		Resource graph = RdfUtils.codesListGraph();

		RdfUtils.addTripleDateTime(codeListIri, DCTERMS.CREATED, codesList.getString("created"), model, graph);
		RdfUtils.addTripleDateTime(codeListIri, DCTERMS.MODIFIED, DateUtils.getCurrentDate(), model, graph);

		return this.createOrUpdateCodeList(model, graph, codesList, codeListIri, partial);
	}

	@Override
	public String getPartialCodeListByParent(String parentCode) throws RmesException {
		JSONObject parent = this.getDetailedCodesListJson(parentCode, false);
		String parentIRI = parent.getString("iri");
		JSONArray partials = repoGestion.getResponseAsArray(CodeListQueries.getPartialCodeListByParentUri(parentIRI));
		return partials.toString();
	}

	@Override
	public void deleteCodeList(String notation, boolean partial) throws RmesException {
		JSONObject codesList = getDetailedCodesListJson(notation, partial);
		String iri = codesList.getString("iri");

		if(!codesList.getString("validationState").equalsIgnoreCase("Unpublished")){
			throw new RmesUnauthorizedException(ErrorCodes.CODE_LIST_DELETE_ONLY_UNPUBLISHED, "Only unpublished codelist can be deleted");
		}

		if(!partial){
			JSONArray partials = repoGestion.getResponseAsArray(CodeListQueries.getPartialCodeListByParentUri(iri));
			if(partials.length() > 0){
				throw new RmesUnauthorizedException(ErrorCodes.CODE_LIST_DELETE_CODELIST_WITHOUT_PARTIAL, "Only codelist with partial codelists can be deleted");
			}

			JSONObject codes = codesList.getJSONObject(CODES);
			for (String key : codes.keySet()) {
				String codeIri = codes.getJSONObject(key).getString("codeUri");
				repoGestion.deleteObject(RdfUtils.toURI(codeIri), null);
			}
		}

		repoGestion.deleteObject(RdfUtils.toURI(iri), null);
	}

	private String createOrUpdateCodeList(Model model, Resource graph, JSONObject codesList, IRI codeListIri, boolean partial) throws RmesException {
		String codeListId = codesList.getString(Constants.ID);

		model.add(codeListIri, INSEE.VALIDATION_STATE, RdfUtils.setLiteralString(ValidationStatus.UNPUBLISHED), graph);

		IRI type = partial ? SKOS.COLLECTION : SKOS.CONCEPT_SCHEME ;
		RdfUtils.addTripleUri(codeListIri, RDF.TYPE, type, model, graph);
		model.add(codeListIri, SKOS.NOTATION, RdfUtils.setLiteralString(codeListId), graph);


		if(codesList.has("disseminationStatus")){
			RdfUtils.addTripleUri(codeListIri, INSEE.DISSEMINATIONSTATUS, codesList.getString("disseminationStatus"), model, graph);
		}

		model.add(codeListIri, SKOS.PREF_LABEL, RdfUtils.setLiteralString(codesList.getString(Constants.LABEL_LG1), config.getLg1()), graph);
		model.add(codeListIri, SKOS.PREF_LABEL, RdfUtils.setLiteralString(codesList.getString(Constants.LABEL_LG2), config.getLg2()), graph);


		if(codesList.has(Constants.DESCRIPTION_LG1)){
			model.add(codeListIri, SKOS.DEFINITION, RdfUtils.setLiteralString(codesList.getString(Constants.DESCRIPTION_LG1), config.getLg1()), graph);
		}
		if(codesList.has(Constants.DESCRIPTION_LG2)){
			model.add(codeListIri, SKOS.DEFINITION, RdfUtils.setLiteralString(codesList.getString(Constants.DESCRIPTION_LG2), config.getLg2()), graph);
		}
		if(codesList.has(Constants.CREATOR)){
			RdfUtils.addTripleString(codeListIri, DC.CREATOR, codesList.getString(Constants.CREATOR), model, graph);
		}
		if(codesList.has(Constants.CONTRIBUTOR)){
			RdfUtils.addTripleString(codeListIri, DC.CONTRIBUTOR, codesList.getString(Constants.CONTRIBUTOR), model, graph);
		}

		if(partial){
			if(codesList.has(CODES)) {
				JSONObject codes = codesList.getJSONObject(CODES);
				for (String key : codes.keySet()) {
					JSONObject code = codes.getJSONObject(key);
					RdfUtils.addTripleUri(codeListIri, SKOS.MEMBER, RdfUtils.createIRI(code.getString("codeUri")), model, graph);
				}
			}
			if(codesList.has("iriParent")){
				RdfUtils.addTripleUri(codeListIri, PROV.WAS_DERIVED_FROM, codesList.getString("iriParent"), model, graph);
			}
		} else {
			IRI owlClassUri = RdfUtils.codeListIRI("concept/" + codesList.getString(LAST_CLASS_URI_SEGMENT));
			RdfUtils.addTripleUri(codeListIri, RDFS.SEEALSO, owlClassUri, model, graph);
			RdfUtils.addTripleUri(owlClassUri, RDF.TYPE, OWL.CLASS, model, graph);
			RdfUtils.addTripleUri(owlClassUri, RDFS.SEEALSO, codeListIri, model, graph);
			
			CodeList original = getCodeList(codeListId);
			deleteCodes(codesList, original);
			createCodeTriplet(graph, codesList, codeListIri, model, owlClassUri);
		}
		repoGestion.loadSimpleObject(codeListIri, model, null);
		return codeListId;
	}

	private void deleteCodes(JSONObject codesList, CodeList original) {
		if(original.getCodes() != null) {
			original.getCodes().forEach(code -> {
				IRI codeIri = RdfUtils.codeListIRI(codesList.getString(LAST_LIST_URI_SEGMENT) + "/" + code.getCode());
				try {
					repoGestion.deleteObject(codeIri, null);
				} catch (RmesException e) {
					logger.error(e.getMessage());
				}
			});
		}
	}

	private void createCodeTriplet(Resource graph, JSONObject codesList, IRI codeListIri, Model codeListModel, IRI uriOwlClass) {
		if(codesList.has(CODES)){
			String lastCodeUriSegment = codesList.getString(LAST_CODE_URI_SEGMENT);
			JSONObject parentsModel = new JSONObject();
			JSONObject codes = codesList.getJSONObject(CODES);
			for (String key : codes.keySet()) {
				try {
					JSONObject code = codes.getJSONObject(key);

					Model codeModel = new LinkedHashModel();
					IRI codeIri = RdfUtils.codeListIRI(  lastCodeUriSegment + "/" + code.get(CODE));

					createMainCodeTriplet(graph, codeListIri, code, codeModel, codeIri, uriOwlClass);

					if (code.has(Constants.PARENTS)) {
						JSONArray parentsWithPosition = code.getJSONArray(Constants.PARENTS);
						parentsWithPosition.forEach(parentWithPosition -> {
							String parentCode = ((JSONObject) parentWithPosition).getString(CODE);
							if(!parentCode.equalsIgnoreCase("")){
								IRI parentIRI = RdfUtils.codeListIRI(codesList.getString(LAST_LIST_URI_SEGMENT) + "/" + parentCode);
								RdfUtils.addTripleUri(codeIri, SKOS.BROADER, parentIRI, codeModel, graph);

								if (parentsModel.has(parentCode)) {
									parentsModel.getJSONArray(parentCode).put(code.get(CODE));
								} else {
									parentsModel.put(parentCode, new JSONArray().put(code.get(CODE)));
								}
							}
							else if (parentsModel.has("")) {
									parentsModel.getJSONArray("").put(code.get(CODE));
							} else {
									parentsModel.put("", new JSONArray().put(code.get(CODE)));
							}
						});
					}
					repoGestion.loadSimpleObject(codeIri, codeModel, null);
				} catch (Exception e) {
					logger.debug(e.getMessage());
				}
			}

			createCodesSeq(graph, codeListIri, parentsModel, codeListModel, codesList);
			createParentChildRelationForCodes(graph, codesList, parentsModel);

		}
	}

	/**
	 * We will create triplets to define the Sequence of codes
	 * @param graph
	 * @param codeListIri
	 * @param parentsModel
	 * @param codeListModel
	 * @param codesList
	 */
	private void createCodesSeq(Resource graph, IRI codeListIri, JSONObject parentsModel, Model codeListModel, JSONObject codesList) {
		RdfUtils.addTripleUri(codeListIri, RDF.TYPE, RDF.SEQ, codeListModel, graph);
		JSONObject codes = codesList.getJSONObject(CODES);

		AtomicInteger i = new AtomicInteger();

		List<Object> rootNodes = parentsModel.getJSONArray("").toList();
		rootNodes.sort((key1, key2) -> {
			JSONObject parent1 = findParentPositionForCode(key1, "", codes);
			JSONObject parent2 = findParentPositionForCode(key2, "", codes)	;
			return parent1.getInt(POSITION) - parent2.getInt(POSITION);
		});
		rootNodes.forEach(rootNode -> {
			String rootNodeCode = (String) rootNode;

			IRI parentIRI = RdfUtils.codeListIRI(codesList.getString(LAST_LIST_URI_SEGMENT) + "/" + rootNodeCode);
			RdfUtils.addTripleUri(codeListIri, RdfUtils.createIRI(RDF.NAMESPACE + "_" + i), parentIRI, codeListModel, graph);
			i.getAndIncrement();

			if (parentsModel.has(rootNodeCode)) {
				List<Object> children = parentsModel.getJSONArray(rootNodeCode).toList();
				children.sort((child1, child2) -> {
					JSONObject parent1 = findParentPositionForCode(child1, rootNodeCode, codes);
					JSONObject parent2 = findParentPositionForCode(child2, rootNodeCode, codes)	;
					return parent1.getInt(POSITION) - parent2.getInt(POSITION);
				});

				children.forEach(child -> {
					IRI childIri = RdfUtils.codeListIRI(codesList.getString(LAST_LIST_URI_SEGMENT) + "/" + child);
					RdfUtils.addTripleUri(codeListIri, RdfUtils.createIRI(RDF.NAMESPACE + "_" + i), childIri, codeListModel, graph);
					i.getAndIncrement();
				});
			}
		});
	}

	private JSONObject findParentPositionForCode(Object childCode, String parentCode, JSONObject codes) {
		JSONArray parents = codes
								.getJSONObject(((String) childCode))
								.getJSONArray(Constants.PARENTS);
		JSONObject parentWithPosition = new JSONObject();
		for (int i = 0; i < parents.length(); i++){
			if(parents.getJSONObject(i).getString(CODE).equalsIgnoreCase(parentCode)){
				parentWithPosition = parents.getJSONObject(i);
				break;
			}
		}
		return parentWithPosition;
	}

	private void createParentChildRelationForCodes(Resource graph, JSONObject codesList, JSONObject parentsModel) {
		parentsModel.keySet().forEach(key -> {
			Model parentModel = new LinkedHashModel();
			IRI parentIRI = RdfUtils.codeListIRI(codesList.getString(LAST_LIST_URI_SEGMENT) + "/" + key);
			JSONArray children = parentsModel.getJSONArray(key);
			children.forEach(child -> {
				IRI childIri = RdfUtils.codeListIRI(codesList.getString(LAST_LIST_URI_SEGMENT) + "/" + child);
				RdfUtils.addTripleUri(parentIRI, SKOS.NARROWER, RdfUtils.toString(childIri), parentModel, graph);
			});
			try {
				repoGestion.getConnection().add(parentModel);
			} catch (RmesException e) {
				logger.debug(e.getMessage());
			}
		});
	}

	private void createMainCodeTriplet(Resource graph, IRI codeListIri, JSONObject code, Model codeListModel, IRI codeIri, IRI uriOwlClass) {
		RdfUtils.addTripleUri(codeIri, SKOS.IN_SCHEME, codeListIri, codeListModel, graph);
		if(code.has(CODE)){
			RdfUtils.addTripleString(codeIri, SKOS.NOTATION, code.getString(CODE), codeListModel, graph);
		}
		RdfUtils.addTripleUri(codeIri, RDF.TYPE, SKOS.CONCEPT, codeListModel, graph);
		RdfUtils.addTripleUri(codeIri, RDF.TYPE, uriOwlClass, codeListModel, graph);

		if(code.has(Constants.LABEL_LG1)){
			codeListModel.add(codeIri, SKOS.PREF_LABEL, RdfUtils.setLiteralString(code.getString(Constants.LABEL_LG1), config.getLg1()), graph);
		}
		if(code.has(Constants.LABEL_LG2)){
			codeListModel.add(codeIri, SKOS.PREF_LABEL, RdfUtils.setLiteralString(code.getString(Constants.LABEL_LG2), config.getLg2()), graph);
		}

		if(code.has(Constants.DESCRIPTION_LG1)){
			codeListModel.add(codeIri, SKOS.DEFINITION, RdfUtils.setLiteralString(code.getString(Constants.DESCRIPTION_LG1), config.getLg1()), graph);
		}
		if(code.has(Constants.DESCRIPTION_LG2)){
			codeListModel.add(codeIri, SKOS.DEFINITION, RdfUtils.setLiteralString(code.getString(Constants.DESCRIPTION_LG2), config.getLg2()), graph);
		}
	}


	private JSONArray getCodesForList(JSONArray codes, JSONObject list) {
		JSONArray codesList = new JSONArray();
		for (int i = 0 ; i < codes.length(); i++) {
			JSONObject code = codes.getJSONObject(i);
			if(code.getString(Constants.ID).equalsIgnoreCase(list.getString(Constants.ID))){
				codesList.put(code);
			}
		}
		return codesList;
	}

	@Override
	public String getCode(String notationCodeList, String notationCode) throws RmesException{
		JSONObject code = repoGestion.getResponseAsObject(CodeListQueries.getCodeByNotation(notationCodeList,notationCode));
		code.put(CODE, notationCode);
		code.put("notationCodeList", notationCodeList);
		return QueryUtils.correctEmptyGroupConcat(code.toString());
	}

	@Override
	public String getCodeUri(String notationCodeList, String notationCode) throws RmesException{
			if (StringUtils.isEmpty(notationCodeList) || StringUtils.isEmpty(notationCode)) {return null;}
			JSONObject code = repoGestion.getResponseAsObject(CodeListQueries.getCodeUriByNotation(notationCodeList,notationCode));
			return QueryUtils.correctEmptyGroupConcat(code.getString(Constants.URI));
	}

	@Override
	public String getAllCodesLists(boolean partial) throws RmesException {
		return repoGestion.getResponseAsArray(CodeListQueries.getAllCodesLists(partial)).toString();
	}

	@Override
	public String geCodesListByIRI(String iri) throws RmesException {
		return repoGestion.getResponseAsArray(CodeListQueries.geCodesListByIRI(iri)).toString();
	}
}
