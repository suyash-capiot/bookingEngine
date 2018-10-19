package com.coxandkings.travel.bookingengine.utils.xml.xpath;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum Operator {
	OPERATOR_EQ ("=", "eq"),
	OPERATOR_GE (">=", "ge"),
	OPERATOR_GT (">", "gt"),
	OPERATOR_IN ("in"),
	OPERATOR_LE ("<=", "le"),
	OPERATOR_LT ( "<", "lt"),
	OPERATOR_NE ("!=", "<>", "ne"),
	OPERATOR_NI ("!in", "not in");
	
	public static Map<String, Operator> oprSignsMap = new HashMap<String, Operator>();
	static {
		for (Operator opr : Operator.values()) {
			for (String oprSign : opr.getOperatorSigns()) {
				oprSignsMap.put(oprSign, opr);
			}
		}
	}
	
	public static Operator fromStringOperator(String oprSign) {
		String operSign = oprSign.toLowerCase();
		return oprSignsMap.get(operSign);
	}
	
	private List<String> oprSigns;
	private Operator(String... oprs) {
		oprSigns = Arrays.asList(oprs);
	}
	
	private String[] getOperatorSigns() {
		return oprSigns.toArray(new String[oprSigns.size()]);
	}
}
