package com.lightbend.akka.sample;

public class Checksum {

	public static void main(String[] args) {
		Checksum sum = new Checksum();
		System.out.println(sum.getValue());
	}

	public long getValue() {
		byte[] buf = { (byte) 0xed, 0x2A, 0x44, 0x10, 0x03, 0x30 };
		int length = buf.length;
		int i = 0;

		long sum = 0;
		long data = 0;
		while (length > 1) {
			data = 0;
			data = (((buf[i]) << 8) | ((buf[i + 1]) & 0xFF));

			sum += data;
			if ((sum & 0xFFFF0000) > 0) {
				sum = sum & 0xFFFF;
				sum += 1;
			}

			i += 2;
			length -= 2;
		}

		if (length > 0) {
			sum += (buf[i] << 8);
			// sum += buffer[i];
			if ((sum & 0xFFFF0000) > 0) {
				sum = sum & 0xFFFF;
				sum += 1;
			}
		}
		sum = ~sum;
		sum = sum & 0xFFFF;
		return sum;
	}
}
