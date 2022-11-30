/*
 *  Copyright 2022 Red Hat
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.wildfly.halos.proxy.dmr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jboss.dmr.ModelNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ModelNodeHelperTest {

    private ModelNode modelNode;
    private ModelNode foo;
    private ModelNode bar;

    /**
     * Creates the model node
     *
     * <pre>
     *     ("foo" => ("bar" => 42))
     * </pre>
     */
    @BeforeEach
    public void setUp() {
        modelNode = new ModelNode();
        foo = new ModelNode();
        bar = new ModelNode().set(42);
        foo.set("bar", bar);
        modelNode.set("foo", foo);
    }

    @Test
    public void nullPath() {
        assertFalse(ModelNodeHelper.failSafeGet(modelNode, null).isDefined());
    }

    @Test
    public void emptyPath() {
        assertFalse(ModelNodeHelper.failSafeGet(modelNode, "").isDefined());
    }

    @Test
    public void invalidPath() {
        assertFalse(ModelNodeHelper.failSafeGet(modelNode, "/").isDefined());
    }

    @Test
    public void wrongPath() {
        assertFalse(ModelNodeHelper.failSafeGet(modelNode, "a").isDefined());
        assertFalse(ModelNodeHelper.failSafeGet(modelNode, "a/b").isDefined());
        assertFalse(ModelNodeHelper.failSafeGet(modelNode, "foo/bar/baz").isDefined());
    }

    @Test
    public void simplePath() {
        ModelNode node = ModelNodeHelper.failSafeGet(modelNode, "foo");
        assertTrue(node.isDefined());
        assertEquals(foo, node);
    }

    @Test
    public void nestedPath() {
        ModelNode node = ModelNodeHelper.failSafeGet(modelNode, "foo.bar");
        assertTrue(node.isDefined());
        assertEquals(bar, node);
    }
}