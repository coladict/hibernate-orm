/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.envers.boot.internal;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.hibernate.HibernateException;
import org.hibernate.boot.jaxb.Origin;
import org.hibernate.boot.jaxb.SourceType;
import org.hibernate.boot.jaxb.hbm.spi.JaxbHbmHibernateMapping;
import org.hibernate.boot.jaxb.internal.MappingBinder;
import org.hibernate.boot.jaxb.spi.Binding;
import org.hibernate.boot.model.source.internal.hbm.MappingDocument;
import org.hibernate.boot.spi.AdditionalJaxbMappingProducer;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.envers.configuration.internal.MappingCollector;
import org.hibernate.internal.util.xml.XMLHelper;
import org.hibernate.service.ServiceRegistry;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import org.w3c.dom.Document;

/**
 * @author Steve Ebersole
 */
public class AdditionalJaxbMappingProducerImpl implements AdditionalJaxbMappingProducer {
	private static final Logger log = Logger.getLogger( AdditionalJaxbMappingProducerImpl.class );

	@Override
	public Collection<MappingDocument> produceAdditionalMappings(
			final MetadataImplementor metadata,
			IndexView jandexIndex,
			final MappingBinder mappingBinder,
			final MetadataBuildingContext buildingContext) {
		final ServiceRegistry serviceRegistry = metadata.getMetadataBuildingOptions().getServiceRegistry();
		final EnversService enversService = serviceRegistry.getService( EnversService.class );

		if ( !enversService.isEnabled() ) {
			// short-circuit if envers integration has been disabled.
			return Collections.emptyList();
		}

		final ArrayList<MappingDocument> additionalMappingDocuments = new ArrayList<>();

		// atm we do not have distinct origin info for envers
		final Origin origin = new Origin( SourceType.OTHER, "envers" );
//		final DOMWriter writer = new DOMWriter();

		final MappingCollector mappingCollector = new MappingCollector() {
			@Override
			public void addDocument(Document document) {
				dump( enversService.getXmlHelper() ,  document );

				// while the commented-out code here is more efficient (well, understanding that
				// this whole process is un-efficient)  it leads to un-decipherable messages when
				// we get mapping mapping errors from envers output.
//				final DOMSource domSource = new DOMSource( writer.write( document ) );
//				domSource.setSystemId( "envers" );
//				final Binding jaxbBinding = mappingBinder.bind( domSource, origin );

				// this form at least allows us to get better error messages
				final ByteArrayOutputStream baos = new ByteArrayOutputStream();
				try {
					enversService.getXmlHelper().writeToStream(document, baos, 1);
				}
				catch (Exception e) {
					throw new HibernateException( "Unable to bind Envers-generated XML", e );
				}

				ByteArrayInputStream bais = new ByteArrayInputStream( baos.toByteArray() );
				BufferedInputStream bis = new BufferedInputStream( bais );
				final Binding jaxbBinding = mappingBinder.bind( bis, origin );

				final JaxbHbmHibernateMapping jaxbRoot = (JaxbHbmHibernateMapping) jaxbBinding.getRoot();
				additionalMappingDocuments.add( new MappingDocument( jaxbRoot, origin, buildingContext ) );
			}
		};

		enversService.initialize( metadata, mappingCollector );

		return additionalMappingDocuments;
	}

	private static void dump(XMLHelper xmlHelper, Document document) {
		if ( !log.isTraceEnabled() ) {
			return;
		}

		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final Writer w = new BufferedWriter( new OutputStreamWriter( baos, StandardCharsets.UTF_8 ) );

		try {
			Transformer transformer = xmlHelper.getTransformerFactory().newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
			transformer.setOutputProperty(OutputKeys.METHOD, "xml");
			transformer.setOutputProperty(OutputKeys.ENCODING, "utf-8");
			transformer.setOutputProperty(OutputKeys.VERSION, "1.0");
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			// assuming using default XML provider available since 1.6
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "1");

			transformer.transform(new DOMSource(document),
				new StreamResult(w));
			w.flush();
		}
		catch (Exception e1) {
			e1.printStackTrace();
		}

		log.tracef( "Envers-generate entity mapping -----------------------------\n%s", baos.toString() );
		log.trace( "------------------------------------------------------------" );
	}
}
