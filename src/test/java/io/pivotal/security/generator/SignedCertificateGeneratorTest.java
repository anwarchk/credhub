package io.pivotal.security.generator;

import static com.greghaskins.spectrum.Spectrum.beforeEach;
import static com.greghaskins.spectrum.Spectrum.describe;
import static com.greghaskins.spectrum.Spectrum.it;
import static io.pivotal.security.helper.SpectrumHelper.getBouncyCastleProvider;
import static io.pivotal.security.helper.SpectrumHelper.injectMocks;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.greghaskins.spectrum.Spectrum;
import io.pivotal.security.domain.CertificateParameters;
import io.pivotal.security.request.CertificateGenerationParameters;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderResult;
import java.security.cert.CertStore;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaCertStoreBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.data.auditing.DateTimeProvider;

@RunWith(Spectrum.class)
public class SignedCertificateGeneratorTest {

  private static final String SEPARATE_ISSUER_PRINCIPAL_STRING =
      "OU=cool org,C=\"adsf asdf\",ST=\'my fav state\',O=foo\\,inc.";

  private final Instant now = Instant.now();
  private final Calendar nowCalendar = Calendar.getInstance();

  @Mock
  DateTimeProvider timeProvider;

  @Mock
  RandomSerialNumberGenerator serialNumberGenerator;
  SignedCertificateGenerator subject;
  private X509Certificate generatedCert;
  private KeyPair issuerKeyPair;
  private X500Name issuerDistinguishedName;
  private X500Name subjectDistinguishedName;
  private KeyPair certKeyPair;
  private PrivateKey issuerPrivateKey;
  private CertificateGenerationParameters inputParameters;
  private CertificateParameters generationParameters;
  private String isCa;
  private X509ExtensionUtils x509ExtensionUtils = mock(X509ExtensionUtils.class);
  private SubjectKeyIdentifier fakeSubjectKeyIdentifier = new SubjectKeyIdentifier(
      "what's up doc".getBytes());
  private X500Name fakeDn;

  {
    beforeEach(injectMocks(this));

    beforeEach(() -> {
      subject = new SignedCertificateGenerator(timeProvider, serialNumberGenerator,
          getBouncyCastleProvider(), x509ExtensionUtils);

      nowCalendar.setTime(Date.from(now));
      fakeDn = new X500Name("CN=my test cert,O=credhub");
      when(timeProvider.getNow()).thenReturn(nowCalendar);
      when(serialNumberGenerator.generate()).thenReturn(BigInteger.valueOf(12));
      when(x509ExtensionUtils.createSubjectKeyIdentifier(any()))
          .thenReturn(fakeSubjectKeyIdentifier);
    });

    final SuiteBuilder validCertificateSuite = (makeCert) -> () -> {
      describe("with or without alternative names", () -> {
        beforeEach(makeCert::run);

        it("is not null", () -> {
          assertNotNull(generatedCert);
        });

        it("the signature is valid", () -> {
          generatedCert.verify(issuerKeyPair.getPublic());
        });

        it("has the correct metadata", () -> {
          assertThat(new X500Name(generatedCert.getIssuerX500Principal().getName()),
              equalTo(issuerDistinguishedName));
          assertThat(new X500Name(generatedCert.getSubjectX500Principal().getName()),
              equalTo(subjectDistinguishedName));
        });

        it("is valid for the appropriate time range", () -> {
          assertThat(generatedCert.getNotBefore(),
              equalTo(Date.from(now.truncatedTo(ChronoUnit.SECONDS))));
          assertThat(generatedCert.getNotAfter(),
              equalTo(Date.from(now.plus(Duration.ofDays(10)).truncatedTo(ChronoUnit.SECONDS))));
        });

        it("has a random serial number", () -> {
          verify(serialNumberGenerator).generate();
          assertThat(generatedCert.getSerialNumber(), equalTo(BigInteger.valueOf(12)));
        });

        it("contains the public key", () -> {
          assertThat(generatedCert.getPublicKey(), equalTo(certKeyPair.getPublic()));
        });

        it("has no alterative names", () -> {
          assertThat(generatedCert.getExtensionValue(Extension.subjectAlternativeName.getId()),
              nullValue());
        });

        it("sets the correct basic constraints based on type parameter", () -> {
          assertEquals(convertDerBytesToString(
              generatedCert.getExtensionValue(Extension.basicConstraints.getId())), isCa);
        });

        it("contains the subject key identifier", () -> {
          byte[] actual = generatedCert.getExtensionValue(Extension.subjectKeyIdentifier.getId());
          byte[] expected = fakeSubjectKeyIdentifier.getKeyIdentifier();
          // four bit type field is added at the beginning as per RFC 5280
          assertThat(Arrays.copyOfRange(actual, 4, actual.length), equalTo(expected));
        });
      });

      describe("with key usages", () -> {
        beforeEach(() -> {
          inputParameters = new CertificateGenerationParameters();
          inputParameters.setOrganization("my-org");
          inputParameters.setState("NY");
          inputParameters.setCountry("USA");
          generationParameters = new CertificateParameters(inputParameters);
        });

        it("are supported", () -> {
          inputParameters.setKeyUsage(new String[]{"data_encipherment", "crl_sign"});
          generationParameters = new CertificateParameters(inputParameters);
          makeCert.run();

          // Booleans 3 and 6 should be on, everything else off. See getKeyUsage in X509Certificate.
          assertThat(generatedCert.getKeyUsage(),
              equalTo(new boolean[]{false, false, false, true, false, false, true, false, false}));

          assertThat(generatedCert.getCriticalExtensionOIDs(), hasItem(Extension.keyUsage.getId()));
        });

        it("has no key usage settings when no keys are provided", () -> {
          makeCert.run();

          assertThat(generatedCert.getExtensionValue(Extension.keyUsage.getId()), nullValue());
        });
      });

      describe("with extended key usages", () -> {
        beforeEach(() -> {
          inputParameters = new CertificateGenerationParameters();
          inputParameters.setOrganization("my-org");
          inputParameters.setState("NY");
          inputParameters.setCountry("USA");
          generationParameters = new CertificateParameters(inputParameters);
        });

        it("are supported", () -> {
          inputParameters.setExtendedKeyUsage(new String[]{"server_auth", "client_auth"});
          generationParameters = new CertificateParameters(inputParameters);

          makeCert.run();

          final String serverAuthOid = "1.3.6.1.5.5.7.3.1";
          final String clientAuthOid = "1.3.6.1.5.5.7.3.2";

          assertThat(generatedCert.getExtendedKeyUsage(), containsInAnyOrder(
              serverAuthOid,
              clientAuthOid
          ));

          assertThat(generatedCert.getNonCriticalExtensionOIDs(),
              hasItem(Extension.extendedKeyUsage.getId()));
        });

        it("has no extended key usage extension when no keys are provided", () -> {
          makeCert.run();

          assertThat(generatedCert.getExtensionValue(Extension.extendedKeyUsage.getId()),
              nullValue());
        });
      });


    };

    describe("a generated issuer-signed childCertificate", () -> {
      beforeEach(() -> {
        KeyPairGenerator generator = KeyPairGenerator
            .getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(1024); // doesn't matter for testing
        issuerKeyPair = generator.generateKeyPair();
        issuerPrivateKey = issuerKeyPair.getPrivate();

        certKeyPair = generator.generateKeyPair();
        inputParameters = new CertificateGenerationParameters();
        inputParameters.setCommonName("my test cert");
        inputParameters.setCountry("US");
        inputParameters.setState("CA");
        inputParameters.setOrganization("credhub");
        inputParameters.setDuration(10);
        isCa = "[]";
        subjectDistinguishedName = new X500Name("CN=my test cert,C=US,ST=CA,O=credhub");
        ;
        issuerDistinguishedName = new X500Name(SEPARATE_ISSUER_PRINCIPAL_STRING);
        generationParameters = new CertificateParameters(inputParameters);
      });

      final ThrowingRunnable makeCert = () -> {
        generatedCert = subject
            .getSignedByIssuer(issuerDistinguishedName, issuerPrivateKey, certKeyPair,
                generationParameters);
      };

      describe("must behave like", validCertificateSuite.build(makeCert));

      it("is part of a trust chain with the ca", () -> {
        makeCert.run();
        final X509CertSelector target = new X509CertSelector();
        target.setCertificate(generatedCert);

        final TrustAnchor trustAnchor = new TrustAnchor(SEPARATE_ISSUER_PRINCIPAL_STRING,
            issuerKeyPair.getPublic(), null);
        final PKIXBuilderParameters builderParameters = new PKIXBuilderParameters(
            Collections.singleton(trustAnchor), target);

        final CertStore certStore = new JcaCertStoreBuilder()
            .addCertificate(new X509CertificateHolder(generatedCert.getEncoded()))
            .build();

        builderParameters.addCertStore(certStore);
        builderParameters.setRevocationEnabled(false);

        final CertPathBuilder certPathBuilder = CertPathBuilder
            .getInstance("PKIX", BouncyCastleProvider.PROVIDER_NAME);
        final CertPathBuilderResult builderResult = certPathBuilder.build(builderParameters);
        builderResult.getCertPath();
      });
    });

    describe("a self-signed certificate", () -> {
      beforeEach(() -> {
        KeyPairGenerator generator = KeyPairGenerator
            .getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(1024); // doesn't matter for testing
        certKeyPair = generator.generateKeyPair();

        issuerKeyPair = certKeyPair; // self-signed
        inputParameters = new CertificateGenerationParameters();
        inputParameters.setCommonName("my test cert");
        inputParameters.setOrganization("credhub");
        inputParameters.setDuration(10);
        isCa = "[]";

        // subject and issuer have same name-- we're self-signed.
        subjectDistinguishedName = new X500Name("CN=my test cert,O=credhub");
        issuerDistinguishedName = subjectDistinguishedName;
        generationParameters = new CertificateParameters(inputParameters);
      });

      final ThrowingRunnable makeCert = () -> {
        generatedCert = subject.getSelfSigned(certKeyPair, generationParameters);
      };

      describe("must behave like", validCertificateSuite.build(makeCert));
    });

    describe("a generated self-signed CA", () -> {
      beforeEach(() -> {
        KeyPairGenerator generator = KeyPairGenerator
            .getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(1024); // doesn't matter for testing
        issuerKeyPair = generator.generateKeyPair();

        certKeyPair = issuerKeyPair; // self-signed
        inputParameters = new CertificateGenerationParameters();
        inputParameters.setCommonName("my test ca");
        inputParameters.setCountry("US");
        inputParameters.setState("CA");
        inputParameters.setOrganization("credhub");
        inputParameters.setDuration(10);
        inputParameters.setIsCa(true);
        isCa = "[TRUE]";
        subjectDistinguishedName = new X500Name("CN=my test ca,C=US,ST=CA,O=credhub");
        issuerDistinguishedName = subjectDistinguishedName;
        generationParameters = new CertificateParameters(inputParameters);
      });

      ThrowingRunnable makeCert = () -> {
        generatedCert = subject.getSelfSigned(certKeyPair, generationParameters);
      };

      describe("must behave like", validCertificateSuite.build(makeCert));
    });
  }

  private String convertDerBytesToString(byte[] data) {
    try {
      DEROctetString derOctetString = (DEROctetString) bytesToDerConversion(data);
      return bytesToDerConversion(derOctetString.getOctets()).toString();
    } catch (Exception e) {
      return "";
    }
  }

  private ASN1Primitive bytesToDerConversion(byte[] data) throws IOException {
    return data == null ? null : new ASN1InputStream(new ByteArrayInputStream(data)).readObject();
  }

  interface ThrowingRunnable {

    void run() throws Exception;
  }

  interface SuiteBuilder {

    Spectrum.Block build(ThrowingRunnable makeCert);
  }
}
