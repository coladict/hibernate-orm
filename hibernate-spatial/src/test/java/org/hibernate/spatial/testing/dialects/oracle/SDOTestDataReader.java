/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

package org.hibernate.spatial.testing.dialects.oracle;

import java.util.List;

import org.hibernate.spatial.testing.TestDataElement;
import org.hibernate.spatial.testing.TestDataReader;

import org.w3c.dom.Element;

public class SDOTestDataReader extends TestDataReader {


	@Override
	protected void addDataElement(Element element, List<TestDataElement> testDataElements) {
		int id = Integer.parseInt( getSingleChildElement( element, "id" ).getTextContent() );
		String type = getSingleChildElement( element, "type" ).getTextContent();
		String wkt = getSingleChildElement( element, "wkt" ).getTextContent();
		String sdo = getSingleChildElement( element, "sdo" ).getTextContent();
		TestDataElement testDataElement = new SDOTestDataElement( id, type, wkt, sdo );
		testDataElements.add( testDataElement );
	}

}
