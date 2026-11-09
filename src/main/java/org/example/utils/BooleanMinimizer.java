package org.example.utils;

import java.util.*;

/**
 * Quine–McCluskey simplification: turns the set of week outcomes where
 * something happens into a short OR of AND-terms, e.g.
 * "(A beats B AND C beats D) OR E beats F".
 *
 * Bit g of an outcome is game g's result. Variables are a week's games, so
 * there are only a handful.
 */
public final class BooleanMinimizer {

    /** An AND-term: the bits in {@code care} must equal the same bits in {@code value}. */
    public record Term(int value, int care) {
        public boolean covers(int outcome) {
            return (outcome & care) == (value & care);
        }
    }

    private BooleanMinimizer() {
    }

    /**
     * @param vars     number of variables (games)
     * @param outcomes outcomes where the condition is true
     * @return a small set of terms whose OR is exactly {@code outcomes}
     */
    public static List<Term> minimize(int vars, Collection<Integer> outcomes) {
        if (outcomes.isEmpty()) {
            return List.of();
        }
        int all = (1 << vars) - 1;
        if (outcomes.size() == (1 << vars)) {
            return List.of(new Term(0, 0)); // always true
        }

        // Combine terms that differ in one cared-about bit until nothing changes.
        Set<Term> current = new HashSet<>();
        for (int o : outcomes) {
            current.add(new Term(o, all));
        }
        Set<Term> primes = new HashSet<>();
        while (!current.isEmpty()) {
            Set<Term> next = new HashSet<>();
            Set<Term> used = new HashSet<>();
            List<Term> list = new ArrayList<>(current);
            for (int a = 0; a < list.size(); a++) {
                for (int b = a + 1; b < list.size(); b++) {
                    Term x = list.get(a), y = list.get(b);
                    if (x.care() != y.care())
                        continue;
                    int diff = (x.value() ^ y.value()) & x.care();
                    if (Integer.bitCount(diff) == 1) {
                        int care = x.care() & ~diff;
                        next.add(new Term(x.value() & care, care));
                        used.add(x);
                        used.add(y);
                    }
                }
            }
            for (Term t : current) {
                if (!used.contains(t))
                    primes.add(t);
            }
            current = next;
        }

        // Cover: required terms first, then the term covering the most uncovered outcomes.
        Set<Integer> left = new TreeSet<>(outcomes);
        List<Term> chosen = new ArrayList<>();
        for (int o : outcomes) {
            Term only = null;
            int count = 0;
            for (Term t : primes) {
                if (t.covers(o)) {
                    only = t;
                    count++;
                }
            }
            if (count == 1 && !chosen.contains(only)) {
                chosen.add(only);
            }
        }
        for (Term t : chosen) {
            left.removeIf(t::covers);
        }
        List<Term> pool = new ArrayList<>(primes);
        pool.sort(Comparator.comparingInt((Term t) -> Integer.bitCount(t.care())).thenComparingInt(Term::value));
        while (!left.isEmpty()) {
            Term best = null;
            long bestCount = 0;
            for (Term t : pool) {
                long c = left.stream().filter(t::covers).count();
                if (c > bestCount) {
                    best = t;
                    bestCount = c;
                }
            }
            chosen.add(best);
            Term b = best;
            left.removeIf(b::covers);
        }

        chosen.sort(Comparator.comparingInt((Term t) -> Integer.bitCount(t.care())).thenComparingInt(Term::value));
        return chosen;
    }
}
