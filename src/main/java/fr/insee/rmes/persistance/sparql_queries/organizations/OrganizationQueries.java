package fr.insee.rmes.persistance.sparql_queries.organizations;

import fr.insee.rmes.persistance.sparql_queries.GenericQueries;

public class OrganizationQueries extends GenericQueries{

	public static String organizationQuery(String identifier) {
		return "SELECT  ?labelLg1 ?labelLg2 ?altLabel ?type ?motherOrganization ?linkedTo ?seeAlso \n"
				+ "FROM <"+config.getOrganizationsGraph()+"> \n "
				+ "FROM <"+config.getOrgInseeGraph()+"> \n "

				+ "WHERE { \n"
				//id
				+ "?organization dcterms:identifier '"+ identifier +"' . \n"

				//labels
				+ "OPTIONAL { ?organization skos:prefLabel ?labelLg1 . \n"
				+ "FILTER (lang(?labelLg1) = '" + config.getLg1() + "')} \n"
				+ "OPTIONAL {?organization skos:prefLabel ?labelLg2 . \n"
				+ "FILTER (lang(?labelLg2) = '" + config.getLg2() + "') }\n"
				+ "OPTIONAL {?organization skos:altLabel ?altLabel .} \n"

				//type (exclude org:Organization and org:OrganizationUnit)
				+ "OPTIONAL {?organization rdf:type ?type . \n"
				+ "FILTER (!strstarts(str(?type),str(org:))) } \n"

				//links
				+ "OPTIONAL {?organization org:unitOf ?motherOrganizationUri ."
				+ "?motherOrganizationUri  dcterms:identifier ?motherOrganization .} \n"
				+ "OPTIONAL {?organization org:linkedTo ?linkedToUri ."
				+ "?linkedToUri  dcterms:identifier ?linkedTo .} \n"

				//seeAlso
				+ "OPTIONAL {?organization rdfs:seeAlso ?seeAlso .} \n"

				+ "} \n" ;
	}

	public static String organizationsQuery() {
		return "SELECT DISTINCT ?id ?label ?altLabel \n"
				+ "FROM <"+config.getOrganizationsGraph()+"> \n "
				+ "FROM <"+config.getOrgInseeGraph()+"> \n "

				+ "WHERE { \n"
				//id
				+ "?organization dcterms:identifier ?id . \n"

				//labels
				+ "OPTIONAL { ?organization skos:prefLabel ?label . \n"
				+ "FILTER (lang(?label) = '" + config.getLg1() + "')} \n"
				+ "OPTIONAL {?organization skos:altLabel ?altLabel .} \n"

				+ "} \n" 
				+ "GROUP BY ?id ?label ?altLabel \n"
				+ "ORDER BY ?label ";
	}

	public static String organizationsTwoLangsQuery() {
		return "SELECT DISTINCT ?id ?labelLg1  ?labelLg2  ?altLabel \n"
				+ "FROM <"+config.getOrganizationsGraph()+"> \n "
				+ "FROM <"+config.getOrgInseeGraph()+"> \n "

				+ "WHERE { \n"
				//id
				+ "?organization dcterms:identifier ?id . \n"

				//labels
				+ "OPTIONAL { ?organization skos:prefLabel ?labelLg1 . \n"
				+ "FILTER (lang(?labelLg1) = '" + config.getLg1() + "')} \n"
				+ "OPTIONAL { ?organization skos:prefLabel ?labelLg2 . \n"
				+ "FILTER (lang(?labelLg2) = '" + config.getLg2() + "')} \n"
				+ "OPTIONAL {?organization skos:altLabel ?altLabel .} \n"

				+ "} \n" 
				+ "GROUP BY ?id ?labelLg1 ?labelLg2 ?altLabel \n"
				+ "ORDER BY ?labelLg1 ";
	}

	public static String getUriById(String identifier) {
		return "SELECT  ?uri \n"
				+ "FROM <"+config.getOrganizationsGraph()+"> \n "
				+ "FROM <"+config.getOrgInseeGraph()+"> \n "

				+ "WHERE { \n"
				+ "?uri dcterms:identifier '"+ identifier +"' . \n"

				+ "} \n" ;
	}

	  private OrganizationQueries() {
		    throw new IllegalStateException("Utility class");
	}


}
