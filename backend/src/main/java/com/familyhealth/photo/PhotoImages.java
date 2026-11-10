package com.familyhealth.photo;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import com.familyhealth.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Turns an uploaded photo into a small square JPEG. Re-encoding keeps only the pixels, so camera
 * details and GPS location are dropped. Uses the JDK's own image tools, so it adds no dependency.
 */
final class PhotoImages {

	static final int MAX_BYTES = 5 * 1024 * 1024;

	static final int SIZE = 512;

	/** Checked before decoding, so a small file can't claim a huge picture and exhaust memory. */
	static final long MAX_PIXELS = 40_000_000L;

	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };

	private PhotoImages() {
	}

	static byte[] process(byte[] upload) {
		if (upload == null || upload.length == 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_REQUIRED", "Choose a photo to upload");
		}
		if (upload.length > MAX_BYTES) {
			throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "A photo can be at most 5 MB");
		}
		boolean jpeg = startsWith(upload, JPEG);
		if (!jpeg && !startsWith(upload, PNG)) {
			throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
					"A photo must be a JPEG or PNG image");
		}
		BufferedImage source = decode(upload);
		int orientation = jpeg ? ExifOrientation.read(upload) : 1;
		return encode(orient(squareAndScale(source), orientation));
	}

	private static BufferedImage decode(byte[] upload) {
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (input == null || !readers.hasNext()) {
				throw unreadable();
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
				if (pixels > MAX_PIXELS) {
					throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_TOO_LARGE",
							"This photo is too large. Use one under 40 megapixels");
				}
				BufferedImage image = reader.read(0);
				if (image == null) {
					throw unreadable();
				}
				return image;
			}
			finally {
				reader.dispose();
			}
		}
		catch (IOException | RuntimeException ex) {
			if (ex instanceof ApiException api) {
				throw api;
			}
			throw unreadable();
		}
	}

	/** The centre square, scaled down to at most {@link #SIZE}, on white so transparency doesn't turn black. */
	private static BufferedImage squareAndScale(BufferedImage source) {
		int side = Math.min(source.getWidth(), source.getHeight());
		int x = (source.getWidth() - side) / 2;
		int y = (source.getHeight() - side) / 2;
		int target = Math.min(side, SIZE);
		BufferedImage out = new BufferedImage(target, target, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, target, target);
			g.drawImage(source, 0, 0, target, target, x, y, x + side, y + side, null);
		}
		finally {
			g.dispose();
		}
		return out;
	}

	/**
	 * Applies a camera's EXIF orientation. The image is square by now, so every orientation keeps
	 * its size, and cropping first gives the same centre as rotating first.
	 */
	static BufferedImage orient(BufferedImage square, int orientation) {
		if (orientation < 2 || orientation > 8) {
			return square;
		}
		int n = square.getWidth();
		BufferedImage out = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) {
				int sx;
				int sy;
				switch (orientation) {
					case 2 -> { sx = n - 1 - x; sy = y; }
					case 3 -> { sx = n - 1 - x; sy = n - 1 - y; }
					case 4 -> { sx = x; sy = n - 1 - y; }
					case 5 -> { sx = y; sy = x; }
					case 6 -> { sx = y; sy = n - 1 - x; }
					case 7 -> { sx = n - 1 - y; sy = n - 1 - x; }
					default -> { sx = n - 1 - y; sy = x; }
				}
				out.setRGB(x, y, square.getRGB(sx, sy));
			}
		}
		return out;
	}

	private static byte[] encode(BufferedImage image) {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
			writer.setOutput(output);
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(0.85f);
			writer.write(null, new IIOImage(image, null, null), param);
		}
		catch (IOException ex) {
			throw new IllegalStateException("A photo couldn't be encoded", ex);
		}
		finally {
			writer.dispose();
		}
		return bytes.toByteArray();
	}

	private static boolean startsWith(byte[] data, byte[] prefix) {
		return data.length >= prefix.length && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
	}

	private static ApiException unreadable() {
		return new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_UNREADABLE", "This photo couldn't be read");
	}

}
