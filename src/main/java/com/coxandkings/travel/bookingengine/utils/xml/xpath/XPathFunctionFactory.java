package com.coxandkings.travel.bookingengine.utils.xml.xpath;

public class XPathFunctionFactory {

	private static String PACKAGE_NAME = XPathFunctionFactory.class.getPackage().getName();
	
	@SuppressWarnings("rawtypes")
	public static XPathFunction getFunction(String funcName) {
		if (funcName == null || funcName.isEmpty()) {
			return new NoFunction();
		}
		else {
			String className = String.format("%s.%c%sFunction", PACKAGE_NAME, Character.toUpperCase(funcName.charAt(0)), funcName.substring(1).toLowerCase());
			try {
				Class xpathFunc = Class.forName(className);
				return (XPathFunction) xpathFunc.newInstance();
			}
			catch (Exception x) {
				// TODO: Log a warning here
				return new NoFunction();
			}
		}
	}
}
