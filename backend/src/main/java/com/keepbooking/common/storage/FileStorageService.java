package com.keepbooking.common.storage;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.keepbooking.common.config.AppProperties;
import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final S3Client s3Client;
    private final AppProperties appProperties;

    @PostConstruct
    public void ensureBucketExists() {
        String bucket = appProperties.getStorage().getBucket();
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(b -> b.bucket(bucket));
            log.info("Created storage bucket '{}'", bucket);
        }
        // Photos are meant to be publicly viewable (restaurant listings) — buckets are private by default.
        s3Client.putBucketPolicy(PutBucketPolicyRequest.builder()
                .bucket(bucket)
                .policy("""
                        {
                          "Version": "2012-10-17",
                          "Statement": [{
                            "Effect": "Allow",
                            "Principal": "*",
                            "Action": "s3:GetObject",
                            "Resource": "arn:aws:s3:::%s/*"
                          }]
                        }
                        """.formatted(bucket))
                .build());
    }

    public String upload(MultipartFile file, String keyPrefix) {
        validate(file);

        String extension = extensionOf(file.getOriginalFilename());
        String key = keyPrefix + "/" + UUID.randomUUID() + extension;

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(appProperties.getStorage().getBucket())
                            .key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | SdkException e) {
            log.error("Failed to upload file to storage", e);
            throw new ApiException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        return appProperties.getStorage().getPublicUrl() + "/" + appProperties.getStorage().getBucket() + "/" + key;
    }

    public void delete(String url) {
        String prefix = appProperties.getStorage().getPublicUrl() + "/" + appProperties.getStorage().getBucket() + "/";
        if (!url.startsWith(prefix)) {
            return;
        }
        String key = url.substring(prefix.length());
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(appProperties.getStorage().getBucket())
                    .key(key)
                    .build());
        } catch (SdkException e) {
            log.warn("Failed to delete object '{}' from storage", key, e);
        }
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE);
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new ApiException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "Allowed types: " + List.copyOf(ALLOWED_CONTENT_TYPES));
        }
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}
