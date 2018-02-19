/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

package org.hibernate.spatial.testing;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class TestDataReader {

	private final DocumentBuilderFactory XMLFactory;
	public TestDataReader() {
		XMLFactory = DocumentBuilderFactory.newInstance();
	}

	public List<TestDataElement> read(String fileName) {
		if ( fileName == null ) {
			throw new RuntimeException( "Null testsuite-suite data file specified." );
		}
		DocumentBuilderFactory XMLFactory = DocumentBuilderFactory.newInstance();
		List<TestDataElement> testDataElements = new ArrayList<TestDataElement>();
		try {
			DocumentBuilder builder = XMLFactory.newDocumentBuilder();
			Document document = builder.parse(getInputStream( fileName ));
			addDataElements( document, testDataElements );
		}
		catch (Exception e) {
			throw new RuntimeException( e );
		}
		return testDataElements;
	}

	protected void addDataElements(Document document, List<TestDataElement> testDataElements) {
		Element root = document.getDocumentElement();
		NodeList it = root.getChildNodes();
		for (int i = 0; i < it.getLength(); i++) {
			Node node = it.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				Element element = (Element) node;
				addDataElement( element, testDataElements );
			}
		}
	}

	protected void addDataElement(Element element, List<TestDataElement> testDataElements) {
		int id = Integer.parseInt( getSingleChildElement( element, "id" ).getTextContent() );
		String type = getSingleChildElement( element, "type" ).getTextContent();
		String wkt = getSingleChildElement( element, "wkt" ).getTextContent();
		TestDataElement testDataElement = new TestDataElement( id, type, wkt );
		testDataElements.add( testDataElement );
	}

	protected InputStream getInputStream(String fileName) {
		InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream( fileName );
		if ( is == null ) {
			throw new RuntimeException( String.format( "File %s not found on classpath.", fileName ) );
		}
		return is;
	}

	protected Element getSingleChildElement(Element parent, String name) {
		NodeList nl = parent.getElementsByTagName(name);
		if (nl.getLength() > 0) {
			return (Element) nl.item(0);
		}
		else {
			return null;
		}
	}
}
