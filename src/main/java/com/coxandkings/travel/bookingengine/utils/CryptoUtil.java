package com.coxandkings.travel.bookingengine.utils;


import java.util.Base64;
import javax.crypto.Cipher;

/**
 * -----------------------------------------------------------------------------
 * The following class is used for encrypting and decrypting password strings
 * using MD5DES Cipher algorithms. The class is created with a key and can be
 * used repeatedly to encrypt and decrypt strings using that key.
 * 
 * @version 1.0
 */

public class CryptoUtil {

    /**
     * Takes a single String as an argument and returns an Encrypted version of
     * that String.
     * 
     * @param str
     *            String to be encrypted
     * @return <code>String</code> Encrypted version of the provided String
     */
    public static String encrypt(String str) {
		try {
			Cipher ecipher = CipherFactory.getEncryptCipher();
		    // Encode the string into bytes using utf-8
		    byte[] utf8 = str.getBytes("UTF8");
	
		    // Encrypt
		    byte[] enc = ecipher.doFinal(utf8);
	
		    // Encode bytes to base64 to get a string
		    final String encodedString = new String(Base64.getEncoder().encode(enc));
	
		    return encodedString;
		} 
		catch (Exception e) {
			// In case an exception occurs that is not an instance of any of above, it will be
			// caught here
			System.out.printf("Encrypt EXCEPTION: %s: %s\n", e.getClass().getName(), e.getMessage());
		}
	
		// If an exception occurs and encrypted string cannot be returned, return the original
		// so as not to cause a NPE in calling class.
		return str;
    }

    /**
     * Takes a encrypted String as an argument, decrypts and returns the
     * decrypted String.
     * 
     * @param str
     *            Encrypted String to be decrypted
     * @return <code>String</code> Decrypted version of the provided String
     */
    public static String decrypt(String str) {
		try {
			Cipher dcipher = CipherFactory.getDecryptCipher();
		    // Decode base64 to get bytes
		    byte[] dec = Base64.getDecoder().decode(str);
	
		    // Decrypt
		    byte[] utf8 = dcipher.doFinal(dec);
	
		    // Decode using utf-8
		    return new String(utf8, "UTF8");
	
		} 
		catch (Exception e) {
			// In case an exception occurs that is not an instance of any of above, it will be
			// caught here
			System.out.printf("Decrypt EXCEPTION: %s: %s\n", e.getClass().getName(), e.getMessage());
		}
		
		// If an exception occurs and decrypted string cannot be returned, return the original
		// so as not to cause a NPE in calling class.
		return str;
    }

}
