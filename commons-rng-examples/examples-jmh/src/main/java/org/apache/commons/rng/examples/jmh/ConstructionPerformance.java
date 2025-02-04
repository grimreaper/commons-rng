/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.rng.examples.jmh;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.core.source32.ISAACRandom;
import org.apache.commons.rng.core.source32.JDKRandom;
import org.apache.commons.rng.core.source32.KISSRandom;
import org.apache.commons.rng.core.source32.MersenneTwister;
import org.apache.commons.rng.core.source32.MultiplyWithCarry256;
import org.apache.commons.rng.core.source32.Well1024a;
import org.apache.commons.rng.core.source32.Well19937a;
import org.apache.commons.rng.core.source32.Well19937c;
import org.apache.commons.rng.core.source32.Well44497a;
import org.apache.commons.rng.core.source32.Well44497b;
import org.apache.commons.rng.core.source32.Well512a;
import org.apache.commons.rng.core.source64.MersenneTwister64;
import org.apache.commons.rng.core.source64.SplitMix64;
import org.apache.commons.rng.core.source64.TwoCmres;
import org.apache.commons.rng.core.source64.XorShift1024Star;
import org.apache.commons.rng.core.util.NumberFactory;
import org.apache.commons.rng.simple.RandomSource;
import org.apache.commons.rng.simple.internal.ProviderBuilder.RandomSourceInternal;

/**
 * Executes a benchmark to compare the speed of construction of random number providers.
 *
 * <p>Note that random number providers are created and then used. Thus the construction time must
 * be analysed together with the run time performance benchmark (see
 * {@link GenerationPerformance}).
 *
 * <pre>
 * [Total time] = [Construction time] + [Run time]
 * </pre>
 *
 * <p>Selection of a suitable random number provider based on construction speed should consider
 * when the construction time is a large fraction of the run time. In the majority of cases the
 * run time will be the largest component of the total time and the provider should be selected
 * based on its other properties such as the period, statistical randomness and speed.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = { "-server", "-Xms512M", "-Xmx512M" })
public class ConstructionPerformance {

    /** The number of different constructor seeds. */
    private static final int SEEDS = 500;
    /**
     * The maximum seed array size. This is irrespective of data type just to ensure
     * there is enough data in the random seeds. The value is for WELL_44497_A.
     */
    private static final int MAX_SEED_SIZE = 1391;
    /** The {@link Long} seeds. */
    private static final Long[] LONG_SEEDS;
    /** The {@link Integer} seeds. */
    private static final Integer[] INTEGER_SEEDS;
    /** The {@code long[]} seeds. */
    private static final long[][] LONG_ARRAY_SEEDS;
    /** The {@code int[]} seeds. */
    private static final int[][] INT_ARRAY_SEEDS;
    /** The {@code byte[]} seeds. */
    private static final byte[][] BYTE_ARRAY_SEEDS;

    static {
        LONG_SEEDS = new Long[SEEDS];
        INTEGER_SEEDS = new Integer[SEEDS];
        LONG_ARRAY_SEEDS = new long[SEEDS][];
        INT_ARRAY_SEEDS = new int[SEEDS][];
        BYTE_ARRAY_SEEDS = new byte[SEEDS][];
        final UniformRandomProvider rng = RandomSource.create(RandomSource.XOR_SHIFT_1024_S);
        for (int i = 0; i < SEEDS; i++) {
            final long[] longArray = new long[MAX_SEED_SIZE];
            final int[] intArray = new int[MAX_SEED_SIZE];
            for (int j = 0; j < MAX_SEED_SIZE; j++) {
                longArray[j] = rng.nextLong();
                intArray[j] = (int) longArray[j];
            }
            LONG_SEEDS[i] = longArray[0];
            INTEGER_SEEDS[i] = intArray[0];
            LONG_ARRAY_SEEDS[i] = longArray;
            INT_ARRAY_SEEDS[i] = intArray;
            BYTE_ARRAY_SEEDS[i] = NumberFactory.makeByteArray(longArray);
        }
    }

    /**
     * The benchmark state (retrieve the various "RandomSource"s).
     */
    @State(Scope.Benchmark)
    public static class Sources {
        /**
         * RNG providers.
         */
        @Param({"JDK",
                "WELL_512_A",
                "WELL_1024_A",
                "WELL_19937_A",
                "WELL_19937_C",
                "WELL_44497_A",
                "WELL_44497_B",
                "MT",
                "ISAAC",
                "SPLIT_MIX_64",
                "XOR_SHIFT_1024_S",
                "TWO_CMRES",
                "MT_64",
                "MWC_256",
                "KISS",
                })
        private String randomSourceName;

        /** The RandomSource. */
        private RandomSource randomSource;

        /** The native seeds. */
        private Object[] nativeSeeds;

        /** The native seeds with arrays truncated to 1 element. */
        private Object[] nativeSeeds1;

        /** The {@code byte[]} seeds, truncated to the appropriate length for the native seed type. */
        private byte[][] byteSeeds;

        /** The implementing class for the random source. */
        private Class<?> implementingClass;

        /** The constructor. */
        private Constructor<Object> constructor;

        /**
         * Gets the random source.
         *
         * @return the random source
         */
        public RandomSource getRandomSource() {
            return randomSource;
        }

        /**
         * Gets the native seeds for the RandomSource.
         *
         * @return the native seeds
         */
        public Object[] getNativeSeeds() {
            return nativeSeeds;
        }

        /**
         * Gets the native seeds for the RandomSource with arrays truncated to length 1.
         *
         * @return the native seeds
         */
        public Object[] getNativeSeeds1() {
            return nativeSeeds1;
        }

        /**
         * Gets the native seeds for the RandomSource.
         *
         * @return the native seeds
         */
        public byte[][] getByteSeeds() {
            return byteSeeds;
        }

        /**
         * Gets the implementing class.
         *
         * @return the implementing class
         */
        public Class<?> getImplementingClass() {
            return implementingClass;
        }

        /**
         * Gets the constructor.
         *
         * @return the constructor
         */
        public Constructor<Object> getConstructor() {
            return constructor;
        }

        /**
         * Create the random source and the test seeds.
         *
         * @throws NoSuchMethodException If the constructor cannot be found
         */
        @SuppressWarnings("unchecked")
        @Setup(value=Level.Trial)
        public void setup() throws NoSuchMethodException {
            randomSource = RandomSource.valueOf(randomSourceName);
            nativeSeeds = findNativeSeeds(randomSource);

            // Truncate array seeds to length 1
            if (nativeSeeds[0].getClass().isArray()) {
                nativeSeeds1 = new Object[SEEDS];
                for (int i = 0; i < SEEDS; i++) {
                    nativeSeeds1[i] = copy(nativeSeeds[i], 1);
                }
            } else {
                // N/A
                nativeSeeds1 = nativeSeeds;
            }

            // Convert seeds to bytes
            byteSeeds = new byte[SEEDS][];
            final int byteSize = findNativeSeedLength(randomSource) *
                           findNativeSeedElementByteSize(randomSource);
            for (int i = 0; i < SEEDS; i++) {
                byteSeeds[i] = Arrays.copyOf(BYTE_ARRAY_SEEDS[i], byteSize);
            }

            // Cache the class type and constructor
            implementingClass = getRandomSourceInternal(randomSource).getRng();
            constructor = (Constructor<Object>) implementingClass.getConstructor(nativeSeeds[0].getClass());
        }

        /**
         * Copy the specified length of the provided array object
         *
         * @param object the object
         * @param length the length
         * @return the copy
         */
        private static Object copy(Object object, int length) {
            if (object instanceof int[]) {
                return Arrays.copyOf((int[]) object, length);
            }
            if (object instanceof long[]) {
                return Arrays.copyOf((long[]) object, length);
            }
            throw new AssertionError("Unknown seed array");
        }

        /**
         * Find the native seeds for the RandomSource.
         *
         * @param randomSource the random source
         * @return the native seeds
         */
        private static Object[] findNativeSeeds(RandomSource randomSource) {
            switch (randomSource) {
            case TWO_CMRES:
            case TWO_CMRES_SELECT:
                return INTEGER_SEEDS;
            case JDK:
            case SPLIT_MIX_64:
                return LONG_SEEDS;
            case WELL_512_A:
            case WELL_1024_A:
            case WELL_19937_A:
            case WELL_19937_C:
            case WELL_44497_A:
            case WELL_44497_B:
            case MT:
            case ISAAC:
            case MWC_256:
            case KISS:
                return INT_ARRAY_SEEDS;
            case XOR_SHIFT_1024_S:
            case MT_64:
                return LONG_ARRAY_SEEDS;
            default:
                throw new AssertionError("Unknown native seed");
            }
        }

        /**
         * Find the length of the native seed (number of elements).
         *
         * @param randomSource the random source
         * @return the seed length
         */
        private static int findNativeSeedLength(RandomSource randomSource) {
            switch (randomSource) {
            case JDK:
            case SPLIT_MIX_64:
            case TWO_CMRES:
            case TWO_CMRES_SELECT:
                return 1;
            case WELL_512_A:
                return 16;
            case WELL_1024_A:
                return 32;
            case WELL_19937_A:
            case WELL_19937_C:
                return 624;
            case WELL_44497_A:
            case WELL_44497_B:
                return 1391;
            case MT:
                return 624;
            case ISAAC:
                return 256;
            case XOR_SHIFT_1024_S:
                return 16;
            case MT_64:
                return 312;
            case MWC_256:
                return 257;
            case KISS:
                return 4;
            default:
                throw new AssertionError("Unknown native seed size");
            }
        }

        /**
         * Find the byte size of a single element of the native seed.
         *
         * @param randomSource the random source
         * @return the seed element byte size
         */
        private static int findNativeSeedElementByteSize(RandomSource randomSource) {
            switch (randomSource) {
            case JDK:
            case WELL_512_A:
            case WELL_1024_A:
            case WELL_19937_A:
            case WELL_19937_C:
            case WELL_44497_A:
            case WELL_44497_B:
            case MT:
            case ISAAC:
            case TWO_CMRES:
            case TWO_CMRES_SELECT:
            case MWC_256:
            case KISS:
                return 4; // int
            case SPLIT_MIX_64:
            case XOR_SHIFT_1024_S:
            case MT_64:
                return 8; // long
            default:
                throw new AssertionError("Unknown native seed element byte size");
            }
        }

        /**
         * Gets the random source internal.
         *
         * @param randomSource the random source
         * @return the random source internal
         */
        private static RandomSourceInternal getRandomSourceInternal(RandomSource randomSource) {
            switch (randomSource) {
            case JDK: return RandomSourceInternal.JDK;
            case WELL_512_A: return RandomSourceInternal.WELL_512_A;
            case WELL_1024_A: return RandomSourceInternal.WELL_1024_A;
            case WELL_19937_A: return RandomSourceInternal.WELL_19937_A;
            case WELL_19937_C: return RandomSourceInternal.WELL_19937_C;
            case WELL_44497_A: return RandomSourceInternal.WELL_44497_A;
            case WELL_44497_B: return RandomSourceInternal.WELL_44497_B;
            case MT: return RandomSourceInternal.MT;
            case ISAAC: return RandomSourceInternal.ISAAC;
            case TWO_CMRES: return RandomSourceInternal.TWO_CMRES;
            case TWO_CMRES_SELECT: return RandomSourceInternal.TWO_CMRES_SELECT;
            case MWC_256: return RandomSourceInternal.MWC_256;
            case KISS: return RandomSourceInternal.KISS;
            case SPLIT_MIX_64: return RandomSourceInternal.SPLIT_MIX_64;
            case XOR_SHIFT_1024_S: return RandomSourceInternal.XOR_SHIFT_1024_S;
            case MT_64: return RandomSourceInternal.MT_64;
            default:
                throw new AssertionError("Unknown random source internal");
            }
        }
    }

    /**
     * @param bh Data sink.
     */
    @Benchmark
    public void newJDKRandom(Blackhole bh) {
        for (int i = 0; i < SEEDS; i++) {
            bh.consume(new JDKRandom(LONG_SEEDS[i]));
        }
    }

    /**
     * @param bh Data sink.
     */
    @Benchmark
    public void newWell512a(Blackhole bh) {
        for (int i = 0; i < SEEDS; i++) {
            bh.consume(new Well512a(INT_ARRAY_SEEDS[i]));
        }
    }

    /**
     * @param bh Data sink.
     */
    @Benchmark
    public void newWell1024a(Blackhole bh) {
        for (int i = 0; i < SEEDS; i++) {
            bh.consume(new Well1024a(INT_ARRAY_SEEDS[i]));
        }
    }

    /**
     * @param bh Data sink.
     */
    @Benchmark
    public void newWell19937a(Blackhole bh) {
        for (int i = 0; i < SEEDS; i++) {
            bh.consume(new Well19937a(INT_ARRAY_SEEDS[i]));
        }
    }

    /**
     * @param bh Data sink.
     */
    @Benchmark
    public void newWell19937c(Blackhole bh) {
        for (int i = 0; i < SEEDS; i++) {
            bh.consume(new Well19937c(INT_ARRAY_SEEDS[i]));
        }
    }

    /**
     * @param bh Data sink.
     */
    @Benchmark
    public void newWell44497a(Blackhole bh) {
        for (int i = 0; i < SEEDS; i++) {
            bh.consume(new Well44497a(INT_ARRAY_SEEDS[i]));
        }
    }

    /**
     * @param bh Data sink.
     */
    @Benchmark
    public void newWell44497b(Blackhole bh) {
        for (int i = 0; i < SEEDS; i++) {
            bh.consume(new Well44497b(INT_ARRAY_SEEDS[i]));
        }
    }

    /**
     * @param bh Data sink.
     */
    @Benchmark
    public void newMersenneTwister(Blackhole bh) {
        for (int i = 0; i < SEEDS; i++) {
            bh.consume(new MersenneTwister(INT_ARRAY_SEEDS[i]));
        }
    }

    /**
     * @param bh Data sink.
     */
    @Benchmark
    public void newISAACRandom(Blackhole bh) {
        for (int i = 0; i < SEEDS; i++) {
            bh.consume(new ISAACRandom(INT_ARRAY_SEEDS[i]));
        }
    }

    /**
     * @param bh Data sink.
     */
    @Benchmark
    public void newSplitMix64(Blackhole bh) {
        for (int i = 0; i < SEEDS; i++) {
            bh.consume(new SplitMix64(LONG_SEEDS[i]));
        }
    }

    /**
     * @param bh Data sink.
     */
    @Benchmark
    public void newXorShift1024Star(Blackhole bh) {
        for (int i = 0; i < SEEDS; i++) {
            bh.consume(new XorShift1024Star(LONG_ARRAY_SEEDS[i]));
        }
    }

    /**
     * @param bh Data sink.
     */
    @Benchmark
    public void newTwoCmres(Blackhole bh) {
        for (int i = 0; i < SEEDS; i++) {
            bh.consume(new TwoCmres(INTEGER_SEEDS[i]));
        }
    }

    /**
     * @param bh Data sink.
     */
    @Benchmark
    public void newMersenneTwister64(Blackhole bh) {
        for (int i = 0; i < SEEDS; i++) {
            bh.consume(new MersenneTwister64(LONG_ARRAY_SEEDS[i]));
        }
    }

    /**
     * @param bh Data sink.
     */
    @Benchmark
    public void newMultiplyWithCarry256(Blackhole bh) {
        for (int i = 0; i < SEEDS; i++) {
            bh.consume(new MultiplyWithCarry256(INT_ARRAY_SEEDS[i]));
        }
    }

    /**
     * @param bh Data sink.
     */
    @Benchmark
    public void newKISSRandom(Blackhole bh) {
        for (int i = 0; i < SEEDS; i++) {
            bh.consume(new KISSRandom(INT_ARRAY_SEEDS[i]));
        }
    }

    /**
     * Create a new instance using reflection with a cached constructor.
     *
     * @param sources Source of randomness.
     * @param bh      Data sink.
     * @throws Exception If reflection failed.
     */
    @Benchmark
    public void newInstance(Sources sources, Blackhole bh) throws Exception {
        final Object[] nativeSeeds = sources.getNativeSeeds();
        final Constructor<?> constructor = sources.getConstructor();
        for (int i = 0; i < SEEDS; i++) {
            bh.consume(constructor.newInstance(nativeSeeds[i]));
        }
    }

    /**
     * Create a new instance using reflection to lookup the constructor then invoke it.
     *
     * @param sources Source of randomness.
     * @param bh      Data sink.
     * @throws Exception If reflection failed.
     */
    @Benchmark
    public void lookupNewInstance(Sources sources, Blackhole bh) throws Exception {
        final Object[] nativeSeeds = sources.getNativeSeeds();
        final Class<?> implementingClass = sources.getImplementingClass();
        for (int i = 0; i < SEEDS; i++) {
            bh.consume(implementingClass.getConstructor(nativeSeeds[i].getClass()).newInstance(nativeSeeds[i]));
        }
    }

    /**
     * @param sources Source of randomness.
     * @param bh      Data sink.
     */
    @Benchmark
    public void createNullSeed(Sources sources, Blackhole bh) {
        final RandomSource randomSource = sources.getRandomSource();
        for (int i = 0; i < SEEDS; i++) {
            bh.consume(RandomSource.create(randomSource, null));
        }
    }

    /**
     * @param sources Source of randomness.
     * @param bh      Data sink.
     */
    @Benchmark
    public void createNativeSeed(Sources sources, Blackhole bh) {
        final RandomSource randomSource = sources.getRandomSource();
        final Object[] nativeSeeds = sources.getNativeSeeds();
        for (int i = 0; i < SEEDS; i++) {
            bh.consume(RandomSource.create(randomSource, nativeSeeds[i]));
        }
    }

    /**
     * Test the native seed with arrays truncated to length 1. This tests the speed
     * of self-seeding.
     *
     * <p>This test is the same as {@link #createNativeSeed(Sources, Blackhole)} if
     * the random source native seed is not an array.
     *
     * @param sources Source of randomness.
     * @param bh      Data sink.
     */
    @Benchmark
    public void createSelfSeed(Sources sources, Blackhole bh) {
        final RandomSource randomSource = sources.getRandomSource();
        final Object[] nativeSeeds1 = sources.getNativeSeeds1();
        for (int i = 0; i < SEEDS; i++) {
            bh.consume(RandomSource.create(randomSource, nativeSeeds1[i]));
        }
    }

    /**
     * @param sources Source of randomness.
     * @param bh      Data sink.
     */
    @Benchmark
    public void createLongSeed(Sources sources, Blackhole bh) {
        final RandomSource randomSource = sources.getRandomSource();
        for (int i = 0; i < SEEDS; i++) {
            bh.consume(RandomSource.create(randomSource, LONG_SEEDS[i]));
        }
    }

    /**
     * @param sources Source of randomness.
     * @param bh      Data sink.
     */
    @Benchmark
    public void createByteArray(Sources sources, Blackhole bh) {
        final RandomSource randomSource = sources.getRandomSource();
        final byte[][] byteSeeds = sources.getByteSeeds();
        for (int i = 0; i < SEEDS; i++) {
            bh.consume(RandomSource.create(randomSource, byteSeeds[i]));
        }
    }
 }
