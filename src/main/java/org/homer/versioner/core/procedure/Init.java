package org.homer.versioner.core.procedure;

import org.homer.versioner.core.core.CoreProcedure;
import org.homer.versioner.core.output.NodeOutput;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static org.homer.versioner.core.Utility.*;

/**
 * Init class, it contains all the Procedures needed to initialize an Entity node
 */
public class Init extends CoreProcedure {

    @Procedure(value = "graph.versioner.init", mode = Mode.WRITE)
    @Description("graph.versioner.init(entityLabel, {key:value,...}, {key:value,...}, additionalLabel, date) - Create an Entity node with an optional initial State.")
    public Stream<NodeOutput> init(
            @Name("entityLabel") String entityLabel,
            @Name(value = "entityProps", defaultValue = "{}") Map<String, Object> entityProps,
            @Name(value = "stateProps", defaultValue = "{}") Map<String, Object> stateProps,
            @Name(value = "additionalLabel", defaultValue = "") String additionalLabel,
            @Name(value = "date", defaultValue = "null") LocalDateTime date) {

        Node entity = createNode(entityProps, singletonList(entityLabel), transaction);

        Node state = createNode(stateProps, getStateLabels(additionalLabel), transaction);

        connectWithCurrentRelationship(entity, state, date);

        log.info(LOGGER_TAG + "Created a new Entity with label {} and id {}", entityLabel, entity.getId());

        createRNodeAndAssociateTo(entity, transaction);

        return streamOfNodes(entity);
    }

    private Node createNode(Map<String, Object> properties, List<String> labels, Transaction transaction)  {

        return setProperties(transaction.createNode(asLabels(labels)), properties);
    }

    private void connectWithCurrentRelationship(Node entity, Node state, LocalDateTime date) {

        LocalDateTime instantDate = (date == null) ? convertEpochToLocalDateTime(Calendar.getInstance().getTimeInMillis()) : date;
        addCurrentState(state, entity, instantDate);
    }

    private void createRNodeAndAssociateTo(Node entity, Transaction transaction) {

        Node rNode = transaction.createNode(Label.label("R"));
        rNode.createRelationshipTo(entity, RelationshipType.withName("FOR"));
    }
}
