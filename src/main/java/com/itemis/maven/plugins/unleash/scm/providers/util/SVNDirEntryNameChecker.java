package com.itemis.maven.plugins.unleash.scm.providers.util;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;

import com.google.common.base.Objects;

public class SVNDirEntryNameChecker implements ISVNDirEntryHandler {
  private String entryname;
  private boolean isPresent;

  public SVNDirEntryNameChecker(String entryname) {
    this.entryname = entryname;
  }

  @Override
  public void handleDirEntry(SVNDirEntry dirEntry) throws SVNException {
    if (Objects.equal(dirEntry.getName(), this.entryname)) {
      this.isPresent = true;
    }
  }

  public boolean isPresent() {
    return this.isPresent;
  }
}