/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.client.replicatedmap;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.config.Config;
import com.hazelcast.config.InMemoryFormat;
import com.hazelcast.config.NearCacheConfig;
import com.hazelcast.config.ReplicatedMapConfig;
import com.hazelcast.config.SerializationConfig;
import com.hazelcast.core.EntryEventType;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ReplicatedMap;
import com.hazelcast.nio.serialization.Portable;
import com.hazelcast.nio.serialization.PortableFactory;
import com.hazelcast.nio.serialization.PortableReader;
import com.hazelcast.nio.serialization.PortableWriter;
import com.hazelcast.test.AssertTask;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.WatchedOperationExecutor;
import com.hazelcast.test.annotation.ParallelTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelTest.class})
public class ClientReplicatedMapTest extends HazelcastTestSupport {

    private static final int OPERATION_COUNT = 100;

    private final TestHazelcastFactory hazelcastFactory = new TestHazelcastFactory();

    @After
    public void cleanup() {
        hazelcastFactory.terminateAll();
    }

    @Test
    public void testEmptyMapIsEmpty() {
        hazelcastFactory.newHazelcastInstance();
        HazelcastInstance client = hazelcastFactory.newHazelcastClient();
        ReplicatedMap<Integer, Integer> map = client.getReplicatedMap(randomName());
        assertTrue("map should be empty", map.isEmpty());
    }

    @Test
    public void testNonEmptyMapIsNotEmpty() {
        hazelcastFactory.newHazelcastInstance();
        HazelcastInstance client = hazelcastFactory.newHazelcastClient();
        ReplicatedMap<Integer, Integer> map = client.getReplicatedMap(randomName());
        map.put(1, 1);
        assertFalse("map should not be empty", map.isEmpty());
    }

    @Test
    public void testPutAll() throws TimeoutException {
        HazelcastInstance server = hazelcastFactory.newHazelcastInstance();
        HazelcastInstance client = hazelcastFactory.newHazelcastClient();
        ReplicatedMap<String, String> map1 = client.getReplicatedMap("default");
        ReplicatedMap<String, String> map2 = server.getReplicatedMap("default");

        Map<String, String> mapTest = new HashMap<String, String>();
        for (int i = 0; i < 100; i++) {
            mapTest.put("foo-" + i, "bar");
        }
        map1.putAll(mapTest);
        for (Entry<String, String> entry : map2.entrySet()) {
            assertStartsWith("foo-", entry.getKey());
            assertEquals("bar", entry.getValue());
        }
        for (Entry<String, String> entry : map1.entrySet()) {
            assertStartsWith("foo-", entry.getKey());
            assertEquals("bar", entry.getValue());
        }
    }

    @Test
    public void testGetObjectDelay0() throws Exception {
        testGet(buildConfig(InMemoryFormat.OBJECT, 0));
    }

    @Test
    public void testGetBinaryDelay0() throws Exception {
        testGet(buildConfig(InMemoryFormat.BINARY, 0));
    }

    private void testGet(Config config) throws Exception {
        HazelcastInstance instance1 = hazelcastFactory.newHazelcastInstance(config);
        HazelcastInstance instance2 = hazelcastFactory.newHazelcastClient();

        final ReplicatedMap<String, String> map1 = instance1.getReplicatedMap("default");
        final ReplicatedMap<String, String> map2 = instance2.getReplicatedMap("default");

        WatchedOperationExecutor executor = new WatchedOperationExecutor();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < OPERATION_COUNT; i++) {
                    map1.put("foo-" + i, "bar");
                }
            }
        }, 60, EntryEventType.ADDED, OPERATION_COUNT, 1, map1, map2);

        for (int i = 0; i < OPERATION_COUNT; i++) {
            assertEquals("bar", map1.get("foo-" + i));
            assertEquals("bar", map2.get("foo-" + i));
        }
    }

    @Test
    public void testPutNullReturnValueDeserialization() {
        hazelcastFactory.newHazelcastInstance();
        HazelcastInstance client = hazelcastFactory.newHazelcastClient();
        ReplicatedMap<Object, Object> map = client.getReplicatedMap(randomMapName());
        assertNull(map.put(1, 2));
    }

    @Test
    public void testPutReturnValueDeserialization() {
        hazelcastFactory.newHazelcastInstance();
        HazelcastInstance client = hazelcastFactory.newHazelcastClient();
        ReplicatedMap<Object, Object> map = client.getReplicatedMap(randomMapName());
        map.put(1, 2);
        assertEquals(2, map.put(1, 3));
    }

    @Test
    public void testAddObjectDelay0() throws Exception {
        testAdd(buildConfig(InMemoryFormat.OBJECT, 0));
    }

    @Test
    public void testAddBinaryDelay0() throws Exception {
        testAdd(buildConfig(InMemoryFormat.BINARY, 0));
    }

    private void testAdd(Config config) throws Exception {
        HazelcastInstance instance1 = hazelcastFactory.newHazelcastInstance(config);
        HazelcastInstance instance2 = hazelcastFactory.newHazelcastClient();

        final ReplicatedMap<String, String> map1 = instance1.getReplicatedMap("default");
        final ReplicatedMap<String, String> map2 = instance2.getReplicatedMap("default");

        WatchedOperationExecutor executor = new WatchedOperationExecutor();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < OPERATION_COUNT; i++) {
                    map1.put("foo-" + i, "bar");
                }
            }
        }, 60, EntryEventType.ADDED, OPERATION_COUNT, 1, map1, map2);

        for (Map.Entry<String, String> entry : map2.entrySet()) {
            assertStartsWith("foo-", entry.getKey());
            assertEquals("bar", entry.getValue());
        }

        for (Map.Entry<String, String> entry : map1.entrySet()) {
            assertStartsWith("foo-", entry.getKey());
            assertEquals("bar", entry.getValue());
        }
    }

    @Test
    public void testClearObjectDelay0() throws Exception {
        testClear(buildConfig(InMemoryFormat.OBJECT, 0));
    }

    @Test
    public void testClearBinaryDelay0() throws Exception {
        testClear(buildConfig(InMemoryFormat.BINARY, 0));
    }

    private void testClear(Config config) throws Exception {
        HazelcastInstance instance1 = hazelcastFactory.newHazelcastInstance(config);
        HazelcastInstance instance2 = hazelcastFactory.newHazelcastClient();

        final ReplicatedMap<String, String> map1 = instance1.getReplicatedMap("default");
        final ReplicatedMap<String, String> map2 = instance2.getReplicatedMap("default");

        WatchedOperationExecutor executor = new WatchedOperationExecutor();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < OPERATION_COUNT; i++) {
                    map1.put("foo-" + i, "bar");
                }
            }
        }, 60, EntryEventType.ADDED, OPERATION_COUNT, 1, map1, map2);

        for (Map.Entry<String, String> entry : map2.entrySet()) {
            assertStartsWith("foo-", entry.getKey());
            assertEquals("bar", entry.getValue());
        }

        for (Map.Entry<String, String> entry : map1.entrySet()) {
            assertStartsWith("foo-", entry.getKey());
            assertEquals("bar", entry.getValue());
        }

        // TODO: should clear() be a synchronous operation? what happens on lost clear() message?
        AtomicBoolean happened = new AtomicBoolean(false);
        for (int i = 0; i < 10; i++) {
            map1.clear();
            sleepSeconds(1);
            try {
                assertEquals(0, map1.size());
                assertEquals(0, map2.size());
                happened.set(true);
            } catch (AssertionError ignore) {
                // ignore and retry
            }
            if (happened.get()) {
                break;
            }
        }
    }

    @Test
    public void testUpdateObjectDelay0() throws Exception {
        testUpdate(buildConfig(InMemoryFormat.OBJECT, 0));
    }

    @Test
    public void testUpdateBinaryDelay0() throws Exception {
        testUpdate(buildConfig(InMemoryFormat.BINARY, 0));
    }

    private void testUpdate(Config config) throws Exception {
        HazelcastInstance instance1 = hazelcastFactory.newHazelcastInstance(config);
        HazelcastInstance instance2 = hazelcastFactory.newHazelcastClient();

        final ReplicatedMap<String, String> map1 = instance1.getReplicatedMap("default");
        final ReplicatedMap<String, String> map2 = instance2.getReplicatedMap("default");

        WatchedOperationExecutor executor = new WatchedOperationExecutor();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < OPERATION_COUNT; i++) {
                    map1.put("foo-" + i, "bar");
                }
            }
        }, 60, EntryEventType.ADDED, OPERATION_COUNT, 1, map1, map2);

        for (Map.Entry<String, String> entry : map2.entrySet()) {
            assertStartsWith("foo-", entry.getKey());
            assertEquals("bar", entry.getValue());
        }
        for (Map.Entry<String, String> entry : map1.entrySet()) {
            assertStartsWith("foo-", entry.getKey());
            assertEquals("bar", entry.getValue());
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < OPERATION_COUNT; i++) {
                    map2.put("foo-" + i, "bar2");
                }
            }
        }, 60, EntryEventType.UPDATED, OPERATION_COUNT, 1, map1, map2);

        int map2Updated = 0;
        for (Map.Entry<String, String> entry : map2.entrySet()) {
            if ("bar2".equals(entry.getValue())) {
                map2Updated++;
            }
        }
        int map1Updated = 0;
        for (Map.Entry<String, String> entry : map1.entrySet()) {
            if ("bar2".equals(entry.getValue())) {
                map1Updated++;
            }
        }

        assertMatchSuccessfulOperationQuota(1, OPERATION_COUNT, map1Updated, map2Updated);
    }

    @Test
    public void testRemoveObjectDelay0() throws Exception {
        testRemove(buildConfig(InMemoryFormat.OBJECT, 0));
    }

    @Test
    public void testRemoveBinaryDelay0() throws Exception {
        testRemove(buildConfig(InMemoryFormat.BINARY, 0));
    }

    private void testRemove(Config config) throws Exception {
        HazelcastInstance instance1 = hazelcastFactory.newHazelcastInstance(config);
        HazelcastInstance instance2 = hazelcastFactory.newHazelcastClient();

        final ReplicatedMap<String, String> map1 = instance1.getReplicatedMap("default");
        final ReplicatedMap<String, String> map2 = instance2.getReplicatedMap("default");

        WatchedOperationExecutor executor = new WatchedOperationExecutor();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < OPERATION_COUNT; i++) {
                    map1.put("foo-" + i, "bar");
                }
            }
        }, 60, EntryEventType.ADDED, OPERATION_COUNT, 1, map1, map2);

        for (Map.Entry<String, String> entry : map2.entrySet()) {
            assertStartsWith("foo-", entry.getKey());
            assertEquals("bar", entry.getValue());
        }
        for (Map.Entry<String, String> entry : map1.entrySet()) {
            assertStartsWith("foo-", entry.getKey());
            assertEquals("bar", entry.getValue());
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < OPERATION_COUNT; i++) {
                    map2.remove("foo-" + i);
                }
            }
        }, 60, EntryEventType.REMOVED, OPERATION_COUNT, 1, map1, map2);

        int map2Updated = 0;
        for (int i = 0; i < OPERATION_COUNT; i++) {
            Object value = map2.get("foo-" + i);
            if (value == null) {
                map2Updated++;
            }
        }
        int map1Updated = 0;
        for (int i = 0; i < OPERATION_COUNT; i++) {
            Object value = map1.get("foo-" + i);
            if (value == null) {
                map1Updated++;
            }
        }

        assertMatchSuccessfulOperationQuota(1, OPERATION_COUNT, map1Updated, map2Updated);
    }

    @Test
    public void testSizeObjectDelay0() throws Exception {
        testSize(buildConfig(InMemoryFormat.OBJECT, 0));
    }

    @Test
    public void testSizeBinaryDelay0() throws Exception {
        testSize(buildConfig(InMemoryFormat.BINARY, 0));
    }

    private void testSize(Config config) throws Exception {
        HazelcastInstance instance1 = hazelcastFactory.newHazelcastInstance(config);
        HazelcastInstance instance2 = hazelcastFactory.newHazelcastClient();

        final ReplicatedMap<Integer, Integer> map1 = instance1.getReplicatedMap("default");
        final ReplicatedMap<Integer, Integer> map2 = instance2.getReplicatedMap("default");

        final AbstractMap.SimpleEntry<Integer, Integer>[] testValues = buildTestValues();

        WatchedOperationExecutor executor = new WatchedOperationExecutor();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                int half = testValues.length / 2;
                for (int i = 0; i < testValues.length; i++) {
                    ReplicatedMap<Integer, Integer> map = i < half ? map1 : map2;
                    AbstractMap.SimpleEntry<Integer, Integer> entry = testValues[i];
                    map.put(entry.getKey(), entry.getValue());
                }
            }
        }, 2, EntryEventType.ADDED, 100, 1, map1, map2);

        assertMatchSuccessfulOperationQuota(1, map1.size(), map2.size());
    }

    @Test
    public void testContainsKeyObjectDelay0() throws Exception {
        testContainsKey(buildConfig(InMemoryFormat.OBJECT, 0));
    }

    @Test
    public void testContainsKeyBinaryDelay0() throws Exception {
        testContainsKey(buildConfig(InMemoryFormat.BINARY, 0));
    }

    private void testContainsKey(Config config) throws Exception {
        HazelcastInstance instance1 = hazelcastFactory.newHazelcastInstance(config);
        HazelcastInstance instance2 = hazelcastFactory.newHazelcastClient();

        final ReplicatedMap<String, String> map1 = instance1.getReplicatedMap("default");
        final ReplicatedMap<String, String> map2 = instance2.getReplicatedMap("default");

        WatchedOperationExecutor executor = new WatchedOperationExecutor();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < OPERATION_COUNT; i++) {
                    map1.put("foo-" + i, "bar");
                }
            }
        }, 60, EntryEventType.ADDED, OPERATION_COUNT, 1, map1, map2);

        int map2Contains = 0;
        for (int i = 0; i < OPERATION_COUNT; i++) {
            if (map2.containsKey("foo-" + i)) {
                map2Contains++;
            }
        }
        int map1Contains = 0;
        for (int i = 0; i < OPERATION_COUNT; i++) {
            if (map1.containsKey("foo-" + i)) {
                map1Contains++;
            }
        }

        assertMatchSuccessfulOperationQuota(1, OPERATION_COUNT, map1Contains, map2Contains);
    }

    @Test
    public void testContainsValueObjectDelay0() throws Exception {
        testContainsValue(buildConfig(InMemoryFormat.OBJECT, 0));
    }

    @Test
    public void testContainsValueBinaryDelay0() throws Exception {
        testContainsValue(buildConfig(InMemoryFormat.BINARY, 0));
    }

    private void testContainsValue(Config config) throws Exception {
        HazelcastInstance instance1 = hazelcastFactory.newHazelcastInstance(config);
        HazelcastInstance instance2 = hazelcastFactory.newHazelcastClient();

        final ReplicatedMap<Integer, Integer> map1 = instance1.getReplicatedMap("default");
        final ReplicatedMap<Integer, Integer> map2 = instance2.getReplicatedMap("default");

        final AbstractMap.SimpleEntry<Integer, Integer>[] testValues = buildTestValues();

        WatchedOperationExecutor executor = new WatchedOperationExecutor();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                int half = testValues.length / 2;
                for (int i = 0; i < testValues.length; i++) {
                    ReplicatedMap<Integer, Integer> map = i < half ? map1 : map2;
                    AbstractMap.SimpleEntry<Integer, Integer> entry = testValues[i];
                    map.put(entry.getKey(), entry.getValue());
                }
            }
        }, 2, EntryEventType.ADDED, testValues.length, 1, map1, map2);

        int map2Contains = 0;
        for (AbstractMap.SimpleEntry<Integer, Integer> testValue : testValues) {
            if (map2.containsValue(testValue.getValue())) {
                map2Contains++;
            }
        }
        int map1Contains = 0;
        for (AbstractMap.SimpleEntry<Integer, Integer> testValue : testValues) {
            if (map1.containsValue(testValue.getValue())) {
                map1Contains++;
            }
        }

        assertMatchSuccessfulOperationQuota(1, testValues.length, map1Contains, map2Contains);
    }

    @Test
    public void testValuesObjectDelay0() throws Exception {
        testValues(buildConfig(InMemoryFormat.OBJECT, 0));
    }

    @Test
    public void testValuesBinaryDelay0() throws Exception {
        testValues(buildConfig(InMemoryFormat.BINARY, 0));
    }

    private void testValues(Config config) throws Exception {
        HazelcastInstance instance1 = hazelcastFactory.newHazelcastInstance(config);
        HazelcastInstance instance2 = hazelcastFactory.newHazelcastClient();

        final ReplicatedMap<Integer, Integer> map1 = instance1.getReplicatedMap("default");
        final ReplicatedMap<Integer, Integer> map2 = instance2.getReplicatedMap("default");

        final AbstractMap.SimpleEntry<Integer, Integer>[] testValues = buildTestValues();

        final List<Integer> valuesTestValues = new ArrayList<Integer>(testValues.length);
        WatchedOperationExecutor executor = new WatchedOperationExecutor();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                int half = testValues.length / 2;
                for (int i = 0; i < testValues.length; i++) {
                    ReplicatedMap<Integer, Integer> map = i < half ? map1 : map2;
                    AbstractMap.SimpleEntry<Integer, Integer> entry = testValues[i];
                    map.put(entry.getKey(), entry.getValue());
                    valuesTestValues.add(entry.getValue());
                }
            }
        }, 2, EntryEventType.ADDED, 100, 1, map1, map2);

        List<Integer> values1 = copyToList(map1.values());
        List<Integer> values2 = copyToList(map2.values());

        int map1Contains = 0;
        int map2Contains = 0;
        for (Integer value : valuesTestValues) {
            if (values2.contains(value)) {
                map2Contains++;
            }
            if (values1.contains(value)) {
                map1Contains++;
            }
        }

        assertMatchSuccessfulOperationQuota(1, testValues.length, map1Contains, map2Contains);
    }

    @Test
    public void testKeySetObjectDelay0() throws Exception {
        testKeySet(buildConfig(InMemoryFormat.OBJECT, 0));
    }

    @Test
    public void testKeySetBinaryDelay0() throws Exception {
        testKeySet(buildConfig(InMemoryFormat.BINARY, 0));
    }

    private void testKeySet(Config config) throws Exception {
        HazelcastInstance instance1 = hazelcastFactory.newHazelcastInstance(config);
        HazelcastInstance instance2 = hazelcastFactory.newHazelcastClient();

        final ReplicatedMap<Integer, Integer> map1 = instance1.getReplicatedMap("default");
        final ReplicatedMap<Integer, Integer> map2 = instance2.getReplicatedMap("default");

        final AbstractMap.SimpleEntry<Integer, Integer>[] testValues = buildTestValues();

        final List<Integer> keySetTestValues = new ArrayList<Integer>(testValues.length);
        WatchedOperationExecutor executor = new WatchedOperationExecutor();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                int half = testValues.length / 2;
                for (int i = 0; i < testValues.length; i++) {
                    ReplicatedMap<Integer, Integer> map = i < half ? map1 : map2;
                    AbstractMap.SimpleEntry<Integer, Integer> entry = testValues[i];
                    map.put(entry.getKey(), entry.getValue());
                    keySetTestValues.add(entry.getKey());
                }
            }
        }, 2, EntryEventType.ADDED, 100, 1, map1, map2);

        List<Integer> keySet1 = copyToList(map1.keySet());
        List<Integer> keySet2 = copyToList(map2.keySet());

        int map1Contains = 0;
        int map2Contains = 0;
        for (Integer value : keySetTestValues) {
            if (keySet2.contains(value)) {
                map2Contains++;
            }
            if (keySet1.contains(value)) {
                map1Contains++;
            }
        }

        assertMatchSuccessfulOperationQuota(1, testValues.length, map1Contains, map2Contains);
    }

    @Test
    public void testEntrySetObjectDelay0() throws Exception {
        testEntrySet(buildConfig(InMemoryFormat.OBJECT, 0));
    }

    @Test
    public void testEntrySetBinaryDelay0() throws Exception {
        testEntrySet(buildConfig(InMemoryFormat.BINARY, 0));
    }

    private void testEntrySet(Config config) throws Exception {
        HazelcastInstance instance1 = hazelcastFactory.newHazelcastInstance(config);
        HazelcastInstance instance2 = hazelcastFactory.newHazelcastClient();

        final ReplicatedMap<Integer, Integer> map1 = instance1.getReplicatedMap("default");
        final ReplicatedMap<Integer, Integer> map2 = instance2.getReplicatedMap("default");

        final AbstractMap.SimpleEntry<Integer, Integer>[] testValues = buildTestValues();
        WatchedOperationExecutor executor = new WatchedOperationExecutor();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                int half = testValues.length / 2;
                for (int i = 0; i < testValues.length; i++) {
                    ReplicatedMap<Integer, Integer> map = i < half ? map1 : map2;
                    AbstractMap.SimpleEntry<Integer, Integer> entry = testValues[i];
                    map.put(entry.getKey(), entry.getValue());
                }
            }
        }, 2, EntryEventType.ADDED, 100, 1, map1, map2);

        List<Entry<Integer, Integer>> entrySet1 = copyToList(map1.entrySet());
        List<Entry<Integer, Integer>> entrySet2 = copyToList(map2.entrySet());

        int map2Contains = 0;
        for (Entry<Integer, Integer> entry : entrySet2) {
            Integer value = findValue(entry.getKey(), testValues);
            if (entry.getValue().equals(value)) {
                map2Contains++;
            }
        }

        int map1Contains = 0;
        for (Entry<Integer, Integer> entry : entrySet1) {
            Integer value = findValue(entry.getKey(), testValues);
            if (entry.getValue().equals(value)) {
                map1Contains++;
            }
        }

        assertMatchSuccessfulOperationQuota(1, testValues.length, map1Contains, map2Contains);
    }

    @Test
    public void testRetrieveUnknownValueObjectDelay0() {
        testRetrieveUnknownValue(buildConfig(InMemoryFormat.OBJECT, 0));
    }

    @Test
    public void testRetrieveUnknownValueBinaryDelay0() {
        testRetrieveUnknownValue(buildConfig(InMemoryFormat.BINARY, 0));
    }

    private void testRetrieveUnknownValue(Config config) {
        hazelcastFactory.newHazelcastInstance(config);
        HazelcastInstance instance = hazelcastFactory.newHazelcastClient();

        ReplicatedMap<String, String> map = instance.getReplicatedMap("default");
        String value = map.get("foo");
        assertNull(value);
    }

    @Test
    public void testNearCacheInvalidation() {
        String mapName = randomString();
        ClientConfig config = getClientConfigWithNearCacheInvalidationEnabled();

        hazelcastFactory.newHazelcastInstance();
        HazelcastInstance client1 = hazelcastFactory.newHazelcastClient(config);
        HazelcastInstance client2 = hazelcastFactory.newHazelcastClient(config);

        final ReplicatedMap<Integer, Integer> replicatedMap1 = client1.getReplicatedMap(mapName);

        replicatedMap1.put(1, 1);
        // puts key 1 to near cache
        replicatedMap1.get(1);

        ReplicatedMap<Integer, Integer> replicatedMap2 = client2.getReplicatedMap(mapName);
        // this should invalidate near cache of replicatedMap1
        replicatedMap2.put(1, 2);

        assertTrueEventually(new AssertTask() {
            @Override
            public void run() throws Exception {
                assertEquals(2, (int) replicatedMap1.get(1));
            }
        });
    }

    @Test
    public void testNearCacheInvalidation_withClear() {
        hazelcastFactory.newHazelcastInstance();
        ClientConfig config = getClientConfigWithNearCacheInvalidationEnabled();
        HazelcastInstance client1 = hazelcastFactory.newHazelcastClient(config);
        HazelcastInstance client2 = hazelcastFactory.newHazelcastClient(config);

        String mapName = randomString();
        final ReplicatedMap<Integer, Integer> replicatedMap1 = client1.getReplicatedMap(mapName);

        replicatedMap1.put(1, 1);
        // puts key 1 to near cache
        replicatedMap1.get(1);

        ReplicatedMap replicatedMap2 = client2.getReplicatedMap(mapName);
        // this should invalidate near cache of replicatedMap1
        replicatedMap2.clear();

        assertTrueEventually(new AssertTask() {
            @Override
            public void run() throws Exception {
                assertEquals(null, replicatedMap1.get(1));
            }
        });
    }

    @Test
    public void testClientPortableWithoutRegisteringToNode() {
        hazelcastFactory.newHazelcastInstance(buildConfig(InMemoryFormat.BINARY, 0));
        SerializationConfig serializationConfig = new SerializationConfig();
        serializationConfig.addPortableFactory(5, new PortableFactory() {
            public Portable create(int classId) {
                return new SamplePortable();
            }
        });
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setSerializationConfig(serializationConfig);

        HazelcastInstance client = hazelcastFactory.newHazelcastClient(clientConfig);

        ReplicatedMap<Integer, SamplePortable> sampleMap = client.getReplicatedMap(randomString());
        sampleMap.put(1, new SamplePortable(666));
        SamplePortable samplePortable = sampleMap.get(1);
        assertEquals(666, samplePortable.a);
    }

    private ClientConfig getClientConfigWithNearCacheInvalidationEnabled() {
        ClientConfig config = new ClientConfig();
        NearCacheConfig nnc = new NearCacheConfig();
        nnc.setInvalidateOnChange(true);
        nnc.setInMemoryFormat(InMemoryFormat.OBJECT);
        config.addNearCacheConfig(nnc);
        return config;
    }

    private Config buildConfig(InMemoryFormat inMemoryFormat, long replicationDelay) {
        Config config = new Config();
        ReplicatedMapConfig replicatedMapConfig = config.getReplicatedMapConfig("default");
        replicatedMapConfig.setReplicationDelayMillis(replicationDelay);
        replicatedMapConfig.setInMemoryFormat(inMemoryFormat);
        return config;
    }

    private Integer findValue(int key, AbstractMap.SimpleEntry<Integer, Integer>[] values) {
        for (AbstractMap.SimpleEntry<Integer, Integer> value : values) {
            if (value.getKey().equals(key)) {
                return value.getValue();
            }
        }
        return null;
    }

    private void assertMatchSuccessfulOperationQuota(double quota, int completeOps, int... values) {
        float[] quotas = new float[values.length];
        Object[] args = new Object[values.length + 1];
        args[0] = quota;

        for (int i = 0; i < values.length; i++) {
            quotas[i] = (float) values[i] / completeOps;
            args[i + 1] = quotas[i];
        }

        boolean success = true;
        for (int i = 0; i < values.length; i++) {
            if (quotas[i] < quota) {
                success = false;
                break;
            }
        }

        if (!success) {
            StringBuilder sb = new StringBuilder("Quote (%s) for updates not reached,");
            for (int i = 0; i < values.length; i++) {
                sb.append(" map").append(i + 1).append(": %s,");
            }
            sb.deleteCharAt(sb.length() - 1);
            fail(String.format(sb.toString(), args));
        }
    }

    @SuppressWarnings("unchecked")
    private AbstractMap.SimpleEntry<Integer, Integer>[] buildTestValues() {
        Random random = new Random();
        AbstractMap.SimpleEntry<Integer, Integer>[] testValues = new AbstractMap.SimpleEntry[100];
        for (int i = 0; i < testValues.length; i++) {
            testValues[i] = new AbstractMap.SimpleEntry<Integer, Integer>(random.nextInt(), random.nextInt());
        }
        return testValues;
    }

    /**
     * This method works around a bug in IBM's Java 6 J9 JVM where ArrayList's copy constructor
     * is somehow broken and either includes nulls as values or copies not all elements.
     * This is known to happen with a CHM (which is inside the ReplicatedMap implementation)<br>
     * http://www-01.ibm.com/support/docview.wss?uid=swg1IV45453
     * http://www-01.ibm.com/support/docview.wss?uid=swg1IV67555
     */
    private <V> List<V> copyToList(Collection<V> collection) {
        List<V> values = new ArrayList<V>(collection.size());
        for (V aCollection : collection) {
            values.add(aCollection);
        }
        return values;
    }

    static class SamplePortable implements Portable {

        public int a;

        SamplePortable(int a) {
            this.a = a;
        }

        SamplePortable() {
        }

        public int getFactoryId() {
            return 5;
        }

        public int getClassId() {
            return 6;
        }

        public void writePortable(PortableWriter writer) throws IOException {
            writer.writeInt("a", a);
        }

        public void readPortable(PortableReader reader) throws IOException {
            a = reader.readInt("a");
        }
    }
}
