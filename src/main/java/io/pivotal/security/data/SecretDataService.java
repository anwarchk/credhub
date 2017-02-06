package io.pivotal.security.data;

import io.pivotal.security.entity.NamedSecret;
import io.pivotal.security.entity.NamedSecretImpl;
import io.pivotal.security.entity.SecretMetadata;
import io.pivotal.security.repository.SecretMetadataRepository;
import io.pivotal.security.repository.SecretRepository;
import io.pivotal.security.service.EncryptionKeyCanaryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import static io.pivotal.security.repository.SecretRepository.SECRET_BATCH_SIZE;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

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
      "UPPER(named_secret.name) LIKE UPPER(?) OR UPPER(named_secret.name) LIKE UPPER(?) " +
    "ORDER BY version_created_at DESC";



  private final SecretRepository secretRepository;
  private final SecretMetadataRepository secretMetadataRepository;
  private final JdbcTemplate jdbcTemplate;
  private final EncryptionKeyCanaryMapper encryptionKeyCanaryMapper;

  @Autowired
  SecretDataService(
      SecretRepository secretRepository,
      SecretMetadataRepository secretMetadataRepository,
      JdbcTemplate jdbcTemplate,
      EncryptionKeyCanaryMapper encryptionKeyCanaryMapper
  ) {
    this.secretRepository = secretRepository;
    this.secretMetadataRepository = secretMetadataRepository;
    this.jdbcTemplate = jdbcTemplate;
    this.encryptionKeyCanaryMapper = encryptionKeyCanaryMapper;
  }

  public <Z extends NamedSecret> Z createOrReplace(Z namedSecret) {
    if (namedSecret.getEncryptionKeyUuid() == null) {
      namedSecret.setEncryptionKeyUuid(encryptionKeyCanaryMapper.getActiveUuid());
    }
    return secretRepository.saveAndFlush(namedSecret);
  }

  public NamedSecret createIfNotExists(NamedSecret namedSecret) {
    if (namedSecret.getEncryptionKeyUuid() == null) {
      namedSecret.setEncryptionKeyUuid(encryptionKeyCanaryMapper.getActiveUuid());
    }
    return secretRepository.createIfNotExists(namedSecret);
  }

  public List<String> findAllPaths() {
    return secretRepository.findAllPaths(true);
  }

  public NamedSecret findMostRecent(String name) {
    return secretRepository.findFirstBySecretMetadataNameIgnoreCaseOrderByVersionCreatedAtDesc(name);
  }

  public NamedSecret findByUuid(String uuid) {
    return secretRepository.findOneByUuid(UUID.fromString(uuid));
  }

  public List<NamedSecret> findContainingName(String name) {
    List<SecretMetadata> namesList = secretMetadataRepository.findByNameContainingIgnoreCase(name);
    List<NamedSecret> secrets = Collections.emptyList();
    namesList.forEach((metadata) -> secrets.addAll(secretRepository.findBySecretMetadata(metadata)));
    return secrets;
  }

  public List<NamedSecret> findStartingWithName(String name) {
    List<SecretMetadata> namesList = secretMetadataRepository.findByNameStartingWith(name);
    if (!name.endsWith("/")) {
      name += '/';
    }
    name += '%';

    return findMostRecentLikeSubstrings(name, name);
  }

  public List<NamedSecret> delete(String name) {
    return secretRepository.deleteBySecretMetadataNameIgnoreCase(name);
  }

  public List<NamedSecret> findAllByName(String name) {
    return secretRepository.findAllBySecretMetadataNameIgnoreCase(name);
  }

  private List<NamedSecret> findMostRecentLikeSubstrings(String substring1, String substring2) {
    secretRepository.flush();

    // The subquery gets us the right name/version_created_at pairs, but changes the capitalization of the names.
    return jdbcTemplate.query(
        FIND_MOST_RECENT_BY_SUBSTRING_QUERY,
      new Object[] {substring1, substring2},
      (rowSet, rowNum) -> {
        String name = rowSet.getString("name");
        NamedSecret secret = new NamedSecretImpl(name);
        secret.setVersionCreatedAt(Instant.ofEpochMilli(rowSet.getLong("version_created_at")));

        return secret;
      }
    );
  }

  public Slice<NamedSecret> findNotEncryptedByActiveKey() {
    return secretRepository.findByEncryptionKeyUuidNot(
        encryptionKeyCanaryMapper.getActiveUuid(),
        new PageRequest(0, SECRET_BATCH_SIZE)
    );
  }
}
