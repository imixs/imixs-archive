package org.imixs.archive;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test class for the ChecksumGenerator 
 * 
 * @author rsoika
 * 
 */
public class ChecksumGeneratorTest {

	public static String TEST_DATA="/test-data.txt";
	
	
	
	@Test
	public void testGenerateMD5() {
		
		// read test file
		byte[] b;
		try {			
			URL url = this.getClass().getResource(TEST_DATA);
			b = Files.readAllBytes(new File(url.getFile()).toPath());
			String hash=ChecksumGenerator.generateMD5(b);
			Assert.assertEquals("397540C60D888E28C283866D5DD83E14", hash);
		} catch (IOException | NoSuchAlgorithmException e) {
			e.printStackTrace();
			Assert.fail();
		}
		
	}
	
	
	
	@Test
	public void testVerifyMD5() {
		// read test file
		byte[] b;
		try {
			URL url = this.getClass().getResource(TEST_DATA);
			b = Files.readAllBytes(new File(url.getFile()).toPath());
			Assert.assertTrue(ChecksumGenerator.verifyMD5 ("397540C60D888E28C283866D5DD83E14", b));
		} catch (IOException | NoSuchAlgorithmException e) {
			e.printStackTrace();
			Assert.fail();
		}
		
	}
	

	@Test
	public void testVerifyMD5WrongInput() {
		// read test file
		byte[] b;
		try {
			URL url = this.getClass().getResource(TEST_DATA);
			b = Files.readAllBytes(new File(url.getFile()).toPath());
			Assert.assertFalse(ChecksumGenerator.verifyMD5 ("397540C60Dxxxxxxxxxx66D5DD83E14", b));
		} catch (IOException | NoSuchAlgorithmException e) {
			e.printStackTrace();
			Assert.fail();
		}
		
	}

	
}
