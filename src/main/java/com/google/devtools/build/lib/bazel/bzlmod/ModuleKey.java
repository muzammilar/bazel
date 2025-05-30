// Copyright 2021 The Bazel Authors. All rights reserved.
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
//

package com.google.devtools.build.lib.bazel.bzlmod;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.cmdline.RepositoryName;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec;
import java.util.Comparator;
import java.util.List;

/**
 * A module name, version pair that identifies a node in the external dependency graph.
 *
 * @param name The name of the module. Must be empty for the root module.
 * @param version The version of the module. Must be empty iff the module has a {@link
 *     NonRegistryOverride}.
 */
@AutoCodec
public record ModuleKey(String name, Version version) {

  /**
   * A mapping from module name to repository name for certain special "well-known" modules.
   *
   * <p>The repository name of certain modules are required to be exact strings (instead of the
   * normal format seen in {@link #getCanonicalRepoName(boolean)}) due to backwards compatibility
   * reasons. For example, bazel_tools must be known as "@bazel_tools" for WORKSPACE repos to work
   * correctly.
   */
  // Keep in sync with src/tools/bzlmod/utils.bzl.
  private static final ImmutableMap<String, RepositoryName> WELL_KNOWN_MODULES =
      ImmutableMap.of(
          "bazel_tools",
          RepositoryName.BAZEL_TOOLS,
          // Ensures that references to "@platforms" in WORKSPACE files resolve to the repository of
          // the "platforms" module. Without this, constraints on toolchains registered in WORKSPACE
          // would reference the "platforms" repository defined in the WORKSPACE suffix, whereas
          // the host constraints generated by local_config_platform would reference the "platforms"
          // module repository, resulting in a toolchain resolution mismatch.
          "platforms",
          RepositoryName.createUnvalidated("platforms"));

  public static final ModuleKey ROOT = new ModuleKey("", Version.EMPTY);

  public static final Comparator<ModuleKey> LEXICOGRAPHIC_COMPARATOR =
      Comparator.comparing(ModuleKey::name).thenComparing(ModuleKey::version);

  @Override
  public String toString() {
    if (this.equals(ROOT)) {
      return "<root>";
    }
    return name + "@" + (version.isEmpty() ? "_" : version.toString());
  }

  /** Returns a string such as "root module" or "module foo@1.2.3" for display purposes. */
  public String toDisplayString() {
    if (this.equals(ROOT)) {
      return "root module";
    }
    return String.format("module '%s'", this);
  }

  /**
   * Returns the canonical name of the repo backing this module, including its version. This name is
   * always guaranteed to be unique.
   *
   * <p>This method must not be called if the module has a {@link NonRegistryOverride}.
   */
  public RepositoryName getCanonicalRepoNameWithVersion() {
    return getCanonicalRepoName(/* includeVersion= */ true);
  }

  /**
   * Returns the canonical name of the repo backing this module, excluding its version. This name is
   * only guaranteed to be unique when there is a single version of the module in the entire dep
   * graph.
   */
  public RepositoryName getCanonicalRepoNameWithoutVersion() {
    return getCanonicalRepoName(/* includeVersion= */ false);
  }

  private RepositoryName getCanonicalRepoName(boolean includeVersion) {
    if (WELL_KNOWN_MODULES.containsKey(name)) {
      return WELL_KNOWN_MODULES.get(name);
    }
    if (ROOT.equals(this)) {
      return RepositoryName.MAIN;
    }
    String suffix;
    if (includeVersion) {
      // getVersion().isEmpty() is true only for modules with non-registry overrides, which enforce
      // that there is a single version of the module in the dep graph.
      Preconditions.checkState(!version.isEmpty());
      suffix = version.toString();
    } else {
      // This results in canonical repository names such as `rules_foo+` for the module `rules_foo`.
      // This particular format is chosen since:
      // * The plus ensures that canonical and apparent repository names can be distinguished even
      //   in contexts where users don't rely on `@` vs. `@@` to distinguish between them. For
      //   example, this means that the repo mapping as applied by runfiles libraries is idempotent.
      // * Appending a plus even in the case of a unique version means that module repository
      //   names always contain the same number of plus-separated components, which improves
      //   compatibility with existing logic based on the `rules_foo+1.2.3` format.
      // * By making it so that the module name and the canonical repository name of a module are
      //   never identical, even when using an override, we introduce "grease" that intentionally
      //   tickles bugs in code that doesn't properly distinguish between the two, e.g., by not
      //   applying repo mappings. Otherwise, these bugs could go unnoticed in BCR test modules and
      //   would only be discovered when used with a `multiple_version_override`, which is very
      //   rarely used.
      suffix = "";
    }
    return RepositoryName.createUnvalidated(String.format("%s+%s", name, suffix));
  }

  public static ModuleKey fromString(String s) throws Version.ParseException {
    if (s.equals("<root>")) {
      return ModuleKey.ROOT;
    }
    List<String> parts = Splitter.on('@').splitToList(s);
    if (parts.get(1).equals("_")) {
      return new ModuleKey(parts.get(0), Version.EMPTY);
    }

    Version version = Version.parse(parts.get(1));
    return new ModuleKey(parts.get(0), version);
  }
}
