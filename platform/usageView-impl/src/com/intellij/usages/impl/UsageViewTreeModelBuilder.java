// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl;

import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

public final class UsageViewTreeModelBuilder extends DefaultTreeModel {
  private final GroupNode.Root myRootNode;

  private final @Nullable TargetsRootNode myTargetsNode;
  private final UsageTarget[] myTargets;
  private UsageTargetNode[] myTargetNodes;

  public UsageViewTreeModelBuilder(@NotNull UsageViewPresentation presentation, UsageTarget @NotNull [] targets) {
    super(GroupNode.createRoot());
    myRootNode = (GroupNode.Root)root;

    String targetsNodeText = presentation.getTargetsNodeText();
    myTargetsNode = targetsNodeText == null ? null : new TargetsRootNode(targetsNodeText);
    myTargets = targets;

    UIUtil.invokeLaterIfNeeded(() -> {
      addTargetNodes();
      setRoot(myRootNode);
    });
  }

  static final class TargetsRootNode extends Node {
    private TargetsRootNode(@NotNull String name) {
      setUserObject(name);
    }

    @Override
    protected boolean isDataValid() {
      return true;
    }

    @Override
    protected boolean isDataReadOnly() {
      return true;
    }

    @Override
    protected boolean isDataExcluded() {
      return false;
    }

    @NotNull
    @Override
    protected String getNodeText() {
      return getUserObject().toString();
    }
  }

  private void addTargetNodes() {
    if (myTargetsNode == null || myTargets.length == 0) {
      return;
    }
    ThreadingAssertions.assertEventDispatchThread();
    myTargetNodes = new UsageTargetNode[myTargets.length];
    myTargetsNode.removeAllChildren();
    for (int i = 0; i < myTargets.length; i++) {
      UsageTarget target = myTargets[i];
      UsageTargetNode targetNode = new UsageTargetNode(target);
      myTargetsNode.add(targetNode);
      myTargetNodes[i] = targetNode;
    }
    myRootNode.addTargetsNode(myTargetsNode, this);
    reload(myTargetsNode);
  }

  UsageNode getFirstUsageNode() {
    return getFirstChildOfType(myRootNode, UsageNode.class);
  }

  @Nullable GroupNode getFirstGroupNode() {
    return getFirstChildOfType(myRootNode, GroupNode.class);
  }

  private static <T> T getFirstChildOfType(@NotNull GroupNode parent, @NotNull Class<T> type) {
    for (Node child : parent.getChildren()) {
      if (type.isInstance(child)) {
        //noinspection unchecked
        return (T)child;
      }
      else if (child instanceof GroupNode groupNode) {
        T result = getFirstChildOfType(groupNode, type);
        if (result != null) return result;
      }
    }
    return null;
  }

  boolean areTargetsValid() {
    if (myTargetNodes == null) return true;
    for (UsageTargetNode targetNode : myTargetNodes) {
      if (!targetNode.isValid()) return false;
    }
    return true;
  }

  void reset() {
    myRootNode.removeAllChildren();
    addTargetNodes();
    reload(myRootNode);
  }

  @Override
  @NotNull
  public Object getRoot() {
    return myRootNode;
  }

  @Override
  public void nodeChanged(TreeNode node) {
    ThreadingAssertions.assertEventDispatchThread();
    super.nodeChanged(node);
  }

  @Override
  public void nodesWereInserted(TreeNode node, int[] childIndices) {
    ThreadingAssertions.assertEventDispatchThread();
    super.nodesWereInserted(node, childIndices);
  }

  @Override
  public void nodesWereRemoved(TreeNode node, int[] childIndices, Object[] removedChildren) {
    ThreadingAssertions.assertEventDispatchThread();
    super.nodesWereRemoved(node, childIndices, removedChildren);
  }

  @Override
  public void nodesChanged(TreeNode node, int[] childIndices) {
    ThreadingAssertions.assertEventDispatchThread();
    super.nodesChanged(node, childIndices);
  }

  @Override
  public void nodeStructureChanged(TreeNode node) {
    ThreadingAssertions.assertEventDispatchThread();
    super.nodeStructureChanged(node);
  }

  @Override
  protected void fireTreeNodesChanged(Object source, Object[] path, int[] childIndices, Object[] children) {
    ThreadingAssertions.assertEventDispatchThread();
    super.fireTreeNodesChanged(source, path, childIndices, children);
  }

  @Override
  protected void fireTreeNodesInserted(Object source, Object[] path, int[] childIndices, Object[] children) {
    ThreadingAssertions.assertEventDispatchThread();
    super.fireTreeNodesInserted(source, path, childIndices, children);
  }

  @Override
  protected void fireTreeNodesRemoved(Object source, Object[] path, int[] childIndices, Object[] children) {
    ThreadingAssertions.assertEventDispatchThread();
    super.fireTreeNodesRemoved(source, path, childIndices, children);
  }

  @Override
  protected void fireTreeStructureChanged(Object source, Object[] path, int[] childIndices, Object[] children) {
    ThreadingAssertions.assertEventDispatchThread();
    super.fireTreeStructureChanged(source, path, childIndices, children);
  }
}
