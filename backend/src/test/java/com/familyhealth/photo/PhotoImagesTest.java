package com.familyhealth.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import com.familyhealth.common.ApiException;
import org.junit.jupiter.api.Test;

class PhotoImagesTest {

	/** Left half red, right half blue. */
	private static BufferedImage halves(int width, int height, int type) {
		BufferedImage image = new BufferedImage(width, height, type);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.RED);
		g.fillRect(0, 0, width / 2, height);
		g.setColor(Color.BLUE);
		g.fillRect(width / 2, 0, width - width / 2, height);
		g.dispose();
		return image;
	}

	private static byte[] encode(BufferedImage image, String format) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, format, out);
		return out.toByteArray();
	}

	private static BufferedImage decode(byte[] jpeg) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(jpeg));
	}

	/** Inserts an EXIF block with the given orientation straight after the JPEG's start marker. */
	private static byte[] withOrientation(byte[] jpeg, int orientation) {
		ByteBuffer tiff = ByteBuffer.allocate(26);
		tiff.put("MM".getBytes(StandardCharsets.US_ASCII)).putShort((short) 42).putInt(8);
		tiff.putShort((short) 1);
		tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
		tiff.putInt(0);
		byte[] exif = ByteBuffer.allocate(6 + 26).put("Exif\0\0".getBytes(StandardCharsets.US_ASCII)).put(tiff.array())
			.array();
		ByteBuffer out = ByteBuffer.allocate(jpeg.length + 4 + exif.length);
		out.put(jpeg, 0, 2).put((byte) 0xFF).put((byte) 0xE1).putShort((short) (exif.length + 2)).put(exif);
		out.put(jpeg, 2, jpeg.length - 2);
		return out.array();
	}

	private static boolean reddish(int rgb) {
		Color c = new Color(rgb);
		return c.getRed() > 180 && c.getBlue() < 90;
	}

	private static boolean bluish(int rgb) {
		Color c = new Color(rgb);
		return c.getBlue() > 180 && c.getRed() < 90;
	}

	private static void refused(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.code()).isEqualTo(code));
	}

	@Test
	void aLargePhotoBecomesTheCentreSquareAt512Pixels() throws IOException {
		BufferedImage result = decode(PhotoImages.process(encode(halves(1600, 900, BufferedImage.TYPE_INT_RGB), "png")));
		assertThat(result.getWidth()).isEqualTo(512);
		assertThat(result.getHeight()).isEqualTo(512);
		assertThat(reddish(result.getRGB(10, 256))).isTrue();
		assertThat(bluish(result.getRGB(500, 256))).isTrue();
	}

	@Test
	void aSmallPhotoIsNotEnlarged() throws IOException {
		BufferedImage result = decode(PhotoImages.process(encode(halves(120, 80, BufferedImage.TYPE_INT_RGB), "jpg")));
		assertThat(result.getWidth()).isEqualTo(80);
		assertThat(result.getHeight()).isEqualTo(80);
	}

	@Test
	void transparencyBecomesWhiteNotBlack() throws IOException {
		BufferedImage clear = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		BufferedImage result = decode(PhotoImages.process(encode(clear, "png")));
		Color corner = new Color(result.getRGB(5, 5));
		assertThat(corner.getRed()).isGreaterThan(240);
		assertThat(corner.getGreen()).isGreaterThan(240);
	}

	@Test
	void aPhoneRotationFlagIsAppliedAndTheExifBlockDropped() throws IOException {
		byte[] sideways = withOrientation(encode(halves(200, 200, BufferedImage.TYPE_INT_RGB), "jpg"), 6);
		assertThat(ExifOrientation.read(sideways)).isEqualTo(6);

		byte[] processed = PhotoImages.process(sideways);
		BufferedImage result = decode(processed);

		// Rotated a quarter turn clockwise: the left (red) half is now at the top.
		assertThat(reddish(result.getRGB(100, 10))).isTrue();
		assertThat(bluish(result.getRGB(100, 190))).isTrue();
		assertThat(new String(processed, StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
		assertThat(ExifOrientation.read(processed)).isEqualTo(1);
	}

	@Test
	void everyOrientationKeepsTheSquare() {
		BufferedImage square = halves(32, 32, BufferedImage.TYPE_INT_RGB);
		for (int orientation = 1; orientation <= 8; orientation++) {
			BufferedImage result = PhotoImages.orient(square, orientation);
			assertThat(result.getWidth()).isEqualTo(32);
			assertThat(result.getHeight()).isEqualTo(32);
		}
		// Mirrored left to right: blue on the left.
		assertThat(bluish(PhotoImages.orient(square, 2).getRGB(2, 16))).isTrue();
		// A quarter turn anticlockwise: the left (red) half ends at the bottom.
		assertThat(reddish(PhotoImages.orient(square, 8).getRGB(16, 30))).isTrue();
	}

	@Test
	void onlyJpegAndPngAreAcceptedByContentNotName() {
		refused(() -> PhotoImages.process("<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)), "UNSUPPORTED_FILE_TYPE");
		refused(() -> PhotoImages.process("%PDF-1.7 synthetic".getBytes(StandardCharsets.US_ASCII)), "UNSUPPORTED_FILE_TYPE");
	}

	@Test
	void emptyBrokenAndOversizedFilesAreRefused() {
		refused(() -> PhotoImages.process(new byte[0]), "FILE_REQUIRED");
		byte[] broken = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5 };
		refused(() -> PhotoImages.process(broken), "IMAGE_UNREADABLE");
		byte[] huge = new byte[PhotoImages.MAX_BYTES + 1];
		huge[0] = (byte) 0xFF;
		huge[1] = (byte) 0xD8;
		huge[2] = (byte) 0xFF;
		refused(() -> PhotoImages.process(huge), "FILE_TOO_LARGE");
	}

	@Test
	void aSmallFileClaimingAHugePictureIsRefusedBeforeDecoding() {
		ByteBuffer png = ByteBuffer.allocate(8 + 25);
		png.put(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A });
		ByteBuffer ihdr = ByteBuffer.allocate(17);
		ihdr.put("IHDR".getBytes(StandardCharsets.US_ASCII)).putInt(50_000).putInt(50_000);
		ihdr.put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
		CRC32 crc = new CRC32();
		crc.update(ihdr.array());
		png.putInt(13).put(ihdr.array()).putInt((int) crc.getValue());

		refused(() -> PhotoImages.process(png.array()), "IMAGE_TOO_LARGE");
	}

}
