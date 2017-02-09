package io.pivotal.security.mapper;

import com.greghaskins.spectrum.Spectrum;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.ParseContext;
import io.pivotal.security.CredentialManagerApp;
import io.pivotal.security.entity.NamedValueSecretData;
import io.pivotal.security.util.DatabaseProfileResolver;
import io.pivotal.security.view.ParameterizedValidationException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static com.greghaskins.spectrum.Spectrum.beforeEach;
import static com.greghaskins.spectrum.Spectrum.describe;
import static com.greghaskins.spectrum.Spectrum.it;
import static io.pivotal.security.helper.SpectrumHelper.itThrowsWithMessage;
import static io.pivotal.security.helper.SpectrumHelper.wireAndUnwire;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

@RunWith(Spectrum.class)
@ActiveProfiles(value = "unit-test", resolver = DatabaseProfileResolver.class)
@SpringBootTest(classes = CredentialManagerApp.class)
public class StringSetRequestTranslatorTest {

  @Autowired
  private ParseContext jsonPath;

  private StringSetRequestTranslator subject;

  private NamedValueSecretData entity;

  {
    wireAndUnwire(this, false);

    describe("populating entity from JSON", () -> {
      beforeEach(() -> {
        subject = new StringSetRequestTranslator();
        entity = new NamedValueSecretData("rick");
      });

      it("fills in entity with values from JSON", () -> {
        String requestJson = "{\"type\":\"value\",\"value\":\"myValue\"}";

        DocumentContext parsed = jsonPath.parse(requestJson);
        subject.populateEntityFromJson(entity, parsed);
        assertThat(entity.getValue(), equalTo("myValue"));
      });

      itThrowsWithMessage("exception when empty value is given", ParameterizedValidationException.class, "error.missing_string_secret_value", () -> {
        String requestJson = "{\"type\":\"value\",\"value\":\"\"}";
        DocumentContext parsed = jsonPath.parse(requestJson);
        subject.populateEntityFromJson(entity, parsed);
      });

      itThrowsWithMessage("exception when value is omitted", ParameterizedValidationException.class, "error.missing_string_secret_value", () -> {
        String requestJson = "{\"type\":\"value\"}";
        DocumentContext parsed = jsonPath.parse(requestJson);
        subject.populateEntityFromJson(entity, parsed);
      });
    });
  }
}
