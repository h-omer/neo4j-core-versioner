package org.homer.versioner.core.procedure;

import org.homer.versioner.core.builders.GetBuilder;
import org.homer.versioner.core.core.CoreProcedure;
import org.homer.versioner.core.Utility;
import org.homer.versioner.core.output.NodeOutput;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.homer.versioner.core.Utility.defaultToNow;
import static org.homer.versioner.core.procedure.Update.connectStateToRs;
import static org.neo4j.procedure.Mode.WRITE;

/**
 * Rollback class, it contains all the Procedures needed to rollback Entities' States nodes in the database
 */
public class Rollback extends CoreProcedure {

    @Procedure(value = "graph.versioner.rollback", mode = WRITE)
    @Description("graph.versioner.rollback(entity, date) - Rollback the given Entity to its previous State")
    public Stream<NodeOutput> rollback(
            @Name("entity") Node entity,
            @Name(value = "date", defaultValue = "null") LocalDateTime date) {

        LocalDateTime instantDate = defaultToNow(date);

        // Getting the CURRENT rel if it exists
        Spliterator<Relationship> currentRelIterator = entity.getRelationships(Direction.OUTGOING, RelationshipType.withName(Utility.CURRENT_TYPE)).spliterator();
        Optional<Relationship> currentRelationshipOptional = StreamSupport.stream(currentRelIterator, false).filter(Objects::nonNull).findFirst();

        Optional<Node> newState = currentRelationshipOptional.map(currentRelationship -> {

            Node currentState = currentRelationship.getEndNode();

            return getFirstAvailableRollbackNode(currentState).map(rollbackState -> {
                LocalDateTime currentDate = (LocalDateTime) currentRelationship.getProperty("date");

                // Creating the rollback state, from the previous one
                Node result = Utility.cloneNode(transaction, rollbackState);

                //Creating ROLLBACK_TYPE relationship
                result.createRelationshipTo(rollbackState, RelationshipType.withName(Utility.ROLLBACK_TYPE));

                // Updating CURRENT state
                result = Utility.currentStateUpdate(entity, instantDate, currentRelationship, currentState, currentDate, result);

                //Copy all the relationships
                connectStateToRs(rollbackState, result);

                log.info(Utility.LOGGER_TAG + "Rollback executed for Entity with id {}, adding a State with id {}", entity.getId(), result.getId());
                return Optional.of(result);
            }).orElseGet(() -> {
                log.info(Utility.LOGGER_TAG + "Failed rollback for Entity with id {}, only one CURRENT State available", entity.getId());
                return Optional.empty();
            });
        }).orElseGet(() -> {
            log.info(Utility.LOGGER_TAG + "Failed rollback for Entity with id {}, there is no CURRENT State available", entity.getId());
            return Optional.empty();
        });

        return newState.map(Utility::streamOfNodes).orElse(Stream.empty());
    }

    @Procedure(value = "graph.versioner.rollback.to", mode = WRITE)
    @Description("graph.versioner.rollback.to(entity, state, date) - Rollback the given Entity to the given State")
    public Stream<NodeOutput> rollbackTo(
            @Name("entity") Node entity,
            @Name("state") Node state,
            @Name(value = "date", defaultValue = "null") LocalDateTime date) {

        LocalDateTime instantDate = defaultToNow(date);
        Optional<Node> newState = Optional.empty();

        Utility.checkRelationship(entity, state);

        // If the given State is the CURRENT one, null must be returned
        Spliterator<Relationship> currentRelIterator = state.getRelationships(Direction.INCOMING, RelationshipType.withName(Utility.CURRENT_TYPE)).spliterator();
        Optional<Relationship> currentRelationshipOptional = StreamSupport.stream(currentRelIterator, false).filter(Objects::nonNull).findFirst();
        if (!currentRelationshipOptional.isPresent()) {
            // If the given State already has a ROLLBACK relationship, null must be returned
            Spliterator<Relationship> rollbackRelIterator = state.getRelationships(Direction.OUTGOING, RelationshipType.withName(Utility.ROLLBACK_TYPE)).spliterator();
            Optional<Relationship> rollbackRelationshipOptional = StreamSupport.stream(rollbackRelIterator, false).filter(Objects::nonNull).findFirst();
            if (!rollbackRelationshipOptional.isPresent()) {
                // Otherwise, the node can be rolled back
                currentRelIterator = entity.getRelationships(Direction.OUTGOING, RelationshipType.withName(Utility.CURRENT_TYPE)).spliterator();
                currentRelationshipOptional = StreamSupport.stream(currentRelIterator, false).filter(Objects::nonNull).findFirst();

                newState = currentRelationshipOptional.map(currentRelationship -> {
                    Node currentState = currentRelationship.getEndNode();
                    LocalDateTime currentDate = (LocalDateTime) currentRelationship.getProperty("date");

                    // Creating the rollback state, from the previous one
                    Node result = Utility.cloneNode(transaction, state);

                    //Creating ROLLBACK_TYPE relationship
                    result.createRelationshipTo(state, RelationshipType.withName(Utility.ROLLBACK_TYPE));

                    // Updating CURRENT state
                    result = Utility.currentStateUpdate(entity, instantDate, currentRelationship, currentState, currentDate, result);

                    //Copy all the relationships
                    connectStateToRs(state, result);

                    log.info(Utility.LOGGER_TAG + "Rollback executed for Entity with id {}, adding a State with id {}", entity.getId(), result.getId());

                    return Optional.of(result);
                }).orElseGet(() -> {
                    log.info(Utility.LOGGER_TAG + "Failed rollback for Entity with id {}, there is no CURRENT State available", entity.getId());
                    return Optional.empty();
                });
            }
        }

        return newState.map(Utility::streamOfNodes).orElse(Stream.empty());
    }

    /**
     * This method returns the first available State node, by a given State node to rollback
     *
     * @param state state to rollback
     * @return the first available rollback node
     */
    private Optional<Node> getFirstAvailableRollbackNode(Node state) {
        return StreamSupport.stream(state.getRelationships(Direction.OUTGOING, RelationshipType.withName(Utility.ROLLBACK_TYPE)).spliterator(), false).findFirst()
                // Recursive iteration for ROLLBACKed State node
                .map(e -> getFirstAvailableRollbackNode(e.getEndNode()))
                // No ROLLBACK relationship found
                .orElse(StreamSupport.stream(state.getRelationships(Direction.OUTGOING, RelationshipType.withName(Utility.PREVIOUS_TYPE)).spliterator(), false)
                        .findFirst().map(Relationship::getEndNode));
    }

    @Procedure(value = "graph.versioner.rollback.nth", mode = WRITE)
    @Description("graph.versioner.rollback.nth(entity, nth-state, date) - Rollback the given Entity to the nth previous State")
    public Stream<NodeOutput> rollbackNth(
            @Name("entity") Node entity,
            @Name("state") long nthState,
            @Name(value = "date", defaultValue = "null") LocalDateTime date) {

        LocalDateTime instantDate = defaultToNow(date);

        return new GetBuilder().build()
                .flatMap(get -> get.getNthState(entity, nthState).findFirst())
                .map(state -> rollbackTo(entity, state.node, instantDate))
                .orElse(Stream.empty());
    }
}

