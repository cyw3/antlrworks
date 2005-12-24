/*

[The "BSD licence"]
Copyright (c) 2005 Jean Bovet
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

1. Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.
3. The name of the author may not be used to endorse or promote products
derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package org.antlr.works.visualization.graphics;

import org.antlr.analysis.NFAState;
import org.antlr.tool.Grammar;
import org.antlr.works.grammar.EditorGrammar;
import org.antlr.works.grammar.EditorGrammarError;
import org.antlr.works.visualization.fa.FAFactory;
import org.antlr.works.visualization.fa.FAState;
import org.antlr.works.visualization.graphics.graph.GGraph;
import org.antlr.works.visualization.graphics.graph.GGraphGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class GFactory {

    protected GRenderer renderer = new GRenderer();
    protected boolean optimize = true;

    public GFactory() {
    }

    public void toggleNFAOptimization() {
        optimize = !optimize;
    }

    public List buildGraphsForRule(EditorGrammar grammar, String rule, List errors) {
        if(errors == null || errors.size() == 0)
            return buildGraphsForRule(grammar, rule);
        else
            return buildGraphsForErrors(grammar, rule, errors);
    }

    public List buildGraphsForRule(EditorGrammar grammar, String rule) {
        NFAState startState = grammar.getRuleStartState(rule);
        if(startState == null)
            return null;

        FAState state = new FAFactory(grammar.getGrammarForRule(rule)).buildNFA(startState, optimize);
        GGraph graph = renderer.render(state);
        graph.setName(rule);
        return Collections.singletonList(graph);
    }

    public List buildGraphsForErrors(EditorGrammar grammar, String rule) {
        return buildGraphsForErrors(grammar, rule, grammar.getErrors());
    }

    public List buildGraphsForErrors(EditorGrammar grammar, String rule, List errors) {
        List graphs = new ArrayList();

        Iterator iterator = errors.iterator();
        while(iterator.hasNext()) {
            graphs.add(buildGraphGroup(grammar.getGrammarForRule(rule), (EditorGrammarError)iterator.next()));
        }

        return graphs;
    }

    private GGraphGroup buildGraphGroup(Grammar grammar, EditorGrammarError error) {
        // Create one GGraph for each error rules

        List graphs = new ArrayList();
        FAFactory factory = new FAFactory(grammar);
        for (int i = 0; i < error.rules.size(); i++) {
            String rule = (String)error.rules.get(i);
            NFAState startState = grammar.getRuleStartState(rule);
            FAState state = factory.buildNFA(startState, optimize);

            GGraph graph = renderer.render(state);
            graph.setName(rule);
            graphs.add(graph);
        }

        // Add only graphs that are referenced by at least one error path.
        // For example, for the statement rule of the java.g grammar produces
        // states that are not existing in the graphs (they are after the accepted state
        // and are ignored by the FAFactory)

        GGraphGroup gg = new GGraphGroup();
        for (Iterator graphIterator = graphs.iterator(); graphIterator.hasNext();) {
            GGraph graph = (GGraph) graphIterator.next();
            boolean contains = false;
            for (Iterator pathIterator = error.paths.iterator(); pathIterator.hasNext();) {
                List states = (List) pathIterator.next();
                if(graph.containsAtLeastOneState(states)) {
                    contains = true;
                    break;
                }
            }
            if(contains)
                gg.add(graph);
        }

        // Attach to the GGraphGroup all error paths

        for(int i=0; i<error.paths.size(); i++) {
            List states = (List) error.paths.get(i);
            Boolean disabled = (Boolean) error.pathsDisabled.get(i);

            gg.addPath(states, disabled.booleanValue());
        }

        if(error.paths.size()>0)
            gg.pathGroup.setPathVisible(0, true);
        
        return gg;
    }

}
