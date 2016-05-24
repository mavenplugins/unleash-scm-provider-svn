package com.itemis.maven.plugins.unleash.scm.providers.util.collection;

import java.io.File;

import com.google.common.base.Function;

public class FileToWCRelativePath implements Function<File, String> {
  private File workingDir;

  public FileToWCRelativePath(File workingDir) {
    this.workingDir = workingDir;
  }

  @Override
  public String apply(File f) {
    return this.workingDir.toURI().relativize(f.toURI()).toString();
  }
}
