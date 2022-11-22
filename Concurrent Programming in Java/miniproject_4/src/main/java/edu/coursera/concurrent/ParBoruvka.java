package edu.coursera.concurrent;

import edu.coursera.concurrent.AbstractBoruvka;
import edu.coursera.concurrent.SolutionToBoruvka;
import edu.coursera.concurrent.boruvka.Edge;
import edu.coursera.concurrent.boruvka.Component;

import java.util.Queue;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A parallel implementation of Boruvka's algorithm to compute a Minimum
 * Spanning Tree.
 */
public final class ParBoruvka extends AbstractBoruvka<ParBoruvka.ParComponent> {

    /**
     * Constructor.
     */
    public ParBoruvka() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void computeBoruvka(final Queue<ParComponent> nodesLoaded,
                               final SolutionToBoruvka<ParComponent> solution) {
        ParComponent component;
        // работаем в цикле пока head очереди nodesLoaded не нель
        while ((component = nodesLoaded.poll()) != null) {
            if (!component.lock.tryLock())
                continue;
            if (component.isDead) {
                component.lock.unlock();
                continue; // продолжаем если loopNode была уже за мерджина
            }

            // получаем loopNode's с минимальным весом
            Edge<ParComponent> minEdge = component.getMinEdge();
            if (minEdge == null)
                break; // выходим из цикла, новый граф уже был построен

            // ищем другой loopNode's с минимальным весом
            ParComponent other = minEdge.getOther(component);
            if (!other.lock.tryLock()) {
                component.lock.unlock();
                nodesLoaded.add(component); // добаевляем его в очередь
                continue;
            }
            if (other.isDead) {
                other.lock.unlock();
                component.lock.unlock();
                nodesLoaded.add(component); // добаевляем его в очередь
                continue;
            }
            other.isDead = true;
            // мерджим узел в другой узел
            component.merge(other, minEdge.weight());
            component.lock.unlock();
            other.lock.unlock();
            // добавляем новый узел в очередь
            nodesLoaded.add(component);
        }
        // устанавливаем итоговый результат

        solution.setSolution(component);
    }

    /**
     * ParComponent представляет один компонент на графике. Компонент может
     * быть singleton, представляющим один узел в графе, или может быть
     * результатом схлопывания ребер для формирования компонента из нескольких узлов.
     */
    public static final class ParComponent extends Component<ParComponent> {
        public final ReentrantLock lock = new ReentrantLock(); // создаем экземпляр ReentrantLock для использования его интерфейса и методов

        /**
         * A unique identifier for this component in the graph that contains
         * it.
         */
        public final int nodeId;

        /**
         * List of edges attached to this component, sorted by weight from least
         * to greatest.
         */
        private List<Edge<ParComponent>> edges = new ArrayList<>();

        /**
         * The weight this component accounts for. A component gains weight when
         * it is merged with another component across an edge with a certain
         * weight.
         */
        private double totalWeight = 0;

        /**
         * Number of edges that have been collapsed to create this component.
         */
        private long totalEdges = 0;

        /**
         * Whether this component has already been collapsed into another
         * component.
         */
        public boolean isDead = false;

        /**
         * Constructor.
         *
         * @param setNodeId ID for this node.
         */
        public ParComponent(final int setNodeId) {
            super();
            this.nodeId = setNodeId;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int nodeId() {
            return nodeId;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public double totalWeight() {
            return totalWeight;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long totalEdges() {
            return totalEdges;
        }

        /**
         * {@inheritDoc}
         * <p>
         * Edge is inserted in weight order, from least to greatest.
         */
        public void addEdge(final Edge<ParComponent> e) {
            int i = 0;
            while (i < edges.size()) {
                if (e.weight() < edges.get(i).weight()) {
                    break;
                }
                i++;
            }
            edges.add(i, e);
        }

        /**
         * Get the edge with minimum weight from the sorted edge list.
         *
         * @return Edge with the smallest weight attached to this component.
         */
        public Edge<ParComponent> getMinEdge() {
            if (edges.size() == 0) {
                return null;
            }
            return edges.get(0);
        }

        /**
         * Merge two components together, connected by an edge with weight
         * edgeWeight.
         *
         * @param other      The other component to merge into this component.
         * @param edgeWeight Weight of the edge connecting these components.
         */
        public void merge(final ParComponent other, final double edgeWeight) {
            totalWeight += other.totalWeight + edgeWeight;
            totalEdges += other.totalEdges + 1;

            final List<Edge<ParComponent>> newEdges = new ArrayList<>();
            int i = 0;
            int j = 0;
            while (i + j < edges.size() + other.edges.size()) {
                // Get rid of inter-component edges
                while (i < edges.size()) {
                    final Edge<ParComponent> e = edges.get(i);
                    if ((e.fromComponent() != this
                            && e.fromComponent() != other)
                            || (e.toComponent() != this
                            && e.toComponent() != other)) {
                        break;
                    }
                    i++;
                }
                while (j < other.edges.size()) {
                    final Edge<ParComponent> e = other.edges.get(j);
                    if ((e.fromComponent() != this
                            && e.fromComponent() != other)
                            || (e.toComponent() != this
                            && e.toComponent() != other)) {
                        break;
                    }
                    j++;
                }

                if (j < other.edges.size() && (i >= edges.size()
                        || edges.get(i).weight()
                        > other.edges.get(j).weight())) {
                    newEdges.add(other.edges.get(j++).replaceComponent(other,
                            this));
                } else if (i < edges.size()) {
                    newEdges.add(edges.get(i++).replaceComponent(other, this));
                }
            }
            other.edges.clear();
            edges.clear();
            edges = newEdges;
        }

        /**
         * Test for equality based on node ID.
         *
         * @param o Object to compare against.
         * @return true if they are the same component in the graph.
         */
        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }

            if (!(o instanceof Component)) {
                return false;
            }

            final Component component = (Component) o;
            return component.nodeId() == nodeId;
        }

        /**
         * Hash based on component node ID.
         *
         * @return Hash code.
         */
        @Override
        public int hashCode() {
            return nodeId;
        }
    }
    /* End ParComponent */

    /**
     * A ParEdge represents a weighted edge between two ParComponents.
     */
    public static final class ParEdge extends Edge<ParComponent>
            implements Comparable<Edge> {
        /**
         * Source component.
         */
        protected ParComponent fromComponent;

        /**
         * Destination component.
         */
        protected ParComponent toComponent;

        /**
         * Weight of this edge.
         */
        public double weight;

        /**
         * Constructor.
         *
         * @param from From edge.
         * @param to   To edges.
         * @param w    Weight of this edge.
         */
        public ParEdge(final ParComponent from, final ParComponent to,
                       final double w) {
            fromComponent = from;
            toComponent = to;
            weight = w;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ParComponent fromComponent() {
            return fromComponent;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ParComponent toComponent() {
            return toComponent;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public double weight() {
            return weight;
        }

        /**
         * {@inheritDoc}
         */
        public ParComponent getOther(final ParComponent from) {
            if (fromComponent == from) {
                assert (toComponent != from);
                return toComponent;
            }

            if (toComponent == from) {
                assert (fromComponent != from);
                return fromComponent;
            }
            assert (false);
            return null;

        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int compareTo(final Edge e) {
            return Double.compare(weight, e.weight()); // сравниваем через метод .compare weight, e.weight() как Double
        }

        /**
         * {@inheritDoc}
         */
        public ParEdge replaceComponent(final ParComponent from,
                                        final ParComponent to) {
            if (fromComponent == from) {
                fromComponent = to;
            }
            if (toComponent == from) {
                toComponent = to;
            }
            return this;
        }
    }
}
