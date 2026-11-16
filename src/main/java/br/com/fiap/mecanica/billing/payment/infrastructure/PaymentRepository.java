package br.com.fiap.mecanica.billing.payment.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import br.com.fiap.mecanica.billing.payment.domain.Payment;
import br.com.fiap.mecanica.billing.payment.domain.PaymentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PaymentRepository extends MongoRepository<Payment, String> {

    Optional<Payment> findByQuoteId(String quoteId);

    Optional<Payment> findByOsId(String osId);

    List<Payment> findTop50ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            Collection<PaymentStatus> statuses,
            Instant updatedAt
    );
}
