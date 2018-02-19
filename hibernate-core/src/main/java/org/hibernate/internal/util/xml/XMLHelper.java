/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.internal.util.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.Location;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartDocument;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Small helper class that lazy loads DOM and SAX reader and keep them for fast use afterwards.
 *
 * @deprecated Currently only used for integration with HCANN.  The rest of Hibernate uses StAX now
 * for XML processing.  See {@link org.hibernate.boot.jaxb.internal.stax}
 */
@Deprecated
public final class XMLHelper {
	private final DocumentBuilderFactory documentBuilderFactory;
	private final TransformerFactory transformerFactory;

	private static final WeakHashMap<ClassLoaderService, XMLHelper> cache = new WeakHashMap<>();

	public static XMLHelper get(ClassLoaderService classLoaderService) {
		XMLHelper h = cache.get( classLoaderService );
		if (h == null) {
			h = new XMLHelper( classLoaderService );
			cache.put( classLoaderService, h );
		}
		return h;
	}

	private XMLHelper(ClassLoaderService classLoaderService) {
		this.documentBuilderFactory = classLoaderService.workWithClassLoader((ClassLoader classLoader) -> {
			final ClassLoader originalTccl = Thread.currentThread().getContextClassLoader();
			try {
				Thread.currentThread().setContextClassLoader( classLoader );
				return DocumentBuilderFactory.newInstance();
			}
			finally {
				Thread.currentThread().setContextClassLoader( originalTccl );
			}
		});

		this.transformerFactory = classLoaderService.workWithClassLoader((ClassLoader classLoader) -> {
			final ClassLoader originalTccl = Thread.currentThread().getContextClassLoader();
			try {
				Thread.currentThread().setContextClassLoader( classLoader );
				// explicitly use JDK default factory
				return TransformerFactory.newInstance();
			}
			finally {
				Thread.currentThread().setContextClassLoader( originalTccl );
			}
		});
		// set not validating by default
		documentBuilderFactory.setValidating( false );
	}

	public DocumentBuilderFactory getDocumentBuilderFactory() {
		return documentBuilderFactory;
	}

	public TransformerFactory getTransformerFactory() {
		return transformerFactory;
	}

	public Document newEmptyDocument() {
		try {
			return documentBuilderFactory.newDocumentBuilder().newDocument();
		}
		catch (ParserConfigurationException pc) {
			throw new RuntimeException( pc );
		}
	}

	public Document readFromStream(InputStream is) {
		try {
			DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
			return builder.parse( is );
		}
		catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public void writeToStream(Document document, OutputStream out, int indent) {
		try {
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty( OutputKeys.OMIT_XML_DECLARATION, "no" );
			transformer.setOutputProperty( OutputKeys.METHOD, "xml" );
			transformer.setOutputProperty( OutputKeys.ENCODING, "utf-8" );
			transformer.setOutputProperty( OutputKeys.VERSION, "1.0" );
			if (indent > 0) {
				transformer.setOutputProperty( OutputKeys.INDENT, "yes" );
				transformer.setOutputProperty( "{http://xml.apache.org/xslt}indent-amount", Integer.toString( indent ) );
			}
			else {
				transformer.setOutputProperty(OutputKeys.INDENT, "no");
			}

			transformer.transform(new DOMSource(document),
					new StreamResult(new OutputStreamWriter(out, StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public static Node ensureOwner(Node original, Document doc, boolean doCloneIfNeeded) {
		if ( original.getOwnerDocument() == doc ) {
			return original;
		}
		if ( doCloneIfNeeded ) {
			original = original.cloneNode( true );
		}
		return doc.adoptNode( original );
	}

	public static List<Element> asElementList(NodeList nl) {
		ArrayList<Element> lst = new ArrayList<>();
		final int size = nl.getLength();
		for ( int i = 0; i < size; i++ ) {
			Node n = nl.item( i );
			if ( n.getNodeType() == Node.ELEMENT_NODE ) {
				lst.add( (Element) n );
			}
		}
		return Collections.unmodifiableList( lst );
	}

	/**
	 * Returns an iterator over the children of the given element with
	 * the given tag name.
	 *
	 * @param element The parent element
	 * @param tagName The name of the desired child
	 * @return An interator of children or null if element is null.
	 */
	public static List<Element> getChildrenByTagName(
			Element element,
			String tagName) {
		if ( element == null ) {
			return null;
		}

		if ( tagName == null ) {
			return asElementList( element.getChildNodes() );
		}

		return asElementList( element.getElementsByTagName( tagName ) );
	}

	public static List<Element> getChildren(Element element) {
		if ( element == null ) {
			return null;
		}

		return asElementList( element.getChildNodes() );
	}

	/**
	 * Gets the child of the specified element having the specified unique
	 * name.  If there are more than one children elements with the same name
	 * and exception is thrown.
	 *
	 * @param element The parent element
	 * @param tagName The name of the desired child
	 * @return The named child.
	 * @throws Exception Child was not found or was not unique.
	 */
	public static Element getUniqueChild(Element element, String tagName) {
		final Iterable<Element> goodChildrenHolder = getChildrenByTagName( element, tagName );
		final Iterator<Element> goodChildren = goodChildrenHolder == null ? null : goodChildrenHolder.iterator();

		if ( goodChildren != null && goodChildren.hasNext() ) {
			final Element child = (Element) goodChildren.next();
			if ( goodChildren.hasNext() ) {
				throw new RuntimeException( "expected only one " + tagName + " tag" );
			}
			return child;
		}
		throw new RuntimeException( "expected one " + tagName + " tag" );
	}

	public static String getElementPath(Element element) {
		LinkedList<Node> path = new LinkedList<>();
		path.push(element);
		Node parent = element;
		while ( ( parent = parent.getParentNode() ) != null ) {
			path.push(parent);
		}

		StringBuilder sb = new StringBuilder();

		for (Node node : path) {
			sb.append( '/' ).append( node.getNodeName() );
		}

		return sb.toString();
	}
	
	public static List<Element> getChildren(Element element, String tagName) {
		if ( element == null ) {
			return Collections.EMPTY_LIST;
		}

		NodeList nodeList = element.getElementsByTagName( tagName );
		final int size = nodeList.getLength();
		if (size == 0) {
			return Collections.EMPTY_LIST;
		}
		ArrayList<Element> result = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			result.add( (Element) nodeList.item( i ) );
		}

		return Collections.unmodifiableList( result );
	}

	public static Element addChild(Element parent, String tagName) {
		Document owner = parent.getOwnerDocument();
		Element child = owner.createElement( tagName );
		parent.appendChild( child );
		return child;
	}

	/**
	 * Gets the child of the specified element having the
	 * specified name. If the child with this name doesn't exist
	 * then null is returned instead.
	 *
	 * @param element the parent element
	 * @param tagName the name of the desired child
	 * @return either the named child or null
	 */
	public static Element getOptionalChild(Element element, String tagName) {
		return getOptionalChild( element, tagName, null );
	}

	/**
	 * Gets the child of the specified element having the
	 * specified name. If the child with this name doesn't exist
	 * then the supplied default element is returned instead.
	 *
	 * @param element		the parent element
	 * @param tagName		the name of the desired child
	 * @param defaultElement the element to return if the child
	 *                       doesn't exist
	 * @return either the named child or the supplied default
	 */
	public static Element getOptionalChild(
			Element element,
			String tagName,
			Element defaultElement) {
		final Iterable<Element> goodChildrenHolder = getChildrenByTagName( element, tagName );
		final Iterator<Element> goodChildren = goodChildrenHolder == null ? null : goodChildrenHolder.iterator();

		if ( goodChildren != null && goodChildren.hasNext() ) {
			return (Element) goodChildren.next();
		}
		else {
			return defaultElement;
		}
	}

	/**
	 * Get the content of the given element.
	 *
	 * @param element The element to get the content for.
	 * @return The content of the element or null.
	 */
	public static String getElementContent(final Element element) {
		return getElementContent( element, null );
	}

	public static Element cloneWithNewName(Element original, String newName) {
		Document owner = original.getOwnerDocument();
		Element copy = owner.createElement(newName);
		// copy attributes
		NamedNodeMap attrs = original.getAttributes();
		if (attrs != null) {
			final int size = attrs.getLength();
			for (int i = 0; i < size; i++) {
				Attr old = (Attr) attrs.item( i );
				copy.setAttribute( old.getName(), old.getValue() );
			}
		}
		// copy children
		NodeList children = original.getChildNodes();
		final int size = children.getLength();
		for (int i = 0; i < size; i++) {
			Node childClone = children.item( i ).cloneNode( true );
			copy.appendChild( childClone );
		}

		return copy;
	}

	/**
	 * Get the content of the given element.
	 *
	 * @param element	The element to get the content for.
	 * @param defaultStr The default to return when there is no content.
	 * @return The content of the element or the default.
	 */
	public static String getElementContent(Element element, String defaultStr) {
		if ( element == null ) {
			return defaultStr;
		}

		String content = element.getTextContent();
		if (content == null) {
			// element is self-closing
			return defaultStr;
		}
		return content.trim();
	}

	/**
	 * Macro to get the content of a unique child element.
	 *
	 * @param element The parent element.
	 * @param tagName The name of the desired child.
	 * @return The element content or null.
	 */
	public static String getUniqueChildContent(Element element, String tagName) throws Exception {
		return getElementContent( getUniqueChild( element, tagName ) );
	}

	/**
	 * Macro to get the content of an optional child element.
	 *
	 * @param element The parent element.
	 * @param tagName The name of the desired child.
	 * @return The element content or null.
	 */
	public static String getOptionalChildContent(Element element, String tagName) throws Exception {
		return getElementContent( getOptionalChild( element, tagName ) );
	}

	public static boolean getOptionalChildBooleanContent(Element element, String name) throws Exception {
		Element child = getOptionalChild( element, name );
		if ( child != null ) {
			String value = getElementContent( child ).toLowerCase(Locale.ROOT);
			return value.equals( "true" ) || value.equals( "yes" );
		}

		return false;
	}

	private static class NullLocation implements Location {

		@Override
		public int getLineNumber() {
			return -1;
		}

		@Override
		public int getColumnNumber() {
			return -1;
		}

		@Override
		public int getCharacterOffset() {
			return -1;
		}

		@Override
		public String getPublicId() {
			return null;
		}

		@Override
		public String getSystemId() {
			return null;
		}
	}

	private static class DefaultStartDocument implements StartDocument {

		private final Location location;
		DefaultStartDocument() {
			location = new NullLocation();
		}

		@Override
		public String getSystemId() {
			return "";
		}

		@Override
		public String getCharacterEncodingScheme() {
			return "UTF-8";
		}

		@Override
		public boolean encodingSet() {
			return true;
		}

		@Override
		public boolean isStandalone() {
			return true;
		}

		@Override
		public boolean standaloneSet() {
			return false;
		}

		@Override
		public String getVersion() {
			return "1.0";
		}

		@Override
		public int getEventType() {
			return XMLStreamConstants.START_DOCUMENT;
		}

		@Override
		public Location getLocation() {
			return location;
		}

		@Override
		public boolean isStartElement() {
			return false;
		}

		@Override
		public boolean isAttribute() {
			return false;
		}

		@Override
		public boolean isNamespace() {
			return false;
		}

		@Override
		public boolean isEndElement() {
			return false;
		}

		@Override
		public boolean isEntityReference() {
			return false;
		}

		@Override
		public boolean isProcessingInstruction() {
			return false;
		}

		@Override
		public boolean isCharacters() {
			return false;
		}

		@Override
		public boolean isStartDocument() {
			return true;
		}

		@Override
		public boolean isEndDocument() {
			return false;
		}

		@Override
		public StartElement asStartElement() {
			throw new ClassCastException("This element is a StartDocument");
		}

		@Override
		public EndElement asEndElement() {
			throw new ClassCastException("This element is a StartDocument");
		}

		@Override
		public Characters asCharacters() {
			throw new ClassCastException("This element is a StartDocument");
		}

		@Override
		public QName getSchemaType() {
			return null;
		}

		@Override
		public void writeAsEncodedUnicode(Writer writer) throws XMLStreamException {
			try {
				writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			}
			catch (IOException ex) {
				throw new XMLStreamException(ex);
			}
		}
		
	}
	private static class XMLEventReaderWrapper implements XMLEventReader {
		private boolean passedStart;
		private final StartDocument start;
		private final XMLEventReader original;
		XMLEventReaderWrapper(XMLEventReader original) {
			this.original = original;
			passedStart = false;
			start = new DefaultStartDocument();
		}

		@Override
		public XMLEvent nextEvent() throws XMLStreamException {
			if (passedStart) {
				return original.nextEvent();
			}
			passedStart = true;
			return start;
		}

		@Override
		public boolean hasNext() {
			return passedStart ? original.hasNext() : true;
		}

		@Override
		public XMLEvent peek() throws XMLStreamException {
			return passedStart ? original.peek() : start;
		}

		@Override
		public String getElementText() throws XMLStreamException {
			return passedStart
					? original.getElementText()
					: "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
		}

		@Override
		public XMLEvent nextTag() throws XMLStreamException {
			if (passedStart) {
				return original.nextTag();
			}
			passedStart = true;
			return start;
		}

		@Override
		public Object getProperty(String name) throws IllegalArgumentException {
			if (passedStart) {
				if ( "version".equals(name) ) {
					return "1.0";
				}
				if ( "encoding".equals(name) ) {
					return "UTF-8";
				}
				return null;
			}
			return original.getProperty(name);
		}

		@Override
		public void close() throws XMLStreamException {
			original.close();
		}

		@Override
		public Object next() {
			if (passedStart) {
				return original.next();
			}
			passedStart = true;
			return start;
		}
		
	}
	public static XMLEventReader fillStart(XMLEventReader original) throws XMLStreamException {
		if (original.peek().isStartDocument()) {
			// Has document start. No need to fake it.
			return original;
		}

		return new XMLEventReaderWrapper( original );
	}

}
