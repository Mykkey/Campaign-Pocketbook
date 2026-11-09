package dnd.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Dice notation such as {@code 2d6+3}, {@code d8}, {@code 1d6 - 1}, {@code 7} or {@code 1d8+2+1d6}.
 * Used to show average damage on cards and for the optional initiative roll.
 */
public final class Dice {
    private record Term(int sign, int count, int sides, int constant) {
        boolean isDice() { return sides > 0; }
    }

    private final List<Term> terms;
    private final String text;

    private Dice(List<Term> terms, String text) {
        this.terms = terms;
        this.text = text;
    }

    /** Parses an expression, or returns empty if it isn't valid dice notation. */
    public static Optional<Dice> parse(String s) {
        if (s == null) return Optional.empty();
        String t = s.replaceAll("\\s+", "").toLowerCase();
        if (t.isEmpty()) return Optional.empty();
        List<Term> terms = new ArrayList<>();
        int i = 0;
        int sign = 1;
        if (t.charAt(0) == '+' || t.charAt(0) == '-') {
            sign = t.charAt(0) == '-' ? -1 : 1;
            i = 1;
        }
        while (i < t.length()) {
            int j = i;
            while (j < t.length() && t.charAt(j) != '+' && t.charAt(j) != '-') j++;
            String part = t.substring(i, j);
            Term term = parseTerm(part, sign);
            if (term == null) return Optional.empty();
            terms.add(term);
            if (j >= t.length()) break;
            sign = t.charAt(j) == '-' ? -1 : 1;
            i = j + 1;
            if (i >= t.length()) return Optional.empty(); // dangling operator
        }
        return terms.isEmpty() ? Optional.empty() : Optional.of(new Dice(terms, s.trim()));
    }

    private static Term parseTerm(String part, int sign) {
        if (part.isEmpty()) return null;
        int d = part.indexOf('d');
        try {
            if (d < 0) {
                return new Term(sign, 0, 0, Integer.parseInt(part));
            }
            int count = d == 0 ? 1 : Integer.parseInt(part.substring(0, d));
            int sides = Integer.parseInt(part.substring(d + 1));
            if (count < 1 || count > 1000 || sides < 1 || sides > 1000) return null;
            return new Term(sign, count, sides, 0);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean isValid(String s) {
        return parse(s).isPresent();
    }

    /** Average result, rounded down (as monster stat blocks do), e.g. 1d6+2 -> 5. */
    public int average() {
        double sum = 0;
        for (Term t : terms) {
            sum += t.sign * (t.isDice() ? t.count * (t.sides + 1) / 2.0 : t.constant);
        }
        return (int) Math.floor(sum);
    }

    public int min() {
        int sum = 0;
        for (Term t : terms) sum += t.sign * (t.isDice() ? (t.sign > 0 ? t.count : t.count * t.sides) : t.constant);
        return sum;
    }

    public int max() {
        int sum = 0;
        for (Term t : terms) sum += t.sign * (t.isDice() ? (t.sign > 0 ? t.count * t.sides : t.count) : t.constant);
        return sum;
    }

    public int roll(Random r) {
        int sum = 0;
        for (Term t : terms) {
            if (t.isDice()) {
                for (int k = 0; k < t.count; k++) sum += t.sign * (1 + r.nextInt(t.sides));
            } else {
                sum += t.sign * t.constant;
            }
        }
        return sum;
    }

    @Override
    public String toString() {
        return text;
    }
}
