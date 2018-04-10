/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.locking;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.local.model.RootConfigurationMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class DependencyLockingArtifactVisitor implements DependencyArtifactsVisitor {
    private static final Logger LOGGER = Logging.getLogger(DependencyLockingArtifactVisitor.class);

    private final DependencyLockingProvider dependencyLockingProvider;
    private final String configurationName;
    private Set<String> lockingConstraints = Collections.emptySet();
    private List<ModuleComponentIdentifier> allResolvedModules;
    private Set<String> extraModules;
    private DependencyLockingState dependencyLockingState;

    public DependencyLockingArtifactVisitor(String configurationName, DependencyLockingProvider dependencyLockingProvider) {
        this.configurationName = configurationName;
        this.dependencyLockingProvider = dependencyLockingProvider;
    }

    @Override
    public void startArtifacts(DependencyGraphNode root) {
        if (root.isRoot()) {
            RootConfigurationMetadata metadata = ((RootGraphNode) root).getMetadata();
            dependencyLockingState = metadata.getDependencyLockingState();
            if (dependencyLockingState.hasLockState()) {
                lockingConstraints = Sets.newHashSetWithExpectedSize(dependencyLockingState.getLockedDependencies().size());
                for (DependencyConstraint constraint : dependencyLockingState.getLockedDependencies()) {
                    lockingConstraints.add(constraint.getGroup() + ":" + constraint.getName() + ":" + constraint.getVersionConstraint().getPreferredVersion());
                }
                allResolvedModules = Lists.newArrayListWithCapacity(this.lockingConstraints.size());
                extraModules = new TreeSet<String>();
            } else {
                allResolvedModules = new ArrayList<ModuleComponentIdentifier>();
            }
        }
    }

    @Override
    public void visitNode(DependencyGraphNode node) {
        ComponentIdentifier identifier = node.getOwner().getComponentId();
        if (identifier instanceof ModuleComponentIdentifier) {
            ModuleComponentIdentifier id = (ModuleComponentIdentifier) identifier;
            allResolvedModules.add(id);
            if (dependencyLockingState.hasLockState()) {
                String displayName = id.getDisplayName();
                if (!lockingConstraints.remove(displayName)) {
                    extraModules.add(displayName);
                }
            }
        }
    }

    @Override
    public void visitArtifacts(DependencyGraphNode from, DependencyGraphNode to, int artifactSetId, ArtifactSet artifacts) {
        // No-op
    }

    @Override
    public void visitArtifacts(DependencyGraphNode from, LocalFileDependencyMetadata fileDependency, int artifactSetId, ArtifactSet artifactSet) {
        // No-op
    }

    @Override
    public void finishArtifacts() {
        if (dependencyLockingState.hasLockState()) {
            LOGGER.debug(" Dependency lock not matched '{}', extra resolved modules '{}'", lockingConstraints, extraModules);
            Set<String> notResolvedConstraints = Collections.emptySet();
            if (!lockingConstraints.isEmpty()) {
                notResolvedConstraints = new TreeSet<String>(lockingConstraints);
            }
            if (!notResolvedConstraints.isEmpty() || !extraModules.isEmpty()) {
                throwLockOutOfDateException(notResolvedConstraints, extraModules);
            }
        }
    }

    private void throwLockOutOfDateException(Set<String> notResolvedConstraints, Set<String> extraModules) {
        List<String> errors = Lists.newArrayListWithCapacity(notResolvedConstraints.size() + extraModules.size());
        for (String notResolvedConstraint : notResolvedConstraints) {
            errors.add("Dependency graph does not contain '" + notResolvedConstraint + "' which used to be found in the lock file");
        }
        for (String extraModule : extraModules) {
            errors.add("Resolved '" + extraModule + "' which was not found in the lockfile");
        }
        throw LockOutOfDateException.createLockOutOfDateException(configurationName, errors);
    }

    public void complete() {
        dependencyLockingProvider.persistResolvedDependencies(configurationName, allResolvedModules);
    }
}
