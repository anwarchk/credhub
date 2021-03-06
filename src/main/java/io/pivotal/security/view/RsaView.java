package io.pivotal.security.view;

import io.pivotal.security.domain.NamedRsaSecret;
import io.pivotal.security.secret.RsaKey;

@SuppressWarnings("unused")
public class RsaView extends SecretView {

  RsaView() {  /* Jackson */ }

  RsaView(NamedRsaSecret namedRsaSecret) {
    super(
        namedRsaSecret.getVersionCreatedAt(),
        namedRsaSecret.getUuid(),
        namedRsaSecret.getName(),
        namedRsaSecret.getSecretType(),
        new RsaKey(namedRsaSecret.getPublicKey(), namedRsaSecret.getPrivateKey())
    );
  }
}
