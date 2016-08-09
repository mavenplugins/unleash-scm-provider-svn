package com.itemis.maven.plugins.unleash.scm.providers.util;

import java.util.List;
import java.util.Set;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;

import com.google.common.collect.Lists;
import com.itemis.maven.plugins.unleash.scm.results.HistoryCommit;
import com.itemis.maven.plugins.unleash.scm.results.HistoryResult;

public class SVNHistoryLogEntryHandler implements ISVNLogEntryHandler {
  private Set<String> messageFilterPatterns;
  private HistoryResult history;
  private List<HistoryCommit> commits;
  private long maxResults;

  public SVNHistoryLogEntryHandler(Set<String> messageFilterPatterns, long maxResults) {
    this.messageFilterPatterns = messageFilterPatterns;
    this.maxResults = maxResults;
    this.commits = Lists.newArrayList();
  }

  @Override
  public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
    if (this.commits.size() == this.maxResults || isFilteredMessage(logEntry.getMessage())) {
      return;
    }
    HistoryCommit.Builder b = HistoryCommit.builder();
    b.setRevision(Long.toString(logEntry.getRevision()));
    b.setMessage(logEntry.getMessage());
    b.setAuthor(logEntry.getAuthor());
    b.setDate(logEntry.getDate());
    // this detour is necessary because the SVN history comes inverted starting with the oldest entry but we need the
    // latest one first!
    this.commits.add(0, b.build());
  }

  private boolean isFilteredMessage(String message) {
    for (String filter : this.messageFilterPatterns) {
      if (message.matches(filter)) {
        return true;
      }
    }
    return false;
  }

  public HistoryResult getHistory() {
    if (this.history == null) {
      HistoryResult.Builder builder = HistoryResult.builder();
      for (HistoryCommit c : this.commits) {
        builder.addCommit(c);
      }
      this.history = builder.build();
    }
    return this.history;
  }
}
