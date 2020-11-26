package org.homer.versioner.core.procedure;

import org.homer.versioner.core.core.CoreProcedure;
import org.homer.versioner.core.exception.VersionerCoreException;
import org.homer.versioner.core.output.NodeOutput;
import org.neo4j.graphdb.*;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.homer.versioner.core.Utility.*;

/**
 * Update class, it contains all the Procedures needed to update Entities' States
 */
public class Update extends CoreProcedure {

    @Procedure(value = "graph.versioner.update", mode = Mode.WRITE)
    @Description("graph.versioner.update(entity, {key:value,...}, additionalLabel, date) - Add a new State to the given Entity.")
    public Stream<NodeOutput> update(
            @Name("entity") Node entity,
            @Name(value = "stateProps", defaultValue = "{}") Map<String, Object> stateProps,
            @Name(value = "additionalLabel", defaultValue = "") String additionalLabel,
            @Name(value = "date", defaultValue = "null") LocalDateTime date) {

        // Creating the new State
        List<String> labelNames = new ArrayList<>(Collections.singletonList(STATE_LABEL));
        if (!additionalLabel.isEmpty()) {
            labelNames.add(additionalLabel);
        }
        Node result = setProperties(transaction.createNode(asLabels(labelNames)), stateProps);

        LocalDateTime instantDate = defaultToNow(date);

        // Getting the CURRENT rel if it exist
        Spliterator<Relationship> currentRelIterator = entity.getRelationships(Direction.OUTGOING, RelationshipType.withName(CURRENT_TYPE)).spliterator();
        StreamSupport.stream(currentRelIterator, false).forEach(currentRel -> {
            Node currentState = currentRel.getEndNode();

            LocalDateTime currentDate = (LocalDateTime) currentRel.getProperty("date");

            // Creating PREVIOUS relationship between the current and the new State
            result.createRelationshipTo(currentState, RelationshipType.withName(PREVIOUS_TYPE)).setProperty(DATE_PROP, currentDate);

            // Updating the HAS_STATE rel for the current node, adding endDate
            currentState.getRelationships(Direction.INCOMING, RelationshipType.withName(HAS_STATE_TYPE))
                    .forEach(hasStatusRel -> hasStatusRel.setProperty(END_DATE_PROP, instantDate));

            // Refactoring current relationship and adding the new ones
            currentRel.delete();

            // Connecting the new current state to Rs
            connectStateToRs(currentState, result);
        });

        // Connecting the new current state to the Entity
        addCurrentState(result, entity, instantDate);

        log.info(LOGGER_TAG + "Updated Entity with id {}, adding a State with id {}", entity.getId(), result.getId());

        return Stream.of(new NodeOutput(result));
    }

    @Procedure(value = "graph.versioner.patch", mode = Mode.WRITE)
    @Description("graph.versioner.patch(entity, {key:value,...}, additionalLabel, date) - Add a new State to the given Entity, starting from the previous one. It will update all the properties, not asLabels.")
    public Stream<NodeOutput> patch(
            @Name("entity") Node entity,
            @Name(value = "stateProps", defaultValue = "{}") Map<String, Object> stateProps,
            @Name(value = "additionalLabel", defaultValue = "") String additionalLabel,
            @Name(value = "date", defaultValue = "null") LocalDateTime date) {

        List<String> labelNames = getStateLabels(additionalLabel);
        LocalDateTime instantDate = defaultToNow(date);
        Optional<Relationship> currentRelationshipOpt = getCurrentRelationship(entity);

        // Creating the new current state
        Node newState = currentRelationshipOpt
                .map(currentRelationship -> createPatchedState(stateProps, labelNames, instantDate, currentRelationship))
                .orElseGet(() -> {
                    Node result = setProperties(transaction.createNode(asLabels(labelNames)), stateProps);
                    addCurrentState(result, entity, instantDate);
                    return result;
                });

        //Copy all the relationships
        currentRelationshipOpt.ifPresent(rel -> connectStateToRs(rel.getEndNode(), newState));

        log.info(LOGGER_TAG + "Patched Entity with id {}, adding a State with id {}", entity.getId(), newState.getId());

        return Stream.of(new NodeOutput(newState));
    }

    @Procedure(value = "graph.versioner.patch.from", mode = Mode.WRITE)
    @Description("graph.versioner.patch.from(entity, state, useCurrentRel, date) - Add a new State to the given Entity, starting from the given one. It will update all the properties, not asLabels. If useCurrentRel is false, it will replace the current rels to Rs with the state ones.")
    public Stream<NodeOutput> patchFrom(
            @Name("entity") Node entity,
            @Name("state") Node state,
            @Name(value = "useCurrentRel", defaultValue = "true") Boolean useCurrentRel,
            @Name(value = "date", defaultValue = "null") LocalDateTime date) {

        LocalDateTime instantDate = defaultToNow(date);
        List<String> labels = streamOfIterable(state.getLabels()).map(Label::name).collect(Collectors.toList());

        checkRelationship(entity, state);

        Optional<Relationship> currentRelationshipOpt = getCurrentRelationship(entity);

        Node newState = currentRelationshipOpt
                .map(currentRelationship -> createPatchedState(state.getAllProperties(), labels, instantDate, currentRelationship))
                .orElseThrow(() -> new VersionerCoreException("Can't find any current State node for the given entity."));

        //Copy all the relationships
        if (useCurrentRel) {
            currentRelationshipOpt.ifPresent(rel -> connectStateToRs(rel.getEndNode()   , newState));
        } else {
            connectStateToRs(state, newState);
        }

        log.info(LOGGER_TAG + "Patched Entity with id {}, adding a State with id {}", entity.getId(), newState.getId());

        return Stream.of(new NodeOutput(newState));
    }

    private Node createPatchedState(Map<String, Object> stateProps, List<String> labels, LocalDateTime instantDate, Relationship currentRelationship) {

        Node currentState = currentRelationship.getEndNode();
        LocalDateTime currentDate = (LocalDateTime) currentRelationship.getProperty("date");
        Node entity = currentRelationship.getStartNode();

        // Patching the current node into the new one.
        Map<String, Object> patchedProps = currentState.getAllProperties();
        patchedProps.putAll(stateProps);
        Node newStateToElaborate = setProperties(transaction.createNode(asLabels(labels)), patchedProps);

        // Updating CURRENT state
        return currentStateUpdate(entity, instantDate, currentRelationship, currentState, currentDate, newStateToElaborate);
    }

    protected static void connectStateToRs(Node sourceState, Node newState) {
        streamOfIterable(sourceState.getRelationships(Direction.OUTGOING))
                .filter(rel -> rel.getEndNode().hasLabel(Label.label("R")))
                .forEach(rel -> RelationshipProcedure.createRelationship(newState, rel.getEndNode(), rel.getType().name(), rel.getAllProperties()));
    }
}
