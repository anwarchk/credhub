package io.pivotal.security.data;

import io.pivotal.security.domain.NamedCertificateSecret;
import io.pivotal.security.domain.NamedPasswordSecret;
import io.pivotal.security.domain.NamedRsaSecret;
import io.pivotal.security.domain.NamedSecret;
import io.pivotal.security.domain.NamedSshSecret;
import io.pivotal.security.domain.NamedValueSecret;
import io.pivotal.security.entity.NamedCertificateSecretData;
import io.pivotal.security.entity.NamedPasswordSecretData;
import io.pivotal.security.entity.NamedRsaSecretData;
import io.pivotal.security.entity.NamedSecretData;
import io.pivotal.security.entity.NamedSshSecretData;
import io.pivotal.security.entity.NamedValueSecretData;
import io.pivotal.security.entity.SecretName;
import io.pivotal.security.repository.SecretNameRepository;
import io.pivotal.security.repository.SecretRepository;
import io.pivotal.security.service.EncryptionKeyCanaryMapper;
import io.pivotal.security.view.SecretView;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static io.pivotal.security.repository.SecretRepository.BATCH_SIZE;

@Service
public class SecretDataService {
  private final SecretRepository secretRepository;
  private final SecretNameRepository secretNameRepository;
  private final JdbcTemplate jdbcTemplate;
  private final EncryptionKeyCanaryMapper encryptionKeyCanaryMapper;

  private final String FIND_MATCHING_NAME_QUERY =
      " select name.name, secret.version_created_at from (" +
      "   select" +
      "     max(version_created_at) as version_created_at," +
      "     secret_name_uuid" +
      "   from named_secret group by secret_name_uuid" +
      " ) as secret inner join (" +
      "   select * from secret_name" +
      "     where lower(name) like lower(?)" +
      " ) as name" +
      " on secret.secret_name_uuid = name.uuid" +
      " order by version_created_at desc";

  @Autowired
  SecretDataService(
      SecretRepository secretRepository,
      SecretNameRepository secretNameRepository,
      JdbcTemplate jdbcTemplate,
      EncryptionKeyCanaryMapper encryptionKeyCanaryMapper
  ) {
    this.secretRepository = secretRepository;
    this.secretNameRepository = secretNameRepository;
    this.jdbcTemplate = jdbcTemplate;
    this.encryptionKeyCanaryMapper = encryptionKeyCanaryMapper;
  }

  public <Z extends NamedSecret> Z save(Z namedSecret) {
    namedSecret.getSecretName().setName(addLeadingSlashIfMissing(namedSecret.getName()));
    if (namedSecret.getEncryptionKeyUuid() == null) {
      namedSecret.setEncryptionKeyUuid(encryptionKeyCanaryMapper.getActiveUuid());
    }

    SecretName secretName = namedSecret.getSecretName();

    if (secretName.getUuid() == null) {
      namedSecret.setSecretName(createOrFindSecretName(secretName.getName()));
    }

    return (Z) wrap(namedSecret.saveAndFlush(secretRepository));
  }

  public List<String> findAllPaths() {
    return secretRepository.findAllPaths(true);
  }

  public NamedSecret findMostRecent(String name) {
    SecretName secretName = secretNameRepository.findOneByNameIgnoreCase(addLeadingSlashIfMissing(name));

    if (secretName == null) {
      return null;
    } else {
      return wrap(secretRepository.findFirstBySecretNameUuidOrderByVersionCreatedAtDesc(secretName.getUuid()));
    }
  }

  public NamedSecret findByUuid(String uuid) {
    return wrap(secretRepository.findOneByUuid(UUID.fromString(uuid)));
  }

  public NamedSecret findByUuid(UUID uuid) {
    return wrap(secretRepository.findOneByUuid(uuid));
  }

  public List<SecretView> findContainingName(String name) {
    return findMatchingName("%" + name + "%");
  }

  public List<SecretView> findStartingWithPath(String path) {
    path = addLeadingSlashIfMissing(path);
    path = !path.endsWith("/") ? path + "/" : path;

    return findMatchingName(path + "%");
  }

  public long delete(String name) {
    SecretName secretName = secretNameRepository.findOneByNameIgnoreCase(addLeadingSlashIfMissing(name));

    if (secretName != null) {
      return secretNameRepository.deleteByName(secretName.getName());
    } else {
      return 0;
    }
  }

  // TODO make this a test-specific subclass? because self-destruct buttons in production code are scary
  public void deleteAll() { secretRepository.deleteAll(); }

  public List<NamedSecret> findAllByName(String name) {
    SecretName secretName = secretNameRepository.findOneByNameIgnoreCase(addLeadingSlashIfMissing(name));

    return secretName != null ? wrap(secretRepository.findAllBySecretNameUuid(secretName.getUuid())) : newArrayList();
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

  public Slice<NamedSecret> findEncryptedWithAvailableInactiveKey() {
    final Slice<NamedSecretData> namedSecretDataSlice = secretRepository.findByEncryptionKeyUuidIn(
        encryptionKeyCanaryMapper.getCanaryUuidsWithKnownAndInactiveKeys(),
        new PageRequest(0, BATCH_SIZE)
    );
    return new SliceImpl(wrap(namedSecretDataSlice.getContent()));
  }

  private SecretName createOrFindSecretName(String name) {
    try {
      return secretNameRepository.saveAndFlush(new SecretName(name));
    } catch (DataIntegrityViolationException | ConstraintViolationException e) {
      return secretNameRepository.findOneByNameIgnoreCase(name);
    }
  }

  private List<SecretView> findMatchingName(String nameLike) {
    return jdbcTemplate.query(
        FIND_MATCHING_NAME_QUERY,
        new Object[]{nameLike},
        (rowSet, rowNum) -> {
          final Instant versionCreatedAt = Instant.ofEpochMilli(rowSet.getLong("version_created_at"));
          final String name = rowSet.getString("name");
          return new SecretView(versionCreatedAt, name);
        }
    );
  }

  private static String addLeadingSlashIfMissing(String name) {
    return StringUtils.prependIfMissing(name, "/");
  }

  private static List<NamedSecret> wrap(List<NamedSecretData>daos) {
    return daos.stream().map(SecretDataService::wrap).collect(Collectors.toList());
  }

  private static NamedSecret wrap(NamedSecretData dao) {
    if (dao instanceof NamedCertificateSecretData) {
      return new NamedCertificateSecret((NamedCertificateSecretData) dao);
    }
    if (dao instanceof NamedPasswordSecretData) {
      return new NamedPasswordSecret((NamedPasswordSecretData) dao);
    }
    if (dao instanceof NamedRsaSecretData) {
      return new NamedRsaSecret((NamedRsaSecretData) dao);
    }
    if (dao instanceof NamedSshSecretData) {
      return new NamedSshSecret((NamedSshSecretData) dao);
    }
    if (dao instanceof NamedValueSecretData) {
      return new NamedValueSecret((NamedValueSecretData) dao);
    }
    throw new RuntimeException("Unrecognized type: " + dao.getClass().getName());
  }
}
