// Copyright 2023 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.packages;

import com.google.devtools.build.lib.cmdline.Label;
import net.starlark.java.syntax.Location;

/** An input file to the build system which always returns private visibility. */
public class PrivateVisibilityInputFile extends InputFile {

  PrivateVisibilityInputFile(Packageoid pkg, Label label, Location location) {
    super(pkg, label, location);
  }

  @Override
  public boolean isVisibilitySpecified() {
    return true;
  }

  @Override
  public RuleVisibility getVisibility() {
    return RuleVisibility.PRIVATE;
  }
}
