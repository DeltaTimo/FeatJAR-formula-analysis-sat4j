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

import de.featjar.formula.analysis.bool.ABooleanAssignmentList;
import de.featjar.base.data.Pair;
import java.util.ArrayList;
import java.util.List;

/**
 * Covers a given {@link ABooleanAssignmentList expressions} within a list of
 * {@link TWiseConfiguration solutions}.
 *
 * @author Sebastian Krieter
 */
class CoverAll implements ICoverStrategy {

    private final TWiseConfigurationUtil util;

    public CoverAll(TWiseConfigurationUtil util) {
        this.util = util;
    }

    private final List<Pair<SortedIntegerList, TWiseConfiguration>> candidatesList = new ArrayList<>();

    @Override
    public CombinationStatus cover(ABooleanAssignmentList nextCondition) {
        if (util.isCovered(nextCondition)) {
            return CombinationStatus.COVERED;
        }

        util.initCandidatesList(nextCondition, candidatesList);

        if (util.hasSolver) {
            if (util.coverSol(candidatesList)) {
                return CombinationStatus.COVERED;
            }

            if (util.removeInvalidClauses(nextCondition, candidatesList)) {
                return CombinationStatus.INVALID;
            }

            if (util.coverSat(candidatesList)) {
                return CombinationStatus.COVERED;
            }
        } else {
            if (util.coverNoSat(candidatesList)) {
                return CombinationStatus.COVERED;
            }
        }

        util.newConfiguration(nextCondition.get(0));
        return CombinationStatus.COVERED;
    }
}
