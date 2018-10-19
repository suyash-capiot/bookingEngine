package com.coxandkings.travel.bookingengine.utils.xml.xpath;

abstract class BaseXPathFunction implements XPathFunction {
	protected String mFuncName;
	
	public String getName() {
		return mFuncName;
	}
}
