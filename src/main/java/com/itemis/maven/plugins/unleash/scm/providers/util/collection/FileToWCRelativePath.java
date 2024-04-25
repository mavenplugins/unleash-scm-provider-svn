package com.itemis.maven.plugins.unleash.scm.providers.util.collection;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

import com.google.common.base.Function;

public class FileToWCRelativePath implements Function<File, String> {
  private File workingDir;

  public FileToWCRelativePath(File workingDir) {
    this.workingDir = workingDir;
  }

  @Override
  public String apply(File f) {
    URI workingDirURI = this.workingDir.toURI();
    URI fileURI = f.toURI();
    if (SystemUtils.IS_OS_WINDOWS) {
      // On Windows OS deviations in character case of the drive letter may occur
      // => we have to normalize by lower casing the URI!
      String lcURIString = StringUtils.EMPTY;
      try {
        lcURIString = workingDirURI.toString().toLowerCase();
        workingDirURI = new URI(lcURIString);
        lcURIString = fileURI.toString().toLowerCase();
        fileURI = new URI(lcURIString);
      } catch (URISyntaxException e) {
        throw new RuntimeException("Failed to create URI for path: " + lcURIString, e);
      }
    }

    return workingDirURI.relativize(fileURI).toString();
  }
}
