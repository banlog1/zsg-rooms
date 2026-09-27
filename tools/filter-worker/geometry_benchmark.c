/* Diagnostic only: reuse the current finder helpers without changing its executable. */
#define main(...) finder_unused_main(__VA_ARGS__)
#include "seed_finder.c"
#undef main

static int geometry_only(uint64_t seed, Family *f) {
    /* The GEOMETRY predicate is identical for temple and AA temple. */
    return nearby(seed,Bastion,origin,96,&f->bastion)
        && nearby(seed,Fortress,origin,256,&f->fortress)
        && getStructurePos(Desert_Pyramid,MC_1_16_1,seed,0,0,&f->main)
        && axes(f->main,origin,320);
}

typedef struct { uint64_t passes, hash; double ms, stage; } Measurement;

static Measurement measure(uint64_t count, uint64_t stream, int bookkeeping) {
    Measurement m={0,UINT64_C(0xcbf29ce484222325),0,0};
    Family f={0};
    uint64_t lower=(stream*STEP)&MASK;
    double start=now_ms();
    for(uint64_t i=0;i<count;i++,lower=(lower+STEP)&MASK) {
        /* Match the production loop's deadline read and per-stage timing calls. */
        if(bookkeeping && now_ms()-start>3600000) exit(3);
        double stage=bookkeeping?now_ms():0;
        int pass=geometry_only(lower,&f);
        if(bookkeeping) m.stage+=now_ms()-stage;
        if(pass) {
            m.passes++;
            m.hash=(m.hash^lower)*UINT64_C(0x100000001b3);
            m.hash=(m.hash^(uint32_t)f.main.x)*UINT64_C(0x100000001b3);
            m.hash=(m.hash^(uint32_t)f.main.z)*UINT64_C(0x100000001b3);
            m.hash=(m.hash^(uint32_t)f.bastion.x)*UINT64_C(0x100000001b3);
            m.hash=(m.hash^(uint32_t)f.bastion.z)*UINT64_C(0x100000001b3);
            m.hash=(m.hash^(uint32_t)f.fortress.x)*UINT64_C(0x100000001b3);
            m.hash=(m.hash^(uint32_t)f.fortress.z)*UINT64_C(0x100000001b3);
        }
    }
    m.ms=now_ms()-start;
    return m;
}

int main(int argc, char **argv) {
    if(argc!=3) { fprintf(stderr,"usage: geometry-benchmark families stream\n"); return 2; }
    char *end;
    errno=0;
    uint64_t count=strtoull(argv[1],&end,10);
    if(errno || *end || !count || count>UINT64_C(1000000000) || argv[1][0]=='-') return 2;
    errno=0;
    uint64_t stream=strtoull(argv[2],&end,10);
    if(errno || *end || stream>MASK || argv[2][0]=='-') return 2;
    Measurement expected=measure(10000,stream,0);
    Measurement warm=measure(10000,stream,1);
    if(expected.hash!=warm.hash || expected.passes!=warm.passes) return 3;
    uint64_t hash=0, passes=0;
    for(int repeat=0;repeat<3;repeat++) for(int order=0;order<2;order++) {
        int bookkeeping=(repeat+order)%2;
        Measurement m=measure(count,stream,bookkeeping);
        if(repeat==0 && order==0) { hash=m.hash; passes=m.passes; }
        if(m.hash!=hash || m.passes!=passes) return 3;
        printf("{\"repeat\":%d,\"bookkeeping\":%s,\"families\":%" PRIu64
            ",\"passes\":%" PRIu64 ",\"digest\":\"%016" PRIx64
            "\",\"elapsedMs\":%.3f,\"geometryTimedMs\":%.3f}\n",
            repeat,bookkeeping?"true":"false",count,m.passes,m.hash,m.ms,m.stage);
        fflush(stdout);
    }
    return 0;
}
