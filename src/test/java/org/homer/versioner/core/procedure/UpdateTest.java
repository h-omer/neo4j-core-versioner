package org.homer.versioner.core.procedure;

import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.neo4j.driver.*;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.types.Node;
import org.neo4j.harness.junit.rule.Neo4jRule;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.homer.versioner.core.Utility.convertEpochToLocalDateTime;
import static org.junit.Assert.assertThat;

/**
 * UpdateTest class, it contains all the method used to test Update class methods
 */
public class UpdateTest extends GenericProcedureTest {
    @Rule
    public Neo4jRule neo4j = new Neo4jRule()

            // This is the function we want to test
            .withProcedure(Update.class);

    /*------------------------------*/
    /*            update            */
    /*------------------------------*/

    @Test
    public void shouldCreateANewStateWithoutAdditionalLabelAndDate() throws Throwable {
        // This is in a try-block, to make sure we close the driver after the test
        try (Driver driver = GraphDatabase
                .driver(neo4j.boltURI(), Config.builder().build()); Session session = driver.session()) {
            // Given
            session.run("CREATE (e:Entity {key:'immutableValue'})-[:CURRENT {date:localdatetime('1988-10-27T00:00:00')}]->(s:State {key:'initialValue'})");
            session.run("MATCH (e:Entity)-[:CURRENT]->(s:State) CREATE (e)-[:HAS_STATE {startDate:localdatetime('1988-10-27T00:00:00')}]->(s)");

            // When
            Result result = session.run("MATCH (e:Entity) WITH e CALL graph.versioner.update(e, {key:'newValue'}) YIELD node RETURN node");
            Result countStateResult = session.run("MATCH (s:State) RETURN count(s) as s");
            Result nextResult = session.run("MATCH (s1:State)-[:PREVIOUS]->(s2:State) return s2");
            Result correctStateResult = session.run("MATCH (s1:State)-[:PREVIOUS]->(s2:State) WITH s1 MATCH (e:Entity)-[:CURRENT]->(s1) return e");

            // Then
            assertThat(countStateResult.single().get("s").asLong(), equalTo(2L));
            assertThat(nextResult.single().get("s2").asNode().id(), equalTo(1L));
            assertThat(correctStateResult.single().get("e").asNode().id(), equalTo(0L));
        }
    }

    @Test
    public void shouldCreateANewStateWithAdditionalLabelButWithoutDate() throws Throwable {
        // This is in a try-block, to make sure we close the driver after the test
        try (Driver driver = GraphDatabase
                .driver(neo4j.boltURI(), Config.builder().build()); Session session = driver.session()) {
            // Given
            session.run("CREATE (e:Entity {key:'immutableValue'})-[:CURRENT {date:localdatetime('1988-10-27T00:00:00')}]->(s:State {key:'initialValue'})");
            session.run("MATCH (e:Entity)-[:CURRENT]->(s:State) CREATE (e)-[:HAS_STATE {startDate:localdatetime('1988-10-27T00:00:00')}]->(s)");

            // When
            Result result = session.run("MATCH (e:Entity) WITH e CALL graph.versioner.update(e, {key:'newValue'}, 'Error') YIELD node RETURN node");
            Result countStateResult = session.run("MATCH (s:State) RETURN count(s) as s");
            Result nextResult = session.run("MATCH (s1:State)-[:PREVIOUS]->(s2:State) return s2");
            Result correctStateResult = session.run("MATCH (s1:State)-[:PREVIOUS]->(s2:State) WITH s1 MATCH (e:Entity)-[:CURRENT]->(s1) return e");
            Result currentStateResult = session.run("MATCH (e:Entity)-[:CURRENT]->(s) return s");

            // Then
            assertThat(countStateResult.single().get("s").asLong(), equalTo(2L));
            assertThat(nextResult.single().get("s2").asNode().id(), equalTo(1L));
            assertThat(correctStateResult.single().get("e").asNode().id(), equalTo(0L));
            assertThat(currentStateResult.single().get("s").asNode().hasLabel("Error"), equalTo(true));
        }
    }

    @Test
    public void shouldCreateANewStateWithAdditionalLabelAndDate() throws Throwable {
        // This is in a try-block, to make sure we close the driver after the test
        try (Driver driver = GraphDatabase
                .driver(neo4j.boltURI(), Config.builder().build()); Session session = driver.session()) {
            // Given
            session.run("CREATE (e:Entity {key:'immutableValue'})-[:CURRENT {date:localdatetime('1988-10-27T00:00:00')}]->(s:State {key:'initialValue'})");
            session.run("MATCH (e:Entity)-[:CURRENT]->(s:State) CREATE (e)-[:HAS_STATE {startDate:localdatetime('1988-10-27T00:00:00')}]->(s)");

            // When
            Result result = session.run("MATCH (e:Entity) WITH e CALL graph.versioner.update(e, {key:'newValue'}, 'Error', localdatetime('1988-10-27T02:46:40')) YIELD node RETURN node");
            Result countStateResult = session.run("MATCH (s:State) RETURN count(s) as s");
            Result nextResult = session.run("MATCH (s1:State)-[:PREVIOUS]->(s2:State) return s2");
            Result correctStateResult = session.run("MATCH (s1:State)-[:PREVIOUS]->(s2:State) WITH s1 MATCH (e:Entity)-[:CURRENT]->(s1) return e");
            Result currentStateResult = session.run("MATCH (e:Entity)-[:CURRENT]->(s) return s");
            Result dateResult = session.run("MATCH (e:Entity)-[r:CURRENT]->(s) RETURN r.date as relDate");
            Result hasStatusDateResult = session.run("MATCH (e:Entity)-[:CURRENT]->(s:State)-[:PREVIOUS]->(s2:State)<-[rel:HAS_STATE]-(e) RETURN rel.endDate as endDate");

            // Then
            assertThat(countStateResult.single().get("s").asLong(), equalTo(2L));
            assertThat(nextResult.single().get("s2").asNode().id(), equalTo(1L));
            assertThat(correctStateResult.single().get("e").asNode().id(), equalTo(0L));
            assertThat(currentStateResult.single().get("s").asNode().hasLabel("Error"), equalTo(true));
            assertThat(dateResult.single().get("relDate").asLocalDateTime(), equalTo(convertEpochToLocalDateTime(593920000000L)));
            assertThat(hasStatusDateResult.single().get("endDate").asLocalDateTime(), equalTo(convertEpochToLocalDateTime(593920000000L)));
        }
    }

    @Test
    public void shouldCreateANewStateFromAnEntityWithoutAState() throws Throwable {
        // This is in a try-block, to make sure we close the driver after the test
        try (Driver driver = GraphDatabase
                .driver(neo4j.boltURI(), Config.builder().build()); Session session = driver.session()) {
            // Given
            session.run("CREATE (e:Entity {key:'immutableValue'})");

            // When
            Result result = session.run("MATCH (e:Entity) WITH e CALL graph.versioner.update(e, {key:'newValue'}, 'Error', localdatetime('1988-10-27T02:46:40')) YIELD node RETURN node");
            Result correctResult = session.run("MATCH (e:Entity)-[:CURRENT]->(s:State) RETURN id(s) as stateId");

            // Then
            assertThat(correctResult.single().get("stateId").asLong(), equalTo(1L));
        }
    }

    /*------------------------------*/
    /*             patch            */
	/*------------------------------*/

    @Test
    public void shouldCreateAndPatchANewStateWithoutAdditionalLabelAndDate() throws Throwable {
        // This is in a try-block, to make sure we close the driver after the test
        try (Driver driver = GraphDatabase
                .driver(neo4j.boltURI(), Config.builder().build()); Session session = driver.session()) {
            // Given
            session.run("CREATE (e:Entity {key:'immutableValue'})-[:CURRENT {date:localdatetime('1988-10-27T00:00:00')}]->(s:State {key:'initialValue'})");
            session.run("MATCH (e:Entity)-[:CURRENT]->(s:State) CREATE (e)-[:HAS_STATE {startDate:localdatetime('1988-10-27T00:00:00')}]->(s)");

            // When
            Result result = session.run("MATCH (e:Entity) WITH e CALL graph.versioner.patch(e, {key:'newValue', newKey:'newestValue'}) YIELD node RETURN node");
            Node currentState = result.single().get("node").asNode();
            Result countStateResult = session.run("MATCH (s:State) RETURN count(s) as s");
            Result nextResult = session.run("MATCH (s1:State)-[:PREVIOUS]->(s2:State) return s2");
            Result correctStateResult = session.run("MATCH (s1:State)-[:PREVIOUS]->(s2:State) WITH s1 MATCH (e:Entity)-[:CURRENT]->(s1) return e");

            // Then
            assertThat(countStateResult.single().get("s").asLong(), equalTo(2L));
            assertThat(nextResult.single().get("s2").asNode().id(), equalTo(1L));
            assertThat(correctStateResult.single().get("e").asNode().id(), equalTo(0L));
            assertThat(currentState.get("key").asString(), equalTo("newValue"));
            assertThat(currentState.get("newKey").asString(), equalTo("newestValue"));
        }
    }

    @Test
    public void shouldCreateAndPatchANewStateWithAdditionalLabelButWithoutDate() throws Throwable {
        // This is in a try-block, to make sure we close the driver after the test
        try (Driver driver = GraphDatabase
                .driver(neo4j.boltURI(), Config.builder().build()); Session session = driver.session()) {
            // Given
            session.run("CREATE (e:Entity {key:'immutableValue'})-[:CURRENT {date:localdatetime('1988-10-27T00:00:00')}]->(s:State {key:'initialValue'})");
            session.run("MATCH (e:Entity)-[:CURRENT]->(s:State) CREATE (e)-[:HAS_STATE {startDate:localdatetime('1988-10-27T00:00:00')}]->(s)");

            // When
            Result result = session.run("MATCH (e:Entity) WITH e CALL graph.versioner.patch(e, {key:'newValue', newKey:'newestValue'}, 'Error') YIELD node RETURN node");
            Node currentState = result.single().get("node").asNode();
            Result countStateResult = session.run("MATCH (s:State) RETURN count(s) as s");
            Result nextResult = session.run("MATCH (s1:State)-[:PREVIOUS]->(s2:State) return s2");
            Result correctStateResult = session.run("MATCH (s1:State)-[:PREVIOUS]->(s2:State) WITH s1 MATCH (e:Entity)-[:CURRENT]->(s1) return e");
            Result currentStateResult = session.run("MATCH (e:Entity)-[:CURRENT]->(s) return s");

            // Then
            assertThat(countStateResult.single().get("s").asLong(), equalTo(2L));
            assertThat(nextResult.single().get("s2").asNode().id(), equalTo(1L));
            assertThat(correctStateResult.single().get("e").asNode().id(), equalTo(0L));
            assertThat(currentStateResult.single().get("s").asNode().hasLabel("Error"), equalTo(true));
            assertThat(currentState.get("key").asString(), equalTo("newValue"));
            assertThat(currentState.get("newKey").asString(), equalTo("newestValue"));
        }
    }

    @Test
    public void shouldCreateAndPatchANewStateWithAdditionalLabelAndDate() throws Throwable {
        // This is in a try-block, to make sure we close the driver after the test
        try (Driver driver = GraphDatabase
                .driver(neo4j.boltURI(), Config.builder().build()); Session session = driver.session()) {
            // Given
            session.run("CREATE (e:Entity {key:'immutableValue'})-[:CURRENT {date:localdatetime('1988-10-27T00:00:00')}]->(s:State {key:'initialValue'})");
            session.run("MATCH (e:Entity)-[:CURRENT]->(s:State) CREATE (e)-[:HAS_STATE {startDate:localdatetime('1988-10-27T00:00:00')}]->(s)");

            // When
            Result result = session.run("MATCH (e:Entity) WITH e CALL graph.versioner.patch(e, {key:'newValue', newKey:'newestValue'}, 'Error', localdatetime('1988-10-27T02:46:40')) YIELD node RETURN node");
            Node currentState = result.single().get("node").asNode();
            Result countStateResult = session.run("MATCH (s:State) RETURN count(s) as s");
            Result nextResult = session.run("MATCH (s1:State)-[:PREVIOUS]->(s2:State) return s2");
            Result correctStateResult = session.run("MATCH (s1:State)-[:PREVIOUS]->(s2:State) WITH s1 MATCH (e:Entity)-[:CURRENT]->(s1) return e");
            Result currentStateResult = session.run("MATCH (e:Entity)-[:CURRENT]->(s) return s");
            Result dateResult = session.run("MATCH (e:Entity)-[r:CURRENT]->(s) RETURN r.date as relDate");
            Result hasStatusDateResult = session.run("MATCH (e:Entity)-[:CURRENT]->(s:State)-[:PREVIOUS]->(s2:State)<-[rel:HAS_STATE]-(e) RETURN rel.endDate as endDate");

            // Then
            assertThat(countStateResult.single().get("s").asLong(), equalTo(2L));
            assertThat(nextResult.single().get("s2").asNode().id(), equalTo(1L));
            assertThat(correctStateResult.single().get("e").asNode().id(), equalTo(0L));
            assertThat(currentStateResult.single().get("s").asNode().hasLabel("Error"), equalTo(true));
            assertThat(dateResult.single().get("relDate").asLocalDateTime(), equalTo(convertEpochToLocalDateTime(593920000000L)));
            assertThat(hasStatusDateResult.single().get("endDate").asLocalDateTime(), equalTo(convertEpochToLocalDateTime(593920000000L)));
            assertThat(currentState.get("key").asString(), equalTo("newValue"));
            assertThat(currentState.get("newKey").asString(), equalTo("newestValue"));
        }
    }

    @Test
    public void shouldCreateAndPatchANewStateWithAdditionalLabelAndDateButWithANewProp() throws Throwable {
        // This is in a try-block, to make sure we close the driver after the test
        try (Driver driver = GraphDatabase
                .driver(neo4j.boltURI(), Config.builder().build()); Session session = driver.session()) {
            // Given
            session.run("CREATE (e:Entity {key:'immutableValue'})-[:CURRENT {date:localdatetime('1988-10-27T00:00:00')}]->(s:State {key:'initialValue'})");
            session.run("MATCH (e:Entity)-[:CURRENT]->(s:State) CREATE (e)-[:HAS_STATE {startDate:localdatetime('1988-10-27T00:00:00')}]->(s)");

            // When
            Result result = session.run("MATCH (e:Entity) WITH e CALL graph.versioner.patch(e, {newKey:'newestValue'}, 'Error', localdatetime('1988-10-27T02:46:40')) YIELD node RETURN node");
            Node currentState = result.single().get("node").asNode();
            Result countStateResult = session.run("MATCH (s:State) RETURN count(s) as s");
            Result nextResult = session.run("MATCH (s1:State)-[:PREVIOUS]->(s2:State) return s2");
            Result correctStateResult = session.run("MATCH (s1:State)-[:PREVIOUS]->(s2:State) WITH s1 MATCH (e:Entity)-[:CURRENT]->(s1) return e");
            Result currentStateResult = session.run("MATCH (e:Entity)-[:CURRENT]->(s) return s");
            Result dateResult = session.run("MATCH (e:Entity)-[r:CURRENT]->(s) RETURN r.date as relDate");
            Result hasStatusDateResult = session.run("MATCH (e:Entity)-[:CURRENT]->(s:State)-[:PREVIOUS]->(s2:State)<-[rel:HAS_STATE]-(e) RETURN rel.endDate as endDate");

            // Then
            assertThat(countStateResult.single().get("s").asLong(), equalTo(2L));
            assertThat(nextResult.single().get("s2").asNode().id(), equalTo(1L));
            assertThat(correctStateResult.single().get("e").asNode().id(), equalTo(0L));
            assertThat(currentStateResult.single().get("s").asNode().hasLabel("Error"), equalTo(true));
            assertThat(dateResult.single().get("relDate").asLocalDateTime(), equalTo(convertEpochToLocalDateTime(593920000000L)));
            assertThat(hasStatusDateResult.single().get("endDate").asLocalDateTime(), equalTo(convertEpochToLocalDateTime(593920000000L)));
            assertThat(currentState.get("key").asString(), equalTo("initialValue"));
            assertThat(currentState.get("newKey").asString(), equalTo("newestValue"));
        }
    }

    @Test
    public void shouldCreateANewStateFromAnEntityWithoutAStateUsingPatch() throws Throwable {
        // This is in a try-block, to make sure we close the driver after the test
        try (Driver driver = GraphDatabase
                .driver(neo4j.boltURI(), Config.builder().build()); Session session = driver.session()) {
            // Given
            session.run("CREATE (e:Entity {key:'immutableValue'})");

            // When
            Result result = session.run("MATCH (e:Entity) WITH e CALL graph.versioner.patch(e, {key:'newValue'}, 'Error', localdatetime('1988-10-27T02:46:40')) YIELD node RETURN node");
            Result correctResult = session.run("MATCH (e:Entity)-[:CURRENT]->(s:State) RETURN id(s) as stateId");

            // Then
            assertThat(correctResult.single().get("stateId").asLong(), equalTo(1L));
        }
    }

    @Test
    public void shouldCreateACopyOfTheCurrentStateIfPatchedWithoutStateProps() throws Throwable {
        // This is in a try-block, to make sure we close the driver after the test
        try (Driver driver = GraphDatabase
                .driver(neo4j.boltURI(), Config.builder().build()); Session session = driver.session()) {
            // Given
            session.run("CREATE (e:Entity {key:'immutableValue'})-[:CURRENT {date:localdatetime('1988-10-27T00:00:00')}]->(s:State {key:'initialValue'})");
            session.run("MATCH (e:Entity)-[:CURRENT]->(s:State) CREATE (e)-[:HAS_STATE {startDate:localdatetime('1988-10-27T00:00:00')}]->(s)");
            Result stateResult = session.run("MATCH (s:State) RETURN s");
            Node state = stateResult.single().get("s").asNode();

            // When
            Result result = session.run("MATCH (e:Entity) WITH e CALL graph.versioner.patch(e) YIELD node RETURN node");

            // Then
            Node newState = result.single().get("node").asNode();
            assertThat(state.get("key"), equalTo(newState.get("key")));
            assertThat(state.size(), equalTo(newState.size()));
        }
    }

    @Test
    public void shouldMaintainTheRelationshipsFromTheCurrentState() throws Throwable {

        try (Driver driver = GraphDatabase
                .driver(neo4j.boltURI(), Config.builder().build()); Session session = driver.session()) {

            // Given
            Node entityA = initEntity(session);
            Node entityB = initEntity(session);

            String createEntityRelationshipQuery = "MATCH (a:Entity)-[:CURRENT]->(s: State), (b:Entity)<-[:FOR]-(r:R) WHERE id(a) = %d AND id(b) = %d CREATE (s)-[:testType]->(r)";
            session.run(String.format(createEntityRelationshipQuery, entityA.id(), entityB.id()));

            // When
            String patchQuery = "MATCH (e:Entity) WHERE id(e) = %d WITH e CALL graph.versioner.patch(e) YIELD node RETURN node";
            Result result = session.run(String.format(patchQuery, entityA.id()));

            // Then
            Assertions.assertThat(session.run(String.format("MATCH (a:Entity)-[:CURRENT]->(:State)-[:testType]->(:R)-[:FOR]->(b:Entity) WHERE id(a) = %d AND id(b) = %d RETURN a", entityA.id(), entityB.id())).list())
                    .hasSize(1);
        }
    }

    /*------------------------------*/
    /*          patch.from          */
	/*------------------------------*/

	@Test
    public void shouldCreateACopyOfTheGivenStateWithoutAdditionalDate() throws Throwable {
        // This is in a try-block, to make sure we close the driver after the test
        try (Driver driver = GraphDatabase
                .driver(neo4j.boltURI(), Config.builder().build()); Session session = driver.session()) {
            // Given
            session.run("CREATE (e:Entity {key:'immutableValue'})-[:CURRENT {date:localdatetime('1988-10-27T00:00:00')}]->(s:State {key:'initialValue', newKey:'oldestValue'})");
            session.run("MATCH (e:Entity {key:'immutableValue'})-[:CURRENT {date:localdatetime('1988-10-27T00:00:00')}]->(s:State {key:'initialValue'}) CREATE (e)-[:HAS_STATE {startDate:localdatetime('1988-10-27T00:00:00')}]->(s)");
            session.run("MATCH (e:Entity) CREATE (e)-[:HAS_STATE {startDate:localdatetime('1988-10-26T00:00:00'), endDate:localdatetime('1988-10-27T00:00:00')}]->(s:State:Test {newKey:'newestValue'})");
            session.run("MATCH (sc:State)<-[:CURRENT]-(e:Entity)-[:HAS_STATE]->(s:Test) CREATE (sc)-[:PREVIOUS {date:localdatetime('1988-10-26T00:00:00')}]->(s)");
            Result stateResult = session.run("MATCH (s:Test) RETURN s");
            Node originalState = stateResult.single().get("s").asNode();

            // When
            Result result = session.run("MATCH (e:Entity)-[:HAS_STATE]->(s:Test) WITH e, s CALL graph.versioner.patch.from(e, s) YIELD node RETURN node");
            Node currentState = result.single().get("node").asNode();
            Result countStateResult = session.run("MATCH (s:State) RETURN count(s) as s");
            Result correctStateResult = session.run("MATCH (s1:State)-[:PREVIOUS]->(s2:State) WITH s1 MATCH (e:Entity)-[:CURRENT]->(s1) return e");

            // Then
            assertThat(countStateResult.single().get("s").asLong(), equalTo(3L));
            assertThat(correctStateResult.single().get("e").asNode().id(), equalTo(0L));
            assertThat(currentState.get("key").asString(), equalTo("initialValue"));
            assertThat(currentState.get("newKey").asString(), equalTo("newestValue"));
        }
    }

    @Test
    public void shouldCreateACopyOfTheGivenStateWithAdditionalDate() throws Throwable {
        // This is in a try-block, to make sure we close the driver after the test
        try (Driver driver = GraphDatabase
                .driver(neo4j.boltURI(), Config.builder().build()); Session session = driver.session()) {
            // Given
            session.run("CREATE (e:Entity {key:'immutableValue'})-[:CURRENT {date:localdatetime('1988-10-27T00:00:00')}]->(s:State {key:'initialValue', newKey:'oldestValue'})");
            session.run("MATCH (e:Entity {key:'immutableValue'})-[:CURRENT {date:localdatetime('1988-10-27T00:00:00')}]->(s:State {key:'initialValue'}) CREATE (e)-[:HAS_STATE {startDate:localdatetime('1988-10-27T00:00:00')}]->(s)");
            session.run("MATCH (e:Entity) CREATE (e)-[:HAS_STATE {startDate:localdatetime('1988-10-26T00:00:00'), endDate:localdatetime('1988-10-27T00:00:00')}]->(s:State:Test {newKey:'newestValue'})");
            session.run("MATCH (sc:State)<-[:CURRENT]-(e:Entity)-[:HAS_STATE]->(s:Test) CREATE (sc)-[:PREVIOUS {date:localdatetime('1988-10-26T00:00:00')}]->(s)");
            Result stateResult = session.run("MATCH (s:Test) RETURN s");
            Node originalState = stateResult.single().get("s").asNode();

            // When
            Result result = session.run("MATCH (e:Entity)-[:HAS_STATE]->(s:Test) WITH e, s CALL graph.versioner.patch.from(e, s, false, localdatetime('1988-10-27T02:46:40')) YIELD node RETURN node");
            Node currentState = result.single().get("node").asNode();
            Result countStateResult = session.run("MATCH (s:State) RETURN count(s) as s");
            Result correctStateResult = session.run("MATCH (s1:State)-[:PREVIOUS]->(s2:State) WITH s1 MATCH (e:Entity)-[:CURRENT]->(s1) return e");
            Result dateResult = session.run("MATCH (e:Entity)-[r:CURRENT]->(s) RETURN r.date as relDate");
            Result hasStatusDateResult = session.run("MATCH (e:Entity)-[:CURRENT]->(s:State)-[:PREVIOUS]->(s2:State)<-[rel:HAS_STATE]-(e) RETURN rel.endDate as endDate");

            // Then
            assertThat(countStateResult.single().get("s").asLong(), equalTo(3L));
            assertThat(correctStateResult.single().get("e").asNode().id(), equalTo(0L));
            assertThat(currentState.get("key").asString(), equalTo("initialValue"));
            assertThat(currentState.get("newKey").asString(), equalTo("newestValue"));
            assertThat(dateResult.single().get("relDate").asLocalDateTime(), equalTo(convertEpochToLocalDateTime(593920000000L)));
            assertThat(hasStatusDateResult.single().get("endDate").asLocalDateTime(), equalTo(convertEpochToLocalDateTime(593920000000L)));
        }
    }

    @Test
    public void shouldCreateACopyOfTheGivenStateWithAdditionalDateButWithANewProp() throws Throwable {
        // This is in a try-block, to make sure we close the driver after the test
        try (Driver driver = GraphDatabase
                .driver(neo4j.boltURI(), Config.builder().build()); Session session = driver.session()) {
            // Given
            session.run("CREATE (e:Entity {key:'immutableValue'})-[:CURRENT {date:localdatetime('1988-10-27T00:00:00')}]->(s:State {key:'initialValue'})");
            session.run("MATCH (e:Entity {key:'immutableValue'})-[:CURRENT {date:localdatetime('1988-10-27T00:00:00')}]->(s:State {key:'initialValue'}) CREATE (e)-[:HAS_STATE {startDate:localdatetime('1988-10-27T00:00:00')}]->(s)");
            session.run("MATCH (e:Entity) CREATE (e)-[:HAS_STATE {startDate:localdatetime('1988-10-26T00:00:00'), endDate:localdatetime('1988-10-27T00:00:00')}]->(s:State:Test {newKey:'newestValue'})");
            session.run("MATCH (sc:State)<-[:CURRENT]-(e:Entity)-[:HAS_STATE]->(s:Test) CREATE (sc)-[:PREVIOUS {date:localdatetime('1988-10-26T00:00:00')}]->(s)");
            Result stateResult = session.run("MATCH (s:Test) RETURN s");
            Node originalState = stateResult.single().get("s").asNode();

            // When
            Result result = session.run("MATCH (e:Entity)-[:HAS_STATE]->(s:Test) WITH e, s CALL graph.versioner.patch.from(e, s, false,localdatetime('1988-10-27T02:46:40')) YIELD node RETURN node");
            Node currentState = result.single().get("node").asNode();
            Result countStateResult = session.run("MATCH (s:State) RETURN count(s) as s");
            Result correctStateResult = session.run("MATCH (s1:State)-[:PREVIOUS]->(s2:State) WITH s1 MATCH (e:Entity)-[:CURRENT]->(s1) return e");
            Result dateResult = session.run("MATCH (e:Entity)-[r:CURRENT]->(s) RETURN r.date as relDate");
            Result hasStatusDateResult = session.run("MATCH (e:Entity)-[:CURRENT]->(s:State)-[:PREVIOUS]->(s2:State)<-[rel:HAS_STATE]-(e) RETURN rel.endDate as endDate");

            // Then
            //assertThat(currentState.id(), equalTo(21L));
            assertThat(countStateResult.single().get("s").asLong(), equalTo(3L));
            assertThat(correctStateResult.single().get("e").asNode().id(), equalTo(0L));
            assertThat(currentState.get("key").asString(), equalTo("initialValue"));
            assertThat(currentState.get("newKey").asString(), equalTo("newestValue"));
            assertThat(dateResult.single().get("relDate").asLocalDateTime(), equalTo(convertEpochToLocalDateTime(593920000000L)));
            assertThat(hasStatusDateResult.single().get("endDate").asLocalDateTime(), equalTo(convertEpochToLocalDateTime(593920000000L)));
        }
    }

    @Test (expected = ClientException.class)
    public void shouldNotCreateACopyOfTheGivenStateSinceItsADifferentEntityState() throws Throwable {
        // This is in a try-block, to make sure we close the driver after the test
        try (Driver driver = GraphDatabase
                .driver(neo4j.boltURI(), Config.builder().build()); Session session = driver.session()) {
            // Given
            session.run("CREATE (e:Entity {key:'immutableValue'})-[:CURRENT {date:localdatetime('1988-10-27T00:00:00')}]->(s:State {key:'initialValue'})");
            session.run("MATCH (e:Entity {key:'immutableValue'})-[:CURRENT {date:localdatetime('1988-10-27T00:00:00')}]->(s:State {key:'initialValue'}) CREATE (e)-[:HAS_STATE {startDate:localdatetime('1988-10-27T00:00:00')}]->(s)");
            session.run("MATCH (e:Entity) CREATE (e)-[:HAS_STATE {startDate:localdatetime('1988-10-26T00:00:00'), endDate:localdatetime('1988-10-27T00:00:00')}]->(s:State:Test {key:'initialValue'})");
            session.run("MATCH (sc:State)<-[:CURRENT]-(e:Entity)-[:HAS_STATE]->(s:Test) CREATE (sc)-[:PREVIOUS {date:localdatetime('1988-10-26T00:00:00')}]->(s)");
            session.run("CREATE (e:EntityBis {key:'immutableValue'})-[:CURRENT {date:localdatetime('1988-10-27T00:00:00')}]->(s:State {key:'initialValue'})");
            session.run("MATCH (e:EntityBis)-[:CURRENT]->(s:State) CREATE (e)-[:HAS_STATE {startDate:localdatetime('1988-10-27T00:00:00')}]->(s)");

            Result result = session.run("MATCH (e:Entity), (:EntityBis)-[:HAS_STATE]->(s:State) WITH e, s CALL graph.versioner.patch.from(e, s) YIELD node RETURN node");
            Node currentState = result.single().get("node").asNode();
        }
    }
}
