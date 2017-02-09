package io.pivotal.security.entity;


import io.pivotal.security.view.SecretKind;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class NamedRsaSshSecretData extends NamedSecretData<NamedRsaSshSecretData> {
  @Column(length = 7000)
  private String publicKey;

  public NamedRsaSshSecretData(String name) {
    super(name);
  }

  public String getPublicKey() {
    return publicKey;
  }

  public <T extends NamedRsaSshSecretData> T setPublicKey(String publicKey) {
    this.publicKey = publicKey;
    return (T) this;
  }

  public String getPrivateKey() {
    return SecretEncryptionHelperProvider.getInstance().retrieveClearTextValue(this);
  }

  public <T extends NamedRsaSshSecretData> T setPrivateKey(String privateKey) {
    SecretEncryptionHelperProvider.getInstance().refreshEncryptedValue(this, privateKey);
    return (T) this;
  }

  public abstract SecretKind getKind();

  @Override
  public void copyIntoImpl(NamedRsaSshSecretData copy) {
    copy.setPublicKey(getPublicKey());
  }
}
