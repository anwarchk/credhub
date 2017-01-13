package io.pivotal.security.controller.v1.secret;

import com.jayway.jsonpath.DocumentContext;
import io.pivotal.security.controller.v1.SecretKindMappingFactory;
import io.pivotal.security.entity.NamedCertificateAuthority;
import io.pivotal.security.entity.NamedCertificateSecret;
import io.pivotal.security.entity.NamedPasswordSecret;
import io.pivotal.security.entity.NamedRsaSecret;
import io.pivotal.security.entity.NamedSecret;
import io.pivotal.security.entity.NamedSshSecret;
import io.pivotal.security.entity.NamedValueSecret;
import io.pivotal.security.mapper.CASetterRequestTranslator;
import io.pivotal.security.mapper.CertificateSetRequestTranslator;
import io.pivotal.security.mapper.RsaSshSetRequestTranslator;
import io.pivotal.security.mapper.StringSetRequestTranslator;
import io.pivotal.security.view.SecretKind;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.NoSuchAlgorithmException;

@Component
class NamedSecretSetHandler implements SecretKindMappingFactory {

  @Autowired
  StringSetRequestTranslator stringSetRequestTranslator;

  @Autowired
  CertificateSetRequestTranslator certificateSetRequestTranslator;

  @Autowired
  CASetterRequestTranslator caSetRequestTranslator;

  @Autowired
  RsaSshSetRequestTranslator rsaSshSetRequestTranslator;

  @Override
  public SecretKind.CheckedMapping<NamedSecret, NoSuchAlgorithmException> make(String secretPath, DocumentContext parsedRequest) {
    return new SecretKind.CheckedMapping<NamedSecret, NoSuchAlgorithmException>() {
      @Override
      public NamedSecret value(NamedSecret namedSecret) throws NoSuchAlgorithmException {
        return createNewSecret(null, NamedValueSecret::new, secretPath, stringSetRequestTranslator, parsedRequest);
      }

      @Override
      public NamedSecret password(NamedSecret namedSecret) throws NoSuchAlgorithmException {
        return createNewSecret(null, NamedPasswordSecret::new, secretPath, stringSetRequestTranslator, parsedRequest);
      }

      @Override
      public NamedSecret certificate(NamedSecret namedSecret) throws NoSuchAlgorithmException {
        return createNewSecret(null, NamedCertificateSecret::new, secretPath, certificateSetRequestTranslator, parsedRequest);
      }

      @Override
      public NamedSecret certificateAuthority(NamedSecret namedSecret) throws NoSuchAlgorithmException {
        // TODO
        return createNewSecret(null, NamedCertificateAuthority::new, secretPath, caSetRequestTranslator, parsedRequest);
      }

      @Override
      public NamedSecret ssh(NamedSecret namedSecret) throws NoSuchAlgorithmException {
        return createNewSecret(null, NamedSshSecret::new, secretPath, rsaSshSetRequestTranslator, parsedRequest);
      }

      @Override
      public NamedSecret rsa(NamedSecret namedSecret) throws NoSuchAlgorithmException {
        return createNewSecret(null, NamedRsaSecret::new, secretPath, rsaSshSetRequestTranslator, parsedRequest);
      }
    };
  }
}
