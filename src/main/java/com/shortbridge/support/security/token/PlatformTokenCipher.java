package com.shortbridge.support.security.token;

import com.shortbridge.common.exception.ErrorCode;
import com.shortbridge.common.exception.ShortBridgeException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(TokenCipherProperties.class)
public class PlatformTokenCipher {

  private static final String ALGO = "AES/GCM/NoPadding";
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final Map<String, SecretKeySpec> keys;
  private final String activeKeyVersion;
  private final SecureRandom random = new SecureRandom();

  public PlatformTokenCipher(TokenCipherProperties props) {
    if (props.keys() == null || props.keys().isEmpty()) {
      throw new IllegalStateException("shortbridge.security.token-cipher.keys must be configured");
    }
    this.activeKeyVersion = props.activeKeyVersion();
    this.keys =
        props.keys().entrySet().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    Map.Entry::getKey, e -> new SecretKeySpec(derive(e.getValue()), "AES")));
    if (!keys.containsKey(activeKeyVersion)) {
      throw new IllegalStateException("active key version not in keys map: " + activeKeyVersion);
    }
  }

  public String activeVersion() {
    return activeKeyVersion;
  }

  public String encrypt(String plaintext) {
    if (plaintext == null) return null;
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(ALGO);
      cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeKeyVersion), new GCMParameterSpec(TAG_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] payload = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, payload, 0, iv.length);
      System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
      return activeKeyVersion + ":" + Base64.getEncoder().encodeToString(payload);
    } catch (GeneralSecurityException e) {
      throw ShortBridgeException.of(ErrorCode.TOKEN_CIPHER_ERROR, e);
    }
  }

  public String decrypt(String encoded) {
    if (encoded == null) return null;
    int idx = encoded.indexOf(':');
    if (idx <= 0) throw new ShortBridgeException(ErrorCode.TOKEN_CIPHER_ERROR);
    String version = encoded.substring(0, idx);
    SecretKeySpec key = keys.get(version);
    if (key == null) throw new ShortBridgeException(ErrorCode.TOKEN_CIPHER_ERROR);
    try {
      byte[] payload = Base64.getDecoder().decode(encoded.substring(idx + 1));
      byte[] iv = java.util.Arrays.copyOfRange(payload, 0, IV_BYTES);
      byte[] ct = java.util.Arrays.copyOfRange(payload, IV_BYTES, payload.length);
      Cipher cipher = Cipher.getInstance(ALGO);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw ShortBridgeException.of(ErrorCode.TOKEN_CIPHER_ERROR, e);
    }
  }

  private static byte[] derive(String passphrase) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return digest.digest(passphrase.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
