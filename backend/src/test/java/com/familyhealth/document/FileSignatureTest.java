package com.familyhealth.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FileSignatureTest {

	@Test
	void recognisesOnlyTheAllowedTypes() {
		assertThat(FileSignature.detect("%PDF-1.4".getBytes(StandardCharsets.US_ASCII))).contains(FileSignature.PDF);
		assertThat(FileSignature.detect(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0 }))
			.contains(FileSignature.JPEG);
		assertThat(FileSignature.detect(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A }))
			.contains(FileSignature.PNG);
		assertThat(FileSignature.detect("<svg>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
		assertThat(FileSignature.detect("GIF89a".getBytes(StandardCharsets.US_ASCII))).isEmpty();
		assertThat(FileSignature.detect(new byte[] { (byte) 0x89, 'P', 'N' })).isEmpty();
		assertThat(FileSignature.detect(new byte[0])).isEmpty();
	}

	@Test
	void filenamesLoseFoldersAndControlCharacters() {
		assertThat(DocumentService.cleanFilename("../../etc/passwd")).isEqualTo("passwd");
		assertThat(DocumentService.cleanFilename("C:\\Users\\x\\scan.png")).isEqualTo("scan.png");
		assertThat(DocumentService.cleanFilename("a\"b\r\n.pdf")).isEqualTo("ab.pdf");
		assertThat(DocumentService.cleanFilename("  ")).isEqualTo("document");
		assertThat(DocumentService.cleanFilename(null)).isEqualTo("document");
		assertThat(DocumentService.cleanFilename("x".repeat(300) + ".png")).hasSize(200).endsWith(".png");
	}

}
