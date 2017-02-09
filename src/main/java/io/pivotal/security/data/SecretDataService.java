package io.pivotal.security.data;

import io.pivotal.security.entity.NamedSecretData;
import io.pivotal.security.entity.NamedSecretImpl;
import io.pivotal.security.repository.SecretRepository;
import io.pivotal.security.service.EncryptionKeyCanaryMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.pivotal.security.repository.CertificateAuthorityRepository.CERTIFICATE_AUTHORITY_BATCH_SIZE;

@Service
public class SecretDataService {
  private static final String FIND_MOST_RECENT_BY_SUBSTRING_QUERY =
    "SELECT DISTINCT " +
      "name, " +
      "version_created_at " +
    "FROM " +
      "named_secret " +
    "INNER JOIN ( " +
      "SELECT " +
        "UPPER(name) AS inner_name, " +
        "MAX(version_created_at) AS inner_version_created_at " +
      "FROM " +
        "named_secret " +
      "GROUP BY " +
        "UPPER(name) " +
    ") AS most_recent " +
    "ON " +
      "named_secret.version_created_at = most_recent.inner_version_created_at " +
    "AND " +
      "UPPER(named_secret.name) = most_recent.inner_name " +
    "WHERE " +
      "UPPER(named_secret.name) LIKE UPPER(?) " +
    "ORDER BY version_created_at DESC";

  private final SecretRepository secretRepository;
  private final JdbcTemplate jdbcTemplate;
  private final EncryptionKeyCanaryMapper encryptionKeyCanaryMapper;

  @Autowired
  SecretDataService(
      SecretRepository secretRepository,
      JdbcTemplate jdbcTemplate,
      EncryptionKeyCanaryMapper encryptionKeyCanaryMapper
  ) {
    this.secretRepository = secretRepository;
    this.jdbcTemplate = jdbcTemplate;
    this.encryptionKeyCanaryMapper = encryptionKeyCanaryMapper;
  }

  public <Z extends NamedSecretData> Z save(Z namedSecret) {
    namedSecret.setName(addLeadingSlashIfMissing(namedSecret.getName()));
    if (namedSecret.getEncryptionKeyUuid() == null) {
      namedSecret.setEncryptionKeyUuid(encryptionKeyCanaryMapper.getActiveUuid());
    }
    return secretRepository.saveAndFlush(namedSecret);
  }

  public List<String> findAllPaths() {
    return secretRepository.findAllPaths(true);
  }

  public NamedSecretData findMostRecent(String name) {
    return secretRepository.findFirstByNameIgnoreCaseOrderByVersionCreatedAtDesc(addLeadingSlashIfMissing(name));
  }

  public NamedSecretData findByUuid(String uuid) {
    return secretRepository.findOneByUuid(UUID.fromString(uuid));
  }

  public NamedSecretData findByUuid(UUID uuid) {
    return secretRepository.findOneByUuid(uuid);
  }

  public List<NamedSecretData> findContainingName(String name) {
    return findMostRecentLikeSubstring('%' + name + '%');
  }

  public List<NamedSecretData> findStartingWithPath(String path) {
    path = addLeadingSlashIfMissing(path);
    path = !path.endsWith("/") ? path + "/" : path;

    return findMostRecentLikeSubstring(path + "%");
  }

  public long delete(String name) {
    return secretRepository.deleteByNameIgnoreCase(addLeadingSlashIfMissing(name));
  }

  public void deleteAll() { secretRepository.deleteAll(); }

  public List<NamedSecretData> findAllByName(String name) {
    return secretRepository.findAllByNameIgnoreCase(addLeadingSlashIfMissing(name));
  }

  private List<NamedSecretData> findMostRecentLikeSubstring(String substring) {
    // The subquery gets us the right name/version_created_at pairs, but changes the capitalization of the names.
    return jdbcTemplate.query(
        FIND_MOST_RECENT_BY_SUBSTRING_QUERY,
      new Object[] {substring},
      (rowSet, rowNum) -> {
        NamedSecretData secret = new NamedSecretImpl();

        secret.setName(rowSet.getString("name"));
        secret.setVersionCreatedAt(Instant.ofEpochMilli(rowSet.getLong("version_created_at")));

        return secret;
      }
    );
  }

  public Long count() {
    return secretRepository.count();
  }

  public Long countAllNotEncryptedByActiveKey() {
    return secretRepository.countByEncryptionKeyUuidNot(
      encryptionKeyCanaryMapper.getActiveUuid()
    );
  }

  public Long countEncryptedWithKeyUuidIn(List<UUID> uuids) {
    return secretRepository.countByEncryptionKeyUuidIn(uuids);
  }

  public Slice<NamedSecretData> findEncryptedWithAvailableInactiveKey() {
    return secretRepository.findByEncryptionKeyUuidIn(
      encryptionKeyCanaryMapper.getCanaryUuidsWithKnownAndInactiveKeys(),
      new PageRequest(0, CERTIFICATE_AUTHORITY_BATCH_SIZE)
    );
  }

  private String addLeadingSlashIfMissing(String name) {
    return StringUtils.prependIfMissing(name, "/");
  }
}
