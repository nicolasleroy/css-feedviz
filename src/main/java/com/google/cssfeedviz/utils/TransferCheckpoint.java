// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cssfeedviz.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Properties;

public class TransferCheckpoint {
  private static final String DEFAULT_CHECKPOINT_DIR = ".feedviz/checkpoints";
  private static final String CHECKPOINT_DIR =
      System.getProperty("feedviz.checkpoint.dir", DEFAULT_CHECKPOINT_DIR);

  private final Path path;
  private final String nextPageToken;
  private final LocalDateTime transferDate;
  private final long rowsWritten;
  private final long pagesWritten;

  private TransferCheckpoint(
      Path path,
      String nextPageToken,
      LocalDateTime transferDate,
      long rowsWritten,
      long pagesWritten) {
    this.path = path;
    this.nextPageToken = nextPageToken;
    this.transferDate = transferDate;
    this.rowsWritten = rowsWritten;
    this.pagesWritten = pagesWritten;
  }

  public static TransferCheckpoint load(BigInteger domainId, String tableName) throws IOException {
    Path path = getCheckpointPath(domainId, tableName);
    if (!Files.exists(path)) {
      return new TransferCheckpoint(path, "", LocalDateTime.now(), 0, 0);
    }

    Properties properties = new Properties();
    try (InputStream inputStream = Files.newInputStream(path)) {
      properties.load(inputStream);
    }
    return new TransferCheckpoint(
        path,
        properties.getProperty("nextPageToken", ""),
        LocalDateTime.parse(properties.getProperty("transferDate")),
        Long.parseLong(properties.getProperty("rowsWritten", "0")),
        Long.parseLong(properties.getProperty("pagesWritten", "0")));
  }

  private static Path getCheckpointPath(BigInteger domainId, String tableName) {
    String fileName = String.format("%s_%s.properties", tableName, domainId);
    return Path.of(CHECKPOINT_DIR, fileName);
  }

  public String getNextPageToken() {
    return nextPageToken;
  }

  public LocalDateTime getTransferDate() {
    return transferDate;
  }

  public long getRowsWritten() {
    return rowsWritten;
  }

  public long getPagesWritten() {
    return pagesWritten;
  }

  public TransferCheckpoint save(String nextPageToken, long rowsWritten, long pagesWritten)
      throws IOException {
    Files.createDirectories(path.getParent());
    String pageToken = (nextPageToken == null) ? "" : nextPageToken;
    Properties properties = new Properties();
    properties.setProperty("nextPageToken", pageToken);
    properties.setProperty("transferDate", transferDate.toString());
    properties.setProperty("rowsWritten", String.valueOf(rowsWritten));
    properties.setProperty("pagesWritten", String.valueOf(pagesWritten));
    try (OutputStream outputStream = Files.newOutputStream(path)) {
      properties.store(outputStream, "CSS FeedViz transfer checkpoint");
    }
    return new TransferCheckpoint(path, pageToken, transferDate, rowsWritten, pagesWritten);
  }

  public void delete() throws IOException {
    Files.deleteIfExists(path);
  }
}
