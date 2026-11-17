package br.com.fiap.mecanica.billing.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

class MongoTransactionConfigurationTest {

    @Test
    void shouldProvideMongoTransactionManager() {
        MongoDatabaseFactory factory = mock(MongoDatabaseFactory.class);

        MongoTransactionManager manager =
                new MongoTransactionConfiguration().mongoTransactionManager(factory);

        assertThat(manager).isNotNull();
    }
}
