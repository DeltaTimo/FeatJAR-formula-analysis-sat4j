/*
 * Copyright (C) 2023 Sebastian Krieter
 *
 * This file is part of FeatJAR-formula-analysis-sat4j.
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
package de.featjar.formula.analysis.sat4j.solver;

import de.featjar.base.FeatJAR;
import de.featjar.base.computation.ITimeoutDependency;
import de.featjar.base.data.Problem;
import de.featjar.base.data.Result;
import de.featjar.formula.analysis.bool.ABooleanAssignment;
import de.featjar.formula.analysis.bool.BooleanClauseList;
import de.featjar.formula.analysis.bool.BooleanSolution;
import org.sat4j.core.VecInt;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.TimeoutException;

import java.time.Duration;
import java.util.Objects;

/**
 * Base class for solvers using Sat4J.
 *
 * @author Sebastian Krieter
 * @author Elias Kuiter
 */
public abstract class SAT4JSolver implements de.featjar.formula.analysis.ISolver {
    protected final ISolver internalSolver = newInternalSolver();
    protected final SAT4JClauseList clauseList;
    protected final SAT4JAssignment assignment = new SAT4JAssignment();
    protected ISolutionHistory solutionHistory = new ISolutionHistory.RememberUpTo(1000);
    protected Duration timeout = ITimeoutDependency.DEFAULT_TIMEOUT;
    protected boolean globalTimeout;

    protected boolean isTimeoutOccurred;
    protected boolean trivialContradictionFound;

    public SAT4JSolver(BooleanClauseList clauseList) {
        internalSolver.setDBSimplificationAllowed(true);
        internalSolver.setKeepSolverHot(true);
        internalSolver.setVerbose(false);
        this.clauseList = new SAT4JClauseList(this, clauseList);

        final int size = clauseList.getVariableCount();
        try {
            if (!clauseList.isEmpty()) {
                internalSolver.setExpectedNumberOfClauses(clauseList.size() + 1);
            }
            if (size > 0) {
                // due to a bug in SAT4J, each variable must be mentioned at least once
                // so, add a single pseudo-clause that is tautological and mentions every variable
                final VecInt pseudoClause = new VecInt(size + 1);
                for (int i = 1; i <= size; i++) {
                    pseudoClause.push(i);
                }
                pseudoClause.push(-1);
                internalSolver.addClause(pseudoClause);
            }
        } catch (ContradictionException ignored) {
            trivialContradictionFound = true;
        }
    }

    protected abstract ISolver newInternalSolver();

    public SAT4JClauseList getClauseList() {
        return clauseList;
    }

    public SAT4JAssignment getAssignment() {
        return assignment;
    }

    public ISolutionHistory getSolutionHistory() {
        return solutionHistory;
    }

    public void setSolutionHistory(ISolutionHistory solutionHistory) {
        this.solutionHistory = solutionHistory;
    }

    @Override
    public Duration getTimeout() {
        return timeout;
    }

    @Override
    public void setTimeout(Duration timeout) {
        Objects.requireNonNull(timeout);
        FeatJAR.log().debug("setting timeout to " + timeout);
        this.timeout = timeout;
        if (!timeout.isZero())
            internalSolver.setTimeoutMs(timeout.toMillis());
        else internalSolver.expireTimeout();
    }

    public boolean isGlobalTimeout() {
        return globalTimeout;
    }

    public void setGlobalTimeout(boolean globalTimeout) {
        this.globalTimeout = globalTimeout;
    }

    @Override
    public boolean isTimeoutOccurred() {
        return isTimeoutOccurred;
    }

    public boolean isTrivialContradictionFound() {
        return trivialContradictionFound;
    }

    public Result<BooleanSolution> findSolution() {
        return hasSolution().equals(Result.of(true)) ? solutionHistory.getLastSolution() : Result.empty();
    }

    protected Result<Boolean> hasSolution(VecInt integers) {
        if (trivialContradictionFound) {
            solutionHistory.setLastSolution(null);
            return Result.of(false);
        }

        for (final BooleanSolution solution : solutionHistory) {
            if (solution.containsAll(integers.toArray())) {
                solutionHistory.setLastSolution(solution);
                return Result.of(true);
            }
        }

        try {
            FeatJAR.log().debug("calling SAT4J");
            if (internalSolver.isSatisfiable(integers, globalTimeout)) {
                BooleanSolution solution = new BooleanSolution(internalSolver.model());
                FeatJAR.log().debug("has solution " + solution);
                solutionHistory.addNewSolution(solution);
                return Result.of(true);
            } else {
                FeatJAR.log().debug("no solution");
                solutionHistory.setLastSolution(null);
                return Result.of(false);
            }
        } catch (final TimeoutException e) {
            FeatJAR.log().debug("solver timeout occurred");
            solutionHistory.setLastSolution(null);
            isTimeoutOccurred = true;
            return Result.empty(de.featjar.formula.analysis.ISolver.getTimeoutProblem(null));
        }
    }

    public Result<Boolean> hasSolution() {
        return hasSolution(assignment.getIntegers());
    }

    /**
     * Does only consider the given {@code assignment} and <b>not</b> the global
     * assignment variable of the solver.
     */
    public Result<Boolean> hasSolution(ABooleanAssignment assignment) {
        return hasSolution(new VecInt(assignment.get()));
    }

    public int[] getInternalSolution() { // todo: refactor
        return getSolutionHistory().getLastSolution().get().get();
    }
}
