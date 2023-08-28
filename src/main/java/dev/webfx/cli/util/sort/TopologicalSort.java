package dev.webfx.cli.util.sort;

import java.util.*;

/**
 * @author Bruno Salmon
 */
public final class TopologicalSort<T> {

    public static <T> List<T> sortAsc(Map<T, List<T>> dependencyGraph) { // independents first
        return sortDependencyGraph(dependencyGraph, true);
    }

    public static <T> List<T> sortDesc(Map<T, List<T>> dependencyGraph) { // independents last
        return sortDependencyGraph(dependencyGraph, false);
    }

    public static <T> List<T> sortDependencyGraph(Map<T, List<T>> dependencyGraph, boolean asc) {
        return new TopologicalSort<>(dependencyGraph, asc).sort();
    }

    // Private implementation

    private final Map<T, List<T>> dependencyGraph;
    private final Set<T> visited = new HashSet<>();
    private final List<T> sortedObjects = new ArrayList<>();
    private final boolean asc;

    public TopologicalSort(Map<T, List<T>> dependencyGraph, boolean asc) {
        this.dependencyGraph = dependencyGraph;
        this.asc = asc;
    }

    private List<T> sort() {
        for (T object : dependencyGraph.keySet()) {
            if (!visited.contains(object)) {
                deepFirstSearch(object);
            }
        }
        if (!asc)
            Collections.reverse(sortedObjects);
        return sortedObjects;
    }

    private void deepFirstSearch(T object) {
        visited.add(object);
        if (dependencyGraph.containsKey(object)) {
            for (T usedObject : dependencyGraph.get(object)) {
                if (!visited.contains(usedObject)) {
                    deepFirstSearch(usedObject);
                }
            }
        }
        sortedObjects.add(object);
    }

}
