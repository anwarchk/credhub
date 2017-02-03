package io.pivotal.security.service;

import io.pivotal.security.data.CertificateAuthorityDataService;
import io.pivotal.security.data.SecretDataService;
import io.pivotal.security.entity.NamedCertificateAuthority;
import io.pivotal.security.entity.NamedSecret;
import io.pivotal.security.entity.SecretEncryptionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

@Component
public class EncryptionKeyRotator {
  private final SecretEncryptionHelper secretEncryptionHelper;
  private final SecretDataService secretDataService;
  private final CertificateAuthorityDataService certificateAuthorityDataService;
  private final Logger logger;

  EncryptionKeyRotator(
      SecretEncryptionHelper secretEncryptionHelper,
      SecretDataService secretDataService,
      CertificateAuthorityDataService certificateAuthorityDataService
  ) {
    this.secretEncryptionHelper = secretEncryptionHelper;
    this.secretDataService = secretDataService;
    this.certificateAuthorityDataService = certificateAuthorityDataService;
    this.logger = LogManager.getLogger(this.getClass());
  }

  public void rotate() {
    final long start = System.currentTimeMillis();
    logger.info("Starting encryption key rotation.");
    int rotatedRecordCount = 0;

    final long startingNotRotatedRecordCount = secretDataService.countAllNotEncryptedByActiveKey() + certificateAuthorityDataService.countAllNotEncryptedByActiveKey();

    Slice<NamedSecret> secretsEncryptedByOldKey = secretDataService.findEncryptedWithAvailableInactiveKey();
    while (secretsEncryptedByOldKey.hasContent()) {
      for (NamedSecret secret : secretsEncryptedByOldKey.getContent()) {
        secretEncryptionHelper.rotate(secret);
        secretDataService.save(secret);
        rotatedRecordCount++;
      }
      secretsEncryptedByOldKey = secretDataService.findEncryptedWithAvailableInactiveKey();
    }

    Slice<NamedCertificateAuthority> certificateAuthoritiesEncryptedByOldKey = certificateAuthorityDataService.findEncryptedWithAvailableInactiveKey();
    while (certificateAuthoritiesEncryptedByOldKey.hasContent()) {
      for (NamedCertificateAuthority certificateAuthority : certificateAuthoritiesEncryptedByOldKey.getContent()) {
        secretEncryptionHelper.rotate(certificateAuthority);
        certificateAuthorityDataService.save(certificateAuthority);
        rotatedRecordCount++;
      }
      certificateAuthoritiesEncryptedByOldKey = certificateAuthorityDataService.findEncryptedWithAvailableInactiveKey();
    }

    final long finish = System.currentTimeMillis();
    final long duration = finish - start;
    final long endingNotRotatedRecordCount = startingNotRotatedRecordCount - rotatedRecordCount;

    if (rotatedRecordCount == 0 && endingNotRotatedRecordCount == 0) {
      logger.info("Found no records in need of encryption key rotation.");
    } else {
      logger.info("Finished encryption key rotation in " + duration + " milliseconds. Details:");
      logger.info("  Successfully rotated " + rotatedRecordCount + " item(s)");
      logger.info("  Skipped " + endingNotRotatedRecordCount + " item(s) due to missing master encryption key(s).");
    }
  }
}
