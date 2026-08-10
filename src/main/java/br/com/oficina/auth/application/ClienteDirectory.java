package br.com.oficina.auth.application;

import br.com.oficina.auth.domain.Cpf;
import java.util.Optional;

public interface ClienteDirectory {

  Optional<Long> findActiveClientId(Cpf cpf);
}
