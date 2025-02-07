/*
 * Copyright (C) 2022 Sebastian Krieter
 *
 * This file is part of formula-analysis-sat4j.
 *
 * formula-analysis-sat4j is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * formula-analysis-sat4j is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with formula-analysis-sat4j. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/FeatureIDE/FeatJAR-formula-analysis-sat4j> for further information.
 */
package de.featjar.formula.analysis.sat4j.todo.twise;

import de.featjar.formula.analysis.todo.mig.solver.ModalImplicationGraph;
import de.featjar.formula.analysis.sat4j.todo.configuration.AbstractConfigurationGenerator;
import de.featjar.formula.analysis.sat4j.solver.ISelectionStrategy;
import de.featjar.formula.analysis.bool.ABooleanAssignmentList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * YASA sampling algorithm. Generates configurations for a given propositional
 * formula such that t-wise feature coverage is achieved.
 *
 * @author Sebastian Krieter
 */
public class TWiseConfigurationGenerator extends AbstractConfigurationGenerator {
    enum Deduce {
        DP,
        AC,
        NONE
    }

    enum Order {
        RANDOM,
        SORTED
    }

    enum Phase {
        MULTI,
        SINGLE
    }

    /**
     * Converts a set of single literals into a grouped expression list.
     *
     * @param literalSet the literal set
     * @return a grouped expression list (can be used as an input for the
     *         configuration generator).
     */
    public static List<List<ABooleanAssignmentList>> convertLiterals(SortedIntegerList literalSet) {
        return TWiseCombiner.convertGroupedLiterals(Arrays.asList(literalSet));
    }

    /**
     * Converts a grouped set of single literals into a grouped expression list.
     *
     * @param groupedLiterals the grouped literal sets
     * @return a grouped expression list (can be used as an input for the
     *         configuration generator).
     */
    public static List<List<ABooleanAssignmentList>> convertGroupedLiterals(List<SortedIntegerList> groupedLiterals) {
        return TWiseCombiner.convertGroupedLiterals(groupedLiterals);
    }

    /**
     * Converts an expression list into a grouped expression set with a single
     * group.
     *
     * @param expressions the expression list
     * @return a grouped expression list (can be used as an input for the
     *         configuration generator).
     */
    public static List<List<ABooleanAssignmentList>> convertExpressions(List<ABooleanAssignmentList> expressions) {
        return TWiseCombiner.convertExpressions(expressions);
    }

    public static boolean VERBOSE = false;

    public static final int DEFAULT_ITERATIONS = 5;
    public static final int DEFAULT_RANDOM_SAMPLE_SIZE = 100;
    public static final int DEFAULT_LOG_FREQUENCY = 60_000;

    // TODO Variation Point: Iterations of removing low-contributing Configurations
    private int iterations = DEFAULT_ITERATIONS;
    private int randomSampleSize = DEFAULT_RANDOM_SAMPLE_SIZE;
    private int logFrequency = DEFAULT_LOG_FREQUENCY;
    private boolean useMig = true;
    private ModalImplicationGraph modalImplicationGraph;
    private Deduce createConfigurationDeduce = Deduce.DP;
    private Deduce extendConfigurationDeduce = Deduce.NONE;

    protected TWiseConfigurationUtil util;
    protected TWiseCombiner combiner;
    protected Random random = new Random(0);

    protected int t;
    protected List<List<ABooleanAssignmentList>> nodes;
    protected PresenceConditionManager presenceConditionManager;

    protected long numberOfCombinations, count, coveredCount, invalidCount;
    protected int phaseCount;

    private List<TWiseConfiguration> curResult = null;
    private ArrayList<TWiseConfiguration> bestResult = null;

    protected IntervalThread samplingMonitor;
    protected IntervalThread memoryMonitor;

    private int maxSampleSize = Integer.MAX_VALUE;

    public int getMaxSampleSize() {
        return maxSampleSize;
    }

    public void setMaxSampleSize(int maxSampleSize) {
        this.maxSampleSize = maxSampleSize;
    }

    public int getT() {
        return t;
    }

    public void setT(int t) {
        this.t = t;
    }

    public List<List<ABooleanAssignmentList>> getNodes() {
        return nodes;
    }

    public void setNodes(List<List<ABooleanAssignmentList>> nodes) {
        this.nodes = nodes;
    }

    @Override
    public Random random {
        return random;
    }

    @Override
    public void setRandom(Random random) {
        this.random = random;
    }

    @Override
    protected void init(IMonitor monitor) {
        FeatJAR.log().debug("Create util instance... ");
        final CNF cnf = solver.getCnf();
        solver.rememberSolutionHistory(10);
        solver.setSelectionStrategy(ISelectionStrategy.random(random));

        if (nodes == null) {
            nodes = convertLiterals(SortedIntegerList.getLiterals(cnf));
        }
        if (cnf.getClauseList().isEmpty()) {
            util = new TWiseConfigurationUtil(cnf, null);
        } else {
            util = new TWiseConfigurationUtil(cnf, solver);
        }
        util.setMaxSampleSize(maxSampleSize);
        util.setRandom(random);
        util.setCreateConfigurationDeduce(createConfigurationDeduce);
        util.setExtendConfigurationDeduce(extendConfigurationDeduce);

        FeatJAR.log().debug("Compute random sample... ");

        if (!cnf.getClauseList().isEmpty()) {
            util.computeRandomSample(randomSampleSize);
            if (useMig) {
                if (modalImplicationGraph != null) {
                    util.setMIG(modalImplicationGraph);
                } else {
                    util.computeMIG(false, false);
                }
            }
        }

        FeatJAR.log().debug("Set up PresenceConditionManager... ");

        // TODO Variation Point: Sorting Nodes
        presenceConditionManager = new PresenceConditionManager(util, nodes);
        // TODO Variation Point: Building Combinations
        combiner = new TWiseCombiner(cnf.getVariableMap().getVariableCount());

        phaseCount = 0;

        //		memoryMonitor = new UpdateThread(new MemoryMonitor(), 1);
        //		memoryMonitor.start();
        //		if (TWiseConfigurationGenerator.VERBOSE) {
        //			samplingMonitor = new UpdateThread(this::printStatus, logFrequency);
        //			samplingMonitor.start();
        //		}
        try {
            for (int i = 0; i < iterations; i++) {
                trimConfigurations();
                buildCombinations();
            }
            Collections.reverse(bestResult);
        } finally {
            //			memoryMonitor.finish();
            if (TWiseConfigurationGenerator.VERBOSE) {
                samplingMonitor.interrupt();
            }
        }
    }

    @Override
    public SortedIntegerList get() {
        return bestResult.isEmpty()
                ? null
                : bestResult.remove(bestResult.size() - 1).getCompleteSolution();
    }

    private void trimConfigurations() {
        if ((curResult != null) && !curResult.isEmpty()) {
            final CoverageStatistic statistic = new TWiseStatisticFastGenerator()
                    .getCoverage(curResult, presenceConditionManager.getGroupedPresenceConditions(), t);

            final double[] normConfigValues = statistic.getConfigScores();
            double mean = 0;
            for (final double d : normConfigValues) {
                mean += d;
            }
            mean /= normConfigValues.length;

            final double reference = mean;

            int index = 0;
            index = removeSolutions(normConfigValues, reference, index, util.getIncompleteSolutionList());
            index = removeSolutions(normConfigValues, reference, index, util.getCompleteSolutionList());
        }
    }

    private int removeSolutions(
            double[] values, final double reference, int index, List<TWiseConfiguration> solutionList) {
        for (final Iterator<TWiseConfiguration> iterator = solutionList.iterator(); iterator.hasNext(); ) {
            iterator.next();
            if (values[index++] < reference) {
                iterator.remove();
            }
        }
        return index;
    }

    private void buildCombinations() {
        // TODO Variation Point: Cover Strategies
        final List<? extends ICoverStrategy> phaseList = Arrays.asList( //
                new CoverAll(util) //
                );

        // TODO Variation Point: Combination order
        final ICombinationSupplier<ABooleanAssignmentList> it;
        presenceConditionManager.shuffleSort(random);
        final List<List<PresenceCondition>> groupedPresenceConditions =
                presenceConditionManager.getGroupedPresenceConditions();
        if (groupedPresenceConditions.size() == 1) {
            it = new SingleIterator(
                    t, util.getCnf().getVariableMap().getVariableCount(), groupedPresenceConditions.get(0));
        } else {
            it = new MergeIterator3(t, util.getCnf().getVariableMap().getVariableCount(), groupedPresenceConditions);
        }
        numberOfCombinations = it.size();

        coveredCount = 0;
        invalidCount = 0;

        final List<ABooleanAssignmentList> combinationListUncovered = new ArrayList<>();
        count = coveredCount;
        phaseCount++;
        ICoverStrategy phase = phaseList.get(0);
        while (true) {
            final ABooleanAssignmentList combinedCondition = it.get();
            if (combinedCondition == null) {
                break;
            }
            if (combinedCondition.isEmpty()) {
                invalidCount++;
            } else {
                final ICoverStrategy.CombinationStatus covered = phase.cover(combinedCondition);
                switch (covered) {
                    case NOT_COVERED:
                        combinationListUncovered.add(combinedCondition);
                        break;
                    case COVERED:
                        coveredCount++;
                        combinedCondition.clear();
                        break;
                    case INVALID:
                        invalidCount++;
                        combinedCondition.clear();
                        break;
                    default:
                        combinedCondition.clear();
                        break;
                }
            }
            count++;
        }

        int coveredIndex = -1;
        for (int j = 1; j < phaseList.size(); j++) {
            phaseCount++;
            phase = phaseList.get(j);
            count = coveredCount + invalidCount;
            for (int i = coveredIndex + 1; i < combinationListUncovered.size(); i++) {
                final ABooleanAssignmentList combination = combinationListUncovered.get(i);
                final ICoverStrategy.CombinationStatus covered = phase.cover(combination);
                switch (covered) {
                    case COVERED:
                        Collections.swap(combinationListUncovered, i, ++coveredIndex);
                        coveredCount++;
                        break;
                    case NOT_COVERED:
                        break;
                    case INVALID:
                        Collections.swap(combinationListUncovered, i, ++coveredIndex);
                        invalidCount++;
                        break;
                    default:
                        break;
                }
                count++;
            }
        }

        curResult = util.getResultList();
        if ((bestResult == null) || (bestResult.size() > curResult.size())) {
            bestResult = new ArrayList<>(curResult.size());
            curResult.stream().map(TWiseConfiguration::clone).forEach(bestResult::add);
        }
    }

    public boolean printStatus() {
        if (VERBOSE) {
            final long uncoveredCount = (numberOfCombinations - coveredCount) - invalidCount;
            final double phaseProgress =
                    ((int) Math.floor((1 - (((double) count) / numberOfCombinations)) * 1000)) / 10.0;
            final double coverProgress =
                    ((int) Math.floor(((((double) coveredCount) / numberOfCombinations)) * 1000)) / 10.0;
            final double uncoverProgress =
                    ((int) Math.floor(((((double) uncoveredCount) / numberOfCombinations)) * 1000)) / 10.0;
            final double invalidProgress =
                    ((int) Math.floor(((((double) invalidCount) / numberOfCombinations)) * 1000)) / 10.0;
            final StringBuilder sb = new StringBuilder();

            sb.append(phaseCount);
            sb.append(" - ");
            sb.append(phaseProgress);
            sb.append(" (");
            sb.append(count);

            sb.append(") -- Configurations: ");
            sb.append(util.getIncompleteSolutionList().size()
                    + util.getCompleteSolutionList().size());
            sb.append(" (");
            sb.append(util.getIncompleteSolutionList().size());
            sb.append(" | ");
            sb.append(util.getCompleteSolutionList().size());

            sb.append(") -- Covered: ");
            sb.append(coverProgress);
            sb.append(" (");
            sb.append(coveredCount);
            sb.append(")");

            sb.append(" -- Uncovered: ");
            sb.append(uncoverProgress);
            sb.append(" (");
            sb.append(uncoveredCount);
            sb.append(")");

            sb.append(" -- Invalid: ");
            sb.append(invalidProgress);
            sb.append(" (");
            sb.append(invalidCount);
            sb.append(")");
            FeatJAR.log().progress(sb.toString());
        }
        return true;
    }

    public TWiseConfigurationUtil getUtil() {
        return util;
    }

    public int getIterations() {
        return iterations;
    }

    public void setIterations(int iterations) {
        this.iterations = iterations;
    }

    public int getRandomSampleSize() {
        return randomSampleSize;
    }

    public void setRandomSampleSize(int randomSampleSize) {
        this.randomSampleSize = randomSampleSize;
    }

    public boolean isUseMig() {
        return useMig;
    }

    public void setUseMig(boolean useMig) {
        this.useMig = useMig;
    }

    public void setMIG(ModalImplicationGraph modalImplicationGraph) {
        this.modalImplicationGraph = modalImplicationGraph;
    }

    public ModalImplicationGraph getMig() {
        return modalImplicationGraph;
    }

    public void setMig(ModalImplicationGraph modalImplicationGraph) {
        this.modalImplicationGraph = modalImplicationGraph;
    }

    public int getLogFrequency() {
        return logFrequency;
    }

    public void setLogFrequency(int logFrequency) {
        this.logFrequency = logFrequency;
    }

    public Deduce getCreateConfigurationDeduce() {
        return createConfigurationDeduce;
    }

    public void setCreateConfigurationDeduce(Deduce createConfigurationDeduce) {
        this.createConfigurationDeduce = createConfigurationDeduce;
    }

    public Deduce getExtendConfigurationDeduce() {
        return extendConfigurationDeduce;
    }

    public void setExtendConfigurationDeduce(Deduce extendConfigurationDeduce) {
        this.extendConfigurationDeduce = extendConfigurationDeduce;
    }
}
