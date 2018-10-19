package com.coxandkings.travel.bookingengine.utils.xml.xpath;

import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;
import org.w3c.dom.Node;


public class TrimFunction extends BaseXPathFunction {

	TrimFunction() { 
		mFuncName = "trim";
	}

	@Override
	public String evaluate(Node[] xpathNodes, String[] funcArgs) {
		if (xpathNodes == null || xpathNodes.length == 0) {
			return "";
		}
		
		return XMLUtils.getNodeValue(xpathNodes[0]).trim();
	}
}
