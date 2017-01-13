package io.pivotal.security.mapper;

import com.jayway.jsonpath.DocumentContext;
import io.pivotal.security.entity.NamedCertificateAuthority;
import io.pivotal.security.view.ParameterizedValidationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static com.google.common.collect.ImmutableSet.of;

import java.util.Set;

@Component
public class CASetterRequestTranslator implements RequestTranslator<NamedCertificateAuthority> {

  @Override
  public void populateEntityFromJson(NamedCertificateAuthority namedCA, DocumentContext documentContext) {
    String type = documentContext.read("$.type");
    if (!"root".equals(type)) {
      throw new ParameterizedValidationException("error.type_invalid");
    }
    String certificate = documentContext.read("$.value.certificate");
    String privateKey = documentContext.read("$.value.private_key");
    certificate = StringUtils.isEmpty(certificate) ? null : certificate;
    privateKey = StringUtils.isEmpty(privateKey) ? null : privateKey;
    if (certificate == null || privateKey == null) {
      throw new ParameterizedValidationException("error.missing_ca_credentials");
    }
    namedCA
        .setCertificateAuthorityType(type)
        .setCertificate(certificate)
        .setPrivateKey(privateKey);
  }

  @Override
  public Set<String> getValidKeys() {
    return of(
        "$['type']",
        "$['name']",
        "$['value']",
        "$['value']['certificate']",
        "$['value']['private_key']"
    );
  }
}
