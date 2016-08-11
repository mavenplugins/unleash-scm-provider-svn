package com.itemis.maven.plugins.unleash.scm.providers.util.collection;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.DefaultSVNDiffGenerator;
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;

import com.google.common.collect.Sets;
import com.google.common.io.Closeables;
import com.itemis.maven.plugins.unleash.scm.requests.DiffRequest.DiffType;
import com.itemis.maven.plugins.unleash.scm.results.DiffObject;

public class SVNDiffGenerator extends DefaultSVNDiffGenerator implements ISVNDiffGenerator {
  private Set<DiffObject> diffs = Sets.newHashSet();
  private DiffType type;

  public SVNDiffGenerator(DiffType type) {
    this.type = type;
  }

  @Override
  public void displayFileDiff(String path, File file1, File file2, String rev1, String rev2, String mimeType1,
      String mimeType2, OutputStream result) throws SVNException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    try {
      super.displayFileDiff(path, file1, file2, rev1, rev2, mimeType1, mimeType2, os);

      DiffObject.Builder builder = DiffObject.builder();
      boolean addTextualDiff = this.type == DiffType.FULL;
      if (file1 == null) {
        builder.addition(path);
      } else if (file2 == null) {
        builder.deletion(path);
      } else {
        builder.changed(path);
        addTextualDiff |= this.type == DiffType.CHANGES_ONLY;
      }

      if (addTextualDiff) {
        builder.addTextualDiff(new String(os.toByteArray()));
      }
      this.diffs.add(builder.build());
    } finally {
      try {
        Closeables.close(os, true);
      } catch (IOException e) {
        // should not happen ;)
      }
    }
  }

  @Override
  public void displayDeletedDirectory(String path, String rev1, String rev2) throws SVNException {
    super.displayDeletedDirectory(path, rev1, rev2);

    DiffObject.Builder builder = DiffObject.builder();
    builder.deletion(path);
    this.diffs.add(builder.build());
  }

  @Override
  public void displayAddedDirectory(String path, String rev1, String rev2) throws SVNException {
    super.displayAddedDirectory(path, rev1, rev2);

    DiffObject.Builder builder = DiffObject.builder();
    builder.addition(path);
    this.diffs.add(builder.build());
  }

  public Set<DiffObject> getDiffs() {
    return this.diffs;
  }
}
