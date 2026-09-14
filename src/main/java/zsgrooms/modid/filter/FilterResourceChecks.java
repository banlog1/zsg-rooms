package zsgrooms.modid.filter;

public final class FilterResourceChecks {
    private FilterResourceChecks() {
    }

    public static boolean villageIron(int ingots, int nuggets, int blocks, boolean generatedGolem) {
        if (ingots < 0 || nuggets < 0 || blocks < 0 || !generatedGolem) return false;
        return (long) ingots * 9 + nuggets + (long) blocks * 81 + 27 >= 63;
    }

    public static boolean shipwreckTools(int iron, int diamonds, int gold) {
        if (iron < 0 || diamonds < 0 || gold < 0) return false;
        if (diamonds >= 3) diamonds -= 3;
        else if (iron >= 3) iron -= 3;
        else return false;
        if (iron < 4) return false;
        iron -= 4;
        if (diamonds >= 3) diamonds -= 3;
        else if (iron >= 3) iron -= 3;
        else if (gold >= 3) gold -= 3;
        else return false;
        return diamonds >= 1 || iron >= 1 || gold >= 1;
    }

    public static boolean shipwreckFood(int wheat, int carrots) {
        return wheat >= 0 && carrots >= 0 && (long) wheat + (long) carrots * 6 >= 30;
    }
}
