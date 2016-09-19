package com.itemis.maven.plugins.unleash.scm.providers.util;

import java.io.File;
import java.util.Collection;
import java.util.Set;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.itemis.maven.plugins.unleash.scm.ScmException;
import com.itemis.maven.plugins.unleash.scm.ScmOperation;
import com.itemis.maven.plugins.unleash.scm.merge.MergeClient;
import com.itemis.maven.plugins.unleash.scm.merge.MergeStrategy;
import com.itemis.maven.plugins.unleash.scm.providers.util.collection.FileToWCRelativePath;
import com.itemis.maven.plugins.unleash.scm.providers.util.collection.SVNStatusTypeMapEntryFilter;
import com.itemis.maven.plugins.unleash.scm.requests.UpdateRequest;
import com.itemis.maven.plugins.unleash.scm.requests.UpdateRequest.Builder;

public class SVNUtil {
  private SVNClientManager clientManager;
  private File workingDir;

  public SVNUtil(SVNClientManager clientManager, File workingDir) {
    this.clientManager = clientManager;
    this.workingDir = workingDir;
  }

  public String getCurrentConnectionUrl() throws ScmException {
    SVNWCClient wcClient = this.clientManager.getWCClient();
    try {
      SVNInfo info = wcClient.doInfo(this.workingDir, SVNRevision.WORKING);
      return info.getURL().toString();
    } catch (SVNException e) {
      throw new ScmException(ScmOperation.INFO,
          "An error occurred while retrieving the SVN URL of the current working copy.", e);
    }
  }

  public Collection<File> getFilesToCommit(boolean addUnversioned, Set<String> includeOnly) {
    Multimap<SVNStatusType, File> stati = getFileStatiFromWorkingCopy(includeOnly);

    if (addUnversioned) {
      try {
        // first step: add all unversioned files
        Collection<File> unversionedFiles = Multimaps.filterEntries(stati,
            new SVNStatusTypeMapEntryFilter(SVNStatusType.STATUS_UNVERSIONED, SVNStatusType.STATUS_NONE)).values();
        if (!unversionedFiles.isEmpty()) {
          SVNWCClient wcClient = this.clientManager.getWCClient();
          wcClient.doAdd(unversionedFiles.toArray(new File[unversionedFiles.size()]), true, false, true,
              SVNDepth.INFINITY, true, false, true);
        }
      } catch (SVNException e) {
        throw new ScmException(ScmOperation.COMMIT,
            "An error occurred while adding unversioned files to version control for commiting them.", e);
      }
    }

    // second step: get all modified and added files as well as all unversioned ones (are now added after step one)
    // then commit these files!
    SVNStatusTypeMapEntryFilter filter;
    if (addUnversioned) {
      filter = new SVNStatusTypeMapEntryFilter(SVNStatusType.STATUS_MODIFIED, SVNStatusType.STATUS_ADDED,
          SVNStatusType.STATUS_UNVERSIONED, SVNStatusType.STATUS_NONE);
    } else {
      filter = new SVNStatusTypeMapEntryFilter(SVNStatusType.STATUS_MODIFIED, SVNStatusType.STATUS_ADDED);
    }
    Collection<File> filesToCommit = Multimaps.filterEntries(stati, filter).values();

    return filesToCommit;
  }

  public Multimap<SVNStatusType, File> getFileStatiFromWorkingCopy(final Set<String> onlyIncludeRelativePaths) {
    final Multimap<SVNStatusType, File> stati = HashMultimap.create();
    try {
      this.clientManager.getStatusClient().doStatus(this.workingDir, SVNRevision.WORKING, SVNDepth.INFINITY, false,
          true, false, false, new ISVNStatusHandler() {
            @Override
            public void handleStatus(SVNStatus status) throws SVNException {
              File file = status.getFile();

              boolean include = true;
              if (!onlyIncludeRelativePaths.isEmpty()) {
                String relativePath = SVNUtil.this.workingDir.toURI().relativize(file.toURI()).toString();
                if (relativePath.endsWith("/")) {
                  relativePath = relativePath.substring(0, relativePath.length() - 1);
                }
                include = onlyIncludeRelativePaths.contains(relativePath);
              }

              if (include) {
                stati.put(status.getContentsStatus(), file);
              }
            }
          }, null);
      return stati;
    } catch (SVNException e) {
      throw new ScmException(ScmOperation.STATUS, "Unable to determine status of local SVN working copy.", e);
    }
  }

  public Collection<SVNStatus> getOutdatedFiles(Collection<File> filesToCheck) {
    final SVNStatusClient statusClient = this.clientManager.getStatusClient();
    Collection<SVNStatus> fileStati = Collections2.transform(filesToCheck, new Function<File, SVNStatus>() {
      @Override
      public SVNStatus apply(File f) {
        try {
          return statusClient.doStatus(f, true);
        } catch (SVNException e) {
          throw new ScmException(ScmOperation.INFO,
              "Unable to retrieve status of file: " + new FileToWCRelativePath(SVNUtil.this.workingDir).apply(f), e);
        }
      }
    });

    return Collections2.filter(fileStati, new Predicate<SVNStatus>() {
      @Override
      public boolean apply(SVNStatus status) {
        long localRevision = status.getRevision().getNumber();
        long remoteRevision = status.getRemoteRevision().getNumber();
        return remoteRevision > localRevision;
      }
    });
  }

  public Optional<UpdateRequest> createUpdateRequestForFiles(Collection<File> filesToUpdate, SVNRevision revision,
      MergeStrategy mergeStrategy, Optional<MergeClient> mergeClient) {
    if (filesToUpdate == null || filesToUpdate.isEmpty()) {
      return Optional.absent();
    }

    String revisionString = revision.getName();
    if (revision.getNumber() > -1) {
      revisionString = String.valueOf(revision.getNumber());
    }

    Builder updateRequestBuilder = UpdateRequest.builder().toRevision(revisionString).mergeStrategy(mergeStrategy)
        .mergeClient(mergeClient.orNull());
    Collection<String> relativePaths = Collections2.transform(filesToUpdate, new FileToWCRelativePath(this.workingDir));
    updateRequestBuilder.addPaths(relativePaths.toArray(new String[relativePaths.size()]));

    return Optional.of(updateRequestBuilder.build());
  }
  
  public long getRemoteRevision(SVNURL remoteUrl) throws SVNException {
    SVNRepository repository = clientManager.getRepositoryPool().createRepository(remoteUrl, true);
    SVNDirEntry info = repository.info(".", -1);
    return info.getRevision();
  }
}
