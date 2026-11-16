package br.com.fiap.mecanica.billing.quote.infrastructure;

import java.util.Optional;

import br.com.fiap.mecanica.billing.quote.domain.Quote;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface QuoteRepository extends MongoRepository<Quote, String> {

    Optional<Quote> findByOsId(String osId);

    boolean existsByOsId(String osId);
}
