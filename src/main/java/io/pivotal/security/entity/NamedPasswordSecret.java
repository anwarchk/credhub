package io.pivotal.security.entity;

import io.pivotal.security.controller.v1.PasswordGenerationParameters;
import io.pivotal.security.view.SecretKind;

import static io.pivotal.security.constants.EncryptionConstants.NONCE_SIZE;
import static io.pivotal.security.constants.UuidConstants.UUID_BYTES;

import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "PasswordSecret")
@DiscriminatorValue("password")
public class NamedPasswordSecret extends NamedStringSecret<NamedPasswordSecret> {

  @Column(length = 255 + NONCE_SIZE)
  private byte[] encryptedGenerationParameters;

  @Column(length = NONCE_SIZE)
  private byte[] parametersNonce;

  @Column(length = UUID_BYTES, columnDefinition = "VARBINARY")
  private UUID parameterEncryptionKeyUuid;

  public static final String SECRET_TYPE = "password";

  @SuppressWarnings("unused")
  public NamedPasswordSecret() {
  }

  public NamedPasswordSecret(String name) {
    super(name);
  }

  public byte[] getEncryptedGenerationParameters() {
    return encryptedGenerationParameters;
  }

  public NamedPasswordSecret setEncryptedGenerationParameters(byte[] encryptedGenerationParameters) {
    this.encryptedGenerationParameters = encryptedGenerationParameters;
    return this;
  }

  public byte[] getParametersNonce() {
    return parametersNonce;
  }

  public NamedPasswordSecret setParametersNonce(byte[] parametersNonce) {
    this.parametersNonce = parametersNonce;
    return this;
  }

  public PasswordGenerationParameters getGenerationParameters() {
    return SecretEncryptionHelperProvider.getInstance().retrieveGenerationParameters(this);
  }

  public NamedPasswordSecret setGenerationParameters(PasswordGenerationParameters generationParameters) {
    SecretEncryptionHelperProvider.getInstance().refreshEncryptedGenerationParameters(this, generationParameters);
    return this;
  }

  @Override
  public String getSecretType() {
    return SECRET_TYPE;
  }

  @Override
  void copyIntoImpl(NamedPasswordSecret copy) {
    copy.setEncryptedGenerationParameters(encryptedGenerationParameters);
    copy.setParametersNonce(parametersNonce);
    copy.setParameterEncryptionKeyUuid(parameterEncryptionKeyUuid);
  }

  @Override
  public SecretKind getKind() {
    return SecretKind.PASSWORD;
  }

  @Override
  public void setEncryptionKeyUuid(UUID encryptionKeyUuid) {
    super.setEncryptionKeyUuid(encryptionKeyUuid);

    if (parameterEncryptionKeyUuid == null) {
      setParameterEncryptionKeyUuid(encryptionKeyUuid);
    }
  }

  public UUID getParameterEncryptionKeyUuid() {
    return parameterEncryptionKeyUuid;
  }

  public void setParameterEncryptionKeyUuid(UUID parameterEncryptionKeyUuid) {
    this.parameterEncryptionKeyUuid = parameterEncryptionKeyUuid;
  }
}
