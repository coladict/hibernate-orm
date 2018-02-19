/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.envers.configuration.internal.metadata;

import java.util.Iterator;
import javax.persistence.JoinColumn;

import org.hibernate.envers.internal.tools.StringTools;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Formula;
import org.hibernate.mapping.Selectable;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.hibernate.internal.util.xml.XMLHelper.addChild;
import static org.hibernate.internal.util.xml.XMLHelper.cloneWithNewName;
import static org.hibernate.internal.util.xml.XMLHelper.getChildren;

/**
 * @author Adam Warski (adam at warski dot org)
 * @author Lukasz Antoniak (lukasz dot antoniak at gmail dot com)
 * @author Michal Skowronek (mskowr at o2 dot pl)
 */
public final class MetadataTools {
	private MetadataTools() {
	}

	public static Element addNativelyGeneratedId(
			Element parent, String name, String type,
			boolean useRevisionEntityWithNativeId) {
		final Element idMapping = addChild( parent, "id" );
		idMapping.setAttribute( "name", name );
		idMapping.setAttribute( "type", type );

		final Element generatorMapping = addChild( idMapping, "generator" );
		if ( useRevisionEntityWithNativeId ) {
			generatorMapping.setAttribute( "class", "native" );
		}
		else {
			generatorMapping.setAttribute( "class", "org.hibernate.envers.enhanced.OrderedSequenceGenerator" );
			Element param;
			param = addChild( generatorMapping, "param" );
			param.setAttribute( "name", "sequence_name" );
			param.setTextContent( "REVISION_GENERATOR" );

			param = addChild( generatorMapping, "param" );
			param.setAttribute( "name", "table_name" );
			param.setTextContent( "REVISION_GENERATOR" );

			param = addChild( generatorMapping, "param" );
			param.setAttribute( "name", "initial_value" );
			param.setTextContent( "1" );

			param = addChild( generatorMapping, "param" );
			param.setAttribute( "name", "increment_size" );
			param.setTextContent( "1" );

		}

//		generatorMapping.setAttribute( "class", "sequence" );
//		param = addChild( generatorMapping, "param" );
//		param.setAttribute( "name", "sequence" );
//		param.setTextContent( "custom" );

		return idMapping;
	}

	public static Element addProperty(
			Element parent,
			String name,
			String type,
			boolean insertable,
			boolean updateable,
			boolean key) {
		final Element propMapping;
		if ( key ) {
			propMapping = addChild( parent, "key-property" );
		}
		else {
			propMapping = addChild( parent, "property" );
			propMapping.setAttribute( "insert", Boolean.toString( insertable ) );
			propMapping.setAttribute( "update", Boolean.toString( updateable ) );
		}

		propMapping.setAttribute( "name", name );

		if ( type != null ) {
			propMapping.setAttribute( "type", type );
		}

		return propMapping;
	}

	public static Element addProperty(Element parent, String name, String type, boolean insertable, boolean key) {
		return addProperty( parent, name, type, insertable, false, key );
	}

	public static Element addModifiedFlagProperty(Element parent, String propertyName, String suffix, String modifiedFlagName) {
		return addProperty(
				parent,
				(modifiedFlagName != null) ? modifiedFlagName : getModifiedFlagPropertyName( propertyName, suffix ),
				"boolean",
				true,
				false,
				false
		);
	}

	public static String getModifiedFlagPropertyName(String propertyName, String suffix) {
		return propertyName + suffix;
	}

	private static void addOrModifyAttribute(Element parent, String name, String value) {
		final Attr attribute = parent.getAttributeNode( name );
		if ( attribute == null ) {
			parent.setAttribute( name, value );
		}
		else {
			attribute.setValue( value );
		}
	}

	/**
	 * Column name shall be wrapped with '`' signs if quotation required.
	 */
	public static Element addOrModifyColumn(Element parent, String name) {
		final Element columnMapping = addChild( parent, "column" );

		if ( columnMapping == null ) {
			return addColumn( parent, name, null, null, null, null, null, null );
		}

		if ( !StringTools.isEmpty( name ) ) {
			addOrModifyAttribute( columnMapping, "name", name );
		}

		return columnMapping;
	}

	/**
	 * Adds new <code>column</code> element. Method assumes that the value of <code>name</code> attribute is already
	 * wrapped with '`' signs if quotation required. It shall be invoked when column name is taken directly from configuration
	 * file and not from {@link org.hibernate.mapping.PersistentClass} descriptor.
	 */
	public static Element addColumn(
			Element parent,
			String name,
			Integer length,
			Integer scale,
			Integer precision,
			String sqlType,
			String customRead,
			String customWrite) {
		return addColumn( parent, name, length, scale, precision, sqlType, customRead, customWrite, false );
	}

	public static Element addColumn(
			Element parent,
			String name,
			Integer length,
			Integer scale,
			Integer precision,
			String sqlType,
			String customRead,
			String customWrite,
			boolean quoted) {
		final Element columnMapping = addChild( parent, "column" );

		columnMapping.setAttribute( "name", quoted ? "`" + name + "`" : name );
		if ( length != null ) {
			columnMapping.setAttribute( "length", length.toString() );
		}
		if ( scale != null ) {
			columnMapping.setAttribute( "scale", Integer.toString( scale ) );
		}
		if ( precision != null ) {
			columnMapping.setAttribute( "precision", Integer.toString( precision ) );
		}
		if ( !StringTools.isEmpty( sqlType ) ) {
			columnMapping.setAttribute( "sql-type", sqlType );
		}

		if ( !StringTools.isEmpty( customRead ) ) {
			columnMapping.setAttribute( "read", customRead );
		}
		if ( !StringTools.isEmpty( customWrite ) ) {
			columnMapping.setAttribute( "write", customWrite );
		}

		return columnMapping;
	}

	private static Element createEntityCommon(
			Document document,
			String type,
			AuditTableData auditTableData,
			String discriminatorValue,
			Boolean isAbstract) {
		final Element hibernateMapping = document.createElement( "hibernate-mapping" );
		document.appendChild( hibernateMapping );
		hibernateMapping.setAttribute( "auto-import", "false" );

		final Element classMapping = addChild( hibernateMapping, type );

		if ( auditTableData.getAuditEntityName() != null ) {
			classMapping.setAttribute( "entity-name", auditTableData.getAuditEntityName() );
		}

		if ( discriminatorValue != null ) {
			classMapping.setAttribute( "discriminator-value", discriminatorValue );
		}

		if ( !StringTools.isEmpty( auditTableData.getAuditTableName() ) ) {
			classMapping.setAttribute( "table", auditTableData.getAuditTableName() );
		}

		if ( !StringTools.isEmpty( auditTableData.getSchema() ) ) {
			classMapping.setAttribute( "schema", auditTableData.getSchema() );
		}

		if ( !StringTools.isEmpty( auditTableData.getCatalog() ) ) {
			classMapping.setAttribute( "catalog", auditTableData.getCatalog() );
		}

		if ( isAbstract != null ) {
			classMapping.setAttribute( "abstract", isAbstract.toString() );
		}

		return classMapping;
	}

	public static Element createEntity(
			Document document,
			AuditTableData auditTableData,
			String discriminatorValue,
			Boolean isAbstract) {
		return createEntityCommon( document, "class", auditTableData, discriminatorValue, isAbstract );
	}

	public static Element createSubclassEntity(
			Document document,
			String subclassType,
			AuditTableData auditTableData,
			String extendsEntityName,
			String discriminatorValue,
			Boolean isAbstract) {
		final Element classMapping = createEntityCommon(
				document,
				subclassType,
				auditTableData,
				discriminatorValue,
				isAbstract
		);

		classMapping.setAttribute( "extends", extendsEntityName );

		return classMapping;
	}

	public static Element createJoin(
			Element parent,
			String tableName,
			String schema,
			String catalog) {
		final Element joinMapping = addChild( parent, "join" );

		joinMapping.setAttribute( "table", tableName );

		if ( !StringTools.isEmpty( schema ) ) {
			joinMapping.setAttribute( "schema", schema );
		}

		if ( !StringTools.isEmpty( catalog ) ) {
			joinMapping.setAttribute( "catalog", catalog );
		}

		return joinMapping;
	}

	public static void addColumns(Element anyMapping, Iterator selectables) {
		while ( selectables.hasNext() ) {
			final Selectable selectable = (Selectable) selectables.next();
			if ( selectable.isFormula() ) {
				throw new FormulaNotSupportedException();
			}
			addColumn( anyMapping, (Column) selectable );
		}
	}

	/**
	 * Adds <code>column</code> element with the following attributes (unless empty): <code>name</code>,
	 * <code>length</code>, <code>scale</code>, <code>precision</code>, <code>sql-type</code>, <code>read</code>
	 * and <code>write</code>.
	 *
	 * @param anyMapping Parent element.
	 * @param column Column descriptor.
	 */
	public static void addColumn(Element anyMapping, Column column) {
		addColumn(
				anyMapping,
				column.getName(),
				column.getLength(),
				column.getScale(),
				column.getPrecision(),
				column.getSqlType(),
				column.getCustomRead(),
				column.getCustomWrite(),
				column.isQuoted()
		);
	}

	@SuppressWarnings({"unchecked"})
	private static void changeNamesInColumnElement(Element element, ColumnNameIterator columnNameIterator) {
		for ( final Element property : getChildren( element ) ) {

			if ( "column".equals( property.getNodeName() ) ) {
				final Attr nameAttr = property.getAttributeNode( "name" );
				if ( nameAttr != null ) {
					nameAttr.setValue( columnNameIterator.next() );
				}
			}
		}
	}

	@SuppressWarnings({"unchecked"})
	public static void prefixNamesInPropertyElement(
			Element element,
			String prefix,
			ColumnNameIterator columnNameIterator,
			boolean changeToKey,
			boolean insertable) {
		for ( final Element property : getChildren( element ) ) {

			if ( "property".equals( property.getNodeName() ) || "many-to-one".equals( property.getNodeName() ) ) {
				final Attr nameAttr = property.getAttributeNode( "name" );
				if ( nameAttr != null ) {
					nameAttr.setValue( prefix + nameAttr.getTextContent() );
				}

				changeNamesInColumnElement( property, columnNameIterator );

				if ( changeToKey ) {
					Element copy = cloneWithNewName( property, "key-" + property.getNodeName() );

					// HHH-11463 when cloning a many-to-one to be a key-many-to-one, the FK attribute
					// should be explicitly set to 'none' or added to be 'none' to avoid issues with
					// making references to the main schema.
					if ( copy.getNodeName().equals( "key-many-to-one" ) ) {
						copy.setAttribute( "foreign-key", "none" );
					}

					element.replaceChild( copy, property );
				}

				if ( "property".equals( property.getNodeName() ) ) {
					final Attr insert = property.getAttributeNode("insert" );
					insert.setValue( Boolean.toString( insertable ) );
				}
			}
		}
	}

	/**
	 * Adds <code>formula</code> element.
	 *
	 * @param element Parent element.
	 * @param formula Formula descriptor.
	 */
	public static void addFormula(Element element, Formula formula) {
		addChild( element, "formula" ).setTextContent( formula.getText() );
	}

	/**
	 * Adds all <code>column</code> or <code>formula</code> elements.
	 *
	 * @param element Parent element.
	 * @param columnIterator Iterator pointing at {@link org.hibernate.mapping.Column} and/or
	 * {@link org.hibernate.mapping.Formula} objects.
	 */
	public static void addColumnsOrFormulas(Element element, Iterator columnIterator) {
		while ( columnIterator.hasNext() ) {
			final Object o = columnIterator.next();
			if ( o instanceof Column ) {
				addColumn( element, (Column) o );
			}
			else if ( o instanceof Formula ) {
				addFormula( element, (Formula) o );
			}
		}
	}

	/**
	 * An iterator over column names.
	 */
	public abstract static class ColumnNameIterator implements Iterator<String> {
	}

	public static ColumnNameIterator getColumnNameIterator(final Iterator<Selectable> selectableIterator) {
		return new ColumnNameIterator() {
			public boolean hasNext() {
				return selectableIterator.hasNext();
			}

			public String next() {
				final Selectable next = selectableIterator.next();
				if ( next.isFormula() ) {
					throw new FormulaNotSupportedException();
				}
				return ( (Column) next ).getName();
			}

			public void remove() {
				selectableIterator.remove();
			}
		};
	}

	public static ColumnNameIterator getColumnNameIterator(final JoinColumn[] joinColumns) {
		return new ColumnNameIterator() {
			int counter;

			public boolean hasNext() {
				return counter < joinColumns.length;
			}

			public String next() {
				return joinColumns[counter++].name();
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
}
