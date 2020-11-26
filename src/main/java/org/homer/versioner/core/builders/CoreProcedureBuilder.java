package org.homer.versioner.core.builders;

import org.homer.versioner.core.core.CoreProcedure;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.logging.Log;

import java.util.Optional;

/**
 * CoreProcedureBuilder abstract class, extended by all procedure builders
 *
 * @param <T>
 */
public abstract class CoreProcedureBuilder<T extends CoreProcedure> {

    private Class<T> clazz;

    private Transaction transaction;
    private Log log;

    /**
     * Constructor method
     *
     * @param clazz
     */
    public CoreProcedureBuilder(Class<T> clazz) {
        this.clazz = clazz;
    }

    /**
     * Adds a {@link GraphDatabaseService} and returns the current builder
     *
     * @param transaction
     * @return this
     */
    public CoreProcedureBuilder<T> withTransaction(Transaction transaction) {
        this.transaction = transaction;
        return this;
    }

    /**
     * Adds a {@link Log} and returns the current builder
     *
     * @param log
     * @return this
     */
    public CoreProcedureBuilder<T> withLog(Log log) {
        this.log = log;
        return this;
    }

    /**
     * It builds the procedure
     *
     * @return procedure
     */
    public abstract Optional<T> build();

    /**
     * It returns a new instance of the current builder
     *
     * @return instance
     */
    Optional<T> instantiate() {
        T instance;
        try {
            instance = clazz.newInstance();
            instance.log = log;
            instance.transaction = transaction;
        } catch (InstantiationException | IllegalAccessException e) {
           instance = null;
           log.error(e.getMessage());
        }
        return Optional.ofNullable(instance);
    }

}
