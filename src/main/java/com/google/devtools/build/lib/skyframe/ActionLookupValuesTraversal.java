// Copyright 2022 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.skyframe;

import static com.google.devtools.build.lib.skyframe.ArtifactConflictFinder.NUM_JOBS;

import com.google.devtools.build.lib.actions.ActionLookupKey;
import com.google.devtools.build.lib.actions.ActionLookupValue;
import com.google.devtools.build.lib.analysis.ConfiguredTargetValue;
import com.google.devtools.build.lib.analysis.configuredtargets.InputFileConfiguredTarget;
import com.google.devtools.build.lib.analysis.configuredtargets.OutputFileConfiguredTarget;
import com.google.devtools.build.lib.bugreport.BugReport;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.devtools.build.lib.concurrent.Sharder;
import com.google.devtools.build.lib.concurrent.ThreadSafety.ThreadSafe;
import com.google.devtools.build.skyframe.SkyValue;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/** Represents the traversal of the ActionLookupValues in a build. */
public final class ActionLookupValuesTraversal {
  // Some metrics indicate this is a rough average # of ALVs in a build.
  private final Sharder<ActionLookupValue> actionLookupValueShards =
      new Sharder<>(NUM_JOBS, /* expectedTotalSize= */ 200_000);

  // Metrics.
  private final AtomicInteger configuredObjectCount = new AtomicInteger();
  private final AtomicInteger configuredTargetCount = new AtomicInteger();
  private final LongAdder actionCount = new LongAdder();
  private final LongAdder actionCountNotIncludingAspects = new LongAdder();
  private final AtomicInteger inputFileConfiguredTargetCount = new AtomicInteger();
  private final AtomicInteger outputFileConfiguredTargetCount = new AtomicInteger();
  private final AtomicInteger otherConfiguredTargetCount = new AtomicInteger();

  @ThreadSafe
  void accumulate(ActionLookupKey key, SkyValue value) {
    if (value instanceof RemoteConfiguredTargetValue) {
      // Remotely fetched values do not have actions.
      return;
    }

    boolean isConfiguredTarget = value instanceof ConfiguredTargetValue;
    boolean isActionLookupValue = value instanceof ActionLookupValue;
    if (!isConfiguredTarget && !isActionLookupValue) {
      BugReport.sendBugReport(
          new IllegalStateException(
              String.format(
                  "Should only be called with ConfiguredTargetValue or ActionLookupValue: %s %s"
                      + " %s",
                  value.getClass(), key, value)));
      return;
    }
    if (isConfiguredTarget
        && !Objects.equals(
            key.getConfigurationKey(),
            ((ConfiguredTargetValue) value).getConfiguredTarget().getConfigurationKey())) {
      // The configuration of the key doesn't match the configuration of the value. This means that
      // the ConfiguredTargetValue is delegated from a different key. This ConfiguredTargetValue
      // will show up again under its own key. Avoids double counting by skipping accumulation.
      return;
    }
    configuredObjectCount.incrementAndGet();
    if (isConfiguredTarget) {
      configuredTargetCount.incrementAndGet();
    }
    if (isActionLookupValue) {
      ActionLookupValue alv = (ActionLookupValue) value;
      int numActions = alv.getActions().size();
      actionCount.add(numActions);
      if (isConfiguredTarget) {
        actionCountNotIncludingAspects.add(numActions);
      }
      actionLookupValueShards.add(alv);
      return;
    }
    if (!(value instanceof NonRuleConfiguredTargetValue nonRuleVal)) {
      BugReport.sendBugReport(
          new IllegalStateException(
              String.format("Unexpected value type: %s %s %s", value.getClass(), key, value)));
      return;
    }
    AtomicInteger counter =
        switch (nonRuleVal.getConfiguredTarget()) {
          case InputFileConfiguredTarget input -> inputFileConfiguredTargetCount;
          case OutputFileConfiguredTarget output -> outputFileConfiguredTargetCount;
          default -> otherConfiguredTargetCount;
        };
    counter.incrementAndGet();
  }

  Sharder<ActionLookupValue> getActionLookupValueShards() {
    return actionLookupValueShards;
  }

  int getActionCount() {
    return actionCount.intValue();
  }

  BuildEventStreamProtos.BuildMetrics.BuildGraphMetrics.Builder getMetrics() {
    return BuildEventStreamProtos.BuildMetrics.BuildGraphMetrics.newBuilder()
        .setActionLookupValueCount(configuredObjectCount.get())
        .setActionLookupValueCountNotIncludingAspects(configuredTargetCount.get())
        .setActionCount(actionCount.intValue())
        .setActionCountNotIncludingAspects(actionCountNotIncludingAspects.intValue())
        .setInputFileConfiguredTargetCount(inputFileConfiguredTargetCount.get())
        .setOutputFileConfiguredTargetCount(outputFileConfiguredTargetCount.get())
        .setOtherConfiguredTargetCount(otherConfiguredTargetCount.get());
  }
}
