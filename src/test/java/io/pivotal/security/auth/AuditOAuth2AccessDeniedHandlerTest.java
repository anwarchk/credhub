package io.pivotal.security.auth;

import com.greghaskins.spectrum.Spectrum;
import io.pivotal.security.CredentialManagerApp;
import io.pivotal.security.entity.RequestAuditRecord;
import io.pivotal.security.repository.RequestAuditRecordRepository;
import io.pivotal.security.service.SecurityEventsLogService;
import io.pivotal.security.util.CurrentTimeProvider;
import io.pivotal.security.util.DatabaseProfileResolver;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.token.ResourceServerTokenServices;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.Map;

import static com.greghaskins.spectrum.Spectrum.beforeEach;
import static com.greghaskins.spectrum.Spectrum.describe;
import static com.greghaskins.spectrum.Spectrum.it;
import static io.pivotal.security.controller.v1.secret.SecretsController.API_V1_DATA;
import static io.pivotal.security.helper.SpectrumHelper.mockOutCurrentTimeProvider;
import static io.pivotal.security.helper.SpectrumHelper.wireAndUnwire;
import static io.pivotal.security.util.AuthConstants.INVALID_SCOPE_KEY_JWT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.verify;
import static org.springframework.data.domain.Sort.Direction.DESC;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@RunWith(Spectrum.class)
@ActiveProfiles(value = {"unit-test"}, resolver = DatabaseProfileResolver.class)
@SpringBootTest(classes = CredentialManagerApp.class)
public class AuditOAuth2AccessDeniedHandlerTest {

  private final String credentialUrlPath = "/api/v1/data?name=foo";
  private final String credentialUrlQueryParams = "&query=value";
  private final String credentialUrl = String.join("", credentialUrlPath, credentialUrlQueryParams);
  @Autowired
  WebApplicationContext applicationContext;
  @Autowired
  RequestAuditRecordRepository requestAuditRecordRepository;
  @Autowired
  ResourceServerTokenServices tokenServices;
  @MockBean
  CurrentTimeProvider currentTimeProvider;
  @MockBean
  SecurityEventsLogService securityEventsLogService;
  private MockHttpServletRequestBuilder get;
  private MockMvc mockMvc;

  private final Instant now = Instant.ofEpochSecond(1490903353);

  {
    wireAndUnwire(this);

    beforeEach(() -> {
      mockOutCurrentTimeProvider(currentTimeProvider).accept(now.toEpochMilli());

      mockMvc = MockMvcBuilders
          .webAppContextSetup(applicationContext)
          .apply(springSecurity())
          .build();
    });

    describe("when the scope is invalid", () -> {
      beforeEach(() -> {
        String bearer = "Bearer " + INVALID_SCOPE_KEY_JWT;
        get = get(credentialUrl)
            .header("Authorization", bearer)
            .header("X-Forwarded-For", "1.1.1.1,2.2.2.2")
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .with(request -> {
              request.setRemoteAddr("12346");
              return request;
            });
        mockMvc.perform(get);
      });

      it("should log the failure in the request_audit_record table", () -> {
        RequestAuditRecord auditRecord = requestAuditRecordRepository.findAll(new Sort(DESC, "now")).get(0);

        assertThat(auditRecord.getNow(), equalTo(now));
        assertThat(auditRecord.getPath(), equalTo(API_V1_DATA));
        assertThat(auditRecord.getQueryParameters(), equalTo("name=foo&query=value"));
        assertThat(auditRecord.getRequesterIp(), equalTo("12346"));
        assertThat(auditRecord.getXForwardedFor(), equalTo("1.1.1.1,2.2.2.2"));

        OAuth2AccessToken accessToken = tokenServices
            .readAccessToken(INVALID_SCOPE_KEY_JWT);
        Map<String, Object> additionalInformation = accessToken.getAdditionalInformation();

        assertThat(auditRecord.getUserId(), equalTo(additionalInformation.get("user_id")));
        assertThat(auditRecord.getUserName(), equalTo(additionalInformation.get("user_name")));
        assertThat(auditRecord.getUaaUrl(), equalTo(additionalInformation.get("iss")));
        assertThat(auditRecord.getAuthValidFrom(), equalTo(
            ((Number) additionalInformation.get("iat")).longValue())); // 2737304753L (year 2056)
        assertThat(auditRecord.getAuthValidUntil(), equalTo(
            accessToken.getExpiration().toInstant().getEpochSecond())); // 2737304773L (year 2056)
        assertThat(auditRecord.getClientId(), equalTo("credhub_cli"));
        assertThat(auditRecord.getScope(), equalTo("credhub.bad_scope"));
        assertThat(auditRecord.getGrantType(), equalTo("password"));
        assertThat(auditRecord.getMethod(), equalTo("GET"));
        assertThat(auditRecord.getStatusCode(), equalTo(403));
      });

      it("should log the failure in the CEF syslog file", () -> {
        verify(securityEventsLogService).log(isA(RequestAuditRecord.class));
      });
    });
  }
}
