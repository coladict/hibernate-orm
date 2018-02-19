/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.test.annotations.reflection;

import java.io.BufferedInputStream;
import java.io.InputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Assert;
import org.junit.Test;

import org.hibernate.cfg.EJB3DTDEntityResolver;
import org.hibernate.cfg.annotations.reflection.XMLContext;
import org.hibernate.internal.util.xml.ErrorLogger;
import org.hibernate.internal.util.xml.XMLHelper;

import org.hibernate.testing.boot.ClassLoaderAccessTestingImpl;
import org.hibernate.testing.boot.ClassLoaderServiceTestingImpl;

/**
 * @author Emmanuel Bernard
 */
public class XMLContextTest {
	@Test
	public void testAll() throws Exception {
		final XMLHelper xmlHelper = XMLHelper.get( ClassLoaderServiceTestingImpl.INSTANCE );
		final XMLContext context = new XMLContext( ClassLoaderAccessTestingImpl.INSTANCE );

		final ErrorLogger errorLogger = new ErrorLogger();
		org.w3c.dom.Document doc;
		try (InputStream is = ClassLoaderServiceTestingImpl.INSTANCE.locateResourceStream(
				"org/hibernate/test/annotations/reflection/orm.xml"
		)) {
			Assert.assertNotNull( "ORM.xml not found", is );

			DocumentBuilderFactory dbf = xmlHelper.getDocumentBuilderFactory();

			DocumentBuilder docbuilder = dbf.newDocumentBuilder();
			docbuilder.setErrorHandler( errorLogger );
			docbuilder.setEntityResolver( EJB3DTDEntityResolver.INSTANCE );

			doc = docbuilder.parse( new BufferedInputStream( is ) );
		}

		if ( errorLogger.hasErrors() ) {
			errorLogger.logErrors();
			Assert.fail();
		}
		Assert.assertNotNull( "ORM.xml could not be parsed", doc );
		context.addDocument( doc );
	}
}
