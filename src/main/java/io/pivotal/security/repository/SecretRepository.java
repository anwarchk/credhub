package io.pivotal.security.repository;

import io.pivotal.security.entity.NamedSecret;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;

public interface SecretRepository extends JpaRepository<NamedSecret, UUID> {
  NamedSecret findFirstByNameIgnoreCaseOrderByVersionCreatedAtDesc(String name);
  NamedSecret findOneByUuid(UUID uuid);

  List<NamedSecret> deleteByNameIgnoreCase(String name);
  List<NamedSecret> findAllByNameIgnoreCase(String name);
//  List<NamedSecret> findByEncryptionKeyUuidNot(UUID encryptionKeyUuid);

  Page<NamedSecret> findByEncryptionKeyUuidNot(UUID encryptionKeyUuid, Pageable page);
//  NamedSecret findByEncryptionKeyUuidNotOrderByUpdatedAtLimitOne(UUID encryptionKeyUuid)

//@QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "" + Integer.MIN_VALUE), value = @QueryHint(name = QueryHints.SCROLLABLE_CURSOR))
//  default Stream<NamedSecret> streamByEncryptionKeyUuidNot(UUID encryptionKeyUuid) {
//    Stream<NamedSecret> secretStream = Stream.collect()
//  }

  default List<String> findAllPaths(Boolean findPaths) {
    if (!findPaths) {
      return newArrayList();
    }

    return findAll().stream()
        .map(NamedSecret::getName)
        .flatMap(NamedSecret::fullHierarchyForPath)
        .distinct()
        .sorted()
        .collect(Collectors.toList());
  }
}
