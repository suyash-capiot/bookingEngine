package com.coxandkings.travel.bookingengine.utils;


import java.io.InputStream;
//EXCEPTIONS
import java.security.spec.AlgorithmParameterSpec;
//KEY SPECIFICATIONS
import java.security.spec.KeySpec;
import java.util.Properties;

//CIPHER / GENERATORS
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

/**
 * -----------------------------------------------------------------------------
 * The following class is used for encrypting and decrypting password strings
 * using MD5DES Cipher algorithms. The class is created with a key and can be
 * used repeatedly to encrypt and decrypt strings using that key.
 * 
 * @version 1.0
 */

public class CipherFactory {

    private static final String PASS_PHRASE_KEY = "passphrase.key";
    private transient SecretKey key;
    private transient AlgorithmParameterSpec paramSpec;

    static class SingletonHolder {
		private static CipherFactory cipherFactoryInstance = new CipherFactory();
    }
    
    
    private CipherFactory() {
    	this(getPassPhrase(PASS_PHRASE_KEY));
    }

    /**
     * Constructor used to create this object. Responsible for setting and
     * initializing this object's encrypter and decrypter Chipher instances
     * given a Pass Phrase and algorithm.
     * 
     * @param passPhrase
     *            Pass Phrase used to initialize both the encrypter and
     *            decrypter instances.
     */
    private CipherFactory(String passPhrase) {
		// 8-bytes Salt
		byte[] salt = { (byte) 0xA9, (byte) 0x9B, (byte) 0xC8, (byte) 0x32, (byte) 0x56, (byte) 0x34, (byte) 0xE3, (byte) 0x03 };
	
		// Iteration count
		int iterationCount = 19;
	
		try {
		    KeySpec keySpec = new PBEKeySpec(passPhrase.toCharArray(), salt, iterationCount);
		    key = SecretKeyFactory.getInstance("PBEWithMD5AndDES").generateSecret(keySpec);
		    // Prepare the parameters to the ciphers
		    paramSpec = new PBEParameterSpec(salt, iterationCount);
		} 
		catch (Exception e) {
			// In case an exception occurs that is not an instance of any of above, it will be
			// caught here
			System.out.printf("EXCEPTION: %s: %s\n", e.getClass().getName(), e.getMessage());
		}
    }

    private static String getPassPhrase(String passPhraseKey) {
		String passPhraseValue = null;
		final Properties properties = new Properties();
		try {
			// In app server environment a different CryptoUtil class may be loaded by a different class 
			// loader than the loader for Class. As passphrase.properties is packaged with CryptoUtil, 
			// it might be safer to load it using CryptoUtil class loader.
			final InputStream inputStream = CipherFactory.class.getResourceAsStream("/passphrase.properties");
			
		    properties.load(inputStream);
		    passPhraseValue = properties.getProperty(passPhraseKey);
		} 
		catch (Exception e) {
			System.out.println("Passphrase EXCEPTION: Generic Exception." + e.getMessage());
		}
		return passPhraseValue;
    }
    
	static Cipher getDecryptCipher() throws Exception {
		CipherFactory cipherFactory = SingletonHolder.cipherFactoryInstance;
		Cipher dcipher;
	    dcipher = Cipher.getInstance(cipherFactory.key.getAlgorithm());
	    dcipher.init(Cipher.DECRYPT_MODE, cipherFactory.key, cipherFactory.paramSpec);
		return dcipher;
		
	}
	
	static Cipher getEncryptCipher() throws Exception {
		CipherFactory cipherFactory = SingletonHolder.cipherFactoryInstance;
		Cipher ecipher;
	    ecipher = Cipher.getInstance(cipherFactory.key.getAlgorithm());
	    ecipher.init(Cipher.ENCRYPT_MODE, cipherFactory.key, cipherFactory.paramSpec);
		return ecipher;
	}
    
    
}
