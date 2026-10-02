package zsgrooms.modid;

import java.util.*;

/** Lobby configuration only; tournament results never come from rule-edit payloads. */
public final class TournamentSettings {
    public boolean enabled;
    public boolean bracket;
    public int bestOf = 1;
    public int rounds = 5;
    public List<Integer> points = new ArrayList<>(Arrays.asList(10, 6, 4, 2, 1));
    public List<String> order = new ArrayList<>();
    public List<String> byes = new ArrayList<>();

    public TournamentSettings copy() {
        TournamentSettings copy = new TournamentSettings();
        copy.enabled = enabled; copy.bracket = bracket; copy.bestOf = bestOf; copy.rounds = rounds;
        copy.points = new ArrayList<>(points);
        copy.order = new ArrayList<>(order);
        copy.byes = new ArrayList<>(byes);
        return copy;
    }

    public static int byeCount(int players) {
        int size = 2;
        while (size < players) size *= 2;
        return size - players;
    }

    public boolean valid() {
        if (bestOf < 1 || bestOf > 7 || bestOf % 2 != 1 || rounds < 1 || rounds > 100
                || points == null || points.isEmpty() || points.size() > 64
                || order == null || order.size() > 64 || byes == null || byes.size() > 64) return false;
        int previous = 10000;
        for (Integer score : points) {
            if (score == null || score < 0 || score > previous) return false;
            previous = score;
        }
        return order.stream().allMatch(TournamentSettings::validName)
                && byes.stream().allMatch(TournamentSettings::validName)
                && new HashSet<>(order).size() == order.size()
                && new HashSet<>(byes).size() == byes.size() && order.containsAll(byes);
    }

    private static boolean validName(String name) { return name != null && !name.trim().isEmpty() && name.length() <= 64; }

    public String startError(Collection<String> players) {
        if (!valid()) return "Invalid tournament settings";
        if (order.size() < 2) return "Tournament needs at least two players";
        if (!new HashSet<>(order).equals(new HashSet<>(players))) return "Player list changed. Review the tournament roster";
        if (bracket && byes.size() != byeCount(order.size())) return "Assign exactly " + byeCount(order.size()) + " byes";
        return "";
    }
}
