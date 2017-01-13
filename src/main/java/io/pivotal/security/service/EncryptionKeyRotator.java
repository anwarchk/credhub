package io.pivotal.security.service;

import io.pivotal.security.data.SecretDataService;
import io.pivotal.security.entity.NamedPasswordSecret;
import io.pivotal.security.entity.NamedSecret;
import io.pivotal.security.entity.SecretEncryptionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EncryptionKeyRotator {
  private final SecretEncryptionHelper secretEncryptionHelper;
  private final SecretDataService secretDataService;
  private final Logger logger;

  EncryptionKeyRotator(
      SecretEncryptionHelper secretEncryptionHelper,
      SecretDataService secretDataService
  ) {
    this.secretEncryptionHelper = secretEncryptionHelper;
    this.secretDataService = secretDataService;
    this.logger = LogManager.getLogger(this.getClass());

    rotate();
  }

  // Synchronized to ensure that nothing happens until everything has been rotated.
  // This is the naive version!!!
  // Future stories should improve this (performance, error handling, etc.).
  private synchronized void rotate() {
    List<NamedSecret> secretsEncryptedByOldKey = secretDataService.findAllNotEncryptedByActiveKey();

    logger.info("Started encryption key rotation");

    for (NamedSecret secret : secretsEncryptedByOldKey) {
      secretEncryptionHelper.rotate(secret);
      secretDataService.save(secret);
    }

    List<NamedPasswordSecret> passwordsWithParametersEncryptedByOldEncryptionKey = secretDataService.findAllPasswordsWithParametersNotEncryptedByActiveKey();
    for (NamedPasswordSecret password : passwordsWithParametersEncryptedByOldEncryptionKey) {
      secretEncryptionHelper.rotate(password);
      secretDataService.save(password);
    }

    logger.info("Finished encryption key rotation");
  }
}
