/*
 * Copyright 2019 Aiven Oy
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

package io.aiven.kafka.connect.transforms;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.transforms.Transformation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ExtractTopicFromValueSchema<R extends ConnectRecord<R>> implements Transformation<R> {

    private static final Logger log = LoggerFactory.getLogger(ExtractTopicFromValueSchema.class);

    private ExtractTopicFromValueSchemaConfig config;

    @Override
    public ConfigDef config() {
        return ExtractTopicFromValueSchemaConfig.config();
    }

    @Override
    public void configure(final Map<String, ?> configs) {
        this.config = new ExtractTopicFromValueSchemaConfig(configs);
    }

    @Override
    public R apply(final R record) {

        if (null == record.valueSchema() || null == record.valueSchema().name()) {
            throw new DataException(" value schema name can't be null: " + record);
        }
        // If no extra configs use record.valueSchema().name() -> newTopic
        if (null == config) {
            return createConnectRecord(record, Optional.ofNullable(record.valueSchema().name()).orElse(record.topic()));
        }
        // First check schema value name -> desired topic name mapping and use that if it is set.
        final Optional<Map<String, String>> schemaNameToTopicMap = config.schemaNameToTopicMap();
        if (schemaNameToTopicMap.isPresent() && schemaNameToTopicMap.get().containsKey(record.valueSchema().name())) {
            return createConnectRecord(record, schemaNameToTopicMap.get().get(record.valueSchema().name()));
        }
        // Secondly check if regex parsing from schema value name is set and use that.
        final Optional<String> regex = config.regEx();
        if (regex.isPresent()) {
            final Pattern pattern = Pattern.compile(regex.get());
            final Matcher matcher = pattern.matcher(record.valueSchema().name());
            matcher.find();
            if (matcher.groupCount() > 0) {
                return createConnectRecord(record, matcher.group(1));
            }
        }
        // If no other configurations are set use value schema name as new topic name.
        return createConnectRecord(record, Optional.ofNullable(record.valueSchema().name())
                .orElseThrow(() -> new DataException(" value schema name can't be null: " + record)));
    }

    private R createConnectRecord(final R record, final String newTopicName) {
        return record.newRecord(
                newTopicName,
                record.kafkaPartition(),
                record.keySchema(),
                record.key(),
                record.valueSchema(),
                record.value(),
                record.timestamp(),
                record.headers()
        );
    }

    @Override
    public void close() {
    }

    public static class Name<R extends ConnectRecord<R>> extends ExtractTopicFromValueSchema<R> {

        @Override
        public void close() {
        }
    }
}
