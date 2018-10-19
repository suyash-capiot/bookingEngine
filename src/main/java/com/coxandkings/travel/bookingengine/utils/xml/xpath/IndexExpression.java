package com.coxandkings.travel.bookingengine.utils.xml.xpath;

import com.coxandkings.travel.bookingengine.enums.Operator;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;

//import javax.xml.namespace.QName;

public class IndexExpression {
	private Operator mOperator;
	private String mXPathOperandLeft, mXPathOperandRight;
	private int mXPathOperandRightType;
	
	private int mIndex = -1;
	
	private static final String FIRST_INDEX = "first()";
	private static final String LAST_INDEX = "last()";
	
	private IndexExpression(ArrayList<String> tokens) {
		if (tokens.size() == 1) {
			String idxExpr = tokens.get(0);
			mIndex = (FIRST_INDEX.equals(idxExpr)) ? 1 : ( (LAST_INDEX.equals(idxExpr)) ? Integer.MAX_VALUE : Integer.valueOf(tokens.get(0)));
		}
		else {
			mXPathOperandLeft = tokens.get(0);
			mOperator = Operator.fromStringOperator(tokens.get(1));
			mXPathOperandRight = tokens.get(2);
			checkOperandRight();
		}
	}
	
	private void checkOperandRight() {
		mXPathOperandRightType = 0;
		if (mXPathOperandRight.startsWith("'") && mXPathOperandRight.endsWith("'")) {
			mXPathOperandRight = mXPathOperandRight.substring(1, mXPathOperandRight.length() - 1);
		}
		else { 
			mXPathOperandRightType = 1;
		}
	}

	public static IndexExpression compile(String indexExpression) {
		if (indexExpression == null || indexExpression.isEmpty()) {
			return null;
		}

		ArrayList<String> tokens = new ArrayList<String>();
		StringBuilder strBldr = new StringBuilder();
		for (int i=0; i < indexExpression.length(); i++) {
			if (indexExpression.charAt(i) == ' ') {
				continue;
			}
			
			switch(indexExpression.charAt(i)) {
			    case '!': tokens.add(strBldr.toString());
			              strBldr.setLength(0);
			    	      if (indexExpression.charAt(i + 1) == '=') {
			                  tokens.add("!=");
			                  i += 1;
			              }
			              else {
			            	  throw new RuntimeException(String.format("Invalid index expression format %s. Operator must be '!='", indexExpression));			            	  
			              }
			              break;
			               
			    case '<': tokens.add(strBldr.toString());
			              strBldr.setLength(0);
			    	      if (indexExpression.charAt(i + 1) == '=') {
	                          tokens.add("<=");
	                          i += 1;
	                      }
			              else {
			            	  tokens.add("<");
			              }
			              break;
			              
			    case '>': tokens.add(strBldr.toString());
			              strBldr.setLength(0);
			    	      if (indexExpression.charAt(i + 1) == '=') {
                              tokens.add(">=");
                              i += 1;
                          }
	                      else {
	            	          tokens.add(">");
	                      }
	                      break;
	                      
			    case '=': tokens.add(strBldr.toString());
			              strBldr.setLength(0);
			    	      tokens.add("=");
			              break;
			    
			    case '\'' : do{
			    				strBldr.append(indexExpression.charAt(i));
			    				i++;
			    			}while(i < indexExpression.length() && indexExpression.charAt(i)!='\'');
			    
			    			strBldr.append(indexExpression.charAt(i));
			    			i++;
			    			break;
			              
				default : strBldr.append(indexExpression.charAt(i));
				          break;
			}
		}
		
		tokens.add(strBldr.toString());
		
		if (tokens.size() != 1 && tokens.size() != 3) {
			throw new RuntimeException(String.format("Invalid index expression format %s. Expected format is either <index> or <operand> <operator> <operand>", indexExpression));
		}
		
		return new IndexExpression(tokens);
	}
	
	//The following method has been overloaded to check index of child with mindex
	private boolean isCorrectChildIndex(Element contextElem,int contextElemPos) {
		return (mIndex == Integer.MAX_VALUE) ? isLastChildIndex(contextElem, contextElemPos) : (contextElemPos == mIndex);
	}
		
//	private boolean isCorrectChildIndex(Element contextElem) {
//		Node parentNode = contextElem.getParentNode();
//		if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
//			return false;
//		}
//		
//		String contextElemQNStr = (new QName(contextElem.getNamespaceURI(), contextElem.getLocalName())).toString();
//		Element parentElem = (Element) parentNode;
//		NodeList children = parentElem.getChildNodes();
//		int childIdx = 0;
//		for (int i=0; i < children.getLength(); i++) {
//			Node child = children.item(i);
//			if (child.getNodeType() != Node.ELEMENT_NODE) {
//				continue;
//			}
//			
//			Element childElem = (Element) child;
//			String childElemQNStr = (new QName(childElem.getNamespaceURI(), childElem.getLocalName())).toString();
//			if (contextElemQNStr.equals(childElemQNStr)) {
//				childIdx++;
//			}
//			
//			if (childIdx == mIndex) {
//				return true;
//			}
//		}
//		
//		return false;
//	}

	private boolean isLastChildIndex(Element contextElem, int contextElemPos) {
		Node parentNode = contextElem.getParentNode();
		if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
			return false;
		}
		
		Element parentElem = (Element) parentNode;
		NodeList children = parentElem.getElementsByTagNameNS(contextElem.getNamespaceURI(), contextElem.getLocalName());
		return (contextElemPos == children.getLength());
	}

	
	public boolean evaluate(Element contextElem,int childptr) {
		if ( mIndex > -1) {
			return isCorrectChildIndex(contextElem,childptr);
		}
		else {
			String leftVal = XMLUtils.getValueAtXPath(contextElem, mXPathOperandLeft);
			String rightVal = (mXPathOperandRightType == 1) ? XMLUtils.getValueAtXPath(contextElem, mXPathOperandRight) : mXPathOperandRight;
			int compareResult = leftVal.compareTo(rightVal);
			switch (mOperator) {
				case OPERATOR_EQ: return (compareResult == 0);
				case OPERATOR_GE: return (compareResult >= 0);
				case OPERATOR_GT: return (compareResult > 0);
				case OPERATOR_LE: return (compareResult <= 0);
				case OPERATOR_LT: return (compareResult < 0);
				case OPERATOR_NE: return (compareResult != 0);
				default: return false;
			}
		}
	}

}
