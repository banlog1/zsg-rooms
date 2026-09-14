#include <inttypes.h>
#include <stdio.h>
#include "finders.h"

/* Private stdin protocol. Never echo seeds, including on errors. */
int main(void) {
    Generator generator;
    setupGenerator(&generator, MC_1_16_1, 0);
    puts("ZSG_SPAWN_1 e61f90580cbdd883214a8054670dacae655e59c0");
    fflush(stdout);
    int64_t seed;
    int status;
    while ((status = scanf("%" SCNd64, &seed)) == 1) {
        applySeed(&generator, DIM_OVERWORLD, (uint64_t)seed);
        Pos spawn = getSpawn(&generator);
        printf("%d %d\n", spawn.x, spawn.z);
        fflush(stdout);
    }
    return status == EOF ? 0 : 2;
}
