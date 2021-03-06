/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.dataflow;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Sets.intersection;

import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableMap;

import org.checkerframework.dataflow.analysis.AbstractValue;
import org.checkerframework.dataflow.analysis.FlowExpressions;
import org.checkerframework.dataflow.analysis.Store;
import org.checkerframework.dataflow.cfg.node.LocalVariableNode;
import org.checkerframework.dataflow.cfg.node.Node;

import java.util.HashMap;
import java.util.Map;

import javax.lang.model.element.Element;

/**
 * Immutable map from each local variable to its {@link AbstractValue}. Note that, while the
 * interface is written in terms of <b>nodes</b>, the stored data is indexed by variable
 * <b>declaration</b>, so values persist across nodes.
 *
 * <p>To derive a new instance, {@linkplain #toBuilder() create a builder} from an old instance. To
 * start from scratch, call {@link #empty()}.
 *
 * @author deminguyen@google.com (Demi Nguyen)
 */
public final class LocalStore<V extends AbstractValue<V>> implements Store<LocalStore<V>> {

  @SuppressWarnings({"unchecked", "rawtypes"}) // fully variant
  private static final LocalStore<?> EMPTY = new LocalStore(ImmutableMap.of());

  @SuppressWarnings("unchecked") // fully variant
  public static <V extends AbstractValue<V>> LocalStore<V> empty() {
    return (LocalStore<V>) EMPTY;
  }

  private final ImmutableMap<Equivalence.Wrapper<Node>, V> contents;

  private LocalStore(Map<Equivalence.Wrapper<Node>, V> contents) {
    this.contents = ImmutableMap.copyOf(contents);
  }

  public V getInformation(Node node) {
    return contents.get(NodeEquivalance.INSTANCE.wrap(node));
  }

  public Builder<V> toBuilder() {
    return new Builder<V>(this);
  }

  /**
   * Builder for {@link LocalStore} instances. To obtain an instance, obtain a {@link LocalStore}
   * (such as {@link LocalStore#empty()}), and call {@link LocalStore#toBuilder() toBuilder()} on
   * it.
   */
  public static final class Builder<V extends AbstractValue<V>> {
    private final Map<Equivalence.Wrapper<Node>, V> contents;

    Builder(LocalStore<V> prototype) {
      contents = new HashMap<>(prototype.contents);
    }

    public Builder<V> setInformation(Node node, V value) {
      contents.put(NodeEquivalance.INSTANCE.wrap(node), checkNotNull(value));
      return this;
    }

    public V getInformation(Node node) {
      return contents.get(NodeEquivalance.INSTANCE.wrap(node));
    }

    public LocalStore<V> build() {
      return new LocalStore<V>(contents);
    }
  }

  @Override
  public LocalStore<V> copy() {
    // No need to copy because it's immutable.
    return this;
  }

  @Override
  public LocalStore<V> leastUpperBound(LocalStore<V> other) {
    Builder<V> result = LocalStore.<V>empty().toBuilder();
    for (Equivalence.Wrapper<Node> var : intersection(contents.keySet(), other.contents.keySet())) {
      result.contents.put(var, contents.get(var).leastUpperBound(other.contents.get(var)));
    }
    return result.build();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof LocalStore)) {
      return false;
    }
    LocalStore<?> other = (LocalStore<?>) o;
    return contents.equals(other.contents);
  }

  @Override
  public int hashCode() {
    return contents.hashCode();
  }

  @Override
  public String toString() {
    return contents.toString();
  }

  @Override
  public boolean canAlias(FlowExpressions.Receiver a, FlowExpressions.Receiver b) {
    return true;
  }

  @Override
  public boolean hasDOToutput() {
    return false;
  }

  @Override
  public String toDOToutput() {
    throw new UnsupportedOperationException("DOT output not supported");
  }

  private static class NodeEquivalance extends Equivalence<Node> {
    static final NodeEquivalance INSTANCE = new NodeEquivalance();

    @Override
    protected boolean doEquivalent(Node a, Node b) {
      // TODO(cpovirk): Remove equiv wrapper & hack if LocalVariableNode.equals is fixed to use
      // the variable's declaring element instead of its name.
      if (a instanceof LocalVariableNode && b instanceof LocalVariableNode) {
        Element aEl = ((LocalVariableNode) a).getElement();
        Element bEl = ((LocalVariableNode) b).getElement();
        return aEl.equals(bEl);
      }
      return a.equals(b);
    }

    @Override
    protected int doHash(Node n) {
      if (n instanceof LocalVariableNode) {
        return ((LocalVariableNode) n).getElement().hashCode();
      }
      return n.hashCode();
    }
  }
}
