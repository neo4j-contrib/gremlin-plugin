/**
 * Copyright (c) 2002-2014 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.server.plugin.gremlin;

import java.util.Map;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;

import com.tinkerpop.blueprints.impls.neo4j2.Neo4j2Graph;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.server.plugins.Description;
import org.neo4j.server.plugins.Name;
import org.neo4j.server.plugins.Parameter;
import org.neo4j.server.plugins.PluginTarget;
import org.neo4j.server.plugins.ServerPlugin;
import org.neo4j.server.plugins.Source;
import org.neo4j.server.rest.repr.BadInputException;
import org.neo4j.server.rest.repr.GremlinObjectToRepresentationConverter;
import org.neo4j.server.rest.repr.Representation;

/* This is a class that will represent a server side
 * Gremlin plugin and will return JSON
 * for the following use cases:
 * Add/delete vertices and edges from the graph.
 * Manipulate the graph indices.
 * Search for elements of a graph.
 * Load graph data from a file or URL.
 * Make use of JUNG algorithms.
 * Make use of SPARQL queries over OpenRDF-based graphs.
 * and much, much more.
 */
@Description("A server side Gremlin plugin for the Neo4j REST server")
public class GremlinPlugin extends ServerPlugin
{

    private final String g = "g";
    private volatile ScriptEngine engine;
    private final EngineReplacementDecision engineReplacementDecision = new ScriptCountingEngineReplacementDecision( 500 );

    private ScriptEngine createQueryEngine()
    {
        return new ScriptEngineManager().getEngineByName( "gremlin-groovy" );
    }

    @Name("execute_script")
    @Description("execute a Gremlin script with 'g' set to the Neo4j2Graph and 'results' containing the results. Only results of one object type is supported.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation executeScript(
            @Source final GraphDatabaseService neo4j,
            @Description("The Gremlin script") @Parameter(name = "script", optional = false) final String script,
            @Description("JSON Map of additional parameters for script variables") @Parameter(name = "params", optional = true) final Map params ) throws BadInputException
    {

        Neo4j2Graph neo4jGraph = new Neo4j2Graph(neo4j);
        try
        {
            engineReplacementDecision.beforeExecution( script );
            neo4jGraph.autoStartTransaction(true);
            final Bindings bindings = createBindings(params, neo4jGraph);

            final Object result = engine().eval( script, bindings );
            Representation representation = GremlinObjectToRepresentationConverter.convert(result);
            neo4jGraph.commit();
            return representation;
        } catch ( final Exception e )
        {
            throw new BadInputException( e.getMessage() );
        }
    }

    private Bindings createBindings(Map params, Neo4j2Graph neo4jGraph)
    {
        final Bindings bindings = createInitialBinding(neo4jGraph);
        if ( params != null )
        {
            bindings.putAll( params );
        }
        return bindings;
    }

    private Bindings createInitialBinding(Neo4j2Graph neo4jGraph)
    {
        final Bindings bindings = new SimpleBindings();
        bindings.put( g, neo4jGraph);
        return bindings;
    }

    private ScriptEngine engine()
    {
        if ( this.engine == null
                || engineReplacementDecision.mustReplaceEngine() )
        {
            this.engine = createQueryEngine();
        }
        return this.engine;
    }

    public Representation getRepresentation( final Object data )
    {
        return GremlinObjectToRepresentationConverter.convert( data );
    }

}
