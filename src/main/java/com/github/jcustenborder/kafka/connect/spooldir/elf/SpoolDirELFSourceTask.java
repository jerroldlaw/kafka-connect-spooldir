/**
 * Copyright © 2016 Jeremy Custenborder (jcustenborder@gmail.com)
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
package com.github.jcustenborder.kafka.connect.spooldir.elf;

import com.github.jcustenborder.kafka.connect.spooldir.AbstractSourceTask;
import com.github.jcustenborder.parsers.elf.ElfParser;
import com.github.jcustenborder.parsers.elf.ElfParserBuilder;
import com.github.jcustenborder.parsers.elf.LogEntry;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.source.SourceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SpoolDirELFSourceTask extends AbstractSourceTask<SpoolDirELFSourceConnectorConfig> {
  private static final Logger log = LoggerFactory.getLogger(SpoolDirELFSourceTask.class);
  ElfParser parser;
  ElfParserBuilder parserBuilder;
  SchemaConversion conversion;
  long offset;

  @Override
  protected SpoolDirELFSourceConnectorConfig config(Map<String, ?> settings) {
    return new SpoolDirELFSourceConnectorConfig(settings);
  }

  @Override
  public void start(Map<String, String> settings) {
    super.start(settings);
    this.parserBuilder = ElfParserBuilder.of();
  }


  @Override
  protected void configure(InputStream inputStream, Long lastOffset) throws IOException {
    if (null != this.parser) {
      log.trace("configure() - Closing existing parser.");
      this.parser.close();
    }

    this.parser = this.parserBuilder.build(inputStream);
    SchemaConversionBuilder builder = new SchemaConversionBuilder(this.parser);
    this.conversion = builder.build();

    this.offset = -1;

    if (null != lastOffset) {
      int skippedRecords = 1;
      while (null != next() && skippedRecords <= lastOffset) {
        skippedRecords++;
      }
      log.trace("configure() - Skipped {} record(s).", skippedRecords);
      log.info("configure() - Starting on offset {}", this.offset);
    }
  }

  LogEntry next() throws IOException {
    this.offset++;
    return this.parser.next();
  }

  @Override
  protected List<SourceRecord> process() {
    int recordCount = 0;
    List<SourceRecord> records = new ArrayList<>(this.config.batchSize);

    LogEntry entry;
    try {
      while (null != (entry = next()) && recordCount < this.config.batchSize) {
        log.trace("process() - Processing LogEntry: {}", entry);
        SchemaAndValue value = conversion.convert(entry);
        SourceRecord record = record(SchemaAndValue.NULL, value, null);
        records.add(record);
        recordCount++;
      }
    } catch (IOException ex) {
      throw new ConnectException(ex);
    }
    return records;
  }

  @Override
  protected long recordOffset() {
    return this.offset;
  }
}
