package io.pivotal.security.view;

import io.pivotal.security.entity.NamedCertificateSecretData;
import io.pivotal.security.entity.NamedPasswordSecretData;
import io.pivotal.security.entity.NamedRsaSecretData;
import io.pivotal.security.entity.NamedSshSecretData;
import io.pivotal.security.entity.NamedValueSecretData;

public interface SecretKindFromString {

  static SecretKind fromString(String type) {
    if (type == null) {
      throw new ParameterizedValidationException("error.type_invalid");
    }
    switch (type) {
      case NamedValueSecretData.SECRET_TYPE:
        return SecretKind.VALUE;
      case NamedPasswordSecretData.SECRET_TYPE:
        return SecretKind.PASSWORD;
      case NamedCertificateSecretData.SECRET_TYPE:
        return SecretKind.CERTIFICATE;
      case NamedSshSecretData.SECRET_TYPE:
        return SecretKind.SSH;
      case NamedRsaSecretData.SECRET_TYPE:
        return SecretKind.RSA;
    }
    throw new ParameterizedValidationException("error.type_invalid");
  }
}
