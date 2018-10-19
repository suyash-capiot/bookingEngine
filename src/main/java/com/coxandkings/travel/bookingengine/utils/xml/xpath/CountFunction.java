package com.coxandkings.travel.bookingengine.utils.xml.xpath;

import org.w3c.dom.*;

public class CountFunction extends BaseXPathFunction {
	CountFunction() {
		mFuncName = "count";
	}
	
	public String evaluate(Node[] xpathNodes, String[] funcArgs) {
		return String.valueOf((xpathNodes != null) ? xpathNodes.length : 0);
	}

}
