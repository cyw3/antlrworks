package org.antlr.works.syntax;

import org.antlr.works.ate.breakpoint.ATEBreakpointEntity;
import org.antlr.works.ate.folding.ATEFoldingEntity;
import org.antlr.works.ate.syntax.generic.ATESyntaxLexer;
import org.antlr.works.ate.syntax.misc.ATEToken;
import org.antlr.works.editor.EditorPersistentObject;
import org.antlr.works.grammar.EditorGrammarError;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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

public class GrammarSyntaxRule implements Comparable, EditorPersistentObject, ATEFoldingEntity, ATEBreakpointEntity {

    public String name;
    public ATEToken start;
    public ATEToken colon;
    public ATEToken end;

    public boolean expanded = true;
    public boolean breakpoint;

    public boolean lexer = false;
    public boolean hasLeftRecursion = false;

    public List errors;
    protected GrammarSyntaxParser parser;

    protected int refsStartIndex = -1;
    protected int refsEndIndex = -1;

    public GrammarSyntaxRule(String name) {
        this.name = name;
        this.lexer = ATEToken.isLexerName(name);
    }

    public GrammarSyntaxRule(GrammarSyntaxParser parser, String name, ATEToken start, ATEToken colon, ATEToken end) {
        this.parser = parser;
        this.name = name;
        this.start = start;
        this.colon = colon;
        this.end = end;
        this.lexer = ATEToken.isLexerName(name);
    }

    public void completed() {
        // Called when the rule has been completely parsed
        this.hasLeftRecursion = detectLeftRecursion();
    }

    public void setReferencesIndexes(int startIndex, int endIndex) {
        this.refsStartIndex = Math.max(0, startIndex);
        this.refsEndIndex = endIndex;
    }

    public List getReferences() {
        if(refsStartIndex != -1 && refsEndIndex != -1)
            return parser.references.subList(refsStartIndex, refsEndIndex+1);
        else
            return null;
    }

    public int getStartIndex() {
        return start.getStartIndex();
    }

    public int getEndIndex() {
        return end.getEndIndex();
    }

    public int getLength() {
        return getEndIndex()-getStartIndex();
    }

    public int getInternalTokensStartIndex() {
        for(Iterator iter = getTokens().iterator(); iter.hasNext(); ) {
            ATEToken token = (ATEToken)iter.next();
            if(token.getAttribute().equals(":")) {
                token = (ATEToken)iter.next();
                return token.getStartIndex();
            }
        }
        return -1;
    }

    public int getInternalTokensEndIndex() {
        ATEToken token = (ATEToken)parser.getTokens().get(end.index-1);
        return token.getEndIndex();
    }

    public List getBlocks() {
        List blocks = new ArrayList();
        ATEToken lastToken = null;
        for(int index=start.index; index<end.index; index++) {
            ATEToken token = (ATEToken)parser.getTokens().get(index);
            if(token.type == GrammarSyntaxLexer.TOKEN_BLOCK) {
                if(lastToken != null && lastToken.type == ATESyntaxLexer.TOKEN_ID && lastToken.getAttribute().equals("options"))
                    continue;

                blocks.add(token);
            }
            lastToken = token;
        }
        return blocks;
    }

    public List getTokens() {
        List t = new ArrayList();
        for(int index=start.index; index<end.index; index++) {
            t.add(parser.getTokens().get(index));
        }
        return t;
    }

    public List getAlternatives() {
        List alts = new ArrayList();
        List alt = null;
        boolean findColon = true;
        int level = 0;
        for(Iterator iter = getTokens().iterator(); iter.hasNext(); ) {
            ATEToken token = (ATEToken)iter.next();
            if(findColon) {
                if(token.getAttribute().equals(":")) {
                    findColon = false;
                    alt = new ArrayList();
                }
            } else {
                if(token.getAttribute().equals("("))
                    level++;
                else if(token.getAttribute().equals(")"))
                    level--;
                else if(token.type != GrammarSyntaxLexer.TOKEN_BLOCK && level == 0) {
                    if(token.getAttribute().equals("|")) {
                        alts.add(alt);
                        alt = new ArrayList();
                        continue;
                    }
                }
                alt.add(token);
            }
        }
        if(alt != null && !alt.isEmpty())
            alts.add(alt);
        return alts;
    }

    public void setErrors(List errors) {
        this.errors = errors;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public boolean hasLeftRecursion() {
        return hasLeftRecursion;
    }

    public boolean detectLeftRecursion() {
        for(Iterator iter = getAlternatives().iterator(); iter.hasNext(); ) {
            List alts = (List)iter.next();
            if(alts.isEmpty())
                continue;

            ATEToken firstTokenInAlt = (ATEToken)alts.get(0);
            if(firstTokenInAlt.getAttribute().equals(name))
                return true;
        }
        return false;
    }

    public String getTextRuleAfterRemovingLeftRecursion() {
        StringBuffer head = new StringBuffer();
        StringBuffer star = new StringBuffer();

        for(Iterator iter = getAlternatives().iterator(); iter.hasNext(); ) {
            List alts = (List)iter.next();
            ATEToken firstTokenInAlt = (ATEToken)alts.get(0);
            if(firstTokenInAlt.getAttribute().equals(name)) {
                if(alts.size() > 1) {
                    if(star.length() > 0)
                        star.append(" | ");
                    int start = ((ATEToken)alts.get(1)).getStartIndex();
                    int end = ((ATEToken)alts.get(alts.size()-1)).getEndIndex();
                    star.append(firstTokenInAlt.text.substring(start, end));
                }
            } else {
                if(head.length() > 0)
                    head.append(" | ");
                int start = firstTokenInAlt.getStartIndex();
                int end = ((ATEToken)alts.get(alts.size()-1)).getEndIndex();
                head.append(firstTokenInAlt.text.substring(start, end));
            }
        }

        StringBuffer sb = new StringBuffer();
        sb.append("(");
        sb.append(head);
        sb.append(") ");
        sb.append("(");
        sb.append(star);
        sb.append(")*");

        return sb.toString();
    }

    public boolean hasErrors() {
        if(errors == null)
            return false;
        else
            return !errors.isEmpty();
    }

    public String getErrorMessageString(int index) {
        EditorGrammarError error = (EditorGrammarError) errors.get(index);
        return error.message;
    }

    public String getErrorMessageHTML() {
        StringBuffer message = new StringBuffer();
        message.append("<html>");
        for (Iterator iterator = errors.iterator(); iterator.hasNext();) {
            EditorGrammarError error = (EditorGrammarError) iterator.next();
            message.append(error.message);
            if(iterator.hasNext())
                message.append("<br>");
        }
        message.append("</html>");
        return message.toString();
    }

    public String toString() {
        return name;
    }

    public boolean containsIndex(int index) {
        return index >= getStartIndex() && index <= getEndIndex();
    }
    
    public int compareTo(Object o) {
        GrammarSyntaxRule otherRule = (GrammarSyntaxRule) o;
        return this.name.compareTo(otherRule.name);
    }

    public int getUniqueIdentifier() {
        return name.hashCode();
    }

    public boolean canBeCollapsed() {
        return colon.startLineNumber <= end.startLineNumber - 1;
    }

    public void foldingEntitySetExpanded(boolean expanded) {
        setExpanded(expanded);
    }

    public boolean foldingEntityIsExpanded() {
        return isExpanded();
    }

    public boolean foldingEntityCanBeCollapsed() {
        return canBeCollapsed();
    }

    public int foldingEntityGetStartParagraphIndex() {
        return getStartIndex();
    }

    public int foldingEntityGetStartIndex() {
        return colon.getStartIndex();
    }

    public int foldingEntityGetEndIndex() {
        return getEndIndex();
    }

    public int foldingEntityGetStartLine() {
        return colon.startLineNumber;
    }

    public int foldingEntityGetEndLine() {
        return end.endLineNumber;
    }

    public String foldingEntityPlaceholderString() {
        return ": ... ;";
    }

    public String foldingEntityID() {
        return String.valueOf(getUniqueIdentifier());
    }

    public int foldingEntityLevel() {
        return 0;
    }

    public int breakpointEntityUniqueID() {
        return getUniqueIdentifier();
    }

    public int breakpointEntityIndex() {
        return getStartIndex();
    }

    public int breakpointEntityLine() {
        return start.startLineNumber;
    }

    public void breakpointEntitySetBreakpoint(boolean flag) {
        this.breakpoint = flag;
    }

    public boolean breakpointEntityIsBreakpoint() {
        return breakpoint;
    }

    public Object getPersistentID() {
        return new Integer(getUniqueIdentifier());
    }

    public void persistentAssign(EditorPersistentObject otherObject) {
        GrammarSyntaxRule oldRule = (GrammarSyntaxRule)otherObject;
        this.expanded = oldRule.expanded;
        this.breakpoint = oldRule.breakpoint;
    }

}