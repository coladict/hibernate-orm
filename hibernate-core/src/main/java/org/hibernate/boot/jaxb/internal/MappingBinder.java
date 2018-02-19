/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.boot.jaxb.internal;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stax.StAXSource;

import org.hibernate.boot.MappingException;
import org.hibernate.boot.UnsupportedOrmXsdVersionException;
import org.hibernate.boot.jaxb.Origin;
import org.hibernate.boot.jaxb.hbm.spi.JaxbHbmHibernateMapping;
import org.hibernate.boot.jaxb.internal.stax.HbmEventReader;
import org.hibernate.boot.jaxb.internal.stax.JpaOrmXmlEventReader;
import org.hibernate.boot.jaxb.internal.stax.LocalSchema;
import org.hibernate.boot.jaxb.spi.Binding;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.internal.util.config.ConfigurationException;
import org.hibernate.internal.util.xml.XMLHelper;

import org.jboss.logging.Logger;

import org.w3c.dom.Document;

/**
 * @author Steve Ebersole
 */
public class MappingBinder extends AbstractBinder {
	private static final Logger log = Logger.getLogger( MappingBinder.class );

	private final XMLEventFactory xmlEventFactory = XMLEventFactory.newInstance();
	private final XMLHelper xmlHelper;
	private JAXBContext hbmJaxbContext;

	public MappingBinder(ClassLoaderService classLoaderService) {
		super( classLoaderService );
		xmlHelper = XMLHelper.get( classLoaderService );
	}

	public MappingBinder(ClassLoaderService classLoaderService, boolean validateXml) {
		super( classLoaderService, validateXml );
		xmlHelper = XMLHelper.get( classLoaderService );
	}

	@Override
	protected Binding doBind(
			XMLEventReader staxEventReader,
			StartElement rootElementStartEvent,
			Origin origin) {
		final String rootElementLocalName = rootElementStartEvent.getName().getLocalPart();
		if ( "hibernate-mapping".equals( rootElementLocalName ) ) {
			log.debugf( "Performing JAXB binding of hbm.xml document : %s", origin.toString() );

			XMLEventReader hbmReader = new HbmEventReader( staxEventReader, xmlEventFactory );
			JaxbHbmHibernateMapping hbmBindings = jaxb( hbmReader, LocalSchema.HBM.getSchema(), hbmJaxbContext(), origin );
			return new Binding<JaxbHbmHibernateMapping>( hbmBindings, origin );
		}
		else {
//			final XMLEventReader reader = new JpaOrmXmlEventReader( staxEventReader );
//			return jaxb( reader, LocalSchema.MAPPING.getSchema(), JaxbEntityMappings.class, origin );

			try {
				final XMLEventReader reader = new JpaOrmXmlEventReader( staxEventReader, xmlEventFactory );
				return new Binding<Document>( toDocument( reader ), origin );
			}
			catch (JpaOrmXmlEventReader.BadVersionException e) {
				throw new UnsupportedOrmXsdVersionException( e.getRequestedVersion(), origin );
			}
			catch (XMLStreamException | TransformerException e) {
				throw new MappingException(
						"An error occurred transforming orm.xml document from StAX to Document representation ",
						e,
						origin
				);
			}
		}
	}

	private JAXBContext hbmJaxbContext() {
		if ( hbmJaxbContext == null ) {
			try {
				hbmJaxbContext = JAXBContext.newInstance( JaxbHbmHibernateMapping.class );
			}
			catch ( JAXBException e ) {
				throw new ConfigurationException( "Unable to build hbm.xml JAXBContext", e );
			}
		}
		return hbmJaxbContext;
	}

	private Document toDocument(XMLEventReader jpaOrmXmlEventReader)
			throws XMLStreamException, TransformerException {
		jpaOrmXmlEventReader = XMLHelper.fillStart(jpaOrmXmlEventReader);
		StAXSource src = new StAXSource(jpaOrmXmlEventReader);
		Transformer transformer = xmlHelper.getTransformerFactory().newTransformer();
		DOMResult result = new DOMResult();
		transformer.transform(src, result);
		return (Document) result.getNode();
	}
}
