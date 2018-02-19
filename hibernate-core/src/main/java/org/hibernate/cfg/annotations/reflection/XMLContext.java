/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.cfg.annotations.reflection;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.persistence.AccessType;
import javax.persistence.AttributeConverter;

import org.hibernate.AnnotationException;
import org.hibernate.boot.AttributeConverterInfo;
import org.hibernate.boot.registry.classloading.spi.ClassLoadingException;
import org.hibernate.boot.spi.ClassLoaderAccess;
import org.hibernate.cfg.AttributeConverterDefinition;
import org.hibernate.internal.CoreLogging;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.internal.util.xml.XMLHelper;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * A helper for consuming orm.xml mappings.
 *
 * @author Emmanuel Bernard
 * @author Brett Meyer
 */
public class XMLContext implements Serializable {
	private static final CoreMessageLogger LOG = CoreLogging.messageLogger( XMLContext.class );

	private final ClassLoaderAccess classLoaderAccess;

	private Default globalDefaults;
	private Map<String, Element> classOverriding = new HashMap<String, Element>();
	private Map<String, Default> defaultsOverriding = new HashMap<String, Default>();
	private List<Element> defaultElements = new ArrayList<Element>();
	private List<String> defaultEntityListeners = new ArrayList<String>();
	private boolean hasContext = false;

	public XMLContext(ClassLoaderAccess classLoaderAccess) {
		this.classLoaderAccess = classLoaderAccess;
	}

	/**
	 * @param doc The xml document to add
	 * @return Add a xml document to this context and return the list of added class names.
	 */
	@SuppressWarnings( "unchecked" )
	public List<String> addDocument(Document doc) {
		hasContext = true;
		List<String> addedClasses = new ArrayList<String>();
		Element root = doc.getDocumentElement();
		//global defaults
		Element metadata = XMLHelper.getOptionalChild( root, "persistence-unit-metadata" );
		if ( metadata != null ) {
			if ( globalDefaults == null ) {
				globalDefaults = new Default();
				globalDefaults.setMetadataComplete(
						XMLHelper.getOptionalChild( metadata, "xml-mapping-metadata-complete" ) != null ?
								Boolean.TRUE :
								null
				);
				Element defaultElement = XMLHelper.getOptionalChild( metadata, "persistence-unit-defaults" );
				if ( defaultElement != null ) {
					Element unitElement = XMLHelper.getOptionalChild( defaultElement, "schema" );
					globalDefaults.setSchema( getTextTrim( unitElement ) );
					unitElement = XMLHelper.getOptionalChild( defaultElement, "catalog" );
					globalDefaults.setCatalog( getTextTrim( unitElement ) );
					unitElement = XMLHelper.getOptionalChild( defaultElement, "access" );
					setAccess( unitElement, globalDefaults );
					unitElement = XMLHelper.getOptionalChild( defaultElement, "cascade-persist" );
					globalDefaults.setCascadePersist( unitElement != null ? Boolean.TRUE : null );
					unitElement = XMLHelper.getOptionalChild( defaultElement, "delimited-identifiers" );
					globalDefaults.setDelimitedIdentifiers( unitElement != null ? Boolean.TRUE : null );
					defaultEntityListeners.addAll( addEntityListenerClasses( defaultElement, null, addedClasses ) );
				}
			}
			else {
				LOG.duplicateMetadata();
			}
		}

		//entity mapping default
		Default entityMappingDefault = new Default();
		Element unitElement = XMLHelper.getOptionalChild( root, "package" );
		String packageName = getTextTrim( unitElement );
		entityMappingDefault.setPackageName( packageName );
		unitElement = XMLHelper.getOptionalChild( root, "schema" );
		entityMappingDefault.setSchema( getTextTrim( unitElement ) );
		unitElement = XMLHelper.getOptionalChild( root, "catalog" );
		entityMappingDefault.setCatalog( getTextTrim( unitElement ) );
		unitElement = XMLHelper.getOptionalChild( root, "access" );
		setAccess( unitElement, entityMappingDefault );
		defaultElements.add( root );
		
		setLocalAttributeConverterDefinitions( XMLHelper.getChildren( root, "converter" ) );

		List<Element> entities = XMLHelper.getChildren( root, "entity" );
		addClass( entities, packageName, entityMappingDefault, addedClasses );

		entities = XMLHelper.getChildren( root, "mapped-superclass" );
		addClass( entities, packageName, entityMappingDefault, addedClasses );

		entities = XMLHelper.getChildren( root, "embeddable" );
		addClass( entities, packageName, entityMappingDefault, addedClasses );
		return addedClasses;
	}

	private static String getTextTrim(Element element) {
		if (element == null) {
			return null;
		}
		final String result = element.getTextContent();
		if (result == null) {
			return result;
		}
		return result.trim();
	}

	private void setAccess(Element unitElement, Default defaultType) {
		if ( unitElement != null ) {
			String access = getTextTrim( unitElement );
			setAccess( access, defaultType );
		}
	}

	private void setAccess( String access, Default defaultType) {
		AccessType type;
		if ( StringHelper.isNotEmpty( access ) ) {
			try {
				type = AccessType.valueOf( access );
			}
			catch ( IllegalArgumentException e ) {
				throw new AnnotationException( "Invalid access type " + access + " (check your xml configuration)" );
			}
			defaultType.setAccess( type );
		}
	}

	private void addClass(List<Element> entities, String packageName, Default defaults, List<String> addedClasses) {
		for (Element element : entities) {
			String className = buildSafeClassName( element.getAttribute( "class" ), packageName );
			if ( classOverriding.containsKey( className ) ) {
				//maybe switch it to warn?
				throw new IllegalStateException( "Duplicate XML entry for " + className );
			}
			addedClasses.add( className );
			classOverriding.put( className, element );
			Default localDefault = new Default();
			localDefault.override( defaults );
			String metadataCompleteString = element.getAttribute( "metadata-complete" );
			if ( StringHelper.isNotEmpty( metadataCompleteString ) ) {
				localDefault.setMetadataComplete( Boolean.parseBoolean( metadataCompleteString ) );
			}
			String access = element.getAttribute( "access" );
			setAccess( access, localDefault );
			defaultsOverriding.put( className, localDefault );

			LOG.debugf( "Adding XML overriding information for %s", className );
			addEntityListenerClasses( element, packageName, addedClasses );
		}
	}

	private List<String> addEntityListenerClasses(Element element, String packageName, List<String> addedClasses) {
		List<String> localAddedClasses = new ArrayList<String>();
		Element listeners = XMLHelper.getOptionalChild( element, "entity-listeners" );
		if ( listeners != null ) {
			@SuppressWarnings( "unchecked" )
			List<Element> elements = XMLHelper.getChildren( listeners, "entity-listener" );
			for (Element listener : elements) {
				String listenerClassName = buildSafeClassName( listener.getAttribute( "class" ), packageName );
				if ( classOverriding.containsKey( listenerClassName ) ) {
					//maybe switch it to warn?
					if ( "entity-listener".equals( classOverriding.get( listenerClassName ).getNodeName() ) ) {
						LOG.duplicateListener( listenerClassName );
						continue;
					}
					throw new IllegalStateException("Duplicate XML entry for " + listenerClassName);
				}
				localAddedClasses.add( listenerClassName );
				classOverriding.put( listenerClassName, listener );
			}
		}
		LOG.debugf( "Adding XML overriding information for listeners: %s", localAddedClasses );
		addedClasses.addAll( localAddedClasses );
		return localAddedClasses;
	}

	@SuppressWarnings("unchecked")
	private void setLocalAttributeConverterDefinitions(List<Element> converterElements) {
		for ( Element converterElement : converterElements ) {
			final String className = converterElement.getAttribute( "class" );
			final String autoApplyAttribute = converterElement.getAttribute( "auto-apply" );
			final boolean autoApply = StringHelper.isNotEmpty( autoApplyAttribute ) && Boolean.parseBoolean( autoApplyAttribute );

			try {
				final Class<? extends AttributeConverter> attributeConverterClass = classLoaderAccess.classForName(
						className
				);
				attributeConverterInfoList.add(
						new AttributeConverterDefinition( attributeConverterClass.newInstance(), autoApply )
				);
			}
			catch (ClassLoadingException e) {
				throw new AnnotationException( "Unable to locate specified AttributeConverter implementation class : " + className, e );
			}
			catch (Exception e) {
				throw new AnnotationException( "Unable to instantiate specified AttributeConverter implementation class : " + className, e );
			}
		}
	}

	public static String buildSafeClassName(String className, String defaultPackageName) {
		if ( className.indexOf( '.' ) < 0 && StringHelper.isNotEmpty( defaultPackageName ) ) {
			className = StringHelper.qualify( defaultPackageName, className );
		}
		return className;
	}

	public static String buildSafeClassName(String className, XMLContext.Default defaults) {
		return buildSafeClassName( className, defaults.getPackageName() );
	}

	public Default getDefault(String className) {
		Default xmlDefault = new Default();
		xmlDefault.override( globalDefaults );
		if ( className != null ) {
			Default entityMappingOverriding = defaultsOverriding.get( className );
			xmlDefault.override( entityMappingOverriding );
		}
		return xmlDefault;
	}

	public Element getXMLTree(String className ) {
		return classOverriding.get( className );
	}

	public List<Element> getAllDocuments() {
		return defaultElements;
	}

	public boolean hasContext() {
		return hasContext;
	}

	private List<AttributeConverterInfo> attributeConverterInfoList = new ArrayList<>();

	public void applyDiscoveredAttributeConverters(AttributeConverterDefinitionCollector collector) {
		for ( AttributeConverterInfo info : attributeConverterInfoList ) {
			collector.addAttributeConverter( info );
		}
		attributeConverterInfoList.clear();
	}

	public static class Default implements Serializable {
		private AccessType access;
		private String packageName;
		private String schema;
		private String catalog;
		private Boolean metadataComplete;
		private Boolean cascadePersist;
		private Boolean delimitedIdentifier;

		public AccessType getAccess() {
			return access;
		}

		protected void setAccess(AccessType access) {
			this.access = access;
		}

		public String getCatalog() {
			return catalog;
		}

		protected void setCatalog(String catalog) {
			this.catalog = catalog;
		}

		public String getPackageName() {
			return packageName;
		}

		protected void setPackageName(String packageName) {
			this.packageName = packageName;
		}

		public String getSchema() {
			return schema;
		}

		protected void setSchema(String schema) {
			this.schema = schema;
		}

		public Boolean getMetadataComplete() {
			return metadataComplete;
		}

		public boolean canUseJavaAnnotations() {
			return metadataComplete == null || !metadataComplete;
		}

		protected void setMetadataComplete(Boolean metadataComplete) {
			this.metadataComplete = metadataComplete;
		}

		public Boolean getCascadePersist() {
			return cascadePersist;
		}

		void setCascadePersist(Boolean cascadePersist) {
			this.cascadePersist = cascadePersist;
		}

		public void override(Default globalDefault) {
			if ( globalDefault != null ) {
				if ( globalDefault.getAccess() != null ) {
					access = globalDefault.getAccess();
				}
				if ( globalDefault.getPackageName() != null ) {
					packageName = globalDefault.getPackageName();
				}
				if ( globalDefault.getSchema() != null ) {
					schema = globalDefault.getSchema();
				}
				if ( globalDefault.getCatalog() != null ) {
					catalog = globalDefault.getCatalog();
				}
				if ( globalDefault.getDelimitedIdentifier() != null ) {
					delimitedIdentifier = globalDefault.getDelimitedIdentifier();
				}
				if ( globalDefault.getMetadataComplete() != null ) {
					metadataComplete = globalDefault.getMetadataComplete();
				}
				//TODO fix that in stone if cascade-persist is set already?
				if ( globalDefault.getCascadePersist() != null ) cascadePersist = globalDefault.getCascadePersist();
			}
		}

		public void setDelimitedIdentifiers(Boolean delimitedIdentifier) {
			this.delimitedIdentifier = delimitedIdentifier;
		}

		public Boolean getDelimitedIdentifier() {
			return delimitedIdentifier;
		}
	}

	public List<String> getDefaultEntityListeners() {
		return defaultEntityListeners;
	}
}
