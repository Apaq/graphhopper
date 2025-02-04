/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.routing;

import com.graphhopper.config.Profile;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.routing.weighting.custom.CustomModelParser;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;

import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;
import static com.graphhopper.routing.weighting.Weighting.INFINITE_U_TURN_COSTS;
import static com.graphhopper.util.Helper.toLowerCase;

public class DefaultWeightingFactory implements WeightingFactory {

    private final BaseGraph graph;
    private final EncodingManager encodingManager;

    public DefaultWeightingFactory(BaseGraph graph, EncodingManager encodingManager) {
        this.graph = graph;
        this.encodingManager = encodingManager;
    }

    @Override
    public Weighting createWeighting(Profile profile, PMap requestHints, boolean disableTurnCosts) {
        // Merge profile hints with request hints, the request hints take precedence.
        // Note that so far we do not check if overwriting the profile hints actually works with the preparation
        // for LM/CH. Later we should also limit the number of parameters that can be used to modify the profile.
        PMap hints = new PMap();
        hints.putAll(profile.getHints());
        hints.putAll(requestHints);

        final String vehicle = profile.getVehicle();
        TurnCostProvider turnCostProvider;
        if (profile.isTurnCosts() && !disableTurnCosts) {
            BooleanEncodedValue turnRestrictionEnc = encodingManager.getTurnBooleanEncodedValue(TurnRestriction.key(vehicle));
            if (turnRestrictionEnc == null)
                throw new IllegalArgumentException("Vehicle " + vehicle + " does not support turn costs");
            int uTurnCosts = hints.getInt(Parameters.Routing.U_TURN_COSTS, INFINITE_U_TURN_COSTS);
            turnCostProvider = new DefaultTurnCostProvider(turnRestrictionEnc, graph.getTurnCostStorage(), uTurnCosts);
        } else {
            turnCostProvider = NO_TURN_COST_PROVIDER;
        }

        String weightingStr = toLowerCase(profile.getWeighting());
        if (weightingStr.isEmpty())
            throw new IllegalArgumentException("You have to specify a weighting");

        Weighting weighting = null;
        BooleanEncodedValue accessEnc = encodingManager.getBooleanEncodedValue(VehicleAccess.key(vehicle));
        DecimalEncodedValue speedEnc = encodingManager.getDecimalEncodedValue(VehicleSpeed.key(vehicle));
        DecimalEncodedValue priorityEnc = encodingManager.hasEncodedValue(VehiclePriority.key(vehicle))
                ? encodingManager.getDecimalEncodedValue(VehiclePriority.key(vehicle))
                : null;
        if (CustomWeighting.NAME.equalsIgnoreCase(weightingStr)) {
            final CustomModel queryCustomModel = requestHints.getObject(CustomModel.KEY, null);
            final CustomModel mergedCustomModel = CustomModel.merge(profile.getCustomModel(), queryCustomModel);
            if (requestHints.has(Parameters.Routing.HEADING_PENALTY))
                mergedCustomModel.setHeadingPenalty(requestHints.getDouble(Parameters.Routing.HEADING_PENALTY, Parameters.Routing.DEFAULT_HEADING_PENALTY));
            weighting = CustomModelParser.createWeighting(accessEnc, speedEnc,
                    priorityEnc, encodingManager, turnCostProvider, mergedCustomModel);
        } else if ("shortest".equalsIgnoreCase(weightingStr)) {
            throw new IllegalArgumentException("Instead of weighting=shortest use weighting=custom with a high distance_influence");
        } else if ("fastest".equalsIgnoreCase(weightingStr)) {
            throw new IllegalArgumentException("Instead of weighting=fastest use weighting=custom with a custom model that avoids road_access == DESTINATION");
        } else if ("curvature".equalsIgnoreCase(weightingStr)) {
            throw new IllegalArgumentException("The curvature weighting is no longer supported since 7.0. Use a custom " +
                    "model with the EncodedValue 'curvature' instead");
        } else if ("short_fastest".equalsIgnoreCase(weightingStr)) {
            throw new IllegalArgumentException("Instead of weighting=short_fastest use weighting=custom with a distance_influence");
        }

        if (weighting == null)
            throw new IllegalArgumentException("Weighting '" + weightingStr + "' not supported");

        return weighting;
    }

}
