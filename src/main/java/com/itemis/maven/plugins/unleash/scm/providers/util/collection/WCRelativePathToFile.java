package com.itemis.maven.plugins.unleash.scm.providers.util.collection;

import java.io.File;

import com.google.common.base.Function;

public class WCRelativePathToFile implements Function<String, File> {
  private File workingDir;

  public WCRelativePathToFile(File workingDir) {
    this.workingDir = workingDir;
  }

  @Override
  public File apply(String path) {
    return new File(this.workingDir, path);
  }
}
