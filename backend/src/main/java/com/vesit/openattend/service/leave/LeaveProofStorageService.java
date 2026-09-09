package com.vesit.openattend.service.leave;

import com.vesit.openattend.entity.LeaveProofDocument;
import com.vesit.openattend.entity.LeaveRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Accepts the proof attached to a leave application.
 *
 * <p>The declared content type is only a hint — a browser will label an upload however it likes —
 * so the file is also sniffed for its magic bytes, and the sniffed type is what gets stored and
 * later served. That keeps the approval dashboard from handing a teacher something the browser
 * would run rather than render.
 */
@Slf4j
@Service
public class LeaveProofStorageService {

    /** A scanned letter or a phone photo of a certificate; larger than this is a scan-setting problem. */
    public static final long MAX_SIZE_BYTES = LeaveProofDocument.MAX_SIZE_BYTES;

    private static final String PDF = MediaType.APPLICATION_PDF_VALUE;
    private static final String JPEG = MediaType.IMAGE_JPEG_VALUE;
    private static final String PNG = MediaType.IMAGE_PNG_VALUE;
    private static final String WEBP = "image/webp";

    private static final Set<String> ACCEPTED_TYPES = Set.of(PDF, JPEG, PNG, WEBP);

    /** Extension used when the upload arrives without a usable filename. */
    private static final Map<String, String> EXTENSIONS = Map.of(
            PDF, ".pdf", JPEG, ".jpg", PNG, ".png", WEBP, ".webp"
    );

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF_MAGIC = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP_MAGIC = "WEBP".getBytes(StandardCharsets.US_ASCII);

    /** Written as a code point so the constant survives every layer that likes to eat escapes. */
    private static final char BACKSLASH = (char) 92;

    /** Validates the upload and builds the document row; the caller persists it with its request. */
    public LeaveProofDocument accept(LeaveRequest request, MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new IllegalArgumentException(
                    "A proof attachment is required - attach the event letter, invite or medical certificate.");
        }
        if (upload.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Proof attachment is " + megabytes(upload.getSize())
                    + " MB; the limit is " + megabytes(MAX_SIZE_BYTES) + " MB. Scan it at a lower resolution.");
        }

        byte[] content = read(upload);
        String detected = sniff(content);

        if (detected == null) {
            throw new IllegalArgumentException(
                    "Proof attachment must be a PDF, JPEG, PNG or WebP file - "
                            + safeName(upload) + " is none of those.");
        }

        String declared = normaliseType(upload.getContentType());
        if (declared != null && ACCEPTED_TYPES.contains(declared) && !declared.equals(detected)) {
            log.warn("Leave proof for request {} declared {} but its contents are {} - storing as {}",
                    request.getId(), declared, detected, detected);
        }

        return LeaveProofDocument.builder()
                .id(UUID.randomUUID().toString())
                .leaveRequest(request)
                .fileName(fileName(upload, detected))
                .contentType(detected)
                .sizeBytes((long) content.length)
                .sha256(sha256(content))
                .content(content)
                .build();
    }

    /** The file's real type from its leading bytes, or null when it is not one we accept. */
    private String sniff(byte[] content) {
        if (startsWith(content, PDF_MAGIC)) {
            return PDF;
        }
        if (startsWith(content, PNG_MAGIC)) {
            return PNG;
        }
        if (startsWith(content, JPEG_MAGIC)) {
            return JPEG;
        }
        // RIFF....WEBP - the four little-endian size bytes sit between the two markers.
        if (startsWith(content, RIFF_MAGIC) && regionMatches(content, 8, WEBP_MAGIC)) {
            return WEBP;
        }
        return null;
    }

    private boolean startsWith(byte[] content, byte[] magic) {
        return regionMatches(content, 0, magic);
    }

    private boolean regionMatches(byte[] content, int offset, byte[] magic) {
        if (content.length < offset + magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[offset + i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * A safe display name. The browser-supplied name is reduced to its last path segment and
     * stripped of anything that would break a Content-Disposition header, because it is echoed
     * back on download.
     */
    private String fileName(MultipartFile upload, String detectedType) {
        String fallback = "proof" + EXTENSIONS.getOrDefault(detectedType, "");
        String raw = upload.getOriginalFilename();
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        // Browsers on Windows send a whole path; only the last segment is the file name.
        String base = raw;
        int lastSeparator = Math.max(base.lastIndexOf('/'), base.lastIndexOf(BACKSLASH));
        if (lastSeparator >= 0) {
            base = base.substring(lastSeparator + 1);
        }

        String cleaned = sanitise(base);
        if (cleaned.isEmpty() || ".".equals(cleaned) || "..".equals(cleaned)) {
            return fallback;
        }
        return cleaned.length() > 200 ? cleaned.substring(cleaned.length() - 200) : cleaned;
    }

    /** Drops control characters and the quoting characters a header or a path would react to. */
    private String sanitise(String value) {
        StringBuilder cleaned = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) || c == '"' || c == '/' || c == BACKSLASH) {
                continue;
            }
            cleaned.append(c);
        }
        return cleaned.toString().trim();
    }

    private String safeName(MultipartFile upload) {
        String raw = upload.getOriginalFilename();
        if (raw == null || raw.isBlank()) {
            return "the attachment";
        }
        String cleaned = sanitise(raw);
        return cleaned.isEmpty() ? "the attachment" : cleaned;
    }

    private String normaliseType(String contentType) {
        if (contentType == null) {
            return null;
        }
        int parameterStart = contentType.indexOf(';');
        String bare = parameterStart >= 0 ? contentType.substring(0, parameterStart) : contentType;
        return bare.trim().toLowerCase();
    }

    private byte[] read(MultipartFile upload) {
        try {
            return upload.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded proof attachment", e);
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    private String megabytes(long bytes) {
        return String.format("%.1f", bytes / (1024.0 * 1024.0));
    }
}
