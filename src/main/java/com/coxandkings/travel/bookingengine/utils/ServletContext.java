package com.coxandkings.travel.bookingengine.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ServletContext {
	
	private static Map<Long,ServletContext> mSrltCtxMap = new HashMap<Long,ServletContext>();
	private HttpServletRequest mhttprq;
	private HttpServletResponse mhttprs;
	private List<SimpleEntry<String,String>> mRstTrckngParamLst = new ArrayList<SimpleEntry<String,String>>();
	private final static ServletContext DEFAULT_SERVLET_CONTEXT = new ServletContext();
	
	public static final String RESTPROP_SERVER_PORT = "ServerPort";
	public static final String RESTPROP_OPERATION_URI = "InboundServiceURI";
	public static final String RESTPROP_OPERATION = "Operation";
	
	public ServletContext(HttpServletRequest req, HttpServletResponse res) {
		this.mhttprq = req;
		this.mhttprs = res;
		populateRestTrackingParams();
	}

	private ServletContext() {
		
	}
	
	private void populateRestTrackingParams() {
		String reqUri = mhttprq!=null?mhttprq.getRequestURI():"";
		int idx = reqUri.lastIndexOf("/");
		mRstTrckngParamLst.add(new SimpleEntry<String,String>(RESTPROP_OPERATION_URI, reqUri.substring(0,idx>=0?idx:reqUri.length())));
		mRstTrckngParamLst.add(new SimpleEntry<String,String>(RESTPROP_SERVER_PORT, mhttprq!=null?String.valueOf(mhttprq.getServerPort()):""));
		mRstTrckngParamLst.add(new SimpleEntry<String,String>(RESTPROP_OPERATION, reqUri.substring(idx+1)));
		
	}

	public static ServletContext getServletContext() {
		ServletContext srvltCtx = mSrltCtxMap.get(Thread.currentThread().getId());
		return srvltCtx!=null?mSrltCtxMap.get(Thread.currentThread().getId()):DEFAULT_SERVLET_CONTEXT;
	}
	
	public static void setServletContext(HttpServletRequest req,HttpServletResponse res) {
		//TODO:what in case of multi thread;
		mSrltCtxMap.put(Thread.currentThread().getId(), new ServletContext(req,res));
	}

	public HttpServletRequest gethttprq() {
		return mhttprq;
	}

	public HttpServletResponse gethttprs() {
		return mhttprs;
	}
	
	public void sethttprq(HttpServletRequest req) {
		this.mhttprq = req;
	}

	public void sethttprs(HttpServletResponse res) {
		this.mhttprs = res;
	}
	
	public static List<SimpleEntry<String,String>> getRestTrackingParamList(){
		return getServletContext().mRstTrckngParamLst;
	}
	
	public String getRestTrackingParam(String paramName) {
		for (SimpleEntry<String,String> trackVal : mRstTrckngParamLst) {
			if (trackVal.getKey().equals(paramName)) {
				return trackVal.getValue();
			}
		}
		
		return "";
	}
	
	public static void duplicateContextFromThread(long sourceThreadID) {
		ServletContext srvltCtx = mSrltCtxMap.get(sourceThreadID);
		if (srvltCtx != null) {
			mSrltCtxMap.put(Thread.currentThread().getId(), srvltCtx);
		}
	}
}
