package com.familyhealth.photo;

/**
 * Reads only the orientation tag (0x0112) from a JPEG's EXIF block. Phones often save a portrait
 * photo sideways with this tag, and the JDK's decoder ignores it. Anything unexpected means 1
 * (as stored), never an error: the photo is still usable.
 */
final class ExifOrientation {

	private ExifOrientation() {
	}

	static int read(byte[] jpeg) {
		try {
			int i = 2;
			while (i + 4 <= jpeg.length && (jpeg[i] & 0xFF) == 0xFF) {
				int marker = jpeg[i + 1] & 0xFF;
				if (marker == 0xDA || marker == 0xD9) {
					return 1;
				}
				int length = u16(jpeg, i + 2, false);
				if (marker == 0xE1 && length >= 8 && isExif(jpeg, i + 4)) {
					return fromTiff(jpeg, i + 10, i + 2 + length);
				}
				i += 2 + length;
			}
		}
		catch (IndexOutOfBoundsException ex) {
			return 1;
		}
		return 1;
	}

	private static boolean isExif(byte[] data, int at) {
		return data[at] == 'E' && data[at + 1] == 'x' && data[at + 2] == 'i' && data[at + 3] == 'f' && data[at + 4] == 0
				&& data[at + 5] == 0;
	}

	private static int fromTiff(byte[] data, int tiff, int end) {
		boolean little;
		if (data[tiff] == 'I' && data[tiff + 1] == 'I') {
			little = true;
		}
		else if (data[tiff] == 'M' && data[tiff + 1] == 'M') {
			little = false;
		}
		else {
			return 1;
		}
		long ifdOffset = u32(data, tiff + 4, little);
		int ifd = tiff + (int) Math.min(ifdOffset, Integer.MAX_VALUE - tiff);
		if (ifd + 2 > end) {
			return 1;
		}
		int entries = u16(data, ifd, little);
		for (int e = 0; e < entries; e++) {
			int entry = ifd + 2 + e * 12;
			if (entry + 12 > end) {
				return 1;
			}
			if (u16(data, entry, little) == 0x0112) {
				int value = u16(data, entry + 8, little);
				return value >= 1 && value <= 8 ? value : 1;
			}
		}
		return 1;
	}

	private static int u16(byte[] data, int at, boolean little) {
		int a = data[at] & 0xFF;
		int b = data[at + 1] & 0xFF;
		return little ? (b << 8) | a : (a << 8) | b;
	}

	private static long u32(byte[] data, int at, boolean little) {
		long a = data[at] & 0xFF;
		long b = data[at + 1] & 0xFF;
		long c = data[at + 2] & 0xFF;
		long d = data[at + 3] & 0xFF;
		return little ? (d << 24) | (c << 16) | (b << 8) | a : (a << 24) | (b << 16) | (c << 8) | d;
	}

}
