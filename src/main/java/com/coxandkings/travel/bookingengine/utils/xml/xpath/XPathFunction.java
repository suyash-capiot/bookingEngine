package com.coxandkings.travel.bookingengine.utils.xml.xpath;

import org.w3c.dom.Node;

public interface XPathFunction {
	public String evaluate(Node[] xpathNodes, String[] funcArgs);
	public String getName();
}
