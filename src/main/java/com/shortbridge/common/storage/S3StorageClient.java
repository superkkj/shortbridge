package com.shortbridge.common.storage;

import com.shortbridge.common.exception.ErrorCode;
import com.shortbridge.common.exception.ShortBridgeException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

public class S3StorageClient implements StorageClient {

  private final S3Client client;
  private final S3Presigner presigner;
  private final String bucket;

  public S3StorageClient(StorageProperties.S3Properties props) {
    AwsBasicCredentials credentials = AwsBasicCredentials.create(props.accessKey(), props.secretKey());
    S3Configuration config = S3Configuration.builder().pathStyleAccessEnabled(props.pathStyleAccess()).build();
    this.client =
        S3Client.builder()
            .endpointOverride(URI.create(props.endpoint()))
            .region(Region.of(props.region()))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .serviceConfiguration(config)
            .build();
    this.presigner =
        S3Presigner.builder()
            .endpointOverride(URI.create(props.endpoint()))
            .region(Region.of(props.region()))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .serviceConfiguration(config)
            .build();
    this.bucket = props.bucket();
  }

  @Override
  public StoredObject store(String key, String contentType, long contentLength, InputStream content) {
    try {
      client.putObject(
          PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).contentLength(contentLength).build(),
          RequestBody.fromInputStream(content, contentLength));
      return new StoredObject(key, contentLength, contentType, null);
    } catch (Exception e) {
      throw ShortBridgeException.of(ErrorCode.STORAGE_ERROR, e);
    }
  }

  @Override
  public InputStream open(String key) {
    try {
      return client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    } catch (Exception e) {
      throw ShortBridgeException.of(ErrorCode.STORAGE_ERROR, e);
    }
  }

  @Override
  public void delete(String key) {
    client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
  }

  @Override
  public String publicUrl(String key) {
    return presignedGetUrl(key, Duration.ofHours(1));
  }

  @Override
  public String presignedGetUrl(String key, Duration ttl) {
    return presigner
        .presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build())
        .url()
        .toString();
  }

  @SuppressWarnings("unused")
  private static void closeQuietly(InputStream stream) {
    if (stream == null) return;
    try {
      stream.close();
    } catch (IOException ignore) {
    }
  }
}
