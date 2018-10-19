package com.coxandkings.travel.bookingengine.utils;

import org.json.JSONObject;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;

public abstract class TrackableExecutor implements Runnable{
	long mParentThreadId;
	protected ProductSupplier mProdSupplier;
	protected JSONObject mReqJson;
	protected Element mReqElem;
	
	public TrackableExecutor() {
		mParentThreadId = Thread.currentThread().getId();
	}
	
	protected abstract void process(ProductSupplier prodSupplier, JSONObject reqJson, Element reqElem);
	
	@Override
	public void run() {
		TrackingContext.duplicateContextFromThread(mParentThreadId);
		process(mProdSupplier, mReqJson, mReqElem);
	}
}
