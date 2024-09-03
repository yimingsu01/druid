/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.indexing.kafka.supervisor;

import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.druid.indexing.kafka.KafkaIndexTaskClientFactory;
import org.apache.druid.indexing.kafka.KafkaIndexTaskModule;
import org.apache.druid.indexing.overlord.IndexerMetadataStorageCoordinator;
import org.apache.druid.indexing.overlord.TaskMaster;
import org.apache.druid.indexing.overlord.TaskStorage;
import org.apache.druid.indexing.overlord.supervisor.SupervisorStateManagerConfig;
import org.apache.druid.jackson.DefaultObjectMapper;
import org.apache.druid.java.util.emitter.service.ServiceEmitter;
import org.apache.druid.java.util.metrics.DruidMonitorSchedulerConfig;
import org.apache.druid.math.expr.ExprMacroTable;
import org.apache.druid.query.expression.LookupEnabledTestExprMacroTable;
import org.apache.druid.segment.incremental.RowIngestionMetersFactory;
import org.apache.druid.server.metrics.NoopServiceEmitter;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

public class KafkaSupervisorSpecTest
{
  private final ObjectMapper mapper;

  public KafkaSupervisorSpecTest()
  {
    mapper = new DefaultObjectMapper();
    mapper.setInjectableValues(
        new InjectableValues.Std()
            .addValue(TaskStorage.class, null)
            .addValue(TaskMaster.class, null)
            .addValue(IndexerMetadataStorageCoordinator.class, null)
            .addValue(KafkaIndexTaskClientFactory.class, null)
            .addValue(ObjectMapper.class, mapper)
            .addValue(ServiceEmitter.class, new NoopServiceEmitter())
            .addValue(DruidMonitorSchedulerConfig.class, null)
            .addValue(RowIngestionMetersFactory.class, null)
            .addValue(SupervisorStateManagerConfig.class, null)
            .addValue(ExprMacroTable.class.getName(), LookupEnabledTestExprMacroTable.INSTANCE)
    );
    mapper.registerModules((Iterable<Module>) new KafkaIndexTaskModule().getJacksonModules());
  }

  @Test
  public void testSerde() throws IOException
  {
    String json = "{\n"
                  + "  \"type\": \"kafka\",\n"
                  + "  \"dataSchema\": {\n"
                  + "    \"dataSource\": \"metrics-kafka\",\n"
                  + "    \"parser\": {\n"
                  + "      \"type\": \"string\",\n"
                  + "      \"parseSpec\": {\n"
                  + "        \"format\": \"json\",\n"
                  + "        \"timestampSpec\": {\n"
                  + "          \"column\": \"timestamp\",\n"
                  + "          \"format\": \"auto\"\n"
                  + "        },\n"
                  + "        \"dimensionsSpec\": {\n"
                  + "          \"dimensions\": [],\n"
                  + "          \"dimensionExclusions\": [\n"
                  + "            \"timestamp\",\n"
                  + "            \"value\"\n"
                  + "          ]\n"
                  + "        }\n"
                  + "      }\n"
                  + "    },\n"
                  + "    \"metricsSpec\": [\n"
                  + "      {\n"
                  + "        \"name\": \"count\",\n"
                  + "        \"type\": \"count\"\n"
                  + "      },\n"
                  + "      {\n"
                  + "        \"name\": \"value_sum\",\n"
                  + "        \"fieldName\": \"value\",\n"
                  + "        \"type\": \"doubleSum\"\n"
                  + "      },\n"
                  + "      {\n"
                  + "        \"name\": \"value_min\",\n"
                  + "        \"fieldName\": \"value\",\n"
                  + "        \"type\": \"doubleMin\"\n"
                  + "      },\n"
                  + "      {\n"
                  + "        \"name\": \"value_max\",\n"
                  + "        \"fieldName\": \"value\",\n"
                  + "        \"type\": \"doubleMax\"\n"
                  + "      }\n"
                  + "    ],\n"
                  + "    \"granularitySpec\": {\n"
                  + "      \"type\": \"uniform\",\n"
                  + "      \"segmentGranularity\": \"HOUR\",\n"
                  + "      \"queryGranularity\": \"NONE\"\n"
                  + "    }\n"
                  + "  },\n"
                  + "  \"ioConfig\": {\n"
                  + "    \"topic\": \"metrics\",\n"
                  + "    \"consumerProperties\": {\n"
                  + "      \"bootstrap.servers\": \"localhost:9092\"\n"
                  + "    },\n"
                  + "    \"taskCount\": 1\n"
                  + "  }\n"
                  + "}";
    KafkaSupervisorSpec spec = mapper.readValue(json, KafkaSupervisorSpec.class);

    Assert.assertNotNull(spec);
    Assert.assertNotNull(spec.getDataSchema());
    Assert.assertEquals(4, spec.getDataSchema().getAggregators().length);
    Assert.assertNotNull(spec.getIoConfig());
    Assert.assertEquals("metrics", spec.getIoConfig().getTopic());
    Assert.assertNull(spec.getIoConfig().getTopicPattern());
    Assert.assertNotNull(spec.getTuningConfig());
    Assert.assertNull(spec.getContext());
    Assert.assertFalse(spec.isSuspended());
    String serialized = mapper.writeValueAsString(spec);

    // expect default values populated in reserialized string
    Assert.assertTrue(serialized.contains("\"tuningConfig\":{"));
    Assert.assertTrue(serialized.contains("\"indexSpec\":{"));
    Assert.assertTrue(serialized.contains("\"suspended\":false"));
    Assert.assertTrue(serialized.contains("\"parser\":{"));

    KafkaSupervisorSpec spec2 = mapper.readValue(serialized, KafkaSupervisorSpec.class);

    String stable = mapper.writeValueAsString(spec2);

    Assert.assertEquals(serialized, stable);
  }

  @Test
  public void testSerdeWithTopicPattern() throws IOException
  {
    String json = "{\n"
                  + "  \"type\": \"kafka\",\n"
                  + "  \"dataSchema\": {\n"
                  + "    \"dataSource\": \"metrics-kafka\",\n"
                  + "    \"parser\": {\n"
                  + "      \"type\": \"string\",\n"
                  + "      \"parseSpec\": {\n"
                  + "        \"format\": \"json\",\n"
                  + "        \"timestampSpec\": {\n"
                  + "          \"column\": \"timestamp\",\n"
                  + "          \"format\": \"auto\"\n"
                  + "        },\n"
                  + "        \"dimensionsSpec\": {\n"
                  + "          \"dimensions\": [],\n"
                  + "          \"dimensionExclusions\": [\n"
                  + "            \"timestamp\",\n"
                  + "            \"value\"\n"
                  + "          ]\n"
                  + "        }\n"
                  + "      }\n"
                  + "    },\n"
                  + "    \"metricsSpec\": [\n"
                  + "      {\n"
                  + "        \"name\": \"count\",\n"
                  + "        \"type\": \"count\"\n"
                  + "      },\n"
                  + "      {\n"
                  + "        \"name\": \"value_sum\",\n"
                  + "        \"fieldName\": \"value\",\n"
                  + "        \"type\": \"doubleSum\"\n"
                  + "      },\n"
                  + "      {\n"
                  + "        \"name\": \"value_min\",\n"
                  + "        \"fieldName\": \"value\",\n"
                  + "        \"type\": \"doubleMin\"\n"
                  + "      },\n"
                  + "      {\n"
                  + "        \"name\": \"value_max\",\n"
                  + "        \"fieldName\": \"value\",\n"
                  + "        \"type\": \"doubleMax\"\n"
                  + "      }\n"
                  + "    ],\n"
                  + "    \"granularitySpec\": {\n"
                  + "      \"type\": \"uniform\",\n"
                  + "      \"segmentGranularity\": \"HOUR\",\n"
                  + "      \"queryGranularity\": \"NONE\"\n"
                  + "    }\n"
                  + "  },\n"
                  + "  \"ioConfig\": {\n"
                  + "    \"topicPattern\": \"metrics.*\",\n"
                  + "    \"consumerProperties\": {\n"
                  + "      \"bootstrap.servers\": \"localhost:9092\"\n"
                  + "    },\n"
                  + "    \"taskCount\": 1\n"
                  + "  }\n"
                  + "}";
    KafkaSupervisorSpec spec = mapper.readValue(json, KafkaSupervisorSpec.class);

    Assert.assertNotNull(spec);
    Assert.assertNotNull(spec.getDataSchema());
    Assert.assertEquals("metrics.*", spec.getIoConfig().getTopicPattern());
    Assert.assertNull(spec.getIoConfig().getTopic());
    Assert.assertNotNull(spec.getTuningConfig());
    Assert.assertNull(spec.getContext());
    String serialized = mapper.writeValueAsString(spec);

    // expect default values populated in reserialized string
    Assert.assertTrue(serialized.contains("\"topicPattern\":\"metrics.*\""));
    Assert.assertTrue(serialized, serialized.contains("\"topic\":null"));

    KafkaSupervisorSpec spec2 = mapper.readValue(serialized, KafkaSupervisorSpec.class);

    String stable = mapper.writeValueAsString(spec2);

    Assert.assertEquals(serialized, stable);
  }
  @Test
  public void testSerdeWithInputFormat() throws IOException
  {
    String json = "{\n"
                  + "  \"type\": \"kafka\",\n"
                  + "  \"dataSchema\": {\n"
                  + "    \"dataSource\": \"metrics-kafka\",\n"
                  + "    \"timestampSpec\": {\n"
                  + "      \"column\": \"timestamp\",\n"
                  + "      \"format\": \"auto\"\n"
                  + "     },\n"
                  + "    \"dimensionsSpec\": {\n"
                  + "      \"dimensions\": [],\n"
                  + "      \"dimensionExclusions\": [\n"
                  + "        \"timestamp\",\n"
                  + "        \"value\"\n"
                  + "       ]\n"
                  + "    },\n"
                  + "    \"metricsSpec\": [\n"
                  + "      {\n"
                  + "        \"name\": \"count\",\n"
                  + "        \"type\": \"count\"\n"
                  + "      },\n"
                  + "      {\n"
                  + "        \"name\": \"value_sum\",\n"
                  + "        \"fieldName\": \"value\",\n"
                  + "        \"type\": \"doubleSum\"\n"
                  + "      },\n"
                  + "      {\n"
                  + "        \"name\": \"value_min\",\n"
                  + "        \"fieldName\": \"value\",\n"
                  + "        \"type\": \"doubleMin\"\n"
                  + "      },\n"
                  + "      {\n"
                  + "        \"name\": \"value_max\",\n"
                  + "        \"fieldName\": \"value\",\n"
                  + "        \"type\": \"doubleMax\"\n"
                  + "      }\n"
                  + "    ],\n"
                  + "    \"granularitySpec\": {\n"
                  + "      \"type\": \"uniform\",\n"
                  + "      \"segmentGranularity\": \"HOUR\",\n"
                  + "      \"queryGranularity\": \"NONE\"\n"
                  + "    }\n"
                  + "  },\n"
                  + "  \"ioConfig\": {\n"
                  + "    \"topic\": \"metrics\",\n"
                  + "    \"inputFormat\": {\n"
                  + "      \"type\": \"json\",\n"
                  + "      \"flattenSpec\": {\n"
                  + "        \"useFieldDiscovery\": true,\n"
                  + "        \"fields\": []\n"
                  + "      },\n"
                  + "      \"featureSpec\": {}\n"
                  + "    },"
                  + "    \"consumerProperties\": {\n"
                  + "      \"bootstrap.servers\": \"localhost:9092\"\n"
                  + "    },\n"
                  + "    \"taskCount\": 1\n"
                  + "  }\n"
                  + "}";
    KafkaSupervisorSpec spec = mapper.readValue(json, KafkaSupervisorSpec.class);

    Assert.assertNotNull(spec);
    Assert.assertNotNull(spec.getDataSchema());
    Assert.assertEquals(4, spec.getDataSchema().getAggregators().length);
    Assert.assertNotNull(spec.getIoConfig());
    Assert.assertEquals("metrics", spec.getIoConfig().getTopic());
    Assert.assertNull(spec.getIoConfig().getTopicPattern());
    Assert.assertNotNull(spec.getTuningConfig());
    Assert.assertNull(spec.getContext());
    Assert.assertFalse(spec.isSuspended());
    String serialized = mapper.writeValueAsString(spec);

    // expect default values populated in reserialized string
    Assert.assertTrue(serialized.contains("\"tuningConfig\":{"));
    Assert.assertTrue(serialized.contains("\"indexSpec\":{"));
    Assert.assertTrue(serialized.contains("\"suspended\":false"));
    Assert.assertTrue(serialized.contains("\"inputFormat\":{"));

    KafkaSupervisorSpec spec2 = mapper.readValue(serialized, KafkaSupervisorSpec.class);

    String stable = mapper.writeValueAsString(spec2);

    Assert.assertEquals(serialized, stable);
  }

  @Test
  public void testSerdeWithSpec() throws IOException
  {
    String json = "{\n"
                  + "  \"type\": \"kafka\",\n"
                  + "  \"spec\": {\n"
                  + "  \"dataSchema\": {\n"
                  + "    \"dataSource\": \"metrics-kafka\",\n"
                  + "    \"parser\": {\n"
                  + "      \"type\": \"string\",\n"
                  + "      \"parseSpec\": {\n"
                  + "        \"format\": \"json\",\n"
                  + "        \"timestampSpec\": {\n"
                  + "          \"column\": \"timestamp\",\n"
                  + "          \"format\": \"auto\"\n"
                  + "        },\n"
                  + "        \"dimensionsSpec\": {\n"
                  + "          \"dimensions\": [],\n"
                  + "          \"dimensionExclusions\": [\n"
                  + "            \"timestamp\",\n"
                  + "            \"value\"\n"
                  + "          ]\n"
                  + "        }\n"
                  + "      }\n"
                  + "    },\n"
                  + "    \"metricsSpec\": [\n"
                  + "      {\n"
                  + "        \"name\": \"count\",\n"
                  + "        \"type\": \"count\"\n"
                  + "      },\n"
                  + "      {\n"
                  + "        \"name\": \"value_sum\",\n"
                  + "        \"fieldName\": \"value\",\n"
                  + "        \"type\": \"doubleSum\"\n"
                  + "      },\n"
                  + "      {\n"
                  + "        \"name\": \"value_min\",\n"
                  + "        \"fieldName\": \"value\",\n"
                  + "        \"type\": \"doubleMin\"\n"
                  + "      },\n"
                  + "      {\n"
                  + "        \"name\": \"value_max\",\n"
                  + "        \"fieldName\": \"value\",\n"
                  + "        \"type\": \"doubleMax\"\n"
                  + "      }\n"
                  + "    ],\n"
                  + "    \"granularitySpec\": {\n"
                  + "      \"type\": \"uniform\",\n"
                  + "      \"segmentGranularity\": \"HOUR\",\n"
                  + "      \"queryGranularity\": \"NONE\"\n"
                  + "    }\n"
                  + "  },\n"
                  + "  \"ioConfig\": {\n"
                  + "    \"topic\": \"metrics\",\n"
                  + "    \"consumerProperties\": {\n"
                  + "      \"bootstrap.servers\": \"localhost:9092\"\n"
                  + "    },\n"
                  + "    \"taskCount\": 1\n"
                  + "  }\n"
                  + "  }\n"
                  + "}";
    KafkaSupervisorSpec spec = mapper.readValue(json, KafkaSupervisorSpec.class);

    Assert.assertNotNull(spec);
    Assert.assertNotNull(spec.getDataSchema());
    Assert.assertEquals(4, spec.getDataSchema().getAggregators().length);
    Assert.assertNotNull(spec.getIoConfig());
    Assert.assertEquals("metrics", spec.getIoConfig().getTopic());
    Assert.assertNull(spec.getIoConfig().getTopicPattern());
    Assert.assertNotNull(spec.getTuningConfig());
    Assert.assertNull(spec.getContext());
    Assert.assertFalse(spec.isSuspended());
    String serialized = mapper.writeValueAsString(spec);

    // expect default values populated in reserialized string
    Assert.assertTrue(serialized.contains("\"tuningConfig\":{"));
    Assert.assertTrue(serialized.contains("\"indexSpec\":{"));
    Assert.assertTrue(serialized.contains("\"suspended\":false"));
    Assert.assertTrue(serialized.contains("\"parser\":{"));

    KafkaSupervisorSpec spec2 = mapper.readValue(serialized, KafkaSupervisorSpec.class);

    String stable = mapper.writeValueAsString(spec2);

    Assert.assertEquals(serialized, stable);
  }

  @Test
  public void testSerdeWithSpecAndInputFormat() throws IOException
  {
    String json = "{\n"
                  + "  \"type\": \"kafka\",\n"
                  + "  \"spec\": {\n"
                  + "  \"dataSchema\": {\n"
                  + "    \"dataSource\": \"metrics-kafka\",\n"
                  + "    \"timestampSpec\": {\n"
                  + "      \"column\": \"timestamp\",\n"
                  + "      \"format\": \"auto\"\n"
                  + "    },\n"
                  + "    \"dimensionsSpec\": {\n"
                  + "      \"dimensions\": [],\n"
                  + "      \"dimensionExclusions\": [\n"
                  + "        \"timestamp\",\n"
                  + "        \"value\"\n"
                  + "      ]\n"
                  + "    },\n"
                  + "    \"metricsSpec\": [\n"
                  + "      {\n"
                  + "        \"name\": \"count\",\n"
                  + "        \"type\": \"count\"\n"
                  + "      },\n"
                  + "      {\n"
                  + "        \"name\": \"value_sum\",\n"
                  + "        \"fieldName\": \"value\",\n"
                  + "        \"type\": \"doubleSum\"\n"
                  + "      },\n"
                  + "      {\n"
                  + "        \"name\": \"value_min\",\n"
                  + "        \"fieldName\": \"value\",\n"
                  + "        \"type\": \"doubleMin\"\n"
                  + "      },\n"
                  + "      {\n"
                  + "        \"name\": \"value_max\",\n"
                  + "        \"fieldName\": \"value\",\n"
                  + "        \"type\": \"doubleMax\"\n"
                  + "      }\n"
                  + "    ],\n"
                  + "    \"granularitySpec\": {\n"
                  + "      \"type\": \"uniform\",\n"
                  + "      \"segmentGranularity\": \"HOUR\",\n"
                  + "      \"queryGranularity\": \"NONE\"\n"
                  + "    }\n"
                  + "  },\n"
                  + "  \"ioConfig\": {\n"
                  + "    \"topic\": \"metrics\",\n"
                  + "    \"inputFormat\": {\n"
                  + "      \"type\": \"json\",\n"
                  + "      \"flattenSpec\": {\n"
                  + "        \"useFieldDiscovery\": true,\n"
                  + "        \"fields\": []\n"
                  + "      },\n"
                  + "      \"featureSpec\": {}\n"
                  + "    },"
                  + "    \"consumerProperties\": {\n"
                  + "      \"bootstrap.servers\": \"localhost:9092\"\n"
                  + "    },\n"
                  + "    \"taskCount\": 1\n"
                  + "  }\n"
                  + "  }\n"
                  + "}";
    KafkaSupervisorSpec spec = mapper.readValue(json, KafkaSupervisorSpec.class);

    Assert.assertNotNull(spec);
    Assert.assertNotNull(spec.getDataSchema());
    Assert.assertEquals(4, spec.getDataSchema().getAggregators().length);
    Assert.assertNotNull(spec.getIoConfig());
    Assert.assertEquals("metrics", spec.getIoConfig().getTopic());
    Assert.assertNull(spec.getIoConfig().getTopicPattern());
    Assert.assertNotNull(spec.getTuningConfig());
    Assert.assertNull(spec.getContext());
    Assert.assertFalse(spec.isSuspended());
    String serialized = mapper.writeValueAsString(spec);

    // expect default values populated in reserialized string
    Assert.assertTrue(serialized.contains("\"tuningConfig\":{"));
    Assert.assertTrue(serialized.contains("\"indexSpec\":{"));
    Assert.assertTrue(serialized.contains("\"suspended\":false"));
    Assert.assertTrue(serialized.contains("\"inputFormat\":{"));

    KafkaSupervisorSpec spec2 = mapper.readValue(serialized, KafkaSupervisorSpec.class);
    String stable = mapper.writeValueAsString(spec2);

    // The test objective is that serialized and stable are the same JSON
    // However, a JSON array called dimensionExclusions is being serialized with different element ordering
    // every time the test is being run, causing the test to be flaky

    // The solution below first removes the element from the raw strings, then separately compare the dimensionExclusion
    // array elements and the rest of the strings.
    
    // Removing first dimensionExclusion from serialized and stable
    StringBuilder sbSerialized = new StringBuilder(serialized);
    StringBuilder sbStable = new StringBuilder(stable);

    // Getting the length of the json array elements in character length for serialized
    String begin = "\"dimensionExclusions\":";
    int startIdxPadding = 23;
    int endIdxPadding = 72;
    int startIdx = serialized.indexOf(begin) + startIdxPadding;
    int endIdx = startIdx + endIdxPadding;

    // Getting the raw string that contains the elements
    String serializedSubstring = serialized.substring(startIdx, endIdx);

    // Updating serialized stringbuilder by deleting the elements
    StringBuilder sbSerialized2 = sbSerialized.delete(startIdx, endIdx);


    // Removing first dimensionExclusion from stable
    startIdx = stable.indexOf(begin) + startIdxPadding;
    endIdx = startIdx + endIdxPadding;

    String stableSubstring = stable.substring(startIdx, endIdx);

    // Updating serialized stringbuilder by deleting the elements
    StringBuilder sbStable2 = sbStable.delete(startIdx, endIdx);

    // Comparing the elements of first DimensionExclusion
    // Since the order doesn't matter, we can just compare the string.
    char[] serializedSubstringChars = serializedSubstring.toCharArray();
    char[] stableSubstringChars = stableSubstring.toCharArray();

    Arrays.sort(serializedSubstringChars);
    Arrays.sort(stableSubstringChars);

    // Assert that they are the same
    Assert.assertArrayEquals(serializedSubstringChars, stableSubstringChars);

    // Deleting second dimensionExclusion from serialized
    begin = "\"dimensionExclusions\":[\"";

    startIdx = sbSerialized2.indexOf(begin) + startIdxPadding;
    endIdx = startIdx + endIdxPadding;
    serializedSubstring = sbSerialized2.substring(startIdx, endIdx);

    StringBuilder sbSerialized3 = sbSerialized.delete(startIdx, endIdx);


    // Deleting second dimensionExclusion from stable
    startIdx = sbStable2.indexOf(begin) + startIdxPadding;
    endIdx = startIdx + endIdxPadding;
    stableSubstring = sbStable2.substring(startIdx, endIdx);

    StringBuilder sbStable3 = sbStable2.delete(startIdx, endIdx);

    // Comparing the second dimensionExclusion elements
    char[] serializedSubstringChars2 = serializedSubstring.toCharArray();
    char[] stableSubstringChars2 = stableSubstring.toCharArray();

    Arrays.sort(serializedSubstringChars2);
    Arrays.sort(stableSubstringChars2);

    Assert.assertArrayEquals(serializedSubstringChars2, stableSubstringChars2);

    // Comparing the json minus the dimensionExclusion elements
    Assert.assertEquals(sbSerialized3.toString(), sbStable3.toString());
  }

  @Test
  public void testSuspendResume() throws IOException
  {
    String json = "{\n"
                  + "  \"type\": \"kafka\",\n"
                  + "  \"dataSchema\": {\n"
                  + "    \"dataSource\": \"metrics-kafka\",\n"
                  + "    \"parser\": {\n"
                  + "      \"type\": \"string\",\n"
                  + "      \"parseSpec\": {\n"
                  + "        \"format\": \"json\",\n"
                  + "        \"timestampSpec\": {\n"
                  + "          \"column\": \"timestamp\",\n"
                  + "          \"format\": \"auto\"\n"
                  + "        },\n"
                  + "        \"dimensionsSpec\": {\n"
                  + "          \"dimensions\": [],\n"
                  + "          \"dimensionExclusions\": [\n"
                  + "            \"timestamp\",\n"
                  + "            \"value\"\n"
                  + "          ]\n"
                  + "        }\n"
                  + "      }\n"
                  + "    },\n"
                  + "    \"metricsSpec\": [\n"
                  + "      {\n"
                  + "        \"name\": \"count\",\n"
                  + "        \"type\": \"count\"\n"
                  + "      },\n"
                  + "      {\n"
                  + "        \"name\": \"value_sum\",\n"
                  + "        \"fieldName\": \"value\",\n"
                  + "        \"type\": \"doubleSum\"\n"
                  + "      },\n"
                  + "      {\n"
                  + "        \"name\": \"value_min\",\n"
                  + "        \"fieldName\": \"value\",\n"
                  + "        \"type\": \"doubleMin\"\n"
                  + "      },\n"
                  + "      {\n"
                  + "        \"name\": \"value_max\",\n"
                  + "        \"fieldName\": \"value\",\n"
                  + "        \"type\": \"doubleMax\"\n"
                  + "      }\n"
                  + "    ],\n"
                  + "    \"granularitySpec\": {\n"
                  + "      \"type\": \"uniform\",\n"
                  + "      \"segmentGranularity\": \"HOUR\",\n"
                  + "      \"queryGranularity\": \"NONE\"\n"
                  + "    }\n"
                  + "  },\n"
                  + "  \"ioConfig\": {\n"
                  + "    \"topic\": \"metrics\",\n"
                  + "    \"consumerProperties\": {\n"
                  + "      \"bootstrap.servers\": \"localhost:9092\"\n"
                  + "    },\n"
                  + "    \"taskCount\": 1\n"
                  + "  }\n"
                  + "}";
    KafkaSupervisorSpec spec = mapper.readValue(json, KafkaSupervisorSpec.class);

    Assert.assertNotNull(spec);
    Assert.assertNotNull(spec.getDataSchema());
    Assert.assertEquals(4, spec.getDataSchema().getAggregators().length);
    Assert.assertNotNull(spec.getIoConfig());
    Assert.assertEquals("metrics", spec.getIoConfig().getTopic());
    Assert.assertNull(spec.getIoConfig().getTopicPattern());
    Assert.assertNotNull(spec.getTuningConfig());
    Assert.assertNull(spec.getContext());
    Assert.assertFalse(spec.isSuspended());

    String suspendedSerialized = mapper.writeValueAsString(spec.createSuspendedSpec());

    // expect default values populated in reserialized string
    Assert.assertTrue(suspendedSerialized.contains("\"tuningConfig\":{"));
    Assert.assertTrue(suspendedSerialized.contains("\"indexSpec\":{"));
    Assert.assertTrue(suspendedSerialized.contains("\"suspended\":true"));

    KafkaSupervisorSpec suspendedSpec = mapper.readValue(suspendedSerialized, KafkaSupervisorSpec.class);

    Assert.assertTrue(suspendedSpec.isSuspended());

    String runningSerialized = mapper.writeValueAsString(spec.createRunningSpec());

    KafkaSupervisorSpec runningSpec = mapper.readValue(runningSerialized, KafkaSupervisorSpec.class);

    Assert.assertFalse(runningSpec.isSuspended());
  }
}
