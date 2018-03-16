package com.lightbend.akka.sample;

import static org.junit.Assert.*;

import org.junit.Test;

public class InternetChecksumTest {
	
	@Test
	public void simplestValidValue() {
		InternetChecksum testObject = new InternetChecksum();

		byte[] buf = new byte[1]; // should work for any-length array of zeros
		long expected = 0xFFFF;

		long actual = testObject.calculateChecksum(buf);

		assertEquals(expected, actual);
	}

	@Test
	public void validSingleByteExtreme() {
		InternetChecksum testObject = new InternetChecksum();

		byte[] buf = new byte[] { (byte) 0xFF };
		long expected = 0xFF;

		long actual = testObject.calculateChecksum(buf);

		assertEquals(expected, actual);
	}

	@Test
	public void validMultiByteExtrema() {
		InternetChecksum testObject = new InternetChecksum();

		byte[] buf = new byte[] { 0x00, (byte) 0xFF };
		long expected = 0xFF00;

		long actual = testObject.calculateChecksum(buf);

		assertEquals(expected, actual);
	}

	@Test
	public void validExampleMessage() {
		InternetChecksum testObject = new InternetChecksum();

		// Berkley example
		// http://www.cs.berkeley.edu/~kfall/EE122/lec06/tsld023.htm
		// e3 4f 23 96 44 27 99 f3
		byte[] buf = { (byte) 0xe3, 0x4f, 0x23, (byte) 0x96, 0x44, 0x27, (byte) 0x99, (byte) 0xf3 };

		long expected = 0x1aff;

		long actual = testObject.calculateChecksum(buf);

		assertEquals(expected, actual);
	}

	@Test
	public void validExampleEvenMessageWithCarryFromRFC1071() {
		InternetChecksum testObject = new InternetChecksum();

		// RFC1071 example http://www.ietf.org/rfc/rfc1071.txt
		// 00 01 f2 03 f4 f5 f6 f7
		byte[] buf = { (byte) 0x00, 0x01, (byte) 0xf2, (byte) 0x03, (byte) 0xf4, (byte) 0xf5, (byte) 0xf6,
				(byte) 0xf7 };

		long expected = 0x220d;

		long actual = testObject.calculateChecksum(buf);

		assertEquals(expected, actual);

	}
}
