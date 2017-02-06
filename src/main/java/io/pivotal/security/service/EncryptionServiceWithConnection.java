package io.pivotal.security.service;

import io.pivotal.security.config.EncryptionKeyMetadata;

import javax.crypto.SecretKey;
import java.security.Key;
import java.security.KeyStore;

public abstract class EncryptionServiceWithConnection extends EncryptionService {
  protected Key createKey(EncryptionKeyMetadata encryptionKeyMetadata, KeyGeneratingConnection dyadicConnection) {
    try {
      KeyStore keyStore = dyadicConnection.getKeyStore();
      String encryptionKeyAlias = encryptionKeyMetadata.getEncryptionKeyName();

      if (!keyStore.containsAlias(encryptionKeyAlias)) {
        SecretKey aesKey = dyadicConnection.getKeyGenerator().generateKey();
        keyStore.setKeyEntry(encryptionKeyAlias, aesKey, null, null);
      }

      return keyStore.getKey(encryptionKeyAlias, null);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
