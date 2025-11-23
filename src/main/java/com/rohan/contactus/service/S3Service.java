package com.rohan.contactus.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    /**
     * Uploads byte content to S3 with specific metadata.
     * @param key S3 object key
     * @param content Byte array to upload
     * @param contentType MIME type (e.g., "application/json", "application/gzip")
     * @param contentEncoding Encoding (e.g., "gzip" or null)
     */
    public void uploadFile(String key, byte[] content, String contentType, String contentEncoding) {
        PutObjectRequest.Builder putObBuilder = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .serverSideEncryption(ServerSideEncryption.AES256);

        if (contentType != null) {
            putObBuilder.contentType(contentType);
        }

        if (contentEncoding != null) {
            putObBuilder.contentEncoding(contentEncoding);
        }

        // Upload from bytes in memory
        s3Client.putObject(putObBuilder.build(), RequestBody.fromBytes(content));
    }

    public String downloadFileAsString(String key) {
        try {
            GetObjectRequest getOb = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getOb);
            return objectBytes.asUtf8String();
        } catch (NoSuchKeyException e) {
            return null;
        }
    }
}