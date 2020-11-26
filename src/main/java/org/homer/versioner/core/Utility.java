package org.homer.versioner.core;

import org.homer.versioner.core.exception.VersionerCoreException;
import org.homer.versioner.core.output.NodeOutput;
import org.homer.versioner.core.output.RelationshipOutput;
import org.homer.versioner.core.procedure.RelationshipProcedure;
import org.neo4j.graphdb.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Utility class, it contains some common utility methods and constants
 */
public class Utility {

    public static final String STATE_LABEL = "State";
    public static final String CURRENT_TYPE = "CURRENT";
    public static final String HAS_STATE_TYPE = "HAS_STATE";
    public static final String PREVIOUS_TYPE = "PREVIOUS";
    public static final String ROLLBACK_TYPE = "ROLLBACK";
    public static final String FOR_TYPE = "FOR";
    public static final String DATE_PROP = "date";
    public static final String START_DATE_PROP = "startDate";
    public static final String END_DATE_PROP = "endDate";
    public static final String LOGGER_TAG = "[graph-versioner] - ";
    /*   DIFF OPERATIONS   */
    public static final String DIFF_OPERATION_REMOVE = "REMOVE";
    public static final String DIFF_OPERATION_ADD = "ADD";
    public static final String DIFF_OPERATION_UPDATE = "UPDATE";
    public static final List<String> DIFF_OPERATIONS_SORTING = Arrays.asList(DIFF_OPERATION_REMOVE, DIFF_OPERATION_UPDATE, DIFF_OPERATION_ADD);
    public static final List<String> SYSTEM_RELS = Arrays.asList(CURRENT_TYPE, HAS_STATE_TYPE, PREVIOUS_TYPE, ROLLBACK_TYPE);

    /**
     * Sets a {@link Map} of properties to a {@link Node}
     *
     * @param node  passed node
     * @param props properties to be set
     * @return a node with properties
     */
    public static Node setProperties(Node node, Map<String, Object> props) {
        props.forEach(node::setProperty);
        return node;
    }

    /**
     * Sets a {@link List} of label names into a {@link Label[]}
     *
     * @param labelNames a {@link List} of label names
     * @return {@link Label[]}
     */
    public static Label[] asLabels(List<String> labelNames) {
        Label[] result;
        if (Objects.isNull(labelNames)) {
            result = new Label[0];
        } else {
            result = labelNames.stream().filter(Objects::nonNull).map(Label::label).toArray(Label[]::new);
        }
        return result;
    }

    public static Label[] asLabels(Iterable<Label> labelsIterable) {
        List<String> labelNames = new ArrayList<>();
        Spliterator<Label> labelsIterator = labelsIterable.spliterator();
        StreamSupport.stream(labelsIterator, false).forEach(label -> labelNames.add(label.name()));
        return asLabels(labelNames);
    }

    /**
     * Sets a {@link String} as a singleton Array {@link Label[]}
     *
     * @param labelName a {@link String} representing the unique label
     * @return {@link Label[]}
     */
    public static Label[] asLabels(String labelName) {
        return new Label[]{Label.label(labelName)};
    }

    /**
     * Creates a new node copying properties and asLabels form a given one
     *
     * @param transaction   a {@link GraphDatabaseService} representing the database where the node will be created
     * @param node a {@link Node} representing the node to clone
     * @return {@link Node}
     */
    public static Node cloneNode(Transaction transaction, Node node) {
        List<String> labelNames = new ArrayList<>();
        Spliterator<Label> labelsIterator = node.getLabels().spliterator();
        StreamSupport.stream(labelsIterator, false).forEach(label -> labelNames.add(label.name()));
        return setProperties(transaction.createNode(asLabels(labelNames)), node.getAllProperties());
    }

    /**
     * Updates an Entity node CURRENT State with the new current one, it will handle all the
     * HAS_STATE / CURRENT / PREVIOUS relationships creation/update
     *
     * @param entity              a {@link Node} representing the Entity
     * @param instantDate         the new current State date
     * @param currentRelationship a {@link Relationship} representing the current CURRENT relationship
     * @param currentState        a {@link Node} representing the current State
     * @param currentDate         the current State date
     * @param result              a {@link Node} representing the new current State
     * @return {@link Node}
     */
    public static Node currentStateUpdate(Node entity, LocalDateTime instantDate, Relationship currentRelationship, Node currentState, LocalDateTime currentDate, Node result) {
        // Creating PREVIOUS relationship between the current and the new State
        result.createRelationshipTo(currentState, RelationshipType.withName(PREVIOUS_TYPE)).setProperty(DATE_PROP, currentDate);

        // Updating the HAS_STATE rel for the current node, adding endDate
        currentState.getRelationships(Direction.INCOMING, RelationshipType.withName(HAS_STATE_TYPE))
                .forEach(hasStatusRel -> hasStatusRel.setProperty(END_DATE_PROP, instantDate));

        // Refactoring current relationship and adding the new ones
        currentRelationship.delete();

        // Connecting the new current state to the Entity
        addCurrentState(result, entity, instantDate);

        return result;
    }

    /**
     * Connects a new State {@link Node} as the Current one, to the given Entity
     *
     * @param state       a {@link Node} representing the new current State
     * @param entity      a {@link Node} representing the Entity
     * @param instantDate the new current State date
     */
    public static void addCurrentState(Node state, Node entity, LocalDateTime instantDate) {
        entity.createRelationshipTo(state, RelationshipType.withName(CURRENT_TYPE)).setProperty(DATE_PROP, instantDate);
        entity.createRelationshipTo(state, RelationshipType.withName(HAS_STATE_TYPE)).setProperty(START_DATE_PROP, instantDate);
    }

    /**
     * Checks if the given entity is related through the HAS_STATE relationship with the given node
     *
     * @param entity a {@link Node} representing the Entity
     * @param state  a {@link Node} representing the State
     * @return {@link Boolean} result
     */
    public static Boolean checkRelationship(Node entity, Node state) {
        Spliterator<Relationship> stateRelIterator = state.getRelationships(Direction.INCOMING, RelationshipType.withName(Utility.HAS_STATE_TYPE)).spliterator();

        Boolean check = StreamSupport.stream(stateRelIterator, false).map(hasStateRel -> {
            Node maybeEntity = hasStateRel.getStartNode();
            if (maybeEntity.getId() != entity.getId()) {
                throw new VersionerCoreException("Can't patch the given entity, because the given State is owned by another entity.");
            }
            return true;
        }).findFirst().orElseGet(() -> {
            throw new VersionerCoreException("Can't find any entity node relate to the given State.");
        });

        return check;
    }

    /**
     * Converts the nodes to a stream of {@link NodeOutput}
     *
     * @param nodes a {@link List} of {@link Node} that will be converted to stream and mapped into {@link NodeOutput}
     * @return {@link Stream} streamOfNodes
     */
    public static Stream<NodeOutput> streamOfNodes(Node... nodes) {
        return Stream.of(nodes).map(NodeOutput::new);
    }

    /**
     * Converts the relationships to a stream of {@link RelationshipOutput}
     *
     * @param relationships a {@link List} of {@link Relationship} that will be converted to stream and mapped into {@link RelationshipOutput}
     * @return
     */
    public static Stream<RelationshipOutput> streamOfRelationships(Relationship... relationships) {
        return Stream.of(relationships).map(RelationshipOutput::new);
    }

    /**
     * Sets the date to the current dateTime if it's null
     *
     * @param date a {@link LocalDateTime} representing the milliseconds of the date
     * @return {@link LocalDateTime} milliseconds of the processed date
     */
    public static LocalDateTime defaultToNow(LocalDateTime date) {
        return (date == null) ? convertEpochToLocalDateTime(Calendar.getInstance().getTimeInMillis()) : date;
    }

    /**
     * Checks that the given node is a Versioner Entity, otherwise throws an exception
     *
     * @param node the {@link Node} to check
     * @throws VersionerCoreException
     */
    public static void isEntityOrThrowException(Node node) throws VersionerCoreException {

        streamOfIterable(node.getRelationships(Direction.OUTGOING, RelationshipType.withName("CURRENT"))).findAny()
                .map(ignored -> streamOfIterable(node.getRelationships(Direction.INCOMING, RelationshipType.withName("R"))).findAny())
                .orElseThrow(() -> new VersionerCoreException("The given node is not a Versioner Core Entity"));
    }

    public static <T> Stream<T> streamOfIterable(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    public static List<String> getStateLabels(String label) {

        return Stream.of(Utility.STATE_LABEL, label)
                .filter(l -> Objects.nonNull(l) && !l.isEmpty())
                .collect(Collectors.toList());
    }

    public static Optional<Relationship> getCurrentRelationship(Node entity) {
        return streamOfIterable(entity.getRelationships(Direction.OUTGOING, RelationshipType.withName(CURRENT_TYPE)))
                .findFirst();
    }

    public static LocalDateTime convertEpochToLocalDateTime(Long epochDateTime) {
        return Instant.ofEpochMilli(epochDateTime).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    public static Boolean isSystemType(String type) {
        return SYSTEM_RELS.contains(type);
    }
}
