package io.pivotal.security.mapper;

import com.jayway.jsonpath.DocumentContext;
import io.pivotal.security.entity.NamedCertificateSecretData;
import io.pivotal.security.view.ParameterizedValidationException;
import org.springframework.stereotype.Component;

import static com.google.common.collect.ImmutableSet.of;
import static io.pivotal.security.util.StringUtil.emptyToNull;

import java.util.Set;

@Component
public class CertificateSetRequestTranslator implements RequestTranslator<NamedCertificateSecretData> {

  @Override
  public void populateEntityFromJson(NamedCertificateSecretData namedCertificateSecret, DocumentContext documentContext) {
    String root = emptyToNull(documentContext.read("$.value.ca"));
    String certificate = emptyToNull(documentContext.read("$.value.certificate"));
    String privateKey = emptyToNull(documentContext.read("$.value.private_key"));
    if (root == null && certificate == null && privateKey == null) {
      throw new ParameterizedValidationException("error.missing_certificate_credentials");
    }
    namedCertificateSecret.setCa(root);
    namedCertificateSecret.setCertificate(certificate);
    namedCertificateSecret.setPrivateKey(privateKey);
  }

  @Override
  public Set<String> getValidKeys() {
    return of("$['type']", "$['name']", "$['overwrite']", "$['value']",
        "$['value']['ca']", "$['value']['certificate']", "$['value']['private_key']");
  }
}
