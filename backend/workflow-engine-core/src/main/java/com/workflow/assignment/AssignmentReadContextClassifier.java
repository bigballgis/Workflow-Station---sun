package com.workflow.assignment;

import org.springframework.stereotype.Component;
import java.util.*;

/** Positive legacy proof is the full actual caller chain; absent projections alone never suffice. */
@Component
public class AssignmentReadContextClassifier {
    private final AssignmentDefinitionPolicy definitions;
    public AssignmentReadContextClassifier(AssignmentDefinitionPolicy definitions) { this.definitions=definitions; }
    public enum Kind { LEGACY, TRACKED, UNAVAILABLE }
    public Map<String,Kind> classify(Map<String,AssignmentTaskReadContexts.Context> contexts) {
        Map<String,Boolean> compiled=new HashMap<>(); Map<String,Kind> result=new LinkedHashMap<>();
        contexts.forEach((id,context)->result.put(id,classify(context,compiled)));
        return Map.copyOf(result);
    }
    private Kind classify(AssignmentTaskReadContexts.Context context,Map<String,Boolean> compiled) {
        var position=context.subject().position();
        if (position.instance()==null) return position.definition()==null && !context.subject().hasState()?Kind.LEGACY:Kind.UNAVAILABLE;
        var chain=context.ancestors();
        if (chain.isEmpty() || !position.instance().equals(chain.get(0).instance())
                || !Objects.equals(position.definition(),chain.get(0).definition())) return Kind.UNAVAILABLE;
        boolean tracked=context.subject().hasState(); UUID registeredCase=null;
        Set<String> visited=new HashSet<>();
        for (int i=0;i<chain.size();i++) {
            var ancestor=chain.get(i);
            if (!visited.add(ancestor.instance()) || ancestor.definition()==null) return Kind.UNAVAILABLE;
            var registered=ancestor.registered();
            if (registered!=null) {
                if (!ancestor.definition().equals(registered.definition()) || registeredCase!=null && !registeredCase.equals(registered.caseId())) return Kind.UNAVAILABLE;
                registeredCase=registered.caseId(); tracked=true;
            }
            tracked|=compiled.computeIfAbsent(ancestor.definition(),definitions::tracked);
            if (ancestor.superCall()==null) return i==chain.size()-1?(tracked?Kind.TRACKED:Kind.LEGACY):Kind.UNAVAILABLE;
            if (i+1>=chain.size() || !Objects.equals(ancestor.parent(),chain.get(i+1).instance())) return Kind.UNAVAILABLE;
        }
        return Kind.UNAVAILABLE;
    }
}
