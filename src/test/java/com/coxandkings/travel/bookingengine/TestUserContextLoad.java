package com.coxandkings.travel.bookingengine;

import java.io.FileReader;
import java.util.List;

import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.MDMConfig;
import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;

public class TestUserContextLoad {

	public static void main(String[] args) {
		try {
			MDMConfig.loadConfig();
			RedisConfig.loadConfig();
			JSONObject reqHdr = new JSONObject(new JSONTokener(new FileReader(args[0])));
			UserContext usrCtx = UserContext.getUserContextForSession(reqHdr);
			System.out.println(usrCtx.toString());
			
			List<ProductSupplier> prodSupps = usrCtx.getSuppliersForProduct("Transportation", "Flight");
			for (ProductSupplier prodSupp : prodSupps) {
				System.out.println("-------------------------------------------------------");
				System.out.println(prodSupp.toJSON().toString());
			}
		}
		catch (Exception x) {
			x.printStackTrace();
		}

		System.exit(0);
	}

}
