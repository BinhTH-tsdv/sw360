/*
 * Copyright Siemens AG, 2013-2015, 2019. Part of the SW360 Portal Project.
 *
 * SPDX-License-Identifier: EPL-1.0
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.sw360.datahandler.businessrules.jgivens;

import com.tngtech.jgiven.Stage;
import com.tngtech.jgiven.annotation.ExpectedScenarioState;
import com.tngtech.jgiven.annotation.ProvidedScenarioState;
import com.tngtech.jgiven.annotation.Quoted;

import org.eclipse.sw360.datahandler.TEnumToString;
import org.eclipse.sw360.datahandler.businessrules.ReleaseClearingStateSummaryComputer;
import org.eclipse.sw360.datahandler.thrift.components.*;

import org.junit.internal.AssumptionViolatedException;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

/**
 * @author daniele.fognini@tngtech.com
 */
public class WhenComputeClearingState extends Stage<WhenComputeClearingState> {
    @ExpectedScenarioState
    List<Release> releaseList;


    @ProvidedScenarioState
    ReleaseClearingStateSummary clearingState;

    private String lastTestedClearingTeam;

    public WhenComputeClearingState the_clearing_state_is_computed_for(@Quoted String clearingTeam) {
        this.lastTestedClearingTeam = clearingTeam;
        clearingState = ReleaseClearingStateSummaryComputer.computeReleaseClearingStateSummary(releaseList, this.lastTestedClearingTeam);
        return self();
    }

    public WhenComputeClearingState team_$_sets_external_tool_workflow_status_$_and_external_tool_status_$(
            @Quoted String clearingTeam,
            @TEnumToString ExternalToolWorkflowStatus w1, @TEnumToString ExternalToolStatus s1) {
        Release release = getFirstRelease();
        Set<ExternalToolRequest> externalToolRequests = release.getExternalToolRequests();

        ExternalToolRequest clearingTeamsRequest = externalToolRequests.stream()
                .filter(etr -> clearingTeam.equals(etr.getToolUserGroup())).findFirst().get();

        clearingTeamsRequest.setExternalToolWorkflowStatus(w1);
        clearingTeamsRequest.setExternalToolStatus(s1);

        the_clearing_state_is_computed_for(lastTestedClearingTeam);
        return self();
    }

    public WhenComputeClearingState the_release_is_sent_for_clearing_to(@Quoted String clearingTeam) {
        Release release = getFirstRelease();

        ExternalToolRequest etr = new ExternalToolRequest();
        etr.setExternalTool(ExternalTool.FOSSOLOGY);
        etr.setExternalToolWorkflowStatus(ExternalToolWorkflowStatus.SENT);
        etr.setExternalToolStatus(ExternalToolStatus.OPEN);
        etr.setToolUserGroup(clearingTeam);

        Set<ExternalToolRequest> externalToolRequests = release.getExternalToolRequests();
        externalToolRequests.add(etr);

        the_clearing_state_is_computed_for(lastTestedClearingTeam);
        return self();
    }

    public WhenComputeClearingState the_release_clearing_state_is_set_to(@TEnumToString ClearingState clearingState) {
        Release release = getFirstRelease();
        Mockito.when(release.getClearingState()).thenReturn(clearingState);

        the_clearing_state_is_computed_for(lastTestedClearingTeam);
        return self();
    }

    private Release getFirstRelease() {
        if (releaseList.size() != 1) {
            throw new AssumptionViolatedException("this test can only handle one release, add a 'n-th' release parameter");
        }
        return releaseList.get(0);
    }
}
