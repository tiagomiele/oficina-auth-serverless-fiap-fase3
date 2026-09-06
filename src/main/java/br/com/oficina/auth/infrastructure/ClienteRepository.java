package br.com.oficina.auth.infrastructure;

import br.com.oficina.auth.application.ClienteDirectory;
import br.com.oficina.auth.config.AuthConfig;
import br.com.oficina.auth.domain.Cpf;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;

public final class ClienteRepository implements ClienteDirectory {

  private static final String QUERY =
      """
      SELECT id_cliente
        FROM clientes
       WHERE documento_normalizado = ?
         AND tipo_documento = 'CPF'
         AND ativo = TRUE
      """;

  private final AuthConfig config;

  public ClienteRepository(AuthConfig config) {
    this.config = config;
  }

  @Override
  public Optional<Long> findActiveClientId(Cpf cpf) {
    try (var connection =
            DriverManager.getConnection(config.dbUrl(), config.dbUser(), config.dbPassword());
        var statement = connection.prepareStatement(QUERY)) {
      statement.setString(1, cpf.value());
      try (var result = statement.executeQuery()) {
        return result.next() ? Optional.of(result.getLong("id_cliente")) : Optional.empty();
      }
    } catch (SQLException exception) {
      throw new IllegalStateException("Falha ao consultar cliente", exception);
    }
  }
}
