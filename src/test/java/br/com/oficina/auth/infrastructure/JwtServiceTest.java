package br.com.oficina.auth.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.oficina.auth.config.AuthConfig;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  @Test
  void issuesAndVerifiesClientToken() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var pair = generator.generateKeyPair();
    var config =
        new AuthConfig(
            null,
            null,
            null,
            pem(pair.getPrivate()),
            pem(pair.getPublic()),
            "oficina-auth-serverless",
            "oficina-backend",
            900);

    var claims = new JwtService(config).verify(new JwtService(config).issue(42L));

    assertEquals("42", claims.getSubject());
    assertEquals(42L, claims.get("client_id", Long.class));
    assertEquals("CLIENTE", claims.get("role", String.class));
  }

  private static String pem(PrivateKey key) {
    return "-----BEGIN PRIVATE KEY-----\n"
        + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(key.getEncoded())
        + "\n-----END PRIVATE KEY-----";
  }

  private static String pem(PublicKey key) {
    return "-----BEGIN PUBLIC KEY-----\n"
        + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(key.getEncoded())
        + "\n-----END PUBLIC KEY-----";
  }
}
