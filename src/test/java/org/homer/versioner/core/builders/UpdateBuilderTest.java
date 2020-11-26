package org.homer.versioner.core.builders;

import org.homer.versioner.core.procedure.Update;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.logging.Log;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;

/**
 * DiffBuilderTest class, it contains all the method used to test procedure builders
 */
public class UpdateBuilderTest {
    @Test
    public void shouldBuildCorrectProcedureInstance() {
        Transaction transaction = mock(Transaction.class);
        Log log = mock(Log.class);

        Optional<Update> result = new UpdateBuilder().withTransaction(transaction).withLog(log).build();

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().transaction, is(transaction));
        assertThat(result.get().log, is(log));
    }
}
