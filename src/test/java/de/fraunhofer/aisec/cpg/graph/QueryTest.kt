package de.fraunhofer.aisec.cpg.graph

import de.fraunhofer.aisec.cpg.ExperimentalGraph
import de.fraunhofer.aisec.cpg.graph.declarations.FunctionDeclaration
import de.fraunhofer.aisec.cpg.graph.declarations.ParamVariableDeclaration
import de.fraunhofer.aisec.cpg.graph.edge.PropertyEdge
import de.fraunhofer.aisec.cpg.graph.types.UnknownType
import de.fraunhofer.aisec.cpg.helpers.Benchmark
import org.junit.jupiter.api.BeforeAll
import org.opencypher.v9_0.ast.Query
import org.opencypher.v9_0.parser.CypherParser
import java.util.stream.Collector
import java.util.stream.Collectors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds

@ExperimentalGraph
@ExperimentalTime
class QueryTest {
    @Test
    fun testQueryExistenceOfEdge() {
        val query = parser.parse("MATCH (n:FunctionDeclaration)-[:PARAMETERS]->(m:ParamVariableDeclaration) RETURN n", null) as Query

        var nodes: List<Node> = db.executeQuery(query)

        assertEquals(2, nodes.size)

        var b: Benchmark = Benchmark(QueryTest::class.java, "something else - stream version")
        val variables: MutableMap<String, MutableList<Node>> = mutableMapOf()
        variables["n"] = mutableListOf()
        variables["m"] = mutableListOf()

        variables["n"] = db.nodes.parallelStream()
                .filter { it is FunctionDeclaration }
                .filter {
                    val parameters = PropertyEdge.unwrap(it["parameters", "OUTGOING"] as List<PropertyEdge<Node>>)

                    val params = parameters.parallelStream().filter { it is ParamVariableDeclaration }.collect(Collectors.toList())

                    variables["m"]?.addAll(params)
                    params.isNotEmpty()
                }
                .collect(Collectors.toList())

        nodes = variables["n"]!!
        assertEquals(2, nodes.size)

        b.stop()
        println("Experimental query2 took: ${b.duration.milliseconds}")

        b = Benchmark(QueryTest::class.java, "something else")
        variables["n"] = mutableListOf()
        variables["m"] = mutableListOf()

        for(node in db.nodes) {
            if(node is FunctionDeclaration) {
                val parameters = PropertyEdge.unwrap(node["parameters", "OUTGOING"] as List<PropertyEdge<Node>>)

                for(parameter in parameters) {
                    if(parameter is ParamVariableDeclaration) {
                        variables["m"]?.plusAssign(parameter)
                    }
                }

                if(parameters.isNotEmpty()) {
                    variables["n"]?.plusAssign(node)
                }

            }
        }

        nodes = variables["n"]!!
        assertEquals(2, nodes.size)

        b.stop()
        println("Experimental query2 took: ${b.duration.milliseconds}")
    }

    @Test
    fun testQueryExistenceOfEdgeOtherVar() {
        val query = parser.parse("MATCH (n:FunctionDeclaration)-[:PARAMETERS]->(m:ParamVariableDeclaration) RETURN m", null) as Query

        val nodes = db.executeQuery(query)

        assertEquals(2, nodes.size)
    }

    @Test
    fun testQueryExistenceOfEdgeWithEquals() {
        val query = parser.parse("MATCH (n:FunctionDeclaration)-[:PARAMETERS]->(m:ParamVariableDeclaration) WHERE m.name = 'paramB' RETURN n", null) as Query

        val nodes = db.executeQuery(query)

        assertEquals(1, nodes.size)
        assertEquals(func2, nodes[0])
    }

    @Test
    fun testQueryWithSimpleProperty() {
        val query = parser.parse("MATCH (n:VariableDeclaration) WHERE n.name = 'myVar' RETURN n", null) as Query
        println(query)

        val nodes = db.executeQuery(query)

        assertEquals(1, nodes.size)
    }

    @Test
    fun testQueryAllNodes() {
        // should return all nodes
        val query = parser.parse("MATCH (n) RETURN n", null) as Query
        println(query)

        val nodes = db.executeQuery(query)

        assertEquals(db.size(), nodes.size)
    }

    @Test
    fun testQueryAllNodesWithEquals() {
        // should return all nodes
        val query = parser.parse("MATCH (n) WHERE 1=1 RETURN n", null) as Query
        println(query)

        val nodes = db.executeQuery(query)

        assertEquals(db.size(), nodes.size)
    }

    @Test
    fun testQueryLimit() {
        // should return all nodes
        val query = parser.parse("MATCH (n) RETURN n LIMIT 25", null) as Query
        println(query)

        val nodes = db.executeQuery(query)

        assertEquals(25, nodes.size)
    }

    @Test
    fun testQueryNoResult() {
        // should return no nodes
        val query = parser.parse("MATCH (n) WHERE 1='a' RETURN n", null) as Query
        println(query)

        val nodes = db.executeQuery(query)
        println(nodes)

        assertTrue(nodes.isEmpty())
    }

    @Test
    fun testQueryLesser() {
        // should return no nodes
        val query = parser.parse("MATCH (n) WHERE 1<0 RETURN n", null) as Query
        println(query)

        val nodes = db.executeQuery(query)
        println(nodes)

        assertTrue(nodes.isEmpty())
    }

    @Test
    fun testQueryGreaterThan() {
        // should return no nodes
        val query = parser.parse("MATCH (n) WHERE 0>1 RETURN n", null) as Query
        println(query)

        val nodes = db.executeQuery(query)
        println(nodes)

        assertTrue(nodes.isEmpty())
    }

    companion object {
        lateinit var db: Graph
        val parser = CypherParser()

        lateinit var func1: FunctionDeclaration
        lateinit var func2: FunctionDeclaration
        lateinit var func3: FunctionDeclaration

        @ExperimentalGraph
        @BeforeAll
        @JvmStatic
        fun before() {
            db = Graph(mutableListOf())
            db += NodeBuilder.newVariableDeclaration("myVar", UnknownType.getUnknownType(), "myVar", false)

            func1 = NodeBuilder.newFunctionDeclaration("func1", "private int func1() { return 1; }")
            func2 = NodeBuilder.newFunctionDeclaration("func2", "private int func2() { return 1; }")
            func3 = NodeBuilder.newFunctionDeclaration("func3", "private int func2() { return 1; }")
            val paramA = NodeBuilder.newMethodParameterIn("paramA", UnknownType.getUnknownType(), false, "paramA");
            val paramB = NodeBuilder.newMethodParameterIn("paramB", UnknownType.getUnknownType(), false, "paramB");
            func1.addParameter(paramA)
            func2.addParameter(paramB)

            // create some dummy nodes to make queries a little bit slower
            for (i in 0..10000) {
                db += NodeBuilder.newVariableDeclaration("var${i}", UnknownType.getUnknownType(), "var${i}", true)
            }

            db += func1
            db += func2
            db += func3
            db += paramA
            db += paramB
        }
    }

}
