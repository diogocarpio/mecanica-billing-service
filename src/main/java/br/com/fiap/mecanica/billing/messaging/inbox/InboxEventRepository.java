package br.com.fiap.mecanica.billing.messaging.inbox;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface InboxEventRepository extends MongoRepository<InboxEvent, String> {
}
