package com.coxandkings.travel.bookingengine.utils.xml.xpath;

import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;
import org.w3c.dom.Node;


public class SubstringFunction extends BaseXPathFunction {

	SubstringFunction() { 
		mFuncName = "substring";
	}

	@Override
	public String evaluate(Node[] xpathNodes, String[] funcArgs) {
		if (xpathNodes == null || xpathNodes.length == 0) {
			return "";
		}
		
		String nodeVal = XMLUtils.getNodeValue(xpathNodes[0]);
		int startIdx = (funcArgs.length == 2) ? (Integer.valueOf(funcArgs[1].trim()) - 1) : 0;
		int endIdx = (funcArgs.length == 3) ? (startIdx + Integer.valueOf(funcArgs[2].trim())) : nodeVal.length();
		// TODO: Check the length of nodeVal before substring
		return nodeVal.substring(startIdx, endIdx);
	}
}
