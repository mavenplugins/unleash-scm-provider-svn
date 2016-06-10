package com.itemis.maven.plugins.unleash.scm.providers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNConflictHandler;
import org.tmatesoft.svn.core.wc.SVNConflictChoice;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNConflictResult;
import org.tmatesoft.svn.core.wc.SVNMergeFileSet;

import com.google.common.base.Optional;
import com.google.common.io.Closeables;
import com.itemis.maven.plugins.unleash.scm.merge.MergeClient;
import com.itemis.maven.plugins.unleash.scm.merge.MergeStrategy;

public class SVNMergeConflictResolver implements ISVNConflictHandler {
  private MergeStrategy strategy;
  private Optional<MergeClient> mergeClient;

  public SVNMergeConflictResolver(MergeStrategy strategy, Optional<MergeClient> mergeClient) {
    this.strategy = strategy;
    this.mergeClient = mergeClient;
  }

  @Override
  public SVNConflictResult handleConflict(SVNConflictDescription conflictDescription) throws SVNException {
    SVNMergeFileSet mergeFiles = conflictDescription.getMergeFiles();

    SVNConflictChoice choice = null;
    FileOutputStream fos = null;
    try {
      fos = new FileOutputStream(mergeFiles.getResultFile());
      File mergedFile;
      switch (this.strategy) {
        case USE_LOCAL:
          choice = SVNConflictChoice.MINE_CONFLICT;
          mergedFile = mergeFiles.getResultFile();
          break;
        case USE_REMOTE:
          choice = SVNConflictChoice.THEIRS_CONFLICT;
          mergedFile = mergeFiles.getResultFile();
          break;
        case FULL_MERGE:
          if (conflictDescription.isPropertyConflict()) {
            throw new SVNException(
                SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Merging SVN property conflicts is not yet supported!"));
          } else if (conflictDescription.isTreeConflict()) {
            throw new SVNException(
                SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Merging SVN tree conflicts is not yet supported!"));
          } else if (conflictDescription.isTextConflict()) {
            if (!this.mergeClient.isPresent()) {
              throw new SVNException(SVNErrorMessage.create(SVNErrorCode.UNKNOWN,
                  "Unable to merge remote and local changes due to missing merge client."));
            }
            FileInputStream local = new FileInputStream(mergeFiles.getLocalFile());
            FileInputStream remote = new FileInputStream(mergeFiles.getRepositoryFile());
            FileInputStream base = new FileInputStream(mergeFiles.getBaseFile());
            this.mergeClient.get().merge(local, remote, base, fos);
          }
          choice = SVNConflictChoice.MERGED;
          mergedFile = mergeFiles.getResultFile();
          break;
        default:
          choice = SVNConflictChoice.POSTPONE;
          mergedFile = null;
          break;
      }
      return new SVNConflictResult(choice, mergedFile);
    } catch (FileNotFoundException e) {
      throw new SVNException(
          SVNErrorMessage.create(SVNErrorCode.IO_WRITE_ERROR, "Could not write to result file while merging."), e);
    } finally {
      try {
        Closeables.close(fos, true);
      } catch (IOException e) {
        // exception is shallowed
      }
    }
  }
}
