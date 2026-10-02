package zsgrooms.modid;

import java.util.*;

/** Host-owned single-elimination or placement-points tournament, independent of world loading. */
public final class Tournament {
    public TournamentSettings settings;
    public List<Match> matches = new ArrayList<>();
    public Map<String, Integer> scores = new LinkedHashMap<>();
    public Set<String> withdrawn = new LinkedHashSet<>();
    public int completedRounds;
    public int bracketRound = 1;
    public boolean complete;
    public String champion = "";
    public String activeRaceId = "";
    public String lastRaceId = "";
    public String lastResult = "";

    public Tournament(TournamentSettings settings) {
        if (!settings.startError(settings.order).isEmpty()) throw new IllegalArgumentException("Incomplete tournament setup");
        this.settings = settings.copy();
        for (String name : settings.order) scores.put(name, 0);
        if (settings.bracket) {
            List<String> paired = new ArrayList<>(settings.order);
            paired.removeAll(settings.byes);
            int matchCount = (settings.order.size() + TournamentSettings.byeCount(settings.order.size())) / 2;
            int bye = 0, player = 0;
            for (int i = 0; i < matchCount; i++) {
                // Spread explicitly chosen byes across the opening round, without random assignment.
                boolean isBye = bye < settings.byes.size() && i == bye * matchCount / settings.byes.size();
                matches.add(new Match(1, isBye ? settings.byes.get(bye++) : paired.get(player++),
                        isBye ? "" : paired.get(player++)));
            }
            advanceBracket();
        }
    }

    public Match currentMatch() {
        for (Match match : matches) if (!match.complete) return match;
        return null;
    }

    public List<String> participants() {
        if (complete) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        if (settings.bracket) {
            Match match = currentMatch();
            if (match != null) { result.add(match.left); result.add(match.right); }
        } else result.addAll(settings.order);
        result.removeIf(name -> name.isEmpty() || withdrawn.contains(name));
        return result;
    }

    public void beginRace(String raceId) {
        if (complete || !activeRaceId.isEmpty() || participants().isEmpty()) throw new IllegalStateException("Tournament not ready");
        activeRaceId = raceId;
    }

    public boolean record(RaceSequence race) {
        if (race == null || !race.complete() || !race.raceId.equals(activeRaceId) || race.raceId.equals(lastRaceId)) return false;
        race.closeAtLimit(-1, 0, false);
        lastRaceId = race.raceId;
        activeRaceId = "";
        if (!settings.bracket) {
            for (RaceSequence.Runner runner : race.standings()) {
                int place = race.place(runner.name);
                if (scores.containsKey(runner.name) && place > 0 && place <= settings.points.size())
                    scores.put(runner.name, scores.get(runner.name) + settings.points.get(place - 1));
            }
            completedRounds++;
            complete = completedRounds >= settings.rounds || participants().isEmpty();
            lastResult = "Round " + completedRounds + " scored";
        } else {
            Match match = currentMatch();
            if (match == null) return false;
            List<RaceSequence.Runner> winners = new ArrayList<>();
            for (RaceSequence.Runner runner : race.standings()) if (race.place(runner.name) == 1) winners.add(runner);
            if (winners.size() == 1) {
                String winner = winners.get(0).name;
                if (winner.equals(match.left)) match.leftWins++;
                else if (winner.equals(match.right)) match.rightWins++;
                if (Math.max(match.leftWins, match.rightWins) >= settings.bestOf / 2 + 1) {
                    match.winner = winner;
                    match.complete = true;
                }
                lastResult = winner + " wins the race (" + match.leftWins + "-" + match.rightWins + ")";
            } else lastResult = "Draw: replay this race with fresh shared seeds";
            advanceBracket();
        }
        return true;
    }

    public boolean withdraw(String name) {
        if (!scores.containsKey(name) || !withdrawn.add(name)) return false;
        if (activeRaceId.isEmpty()) {
            if (settings.bracket) advanceBracket();
            else if (participants().isEmpty()) complete = true;
        }
        return true;
    }

    private void advanceBracket() {
        while (!complete) {
            List<Match> round = new ArrayList<>();
            for (Match match : matches) if (match.round == bracketRound) {
                round.add(match);
                if (!match.complete) {
                    boolean leftOut = match.left.isEmpty() || withdrawn.contains(match.left);
                    boolean rightOut = match.right.isEmpty() || withdrawn.contains(match.right);
                    if (leftOut || rightOut) {
                        match.complete = true;
                        match.winner = leftOut ? rightOut ? "" : match.right : match.left;
                    }
                }
            }
            if (round.stream().anyMatch(m -> !m.complete)) return;
            if (round.size() == 1) {
                complete = true;
                champion = round.get(0).winner;
                return;
            }
            bracketRound++;
            for (int i = 0; i < round.size(); i += 2)
                matches.add(new Match(bracketRound, round.get(i).winner, round.get(i + 1).winner));
        }
    }

    public List<String> leaderboard() {
        List<String> names = new ArrayList<>(scores.keySet());
        names.sort(Comparator.comparingInt((String name) -> -scores.get(name)).thenComparing(name -> name));
        return names;
    }

    public int place(String name) {
        return 1 + (int) scores.values().stream().filter(score -> score > scores.getOrDefault(name, 0)).count();
    }

    public String status() {
        if (complete) return settings.bracket ? champion.isEmpty() ? "Tournament ended" : "Champion: " + champion : "Final points standings";
        if (!settings.bracket) return "Points round " + (completedRounds + 1) + "/" + settings.rounds;
        Match match = currentMatch();
        return match == null ? "Bracket" : "Round " + match.round + ": " + match.left + " " + match.leftWins
                + "-" + match.rightWins + " " + match.right + " (BO" + settings.bestOf + ")";
    }

    public boolean valid() {
        return settings != null && settings.valid() && settings.order.size() >= 2
                && matches != null && matches.size() <= 63 && scores != null
                && scores.keySet().equals(new LinkedHashSet<>(settings.order))
                && scores.values().stream().allMatch(score -> score != null && score >= 0 && score <= 1000000)
                && withdrawn != null && scores.keySet().containsAll(withdrawn)
                && activeRaceId != null && lastRaceId != null && lastResult != null && champion != null
                && completedRounds >= 0 && completedRounds <= settings.rounds && bracketRound >= 1 && bracketRound <= 6
                && matches.stream().allMatch(m -> m != null && m.round >= 1 && m.round <= 6
                    && m.left != null && m.right != null && m.winner != null
                    && (m.left.isEmpty() || scores.containsKey(m.left)) && (m.right.isEmpty() || scores.containsKey(m.right))
                    && (m.winner.isEmpty() || m.winner.equals(m.left) || m.winner.equals(m.right))
                    && m.leftWins >= 0 && m.leftWins <= 4 && m.rightWins >= 0 && m.rightWins <= 4);
    }

    public static final class Match {
        public int round;
        public String left, right, winner = "";
        public int leftWins, rightWins;
        public boolean complete;
        Match(int round, String left, String right) { this.round = round; this.left = left; this.right = right; }
    }
}
