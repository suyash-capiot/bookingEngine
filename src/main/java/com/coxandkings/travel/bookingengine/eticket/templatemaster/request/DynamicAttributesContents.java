package com.coxandkings.travel.bookingengine.eticket.templatemaster.request;

public class DynamicAttributesContents {
	private String paxName;
	private String paxAddress;
	private float price;
	
	public String getPaxName() {
		return paxName;
	}
	public void setPaxName(String paxName) {
		this.paxName = paxName;
	}
	public String getPaxAddress() {
		return paxAddress;
	}
	public void setPaxAddress(String paxAddress) {
		this.paxAddress = paxAddress;
	}
	public float getPrice() {
		return price;
	}
	public void setPrice(float price) {
		this.price = price;
	}

}
