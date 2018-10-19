package com.coxandkings.travel.bookingengine.utils.xml;

import com.coxandkings.travel.bookingengine.utils.xml.xpath.IndexExpression;
import com.coxandkings.travel.bookingengine.utils.xml.xpath.XPathFunction;
import com.coxandkings.travel.bookingengine.utils.xml.xpath.XPathFunctionFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//import javax.xml.xpath.XPathFunction;

public class XMLUtils {
	//public static final String AIR_NS = "http://www.coxandkings.com/integ/suppl/air";
	//public static final String COM_NS = "http://www.coxandkings.com/integ/suppl/common";
	//public static final String OTA_NS = "http://www.opentravel.org/OTA/2003/05";
	
	public static final String SESSION_ELEM = "SessionID";
	public static final String TRANSACTION_ELEM = "TransactionID";
	public static final String USER_ELEM = "UserID";
	public static final String CREDENTIALS_ELEM = "SupplierCredentials";
	public static final String SUPPLIER_ELEM = "SupplierID";
	public static final String REQHDR_ELEM = "RequestHeader";
	
	public static String getElementValue(Element parent, String nsUri, String name) {
		NodeList children = parent.getElementsByTagNameNS(nsUri, name);
		if (children.getLength() == 0) {
			return "";
		}
	
		Node firstChild = ((Element) children.item(0)).getFirstChild();
		return (firstChild != null && firstChild.getNodeType() == Node.TEXT_NODE) ? firstChild.getNodeValue() : "";
	}
	
	public static String getElementValue(Element elem) {
		Node firstChild = elem.getFirstChild();
		return (firstChild != null && firstChild.getNodeType() == Node.TEXT_NODE) ? firstChild.getNodeValue() : "";
	}
	
	
	public static Element getSubElement(Element parent, String nsUri, String name) {
		NodeList children = parent.getElementsByTagNameNS(nsUri, name);
		if (children.getLength() == 0) {
			return null;
		}
		
		return ((Element) children.item(0));
	}
	
	public static Element getFirstElementChild(Element parent) {
		if (parent == null) {
			return null;
		}
		
		NodeList children = parent.getChildNodes();
		for (int i=0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				return (Element) child;
			}
		}
		
		return null;
	}
	
	
	public static Element getChildElement(Element parent, QName childElemQName, String indexExpr) {
		IndexExpression idxExpr = IndexExpression.compile(indexExpr);
		NodeList children = parent.getChildNodes();
		int childpos=0;
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			
			Element currChildElem = (Element) child;
			QName currChildElemQN = new QName(currChildElem.getNamespaceURI(), currChildElem.getLocalName());
			if (currChildElemQN.toString().equals(childElemQName.toString()) == false) {
				continue;
			}
			childpos++;//assign index of child element
			if (idxExpr != null) {
				if (idxExpr.evaluate(currChildElem,childpos) == false) {
					continue;
				}
			}
			
			return currChildElem;
		}
		
		return null;
	}
	
	public static Element[] getChildElements(Element parent, QName childElemQName, String indexExpr) {
		ArrayList<Element> childElems = new ArrayList<Element>();
		IndexExpression idxExpr = IndexExpression.compile(indexExpr);
		NodeList children = parent.getChildNodes();
		int childpos=0;
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			
			Element currChildElem = (Element) child;
			QName currChildElemQN = new QName(currChildElem.getNamespaceURI(), currChildElem.getLocalName());
			if (currChildElemQN.toString().equals(childElemQName.toString()) == false) {
				continue;
			}
			childpos++;
			if (idxExpr != null) {
				if (idxExpr.evaluate(currChildElem,childpos) == false) {
					continue;
				}
			}
			
			childElems.add(currChildElem);
		}
		
		return childElems.toArray(new Element[childElems.size()]);
	}

	
	public static String getNodeValue(Node n) {
		if (n == null) {
			return "";
		}
		switch (n.getNodeType()) {
			case Node.ATTRIBUTE_NODE : return ((Attr) n).getValue();
			case Node.ELEMENT_NODE   : return getElementValue((Element) n);
			default                  : return "";
		}
	}
	
	public static String convertToNS(String prefixedElementName) {
		String[] comps = prefixedElementName.split(":");
		return (comps.length > 1) ? String.format("{%s}%s", NamespacesContext.getNsURI(comps[0]), comps[1]) : comps[0]; 
	}
	
	public static String[] tokenizeXPath(String xpath) {
		ArrayList<String> tokens = new ArrayList<String>();
		StringBuilder strBldr = new StringBuilder();
		int endIdx = 0;
		
		// If the xpath starts with a function, trim out function string and paranthesis
		if (xpath.charAt(0) != '/' && xpath.charAt(0) != '.') {
			int sbIdx = -1;
			if ((sbIdx = xpath.indexOf('(')) > -1) {
				xpath = xpath.substring(sbIdx + 1, xpath.lastIndexOf(')')).split(",")[0];
			}
		}		
		
		for (int i=0; i < xpath.length(); i++) {
			switch (xpath.charAt(i)) {
				case '{' : endIdx = xpath.indexOf('}', i);
					       strBldr.append(xpath.substring(i, endIdx + 1));
				           i = endIdx;
				           break;
				           
				case '[' : endIdx = xpath.indexOf(']', i);
					       strBldr.append(xpath.substring(i, endIdx + 1));
				           i = endIdx;
				           break;
					
				case '/' : if (strBldr.length() > 0) {
				               tokens.add(strBldr.toString());
				               strBldr.setLength(0);
				           }
				           break;
				           
				default  : strBldr.append(xpath.charAt(i));
				           break;
			}
		}
		tokens.add(strBldr.toString());
		
		return tokens.toArray(new String[tokens.size()]);
	}

	private static Node[] getNodesAtXPath(Node xmlElem, String[] xpathComps, int xpathCompsIdx) {
		if (xmlElem == null) {
			return new Node[0];
		}
		
		if (xpathCompsIdx >= xpathComps.length) {
			return new Node[] {xmlElem};
		}

		String xpathComp = xpathComps[xpathCompsIdx];
		if (xpathComp.equals("..")) {
			return getNodesAtXPath(xmlElem.getParentNode(), xpathComps, ++xpathCompsIdx);
		}
		else if (xpathComp.equals(".")) {
			return getNodesAtXPath(xmlElem, xpathComps, ++xpathCompsIdx);
		}
		else if (xpathComp.startsWith("@")) {
			if ( xpathCompsIdx < (xpathComps.length - 1)) {
				throw new IllegalArgumentException(String.format("Attribute %s must be the last component of XPath", xpathComp));
			}
			QName xpathAttrQN = QName.valueOf(convertToNS(xpathComp.substring(1)));
			Attr xpathAttr = ((Element) xmlElem).getAttributeNodeNS((null != xpathAttrQN.getNamespaceURI() && xpathAttrQN.getNamespaceURI().isEmpty()) ? 
					null : xpathAttrQN.getNamespaceURI(), xpathAttrQN.getLocalPart());
			return getNodesAtXPath(xpathAttr, xpathComps, ++xpathCompsIdx);
		}
		else {
			int bracketIdx = -1;
			String idxExpr = null;
			// Check if the current XPath component element is indexed
			if ((bracketIdx = xpathComp.indexOf('[')) > -1) {
				idxExpr = xpathComp.substring(bracketIdx + 1, xpathComp.indexOf(']', bracketIdx));
				xpathComp = xpathComp.substring(0, bracketIdx);
			}

			Element[] xpathElems = getChildElements(((Element)xmlElem), QName.valueOf(convertToNS(xpathComp)), idxExpr);
			ArrayList<Node> xpathNodes = new ArrayList<Node>();
			++xpathCompsIdx;
			for (Element xpathElem : xpathElems) {
				Node[] nodes = getNodesAtXPath(xpathElem, xpathComps, xpathCompsIdx);
				xpathNodes.addAll(Arrays.asList(nodes));
			}
			
			return xpathNodes.toArray(new Node[xpathNodes.size()]);

		}
	}

	public static String getValueAtXPath(Element xmlElem, String elemXPath) {
		String funcName = "";
		String[] funcArgs = new String[0];
		if (elemXPath.charAt(0) != '/' && elemXPath.charAt(0) != '.') {		
			int sbIdx = elemXPath.indexOf('(');
			if (sbIdx > -1) {
				funcArgs = elemXPath.substring(sbIdx + 1, elemXPath.lastIndexOf(')')).split(",");
				funcName = elemXPath.substring(0, sbIdx);
				elemXPath = funcArgs[0]; 
			}
		}
		
		Node[] xpathNodes = getNodesAtXPath(xmlElem, elemXPath);
		XPathFunction xpathFunc = XPathFunctionFactory.getFunction(funcName);
		return xpathFunc.evaluate(xpathNodes, funcArgs);
	}

	public static String[] getAllValuesAtXPath(Element xmlElem, String elemXPath) {
		List<String> valueList = new ArrayList<String>();
		String funcName = "";
		String[] funcArgs = new String[0];
		if (elemXPath.charAt(0) != '/' && elemXPath.charAt(0) != '.') {
			int sbIdx = elemXPath.indexOf('(');
			if (sbIdx > -1) {
				funcArgs = elemXPath.substring(sbIdx + 1, elemXPath.lastIndexOf(')')).split(",");
				funcName = elemXPath.substring(0, sbIdx);
				elemXPath = funcArgs[0]; 
			}
		}
		
		Node[] xpathNodes = getNodesAtXPath(xmlElem, elemXPath);
		XPathFunction xpathFunc = XPathFunctionFactory.getFunction(funcName);
		for (Node xpathNode : xpathNodes) {
			valueList.add(xpathFunc.evaluate(new Node[] {xpathNode}, funcArgs));
		}
		
		return valueList.toArray(new String[valueList.size()]);
	}
	
	public static Element[] getElementsAtXPath(Element xmlElem, String elemXPath) {
		ArrayList<Element> xpathElements = new ArrayList<Element>();
		Node[] nodes = getNodesAtXPath(xmlElem, elemXPath);
		for (Node node : nodes) {
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				xpathElements.add((Element) node);
			}
		}
		
		return xpathElements.toArray(new Element[xpathElements.size()]);
	}
	
	public static Element getFirstElementAtXPath(Element xmlElem, String elemXPath) {
		Element[] elements = getElementsAtXPath(xmlElem, elemXPath);
		return elements.length==0?null:elements[0];
	}
	
	public static Node getFirstNodeAtXPath(Element xmlElem, String elemXPath) {
		Node[] nodes = getNodesAtXPath(xmlElem, elemXPath);
		
		return nodes.length==0?null:nodes[0];
	}
	
	public static Node[] getNodesAtXPath(Element xmlElem, String elemXPath) {
		Node[] emptyNodeArr = new Node[0];
		if (xmlElem == null || elemXPath == null || elemXPath.trim().isEmpty()) {
			return emptyNodeArr;
		}
		
		int xpathCompsIdx = 0;
		String[] elemXPathComps = tokenizeXPath(elemXPath);
	
		try {
			if (elemXPath.startsWith("/")) {
				QName xPathComp = QName.valueOf(convertToNS(elemXPathComps[xpathCompsIdx]));
				QName xmlElemQN = new QName(xmlElem.getNamespaceURI(), xmlElem.getLocalName());
				if (xPathComp.toString().equals(xmlElemQN.toString()) == false) {
					return emptyNodeArr;
				}
				xpathCompsIdx++;
			}
			
			return getNodesAtXPath(xmlElem, elemXPathComps, xpathCompsIdx);
		}
		catch (Exception x) {
			x.printStackTrace();
			return emptyNodeArr;
		}
	}
	
	public static Node replaceNode(Element modifyingElem,Node oldNode,Node replaceNode){
		if(oldNode==null || replaceNode==null || modifyingElem==null){
			//System.out.println("Cannot replace as one of the parameters evaluated to be null");
			return null;
		}
		if(oldNode.getNodeType()!=replaceNode.getNodeType()){
			System.out.println("Cannot replace as node types dont match");
			return null;
		}
		replaceNode=modifyingElem.getOwnerDocument().importNode(replaceNode, true);
		Node parentNode= replaceNode.getNodeType() == Node.ATTRIBUTE_NODE?((Attr)oldNode).getOwnerElement():oldNode.getParentNode();
		parentNode.replaceChild(replaceNode, oldNode);
		return replaceNode;
	}
    
	public static void replaceNodes(Element modifyingElem,Node[] oldNodes,Node[] replaceNodes){
		if(oldNodes==null || replaceNodes==null || modifyingElem==null){
			//System.out.println("Cannot replace as one of the parameters evaluated to be null");
			return;
		}
		if(oldNodes.length!=replaceNodes.length){
			 System.out.println("Cannot replace as Old and New nodes size mismatch");
			 return;
		}
		for(int i=0;i<oldNodes.length;i++){
			replaceNode(modifyingElem,oldNodes[i],replaceNodes[i]);
		}
		
	}
	
	public static void removeNode(Node removeNode){
		if(removeNode==null){
			//System.out.println("Cannot remove as node evaluated to be null");
			return;
		}
		if(removeNode.getNodeType() == Node.ATTRIBUTE_NODE){
			((Attr)removeNode).getOwnerElement().removeAttributeNode((Attr)removeNode);
			return;
		}
		Node parent=removeNode.getParentNode();
		Node prevSibbling=removeNode.getPreviousSibling();
		
		parent.removeChild(removeNode);
		
		/*if(prevSibbling!=null){
			parent.removeChild(prevSibbling.getNextSibling());
			return;
		}
		parent.removeChild(parent.getFirstChild());*/
		
	}
	
	public static void removeNodes(Node[] removeNodes){
		if(removeNodes==null){
			//System.out.println("Cannot remove as nodes evaluated to be null");
			return;
		}
		for(Node node:removeNodes)
			node.getParentNode().removeChild(node);
	}
	 
	public static Node insertChildNode(Element modifyingElem,String parentXpath,Node insertNode,boolean asFirst){
		 Element parentElem=XMLUtils.getFirstElementAtXPath(modifyingElem, parentXpath);
		 if(parentElem==null || insertNode==null){
			 //System.out.println("Cannot insert as one of the parameters evaluated to be null");
			 return null;
		 }
		 
		 insertNode= modifyingElem.getOwnerDocument().importNode(insertNode, true);
		 if(insertNode.getNodeType()==Node.ATTRIBUTE_NODE){
			 parentElem.setAttributeNode((Attr) insertNode);
			 return insertNode;
		 }
		 if(asFirst){
			 parentElem.insertBefore(insertNode,parentElem.getFirstChild());
			 return insertNode;
		 }
		 parentElem.appendChild(insertNode);
		 return insertNode;
			
	}
	
	public static void insertChildNodes(Element modifyingElem,String parentXpath,Node[] insertNodes,boolean asFirst){
		if(insertNodes==null){
			//System.out.println("Cannot insert as insert nodes evaluated to be null");
			return;
		}
		for(Node insertNode:insertNodes)
			insertChildNode(modifyingElem,parentXpath,insertNode,asFirst);
	}
	
	public static Element[] getAllChildElements(Element parentElem) {
		List<Element> childElems = new ArrayList<Element>();
		if (parentElem != null) {
			NodeList childNodesList = parentElem.getChildNodes();
			for (int i=0; i < childNodesList.getLength(); i++) {
				Node childNode = childNodesList.item(i);
				if ( childNode.getNodeType() == Node.ELEMENT_NODE) {
					childElems.add((Element) childNode);
				}
			}
		}
		
		return childElems.toArray(new Element[childElems.size()]);
	}
	
	public static Element getRootElement(Element childElem) {
		return (childElem == null) ? null : ((childElem.getParentNode() == null) ? childElem : getRootElement((Element) childElem.getParentNode()));
	}
	
	public static Element getElementForXPathEvaluation(Element req, Element res, Element currResElem, String xpath) {
		String[] xpathComps = XMLUtils.tokenizeXPath(xpath);
		boolean isResultsElem = xpathComps[0].equals(".");
		boolean isReq = xpathComps[0].endsWith("RQ");
		return ((isResultsElem) ? currResElem : ((isReq) ? req : res));

	}
	
	public static void setValueAtXPath(Element xmlElem, String nodeXPath, String value){
		Node node=getFirstNodeAtXPath(xmlElem, nodeXPath);
		if(value!=null && node!=null)
			node.setTextContent(value);
	}
}
