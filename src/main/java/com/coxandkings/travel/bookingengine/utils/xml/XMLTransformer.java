package com.coxandkings.travel.bookingengine.utils.xml;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;


public class XMLTransformer {
	private static TransformerFactory mXformerFactory;
	private static DocumentBuilderFactory mDocBuilderFactory;
	private static DocumentBuilder mDocBuilder;
	//private static DOMSource mDOMSource = new DOMSource();
	private static final Logger logger = LogManager.getLogger(XMLTransformer.class);
	
	static {
		try {
			mXformerFactory = TransformerFactory.newInstance();
		}
		catch (Exception x) {
			logger.error("An exception occurred while creating TransformerFactory instance", x);
		}
		
		try {
			mDocBuilderFactory = DocumentBuilderFactory.newInstance();
			mDocBuilderFactory.setNamespaceAware(true);
		}
		catch (Exception x) {
			logger.error("An exception occurred while creating DocumentBuilderFactory instance", x);
		}
		
		try {
			mDocBuilder = mDocBuilderFactory.newDocumentBuilder();
		}
		catch (Exception x) {
			logger.error("An exception occurred while creating DocumentBuilder instance", x);
		}

	}

	public static String toString(Element xmlElem) {
		if(xmlElem==null){
			return "";
		}
		StringWriter writer = new StringWriter();
		StreamResult result = new StreamResult(writer);
		DOMSource domSrc = new DOMSource(xmlElem);
		try {
			Transformer transformer = mXformerFactory.newTransformer();
			transformer.transform(domSrc, result);
			return writer.toString();
		}	
		catch (Exception x) {
			logger.error("An exception occurred while converting XML element to String", x);
			return "";
		}
	}
	
	public static Element toXMLElement(String xmlStr) {
		if(xmlStr == null || xmlStr.isEmpty()){
			return null;
		}
		try {
			DocumentBuilder docBldr = mDocBuilderFactory.newDocumentBuilder();
			Document docDOM = docBldr.parse(new InputSource(new StringReader(xmlStr)));
			return docDOM.getDocumentElement();
		}
		catch (Exception x) {
			logger.error("An exception occurred while parsing String to XML element", x);
			return null;
		}
	}
	
	public static String toEscapedString(Element xmlElem){
		return toString(xmlElem).replaceAll("\"", "\\\\\"").replaceAll("\n", "\\\\n").replaceAll("\r", "\\\\r");
	}
	
	public static Element fromEscapedString(String string){
		return (string == null) ? null : toXMLElement(string.replaceAll("\\\\\"","\"").replaceAll( "\\\\n","\n").replaceAll("\\\\r","\r"));
	}
	
	public static Document getWrappedDocument(Element xmlElem) {
		Document wrapDoc = mDocBuilder.newDocument();
		wrapDoc.appendChild(wrapDoc.importNode(xmlElem, true));
		return wrapDoc;
	}
	
	public static DocumentBuilder getNewDocumentBuilder() throws ParserConfigurationException {
		return mDocBuilderFactory.newDocumentBuilder();
	}
}
