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
package de.featjar.formula.analysis.sat4j;

import de.featjar.base.FeatJAR;
import de.featjar.base.computation.*;
import de.featjar.formula.analysis.IAssumedAssignmentDependency;
import de.featjar.formula.analysis.IAssumedClauseListDependency;
import de.featjar.formula.analysis.bool.ABooleanAssignment;
import de.featjar.formula.analysis.bool.BooleanAssignment;
import de.featjar.formula.analysis.bool.BooleanClauseList;
import de.featjar.formula.analysis.sat4j.solver.SAT4JExplanationSolver;
import de.featjar.formula.analysis.sat4j.solver.SAT4JSolutionSolver;
import de.featjar.formula.analysis.sat4j.solver.SAT4JSolver;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public abstract class ASAT4JAnalysis<T> extends AComputation<T>
        implements IAnalysis<BooleanClauseList, T>,
                IAssumedAssignmentDependency<BooleanAssignment>,
                IAssumedClauseListDependency<BooleanClauseList>,
                ITimeoutDependency {
    protected static final Dependency<BooleanClauseList> BOOLEAN_CLAUSE_LIST = newRequiredDependency();
    protected static final Dependency<BooleanAssignment> ASSUMED_ASSIGNMENT =
            newOptionalDependency(new BooleanAssignment());
    protected static final Dependency<BooleanClauseList> ASSUMED_CLAUSE_LIST =
            newOptionalDependency(new BooleanClauseList());
    protected static final Dependency<Duration> TIMEOUT = newOptionalDependency(ITimeoutDependency.DEFAULT_TIMEOUT);

    public ASAT4JAnalysis(IComputation<BooleanClauseList> booleanClauseList, Dependency<?>... dependencies) {
        List<Dependency<?>> dependenciesList = new ArrayList<>();
        dependenciesList.add(BOOLEAN_CLAUSE_LIST);
        dependenciesList.add(ASSUMED_ASSIGNMENT);
        dependenciesList.add(ASSUMED_CLAUSE_LIST);
        dependenciesList.add(TIMEOUT);
        dependenciesList.addAll(List.of(dependencies));
        dependOn(dependenciesList);
        setInput(booleanClauseList);
    }

    @Override
    public Dependency<BooleanClauseList> getInputDependency() {
        return BOOLEAN_CLAUSE_LIST;
    }

    @Override
    public Dependency<Duration> getTimeoutDependency() {
        return TIMEOUT;
    }

    @Override
    public Dependency<BooleanAssignment> getAssumedAssignmentDependency() {
        return ASSUMED_ASSIGNMENT;
    }

    @Override
    public Dependency<BooleanClauseList> getAssumedClauseListDependency() {
        return ASSUMED_CLAUSE_LIST;
    }

    protected abstract SAT4JSolver newSolver(BooleanClauseList clauseList);

    @SuppressWarnings("unchecked")
    public <U extends SAT4JSolver> U initializeSolver(DependencyList dependencyList) {
        BooleanClauseList clauseList = dependencyList.get(BOOLEAN_CLAUSE_LIST);
        ABooleanAssignment assumedAssignment = dependencyList.get(ASSUMED_ASSIGNMENT);
        BooleanClauseList assumedClauseList = dependencyList.get(ASSUMED_CLAUSE_LIST);
        Duration timeout = dependencyList.get(TIMEOUT);
        FeatJAR.log().debug("initializing SAT4J");
        //                    Feat.log().debug(clauseList.toValue().get());
        //                    Feat.log().debug("assuming " +
        // assumedAssignment.toValue(clauseList.getVariableMap()).getAndLogProblems());
        //                    Feat.log().debug("assuming " + assumedClauseList.toValue().get());
        //                    Feat.log().debug(clauseList.getVariableMap());
        FeatJAR.log().debug(clauseList);
        FeatJAR.log().debug("assuming " + assumedAssignment);
        FeatJAR.log().debug("assuming " + assumedClauseList);
        U solver = (U) newSolver(clauseList);
        solver.getClauseList().addAll(assumedClauseList);
        solver.getAssignment().addAll(assumedAssignment);
        solver.setTimeout(timeout);
        solver.setGlobalTimeout(true);
        return solver;
    }

    public abstract static class Solution<T> extends ASAT4JAnalysis<T> {
        public Solution(IComputation<BooleanClauseList> booleanClauseList, Dependency<?>... dependencies) {
            super(booleanClauseList, dependencies);
        }

        @Override
        protected SAT4JSolutionSolver newSolver(BooleanClauseList clauseList) {
            return new SAT4JSolutionSolver(clauseList);
        }
    }

    abstract static class Explanation<T> extends ASAT4JAnalysis<T> {
        public Explanation(IComputation<BooleanClauseList> booleanClauseList) {
            super(booleanClauseList);
        }

        @Override
        protected SAT4JExplanationSolver newSolver(BooleanClauseList clauseList) {
            return new SAT4JExplanationSolver(clauseList);
        }
    }
}
