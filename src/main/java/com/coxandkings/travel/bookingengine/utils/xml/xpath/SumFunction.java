package com.coxandkings.travel.bookingengine.utils.xml.xpath;

import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;
import org.w3c.dom.Node;


public class SumFunction extends BaseXPathFunction {

	SumFunction() { 
		mFuncName = "sum";
	}
	
	@Override
	public String evaluate(Node[] xpathNodes, String[] funcArgs) {
		boolean foundDecimals = false;
		double sumDouble = 0.00d;
		for (Node xpathNode : xpathNodes) {
			String val = XMLUtils.getNodeValue(xpathNode);
			try {
				if (val.indexOf('.') > -1) {
					sumDouble += Double.valueOf(val);
					foundDecimals = true;
				}
				else {
					sumDouble += Long.valueOf(val);
				}
			}
			catch (NumberFormatException nfx) {
				// TODO: log warning
			}
		}
		
		return (foundDecimals) ? String.valueOf(sumDouble) : String.valueOf((long) sumDouble); 
		
	}

}
