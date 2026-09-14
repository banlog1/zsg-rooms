#ifndef ZSG_MODEL_LOOT_H
#define ZSG_MODEL_LOOT_H
#include "finders.h"

/* Adapted from the published MIT ZSG filter_common.zig. See THIRD_PARTY.md. */
typedef struct { int iron, diamonds, gold, food; } Loot;

static uint64_t decoration(uint64_t seed, int x, int z, int salt) {
    uint64_t r;
    setSeed(&r, seed);
    uint64_t a = nextLong(&r) | 1, b = nextLong(&r) | 1;
    return (((uint64_t)(int64_t)x * a + (uint64_t)(int64_t)z * b) ^ seed) + salt;
}

static int count(uint64_t *r, int min, int max) { return min + nextInt(r, max - min + 1); }

static Loot temple_loot(uint64_t seed, Pos pos) {
    static const int levels[37] = {4,4,4,4,4,3,1,3,3,2,1,5,5,5,2,2,3,3,5,1,3,3,5,2,1,1,3,3,3,5,3,1,1,3,4,1,1};
    Loot loot = {0};
    uint64_t decorator;
    setSeed(&decorator, decoration(seed, pos.x, pos.z, 40003));
    for (int chest = 0; chest < 4; chest++) {
        uint64_t r;
        setSeed(&r, nextLong(&decorator));
        int rolls = count(&r, 2, 4);
        for (int i = 0; i < rolls; i++) {
            int choice = nextInt(&r, 232);
            if (choice < 5) loot.diamonds += count(&r, 1, 3);
            else if (choice < 20) loot.iron += count(&r, 1, 5);
            else if (choice < 35) loot.gold += count(&r, 2, 7);
            else if (choice < 100) nextInt(&r, 3);
            else if (choice < 125) nextInt(&r, 5);
            else if (choice >= 175 && choice < 195) {
                int level = levels[nextInt(&r, 37)];
                if (level > 1) nextInt(&r, level);
            }
        }
    }
    return loot;
}

static uint64_t chest_random(uint64_t seed, int x, int z, int longs) {
    uint64_t r, value = 0;
    setSeed(&r, decoration(seed, x, z, 40006));
    for (int i = 0; i < longs; i++) value = nextLong(&r);
    setSeed(&r, value);
    return r;
}

static Loot ship_loot(uint64_t seed, Pos pos) {
    Loot loot = {0};
    uint64_t r = chest_random(seed, pos.x, pos.z, 4); /* CCW90 treasure: indexed chest + RNG trash */
    int rolls = count(&r, 3, 6);
    for (int i = 0; i < rolls; i++) {
        int choice = nextInt(&r, 150);
        if (choice < 90) loot.iron += count(&r, 1, 5);
        else if (choice < 100) loot.gold += count(&r, 1, 5);
        else if (choice < 140) nextInt(&r, 5);
        else if (choice < 145) loot.diamonds++;
    }
    int iron = 0, gold = 0;
    rolls = count(&r, 2, 5);
    for (int i = 0; i < rolls; i++) {
        int choice = nextInt(&r, 80), amount = count(&r, 1, 10);
        if (choice < 50) iron += amount;
        else if (choice < 60) gold += amount; /* Vanilla weight 10, not ZSG's <70 typo. */
    }
    loot.iron += iron / 9;
    loot.gold += gold / 9;
    r = chest_random(seed, pos.x - 16, pos.z, 2);
    rolls = count(&r, 3, 10);
    for (int i = 0; i < rolls; i++) {
        int c = nextInt(&r, 77), enchant;
        if (c < 8) nextInt(&r, 12);
        else if (c < 22) nextInt(&r, 5);
        else if (c < 29) loot.food += 6 * count(&r, 4, 8);
        else if (c < 36) loot.food += count(&r, 8, 21);
        else if (c < 46) { next(&r, 32); next(&r, 32); }
        else if (c < 52) nextInt(&r, 7);
        else if (c < 57) nextInt(&r, 20);
        else if (c < 61) nextInt(&r, 3);
        else if (c < 64) nextInt(&r, 5);
        else if (c < 65) nextInt(&r, 2);
        else if (c < 74) {
            enchant = nextInt(&r, c < 68 ? 11 : 9);
            if (enchant < 4) nextInt(&r, 4);
            else if (enchant == 4 || enchant == 6 || (c < 68 && enchant == 8)) nextInt(&r, 3);
        } else {
            enchant = nextInt(&r, 12);
            if (enchant < 5) nextInt(&r, 4);
            else if (enchant == 7) nextInt(&r, 2);
            else if (enchant == 5 || enchant == 6 || enchant == 9) nextInt(&r, 3);
        }
    }
    return loot;
}

static int ship_resources(Loot loot) {
    if (loot.food < 30) return 0;
    if (loot.diamonds >= 3) loot.diamonds -= 3;
    else if (loot.iron >= 3) loot.iron -= 3;
    else return 0;
    if (loot.iron < 4) return 0;
    loot.iron -= 4;
    if (loot.diamonds >= 3) loot.diamonds -= 3;
    else if (loot.iron >= 3) loot.iron -= 3;
    else if (loot.gold >= 3) loot.gold -= 3;
    else return 0;
    return loot.diamonds + loot.iron + loot.gold >= 1;
}
#endif
