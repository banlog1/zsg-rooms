package zsgrooms.modid.rng;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MirroringRandomTest {
    @Test
    public void boundedResultIsDeterministicAndVanillaAdvancesNormally() {
        Random expectedVanilla = new Random(12345L);
        Random actualVanilla = new Random(12345L);
        Random expectedDeterministic = new Random(98765L);
        MirroringRandom mirrored = new MirroringRandom(
                actualVanilla, new Random(98765L));

        expectedVanilla.nextInt(17);
        int expectedResult = expectedDeterministic.nextInt(17);

        assertEquals(expectedResult, mirrored.nextInt(17));
        assertEquals(expectedVanilla.nextLong(), actualVanilla.nextLong());
    }

    @Test
    public void mixedCallsPreserveBothSequences() {
        Random expectedVanilla = new Random(24680L);
        Random actualVanilla = new Random(24680L);
        Random expectedDeterministic = new Random(13579L);
        MirroringRandom mirrored = new MirroringRandom(
                actualVanilla, new Random(13579L));

        expectedVanilla.nextFloat();
        assertEquals(expectedDeterministic.nextFloat(), mirrored.nextFloat());
        expectedVanilla.nextBoolean();
        assertEquals(expectedDeterministic.nextBoolean(), mirrored.nextBoolean());
        expectedVanilla.nextDouble();
        assertEquals(expectedDeterministic.nextDouble(), mirrored.nextDouble());

        assertEquals(expectedVanilla.nextLong(), actualVanilla.nextLong());
    }
}
