package com.coxandkings.travel.bookingengine.utils.xml.xpath;

import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;
import org.w3c.dom.Node;

public class NoFunction extends BaseXPathFunction {
	
	NoFunction() {
		mFuncName = "";
	}
	
	public String evaluate(Node[] xpathNodes, String[] funcArgs) {
		return (xpathNodes != null && xpathNodes.length > 0) ? XMLUtils.getNodeValue(xpathNodes[0]) : "";
	}
	
}
