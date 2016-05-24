package com.itemis.maven.plugins.unleash.scm.providers.util.collection;

import java.io.File;

import org.tmatesoft.svn.core.wc.SVNStatus;

import com.google.common.base.Function;

public enum SVNStatusToFile implements Function<SVNStatus, File> {
  INSTANCE;

  @Override
  public File apply(SVNStatus status) {
    return status.getFile();
  }
}
