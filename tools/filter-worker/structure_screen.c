#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include "finders.h"

#define ZSG_MASK48 UINT64_C(0xffffffffffff)
#define CORPUS_STEP UINT64_C(0x9e3779b97f4a7c15)

static int floor_div(int value, int divisor) {
    int q = value / divisor;
    return q - (value % divisor < 0);
}

static int within(Pos p, int range) {
    return p.x >= -range && p.x <= range && p.z >= -range && p.z <= range;
}

static int nether_position(uint64_t seed, int feature, int range, Pos *out) {
    StructureConfig config;
    if (!getStructureConfig(feature, MC_1_16_1, &config)) return 0;
    int chunks = (range + 15) / 16;
    for (int x = floor_div(-chunks, config.regionSize); x <= floor_div(chunks, config.regionSize); x++) {
        for (int z = floor_div(-chunks, config.regionSize); z <= floor_div(chunks, config.regionSize); z++) {
            if (getStructurePos(feature, MC_1_16_1, seed, x, z, out) && within(*out, range)) return 1;
        }
    }
    return 0;
}

static uint64_t digest(uint64_t hash, int value) {
    return (hash ^ (uint64_t)(int64_t)value) * UINT64_C(0x100000001b3);
}

/* Fixed public corpus, not a production seed handoff. Never output seed values. */
int main(int argc, char **argv) {
    if (argc != 4) return 2;
    char *end;
    long type = strtol(argv[1], &end, 10);
    if (*end || type < 0 || type > 2) return 2;
    long op = strtol(argv[2], &end, 10);
    if (*end || op < 0 || op > 1) return 2;
    long count = strtol(argv[3], &end, 10);
    if (*end || count < 1 || count > 1000000) return 2;
    const int features[] = {Village, Desert_Pyramid, Shipwreck};
    const int ranges[] = {224, 320, 208};
    uint64_t hash = UINT64_C(0xcbf29ce484222325);
    long counts[4] = {0};
    clock_t started = clock();
    for (long i = 0; i < count; i++) {
        uint64_t seed = ((uint64_t)i * CORPUS_STEP) & ZSG_MASK48;
        Pos main, bastion, fortress;
        int result;
        if (!nether_position(seed, Bastion, op ? 32 : 96, &bastion)) result = 0;
        else if (!nether_position(seed, Fortress, op ? 112 : 256, &fortress)) result = 1;
        else {
            if (!getStructurePos(features[type], MC_1_16_1, seed, 0, 0, &main)) return 3;
            result = within(main, ranges[type]) ? 3 : 2;
        }
        counts[result]++;
        hash = digest(hash, result);
        if (result == 3) {
            hash = digest(hash, main.x / 16);
            hash = digest(hash, main.z / 16);
            hash = digest(hash, bastion.x / 16);
            hash = digest(hash, bastion.z / 16);
            hash = digest(hash, fortress.x / 16);
            hash = digest(hash, fortress.z / 16);
        }
    }
    printf("{\"protocol\":1,\"cubiomesRevision\":\"e61f90580cbdd883214a8054670dacae655e59c0\","
           "\"samples\":%ld,\"checksum\":\"%016" PRIx64 "\",\"counts\":[%ld,%ld,%ld,%ld],\"cpuMs\":%.3f}\n",
           count, hash, counts[0], counts[1], counts[2], counts[3],
           1000.0 * (clock() - started) / CLOCKS_PER_SEC);
    return 0;
}
