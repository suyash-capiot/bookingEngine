package com.coxandkings.travel.bookingengine.utils;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.apache.logging.log4j.core.pattern.PatternConverter;

@Plugin(name = "TrackingContextPatternConverter", category = PatternConverter.CATEGORY)
@ConverterKeys({"trkctx"})
public class TrackingContextPatternConverter extends LogEventPatternConverter {

	private TrackingContextPatternConverter(String[]options) {
		super("TrackingContext", "trackingContext");
	}

	public static TrackingContextPatternConverter newInstance(String[] options) {
		return new TrackingContextPatternConverter(options);
	}
	
	@Override
	public void format(LogEvent event, StringBuilder toAppendTo) {
		toAppendTo.append(TrackingContext.getTrackingContext().toString());
	}

}
