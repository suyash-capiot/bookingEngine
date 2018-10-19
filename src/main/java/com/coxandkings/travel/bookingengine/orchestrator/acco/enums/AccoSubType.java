package com.coxandkings.travel.bookingengine.orchestrator.acco.enums;

public enum AccoSubType {

	//Referred from file at path D:\BookingEngine\ProductCategoriesAndSubTypes.txt
	HOTEL("Hotel"),MOTEL("Motel"),RESORT("Resort"),HOSTEL("Hostel"),HOMESTAY("HomeStay"),LODGE("Lodge"),GUESTHOUSE("Guest House"),HOUSEBOAT("HouseBoat"),
	APARTMENT("Apartment"),BUNGALOW("Bungalow"),COTTAGES("Cottages"),CHALET("Chalet"),VILLAS("Villas"),HOLIDAYHOME("HolidayHome"),
	HOLIDAYS("Holidays"),MOBILEHOMES("Mobile Homes"),TENTS("Tents"),CASTLES("Castles"),PALACES("Palaces"),AYURVEDA("Ayurveda"),
	SPA("SPA"),ROTEL("Rotel"),CONFCENTRE("Conference Centre"),FLOATINGHOTEL("Floating Hotel");
	
	private String mSubCateg;
	private AccoSubType(String subCateg) {
		this.mSubCateg = subCateg;
	}
	
	public String toString() {
		return mSubCateg;
	}
	
	public static AccoSubType forString(String subCategClassStr) { 
		AccoSubType[] subCategories = AccoSubType.values();
		for (AccoSubType subCategory : subCategories) {
			if (subCategory.toString().equals(subCategClassStr)) {
				return subCategory;
			}
		}
		
		return null;
	}
}
