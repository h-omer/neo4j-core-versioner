package org.homer.versioner.core.core;

import org.neo4j.graphdb.Transaction;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;

/**
 * CoreProcedure abstract class, exteded by all the procedure classes that perform write/update to the database
 */
public abstract class CoreProcedure {

    @Context
    public Log log;

    @Context
    public Transaction transaction;

}
