/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.envers.configuration.internal.metadata;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.internal.util.xml.XMLHelper;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * @author Adam Warski (adam at warski dot org)
 */
public class EntityXmlMappingData {
	private Document mainXmlMapping;
	private List<Document> additionalXmlMappings;
	private final XMLHelper xmlHelper;
	/**
	 * The xml element that maps the class. The root can be one of the folowing elements:
	 * class, subclass, union-subclass, joined-subclass
	 */
	private Element classMapping;

	public EntityXmlMappingData(XMLHelper xmlHelper) {
		this.xmlHelper = xmlHelper;
		mainXmlMapping = xmlHelper.newEmptyDocument();
		additionalXmlMappings = new ArrayList<>();
	}

	public Document getMainXmlMapping() {
		return mainXmlMapping;
	}

	public List<Document> getAdditionalXmlMappings() {
		return additionalXmlMappings;
	}

	public Document newAdditionalMapping() {
		Document additionalMapping = xmlHelper.newEmptyDocument();
		additionalXmlMappings.add( additionalMapping );

		return additionalMapping;
	}

	public Element getClassMapping() {
		return classMapping;
	}

	public void setClassMapping(Element classMapping) {
		this.classMapping = classMapping;
	}
}
