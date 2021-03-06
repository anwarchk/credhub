package io.pivotal.security.request;

import static com.google.common.collect.Lists.newArrayList;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.pivotal.security.domain.Encryptor;
import io.pivotal.security.domain.NamedSecret;
import io.pivotal.security.exceptions.ParameterizedValidationException;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(name = "password", value = PasswordSetRequest.class),
    @JsonSubTypes.Type(name = "value", value = ValueSetRequest.class),
    @JsonSubTypes.Type(name = "certificate", value = CertificateSetRequest.class),
    @JsonSubTypes.Type(name = "json", value = JsonSetRequest.class),
    @JsonSubTypes.Type(name = "ssh", value = SshSetRequest.class),
    @JsonSubTypes.Type(name = "rsa", value = RsaSetRequest.class),
    @JsonSubTypes.Type(name = "user", value = UserSetRequest.class)
})
public abstract class BaseSecretSetRequest extends BaseSecretRequest {

  @JsonIgnore
  public abstract NamedSecret createNewVersion(NamedSecret existing, Encryptor encryptor);

  @Override
  public void validate() {
    super.validate();

    if (!isValidTypeForSet(getType())) {
      throw new ParameterizedValidationException("error.invalid_type_with_set_prompt");
    }
  }

  private boolean isValidTypeForSet(String type) {
    return newArrayList("password", "certificate", "rsa", "ssh", "value", "json").contains(type);
  }
}
