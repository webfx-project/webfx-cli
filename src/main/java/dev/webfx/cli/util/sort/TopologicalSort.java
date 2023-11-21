package dev.webfx.cli.util.sort;

import java.util.*;
import java.util.stream.Collectors;

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
        // We will iterate the dependency graph key set, but we sort it first. The purpose is to remove any possible
        // remaining random order in the list we finally return. This will ensure the result is stable between 2
        // executions, and prevent unnecessary / irrelevant changes in files between 2 webfx updates.
        List<T> sortedKeySet = dependencyGraph.keySet().stream().sorted().collect(Collectors.toList());
        for (T object : sortedKeySet) {
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
            // We also sort the values, for the same reason as the keys explained above.
            List<T> usedObjects = dependencyGraph.get(object);
            usedObjects.sort(null);
            for (T usedObject : usedObjects) {
                if (!visited.contains(usedObject)) {
                    deepFirstSearch(usedObject);
                }
            }
        }
        sortedObjects.add(object);
    }

}
